package com.bhardwaj.passkey.data.security

import android.content.Context
import android.os.StatFs
import com.bhardwaj.passkey.BuildConfig
import com.bhardwaj.passkey.data.backup.BackupDetail
import com.bhardwaj.passkey.data.backup.BackupPreview
import com.bhardwaj.passkey.data.local.toSqlCipherRawKey
import com.bhardwaj.passkey.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-keys a pre-5.7 database from the build-time constant to the per-install DEK.
 *
 * Uses `sqlcipher_export()` into a sidecar file rather than `PRAGMA rekey`. Rekey re-encrypts
 * every page in place with no journal-level protection across the whole operation, so an
 * interruption leaves an unopenable file. Exporting keeps the original untouched until a rename,
 * which makes the operation atomic by construction.
 */
@Singleton
class VaultMigration @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val mutex = Mutex()

    sealed interface Result {
        data object NotNeeded : Result
        data class Migrated(val previewRows: Int, val detailRows: Int) : Result
        data class Failed(val reason: FailureReason, val cause: Throwable? = null) : Result
    }

    enum class FailureReason {
        NO_LEGACY_KEY_WORKS, INSUFFICIENT_SPACE, EXPORT_FAILED, VERIFY_FAILED, SWAP_FAILED
    }

    /**
     * Passphrases to try against an un-migrated database, in order.
     *
     * "null" is a genuine candidate, not a placeholder: when PASS_PHRASE is absent from
     * local.properties, `properties.getProperty(...)` returns null and the build embeds the
     * four-character string "null" as the key.
     */
    private val legacyCandidates: List<String>
        get() = listOfNotNull(BuildConfig.LEGACY_PASS_PHRASE, "null").distinct()

    fun databaseExists(): Boolean = context.getDatabasePath(Constants.PASS_KEY_DATABASE).exists()

    suspend fun migrate(dek: ByteArray): Result = mutex.withLock {
        withContext(Dispatchers.IO) { runMigration(dek) }
    }

    private fun runMigration(dek: ByteArray): Result {
        val original = context.getDatabasePath(Constants.PASS_KEY_DATABASE)
        if (!original.exists()) return Result.NotNeeded

        // Step 0 - pre-flight. The export needs room for a full second copy.
        val stat = StatFs(original.parentFile!!.absolutePath)
        if (stat.availableBytes < original.length() * 3) {
            return Result.Failed(FailureReason.INSUFFICIENT_SPACE)
        }

        val newFile = File(original.parentFile, "${Constants.PASS_KEY_DATABASE}.new")
        val backupFile = File(original.parentFile, "${Constants.PASS_KEY_DATABASE}.bak")
        listOf(newFile, sidecar(newFile, "-wal"), sidecar(newFile, "-shm")).forEach { it.delete() }

        var userVersion = 0
        var previewCount = 0
        var detailCount = 0

        // Step 1 - open with a legacy candidate and capture what must be preserved.
        val legacy = openWithAnyLegacyKey(original)
            ?: return Result.Failed(FailureReason.NO_LEGACY_KEY_WORKS)

        try {
            userVersion = legacy.version
            previewCount = legacy.countOf(Constants.PREVIEW_TABLE)
            detailCount = legacy.countOf(Constants.DETAILS_TABLE)

            // Fold the WAL back in: stale -wal pages encrypted under the old key could otherwise
            // resurrect after the swap.
            legacy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            legacy.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }

            // Step 4 - export into a sidecar under the new key.
            val rawKey = String(dek.toSqlCipherRawKey(), Charsets.US_ASCII)
            // The target is created up front rather than letting ATTACH create it. The source
            // connection is opened OPEN_READWRITE without CREATE, so ATTACH of a non-existent
            // file fails with SQLITE_CANTOPEN.
            SQLiteDatabase.openOrCreateDatabase(
                newFile.absolutePath, dek.toSqlCipherRawKey(), null, null
            ).close()

            // The path is inlined rather than bound: SQLite does not accept bind parameters in
            // ATTACH.
            val escapedPath = newFile.absolutePath.replace("'", "''")
            legacy.execSQL("ATTACH DATABASE '$escapedPath' AS newdb KEY \"$rawKey\"")
            legacy.rawQuery("SELECT sqlcipher_export('newdb')", null).use { it.moveToFirst() }
            // THE most important statement here. sqlcipher_export copies schema and rows but NOT
            // user_version. Without this Room sees version 0 on a migrated database and either
            // re-runs migrations against migrated tables or destructively recreates them.
            legacy.execSQL("PRAGMA newdb.user_version = $userVersion")
            legacy.execSQL("DETACH DATABASE newdb")
        } catch (error: Exception) {
            newFile.delete()
            return Result.Failed(FailureReason.EXPORT_FAILED, error)
        } finally {
            runCatching { legacy.close() }
        }

        // Step 5 - verify before anything destructive happens.
        val verified = runCatching {
            SQLiteDatabase.openDatabase(
                newFile.absolutePath, dek.toSqlCipherRawKey(), null,
                SQLiteDatabase.OPEN_READONLY, null, null
            ).use { db ->
                val integrityOk = db.rawQuery("PRAGMA cipher_integrity_check", null)
                    .use { !it.moveToFirst() }
                integrityOk &&
                    db.countOf(Constants.PREVIEW_TABLE) == previewCount &&
                    db.countOf(Constants.DETAILS_TABLE) == detailCount &&
                    db.version == userVersion
            }
        }.getOrDefault(false)

        if (!verified) {
            newFile.delete()
            return Result.Failed(FailureReason.VERIFY_FAILED)
        }

        // Step 6 - swap. The original is only renamed aside, never deleted before this point.
        return runCatching {
            backupFile.delete()
            check(original.renameTo(backupFile)) { "could not move the original aside" }
            check(newFile.renameTo(original)) { "could not move the re-keyed database into place" }
            listOf("-wal", "-shm", "-journal").forEach { sidecar(original, it).delete() }
            Result.Migrated(previewCount, detailCount)
        }.getOrElse { Result.Failed(FailureReason.SWAP_FAILED, it) }
    }

    /** Called only once the new key has been confirmed to work end to end. */
    fun discardBackup() {
        val original = context.getDatabasePath(Constants.PASS_KEY_DATABASE)
        File(original.parentFile, "${Constants.PASS_KEY_DATABASE}.bak").delete()
    }

    /**
     * Reads the un-migrated vault straight through the legacy key.
     *
     * This is what makes a failed migration recoverable rather than a dead end: the original
     * database is untouched by a failed export, so its contents can still be written out to an
     * encrypted backup the user controls.
     */
    internal suspend fun readLegacyVault(): List<BackupPreview>? = withContext(Dispatchers.IO) {
        val original = context.getDatabasePath(Constants.PASS_KEY_DATABASE)
        if (!original.exists()) return@withContext null
        val db = openWithAnyLegacyKey(original) ?: return@withContext null
        try {
            val details = mutableMapOf<Long, MutableList<BackupDetail>>()
            db.rawQuery(
                "SELECT previewId, question, answer, sequence FROM ${Constants.DETAILS_TABLE}",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    details.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(
                        BackupDetail(
                            question = cursor.getString(1),
                            answer = cursor.getString(2),
                            sequence = cursor.getLong(3)
                        )
                    )
                }
            }
            db.rawQuery(
                "SELECT previewId, heading, categoryName, sequence FROM ${Constants.PREVIEW_TABLE}",
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            BackupPreview(
                                heading = cursor.getString(1),
                                categoryName = cursor.getString(2),
                                sequence = cursor.getLong(3),
                                details = details[cursor.getLong(0)].orEmpty()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * Still openable with the legacy key, which is what lets the failure screen offer an export
     * even when the migration cannot complete.
     */
    fun canOpenWithLegacyKey(): Boolean {
        val original = context.getDatabasePath(Constants.PASS_KEY_DATABASE)
        if (!original.exists()) return false
        val db = openWithAnyLegacyKey(original) ?: return false
        runCatching { db.close() }
        return true
    }

    private fun openWithAnyLegacyKey(file: File): SQLiteDatabase? {
        for (candidate in legacyCandidates) {
            val db = runCatching {
                SQLiteDatabase.openDatabase(
                    file.absolutePath, candidate.toByteArray(), null,
                    SQLiteDatabase.OPEN_READWRITE, null, null
                )
            }.getOrNull()
            if (db != null) {
                val usable = runCatching {
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                }.getOrDefault(false)
                if (usable) return db
                runCatching { db.close() }
            }
        }
        return null
    }

    private fun SQLiteDatabase.countOf(table: String): Int =
        rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun sidecar(file: File, suffix: String) = File(file.absolutePath + suffix)
}
