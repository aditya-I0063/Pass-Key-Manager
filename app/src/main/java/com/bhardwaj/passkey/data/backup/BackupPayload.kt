package com.bhardwaj.passkey.data.backup

import kotlinx.serialization.Serializable

/**
 * The decrypted contents of a backup.
 *
 * Details are nested under their preview rather than carrying `previewId`, so no install-local
 * row id crosses the file boundary and a restore into a different install cannot collide.
 */
@Serializable
internal data class BackupPayload(
    val schema: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val previews: List<BackupPreview>
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /** Guards against a hostile file exhausting memory. */
        const val MAX_PREVIEWS = 10_000
        const val MAX_HEADING_CHARS = 256
        const val MAX_QUESTION_CHARS = 256
        const val MAX_ANSWER_CHARS = 4_096
    }
}

@Serializable
internal data class BackupPreview(
    val heading: String,
    val categoryName: String,
    val sequence: Long = 0,
    val details: List<BackupDetail> = emptyList()
)

@Serializable
internal data class BackupDetail(
    val question: String,
    val answer: String,
    val sequence: Long = 0
)
