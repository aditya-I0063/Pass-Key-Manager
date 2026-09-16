package com.bhardwaj.passkey.data.backup

import kotlinx.serialization.Serializable

/**
 * The decrypted contents of a backup.
 *
 * Detail are nested under their preview rather than carrying `previewId`, so no install-local
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
        /**
         * 2 adds `isSecret` and `totp`. A v1 file still restores: both are optional with
         * defaults, and a v1 reader is kept out by the *format* version rather than being left
         * to fail on an unknown key and report "wrong password".
         */
        const val SCHEMA_VERSION = 2

        /** Guards against a hostile file exhausting memory. */
        const val MAX_PREVIEWS = 10_000
        const val MAX_HEADING_CHARS = 256
        const val MAX_QUESTION_CHARS = 256
        const val MAX_ANSWER_CHARS = 4_096
        const val MAX_SECRET_CHARS = 512
    }
}

@Serializable
internal data class BackupPreview(
    val heading: String,
    val categoryName: String,
    val sequence: Long = 0,
    val details: List<BackupDetail> = emptyList(),
    val totp: List<BackupTotp> = emptyList()
)

@Serializable
internal data class BackupDetail(
    val question: String,
    val answer: String,
    val sequence: Long = 0,
    /**
     * Carried since schema 2. Without it a restore reset every entry to keyword matching, which
     * classifies nothing outside English - the exact bug isSecret exists to fix.
     */
    val isSecret: Boolean = false
)

/** The Base32 secret, as printed, plus the parameters needed to reproduce the same codes. */
@Serializable
internal data class BackupTotp(
    val label: String,
    val secret: String,
    val issuer: String? = null,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val periodSeconds: Int = 30
)
