package com.bhardwaj.passkey

import android.net.Uri
import com.bhardwaj.passkey.data.backup.BackupError
import com.bhardwaj.passkey.data.backup.BackupException
import com.bhardwaj.passkey.data.backup.BackupRepository
import com.bhardwaj.passkey.data.backup.ImportMode
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.totp.Base32
import com.bhardwaj.passkey.domain.totp.TotpAlgorithm
import com.bhardwaj.passkey.domain.totp.TotpConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Exercises the real export and import paths end to end, through a content URI.
 *
 * Robolectric is needed only for the ContentResolver; everything under test is the app's own
 * code. The backup file is the one copy of a vault that leaves the device, so a round trip that
 * silently drops a field is a defect nobody notices until they restore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {

    private val context: android.app.Application get() = RuntimeEnvironment.getApplication()
    private val password get() = "correct horse battery".toCharArray()

    private fun repositoryWith(vault: FakePasskeyRepository) = BackupRepository(context, vault)

    /** Captures what export writes, and hands the same bytes back to import. */
    private class BackupFile(private val context: android.app.Application) {
        val uri: Uri = Uri.parse("content://test/backup.pkbak")
        private val sink = ByteArrayOutputStream()

        fun captureWrites() {
            shadowOf(context.contentResolver).registerOutputStream(uri, sink)
        }

        fun bytes(): ByteArray = sink.toByteArray()

        fun serveForReading(bytes: ByteArray = bytes()) {
            shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())
        }
    }

    private suspend fun seedVault(vault: FakePasskeyRepository) {
        val gmail = vault.createPreview("Gmail", Category.MAILS)
        vault.createDetail(gmail, "Username", "alice@example.com", sequence = 0)
        vault.createDetail(gmail, "Password", "s3cret", sequence = 1, isSecret = true)
        vault.createTotp(
            previewId = gmail,
            label = "Gmail",
            config = TotpConfig(
                secret = Base32.decode("GEZDGNBVGY3TQOJQ")!!,
                issuer = "Google",
                algorithm = TotpAlgorithm.SHA256,
                digits = 8,
                periodSeconds = 60
            )
        )
    }

    @Test
    fun `a vault survives a round trip, secrets and authenticators included`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()

        assertThat(repositoryWith(source).export(file.uri, password).isSuccess).isTrue()

        val restored = FakePasskeyRepository()
        file.serveForReading()
        val summary = repositoryWith(restored).import(file.uri, password, ImportMode.MERGE)

        assertThat(summary.isSuccess).isTrue()
        assertThat(summary.getOrThrow().previewsAdded).isEqualTo(1)
        assertThat(summary.getOrThrow().detailsAdded).isEqualTo(2)

        val secret = restored.currentDetails().single { it.question == "Password" }
        // Lost before schema 2, which silently reset every restored entry to keyword matching.
        assertThat(secret.isSecret).isTrue()

        val authenticator = restored.currentTotps().single()
        assertThat(authenticator.config.digits).isEqualTo(8)
        assertThat(authenticator.config.periodSeconds).isEqualTo(60)
        assertThat(authenticator.config.algorithm).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(authenticator.config.secret).isEqualTo(Base32.decode("GEZDGNBVGY3TQOJQ"))
    }

    @Test
    fun `the written file is not readable as text`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()

        repositoryWith(source).export(file.uri, password)

        val raw = file.bytes().toString(Charsets.ISO_8859_1)
        assertThat(raw).startsWith("PKBACKUP")
        assertThat(raw).doesNotContain("s3cret")
        assertThat(raw).doesNotContain("alice@example.com")
        assertThat(raw).doesNotContain("Gmail")
    }

    @Test
    fun `the wrong password is refused and nothing is written`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()
        repositoryWith(source).export(file.uri, password)

        val restored = FakePasskeyRepository()
        file.serveForReading()
        val result = repositoryWith(restored)
            .import(file.uri, "wrong password".toCharArray(), ImportMode.MERGE)

        assertThat((result.exceptionOrNull() as BackupException).error)
            .isEqualTo(BackupError.WrongPasswordOrCorrupt)
        assertThat(restored.currentPreviews()).isEmpty()
    }

    @Test
    fun `a corrupt file is reported the same way as a wrong password`() = runTest {
        // Distinguishing them would tell an attacker holding the file when they had guessed the
        // password correctly but hit a damaged byte.
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()
        repositoryWith(source).export(file.uri, password)

        val damaged = file.bytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        file.serveForReading(damaged)

        val result = repositoryWith(FakePasskeyRepository())
            .import(file.uri, password, ImportMode.MERGE)
        assertThat((result.exceptionOrNull() as BackupException).error)
            .isEqualTo(BackupError.WrongPasswordOrCorrupt)
    }

    @Test
    fun `importing twice does not duplicate entries`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()
        repositoryWith(source).export(file.uri, password)
        val bytes = file.bytes()

        val restored = FakePasskeyRepository()
        file.serveForReading(bytes)
        repositoryWith(restored).import(file.uri, password, ImportMode.MERGE)
        file.serveForReading(bytes)
        val second = repositoryWith(restored).import(file.uri, password, ImportMode.MERGE)

        assertThat(restored.currentPreviews()).hasSize(1)
        assertThat(restored.currentDetails()).hasSize(2)
        assertThat(second.getOrThrow().detailsAdded).isEqualTo(0)
    }

    @Test
    fun `replace-all clears what was there before restoring`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()
        repositoryWith(source).export(file.uri, password)

        val restored = FakePasskeyRepository()
        restored.createPreview("Something else", Category.OTHERS)
        file.serveForReading()
        repositoryWith(restored).import(file.uri, password, ImportMode.REPLACE_ALL)

        assertThat(restored.currentPreviews().map { it.heading }).containsExactly("Gmail")
    }

    @Test
    fun `an encrypted file offered without a password is refused, not parsed`() = runTest {
        val source = FakePasskeyRepository()
        seedVault(source)
        val file = BackupFile(context)
        file.captureWrites()
        repositoryWith(source).export(file.uri, password)
        file.serveForReading()

        val result = repositoryWith(FakePasskeyRepository())
            .import(file.uri, password = null, mode = ImportMode.MERGE)

        assertThat((result.exceptionOrNull() as BackupException).error)
            .isEqualTo(BackupError.WrongPasswordOrCorrupt)
    }

    @Test
    fun `a legacy plaintext CSV still restores, and an unknown category becomes OTHERS`() =
        runTest {
            // The format this app itself wrote before 5.6.0. Category.valueOf used to be called
            // on this untrusted field and threw, aborting a part-finished import.
            // Column order and header line are the old exporter's own.
            val csv = "Category,Heading,Question,Answer\nNOT_A_CATEGORY,Gmail,Password,s3cret\n"
            val file = BackupFile(context)
            file.serveForReading(csv.toByteArray())

            val restored = FakePasskeyRepository()
            val result = repositoryWith(restored).import(file.uri, null, ImportMode.MERGE)

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrThrow().legacyFormat).isTrue()
            assertThat(restored.currentPreviews().single().category).isEqualTo(Category.OTHERS)
        }
}
