package com.bhardwaj.passkey.data.backup

import android.content.Context
import android.net.Uri
import com.bhardwaj.passkey.BuildConfig
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.utils.Categories
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Arrays
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import java.io.ByteArrayOutputStream

/** How an import should treat entries already in the vault. */
enum class ImportMode { MERGE, REPLACE_ALL }

data class ImportSummary(
    val previewsAdded: Int,
    val detailsAdded: Int,
    val skipped: Int,
    val legacyFormat: Boolean
)

sealed interface BackupError {
    data object WrongPasswordOrCorrupt : BackupError
    data class UnsupportedVersion(val reason: String) : BackupError
    data object FileTooLarge : BackupError
    data object SchemaInvalid : BackupError
    data object ReadFailed : BackupError
    data object WriteFailed : BackupError
}

@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PasskeyRepository
) {
    private companion object {
        /** A vault large enough to exceed this is not plausible; a hostile file is. */
        const val MAX_FILE_BYTES = 32 * 1024 * 1024
        const val MAX_LEGACY_CHARS = 8 * 1024 * 1024
    }

    private val json = Json { ignoreUnknownKeys = false }

    suspend fun export(uri: Uri, password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        val previews = runCatching {
            repository.getPreviews().first().map { preview ->
                BackupPreview(
                    heading = preview.heading,
                    categoryName = preview.categoryName.name,
                    sequence = preview.sequence,
                    details = repository.getDetailsByPreviewId(preview.previewId!!).first()
                        .map { BackupDetail(it.question, it.answer, it.sequence) }
                )
            }
        }.getOrElse { return@withContext Result.failure(BackupException(BackupError.ReadFailed)) }
        writeEncrypted(uri, password, previews)
    }

    /**
     * Writes a backup assembled outside the normal repository path.
     *
     * Used by the migration failure screen, which reads the un-migrated vault through the legacy
     * key so a user whose re-key could not complete can still get their data out.
     */
    internal suspend fun exportPreviews(
        uri: Uri,
        password: CharArray,
        previews: List<BackupPreview>
    ): Result<Unit> = withContext(Dispatchers.IO) { writeEncrypted(uri, password, previews) }

    private fun writeEncrypted(
        uri: Uri,
        password: CharArray,
        previews: List<BackupPreview>
    ): Result<Unit> = try {
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            previews = previews
        )
        val plaintext = gzip(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
        val encrypted = try {
            BackupCrypto.encrypt(plaintext, password)
        } finally {
            Arrays.fill(plaintext, 0)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
            ?: return Result.failure(BackupException(BackupError.WriteFailed))
        Result.success(Unit)
    } catch (_: Exception) {
        Result.failure(BackupException(BackupError.WriteFailed))
    }

    suspend fun import(
        uri: Uri,
        password: CharArray?,
        mode: ImportMode
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val bytes = readLimited(uri)
            ?: return@withContext Result.failure(BackupException(BackupError.ReadFailed))
        if (bytes.size > MAX_FILE_BYTES) {
            return@withContext Result.failure(BackupException(BackupError.FileTooLarge))
        }

        // Magic-byte sniffing, not the file extension: the old importer matched on a filename
        // ending in "passkey" and would have rejected every .pkbak outright.
        val isEncrypted = BackupFormat.hasMagic(bytes)
        val parsed: Pair<List<BackupPreview>, Int> = if (isEncrypted) {
            if (password == null) {
                return@withContext Result.failure(
                    BackupException(BackupError.WrongPasswordOrCorrupt)
                )
            }
            when (val result = BackupCrypto.decrypt(bytes, password)) {
                is BackupCrypto.DecryptResult.Success -> {
                    val decoded = runCatching {
                        json.decodeFromString<BackupPayload>(
                            gunzip(result.plaintext).toString(Charsets.UTF_8)
                        )
                    }.getOrElse {
                        return@withContext Result.failure(
                            BackupException(BackupError.SchemaInvalid)
                        )
                    }
                    Arrays.fill(result.plaintext, 0)
                    if (decoded.schema > BackupPayload.SCHEMA_VERSION ||
                        decoded.previews.size > BackupPayload.MAX_PREVIEWS
                    ) {
                        return@withContext Result.failure(
                            BackupException(BackupError.SchemaInvalid)
                        )
                    }
                    decoded.previews to 0
                }

                BackupCrypto.DecryptResult.WrongPasswordOrCorrupt ->
                    return@withContext Result.failure(
                        BackupException(BackupError.WrongPasswordOrCorrupt)
                    )

                is BackupCrypto.DecryptResult.UnsupportedFormat ->
                    return@withContext Result.failure(
                        BackupException(BackupError.UnsupportedVersion(result.reason))
                    )
            }
        } else {
            if (bytes.size > MAX_LEGACY_CHARS) {
                return@withContext Result.failure(BackupException(BackupError.FileTooLarge))
            }
            val legacy = LegacyCsvBackup.parse(bytes.toString(Charsets.UTF_8))
            legacy.previews to legacy.skippedRows
        }

        val (previews, skippedFromParse) = parsed
        try {
            var previewsAdded = 0
            var detailsAdded = 0
            var skipped = skippedFromParse

            // One transaction: a bad row part-way through must not leave a half-restored vault.
            repository.runInTransaction {
                if (mode == ImportMode.REPLACE_ALL) repository.deleteAll()

                previews.forEach { backupPreview ->
                    val heading = backupPreview.heading.trim()
                    if (heading.isEmpty() || heading.length > BackupPayload.MAX_HEADING_CHARS) {
                        skipped++
                        return@forEach
                    }
                    val category = backupPreview.categoryName.toCategoryOrOther()
                    val existing = repository.getPreviewByHeading(heading, category.name)
                    val previewId = existing?.previewId ?: repository.upsertPreview(
                        Preview(
                            heading = heading,
                            categoryName = category,
                            sequence = backupPreview.sequence
                        )
                    ).also { previewsAdded++ }

                    backupPreview.details.forEach { detail ->
                        val question = detail.question.trim()
                        val answer = detail.answer
                        if (question.isEmpty() ||
                            question.length > BackupPayload.MAX_QUESTION_CHARS ||
                            answer.length > BackupPayload.MAX_ANSWER_CHARS
                        ) {
                            skipped++
                            return@forEach
                        }
                        if (repository.getDetailByContent(previewId, question, answer) == null) {
                            repository.upsertDetails(
                                Details(
                                    previewId = previewId,
                                    question = question,
                                    answer = answer,
                                    sequence = detail.sequence
                                )
                            )
                            detailsAdded++
                        }
                    }
                }
            }
            Result.success(
                ImportSummary(
                    previewsAdded = previewsAdded,
                    detailsAdded = detailsAdded,
                    skipped = skipped,
                    legacyFormat = !isEncrypted
                )
            )
        } catch (_: Exception) {
            Result.failure(BackupException(BackupError.SchemaInvalid))
        }
    }

    private fun readLimited(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_FILE_BYTES) return@use ByteArray(MAX_FILE_BYTES + 1)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}

class BackupException(val error: BackupError) : Exception(error.toString())

/**
 * Never [Categories.valueOf]: it throws on unknown input, and the previous importer let that
 * escape into a broad catch that silently abandoned the rest of the file.
 */
internal fun String.toCategoryOrOther(): Categories =
    Categories.entries.firstOrNull { it.name.equals(this.trim(), ignoreCase = true) }
        ?: Categories.OTHERS
