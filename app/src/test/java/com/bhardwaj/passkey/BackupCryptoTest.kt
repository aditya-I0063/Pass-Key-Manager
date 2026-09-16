package com.bhardwaj.passkey

import com.bhardwaj.passkey.data.backup.BackupCrypto
import com.bhardwaj.passkey.data.backup.BackupFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupCryptoTest {

    private val password = "correct horse battery staple".toCharArray()

    private fun encrypt(text: String) =
        BackupCrypto.encrypt(text.toByteArray(Charsets.UTF_8), password.copyOf())

    private fun decrypt(bytes: ByteArray, pw: CharArray = password.copyOf()) =
        BackupCrypto.decrypt(bytes, pw)

    @Test
    fun `round trips content`() {
        val original = """{"schema":1,"previews":[]}"""
        val result = decrypt(encrypt(original))
        assertThat(result).isInstanceOf(BackupCrypto.DecryptResult.Success::class.java)
        val plaintext = (result as BackupCrypto.DecryptResult.Success).plaintext
        assertThat(plaintext.toString(Charsets.UTF_8)).isEqualTo(original)
    }

    @Test
    fun `round trips values that broke the old CSV format`() {
        // Commas, the literal ____ sentinel, newlines and non-ASCII all destroyed or corrupted
        // the previous plaintext CSV export.
        val awkward = "p@ss,word\nwith____sentinel\tandémoji 🔐 \"quoted\""
        val result = decrypt(encrypt(awkward)) as BackupCrypto.DecryptResult.Success
        assertThat(result.plaintext.toString(Charsets.UTF_8)).isEqualTo(awkward)
    }

    @Test
    fun `output is not readable as plaintext`() {
        val secret = "hunter2-super-secret-password"
        val encrypted = encrypt("""{"answer":"$secret"}""")
        assertThat(encrypted.toString(Charsets.ISO_8859_1)).doesNotContain(secret)
    }

    @Test
    fun `starts with the magic header`() {
        assertThat(BackupFormat.hasMagic(encrypt("x"))).isTrue()
    }

    @Test
    fun `wrong password is rejected`() {
        val result = decrypt(encrypt("secret"), "wrong password".toCharArray())
        assertThat(result).isEqualTo(BackupCrypto.DecryptResult.WrongPasswordOrCorrupt)
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = encrypt("secret")
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()
        assertThat(decrypt(encrypted)).isEqualTo(BackupCrypto.DecryptResult.WrongPasswordOrCorrupt)
    }

    @Test
    fun `tampering with the KDF parameters in the header is rejected`() {
        // The header is GCM additional authenticated data precisely so an attacker cannot
        // downgrade the KDF and still have the file verify.
        val encrypted = encrypt("secret")
        // Byte 13 is the low byte of the big-endian iteration count. Flipping a bit keeps the
        // value plausible, so this exercises the AAD check rather than the range check.
        encrypted[13] = (encrypted[13].toInt() xor 0x01).toByte()
        assertThat(decrypt(encrypted)).isEqualTo(BackupCrypto.DecryptResult.WrongPasswordOrCorrupt)
    }

    @Test
    fun `tampering with the salt in the header is rejected`() {
        val encrypted = encrypt("secret")
        encrypted[19] = (encrypted[19].toInt() xor 0xFF).toByte()  // first salt byte
        assertThat(decrypt(encrypted)).isEqualTo(BackupCrypto.DecryptResult.WrongPasswordOrCorrupt)
    }

    @Test
    fun `non-backup input is reported as unsupported rather than wrong password`() {
        val result = decrypt("category,heading,question,answer\n".toByteArray())
        assertThat(result).isInstanceOf(BackupCrypto.DecryptResult.UnsupportedFormat::class.java)
    }

    @Test
    fun `a newer format version is refused explicitly`() {
        val encrypted = encrypt("secret")
        encrypted[8] = (BackupFormat.FORMAT_VERSION + 1).toByte()
        val result = decrypt(encrypted)
        assertThat(result).isInstanceOf(BackupCrypto.DecryptResult.UnsupportedFormat::class.java)
    }

    @Test
    fun `two encryptions of the same input differ`() {
        // Random salt and nonce per file; identical output would leak that two backups match.
        assertThat(encrypt("same").contentEquals(encrypt("same"))).isFalse()
    }
}
