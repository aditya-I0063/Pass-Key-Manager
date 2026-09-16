package com.bhardwaj.passkey

import com.bhardwaj.passkey.data.backup.BackupDetail
import com.bhardwaj.passkey.data.backup.BackupPayload
import com.bhardwaj.passkey.data.backup.BackupPreview
import com.bhardwaj.passkey.data.backup.BackupTotp
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The backup file is the only copy of a vault that leaves the device, so what it does and does
 * not carry is worth asserting rather than assuming.
 */
class BackupPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a schema 2 payload round-trips with its secrets and authenticators`() {
        val payload = BackupPayload(
            exportedAt = 1_700_000_000_000L,
            appVersion = "5.8.0",
            previews = listOf(
                BackupPreview(
                    heading = "Bank",
                    categoryName = "BANKS",
                    details = listOf(BackupDetail("Password", "s3cret", 0, isSecret = true)),
                    totp = listOf(
                        BackupTotp(
                            label = "Bank",
                            secret = "GEZDGNBVGY3TQOJQ",
                            issuer = "Bank",
                            algorithm = "SHA256",
                            digits = 8,
                            periodSeconds = 60
                        )
                    )
                )
            )
        )

        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertThat(decoded).isEqualTo(payload)
        assertThat(decoded.schema).isEqualTo(2)
        assertThat(decoded.previews.single().details.single().isSecret).isTrue()
        assertThat(decoded.previews.single().totp.single().digits).isEqualTo(8)
    }

    @Test
    fun `a schema 1 file written by 5_6 still restores`() {
        // The two fields added in schema 2 are optional with defaults, so an older backup
        // decodes rather than failing and being reported to the user as a wrong password.
        val v1 = """
            {"schema":1,"exportedAt":1700000000000,"appVersion":"5.6.0","previews":[
              {"heading":"Gmail","categoryName":"MAILS","sequence":0,
               "details":[{"question":"Password","answer":"s3cret","sequence":0}]}
            ]}
        """.trimIndent()

        val decoded = json.decodeFromString<BackupPayload>(v1)

        assertThat(decoded.previews.single().heading).isEqualTo("Gmail")
        assertThat(decoded.previews.single().details.single().isSecret).isFalse()
        assertThat(decoded.previews.single().totp).isEmpty()
    }

    @Test
    fun `nothing install-local crosses the file boundary`() {
        // Details nest under their preview rather than carrying previewId, so restoring into a
        // different install cannot collide with ids that already exist there.
        val encoded = json.encodeToString(
            BackupPayload(
                exportedAt = 0L,
                appVersion = "5.8.0",
                previews = listOf(
                    BackupPreview(
                        heading = "Gmail",
                        categoryName = "MAILS",
                        details = listOf(BackupDetail("Password", "s3cret"))
                    )
                )
            )
        )

        assertThat(encoded).doesNotContain("previewId")
        assertThat(encoded).doesNotContain("detailsId")
    }
}
