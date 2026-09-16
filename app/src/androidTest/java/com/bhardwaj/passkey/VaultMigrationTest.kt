package com.bhardwaj.passkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bhardwaj.passkey.data.local.toSqlCipherRawKey
import com.bhardwaj.passkey.data.security.VaultMigration
import com.bhardwaj.passkey.utils.Constants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/**
 * Covers the one-time re-key from the legacy build-time passphrase to a per-install key.
 *
 * This is the highest-risk operation in the app: it rewrites the file every user's passwords
 * live in. The assertions below are deliberately about the properties that would cause silent
 * data loss rather than a visible error.
 */
@RunWith(AndroidJUnit4::class)
class VaultMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File get() = context.getDatabasePath(Constants.PASS_KEY_DATABASE)

    /** Matches what a build with no PASS_PHRASE in local.properties embeds. */
    private val legacyKey = BuildConfig.LEGACY_PASS_PHRASE.toByteArray()

    private lateinit var migration: VaultMigration

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        migration = VaultMigration(context)
        deleteDatabaseFiles()
    }

    @After
    fun tearDown() = deleteDatabaseFiles()

    private fun deleteDatabaseFiles() {
        dbFile.parentFile?.listFiles()
            ?.filter { it.name.startsWith(Constants.PASS_KEY_DATABASE) }
            ?.forEach { it.delete() }
    }

    private fun newDek() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** Builds a database shaped like one produced by the shipped 5.5.x app. */
    private fun createLegacyDatabase(entries: Int = 3, userVersion: Int = 3) {
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, legacyKey, null, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ${Constants.PREVIEW_TABLE} (" +
                "previewId INTEGER PRIMARY KEY AUTOINCREMENT, heading TEXT NOT NULL, " +
                "categoryName TEXT NOT NULL, sequence INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ${Constants.DETAILS_TABLE} (" +
                "detailsId INTEGER PRIMARY KEY AUTOINCREMENT, previewId INTEGER NOT NULL, " +
                "question TEXT NOT NULL, answer TEXT NOT NULL, " +
                "sequence INTEGER NOT NULL DEFAULT 0, isSecret INTEGER NOT NULL DEFAULT 0)"
        )
        repeat(entries) { index ->
            db.execSQL(
                "INSERT INTO ${Constants.PREVIEW_TABLE} (heading, categoryName, sequence) " +
                    "VALUES ('Entry $index', 'BANKS', $index)"
            )
            db.execSQL(
                "INSERT INTO ${Constants.DETAILS_TABLE} " +
                    "(previewId, question, answer, sequence, isSecret) " +
                    "VALUES (${index + 1}, 'Password', 'secret-value-$index', 0, 1)"
            )
        }
        db.version = userVersion
        db.close()
    }

    private fun <T> openWith(key: ByteArray, block: (SQLiteDatabase) -> T): T? = runCatching {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null, null
        ).use(block)
    }.getOrNull()

    @Test
    fun migration_preserves_every_row() {
        createLegacyDatabase(entries = 25)
        val dek = newDek()

        val result = runBlocking { migration.migrate(dek) }

        assertThat(result).isInstanceOf(VaultMigration.Result.Migrated::class.java)
        val migrated = result as VaultMigration.Result.Migrated
        assertThat(migrated.previewRows).isEqualTo(25)
        assertThat(migrated.detailRows).isEqualTo(25)

        val answers = openWith(dek.toSqlCipherRawKey()) { db ->
            db.rawQuery("SELECT answer FROM ${Constants.DETAILS_TABLE} ORDER BY detailsId", null)
                .use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
        }
        assertThat(answers).hasSize(25)
        assertThat(answers).contains("secret-value-0")
        assertThat(answers).contains("secret-value-24")
    }

    @Test
    fun migration_preserves_user_version() {
        // The single highest-severity failure mode in this release. sqlcipher_export copies
        // schema and rows but NOT user_version; if it is lost, Room sees version 0 on an
        // already-migrated database and either re-runs migrations against migrated tables or
        // destructively recreates them, depending on configuration.
        createLegacyDatabase(userVersion = 3)
        val dek = newDek()

        runBlocking { migration.migrate(dek) }

        val version = openWith(dek.toSqlCipherRawKey()) { it.version }
        assertThat(version).isEqualTo(3)
    }

    @Test
    fun migrated_database_no_longer_opens_with_the_legacy_key() {
        createLegacyDatabase()
        val dek = newDek()

        runBlocking { migration.migrate(dek) }

        assertThat(openWith(legacyKey) { it.version }).isNull()
        assertThat(openWith(dek.toSqlCipherRawKey()) { it.version }).isNotNull()
    }

    @Test
    fun migration_is_idempotent_when_rerun_with_the_same_key() {
        createLegacyDatabase(entries = 5)
        val dek = newDek()
        runBlocking { migration.migrate(dek) }

        // A second pass finds a database the legacy keys cannot open, and must fail loudly
        // rather than destroying it.
        val second = runBlocking { migration.migrate(dek) }
        assertThat(second).isInstanceOf(VaultMigration.Result.Failed::class.java)
        assertThat((second as VaultMigration.Result.Failed).reason)
            .isEqualTo(VaultMigration.FailureReason.NO_LEGACY_KEY_WORKS)

        // The data is still there and still readable under the new key.
        val count = openWith(dek.toSqlCipherRawKey()) { db ->
            db.rawQuery("SELECT count(*) FROM ${Constants.PREVIEW_TABLE}", null)
                .use { if (it.moveToFirst()) it.getInt(0) else -1 }
        }
        assertThat(count).isEqualTo(5)
    }

    @Test
    fun migration_leaves_the_original_intact_when_it_cannot_run() {
        createLegacyDatabase(entries = 4)

        // Block the export by occupying the sidecar path with a non-empty directory, which the
        // migration cannot delete or write through. This is the property that matters: the
        // original database must survive a failed export untouched.
        val blocker = File(dbFile.parentFile, "${Constants.PASS_KEY_DATABASE}.new")
        blocker.mkdirs()
        File(blocker, "occupied").writeText("x")

        val result = runBlocking { migration.migrate(newDek()) }
        blocker.deleteRecursively()

        assertThat(result).isInstanceOf(VaultMigration.Result.Failed::class.java)
        // The legacy database must still open, which is what lets the failure screen offer
        // an export rather than stranding the user.
        assertThat(migration.canOpenWithLegacyKey()).isTrue()
        val count = openWith(legacyKey) { db ->
            db.rawQuery("SELECT count(*) FROM ${Constants.PREVIEW_TABLE}", null)
                .use { if (it.moveToFirst()) it.getInt(0) else -1 }
        }
        assertThat(count).isEqualTo(4)
    }

    @Test
    fun no_database_is_not_an_error() {
        assertThat(runBlocking { migration.migrate(newDek()) })
            .isEqualTo(VaultMigration.Result.NotNeeded)
    }
}
