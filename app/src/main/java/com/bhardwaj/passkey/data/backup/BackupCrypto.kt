package com.bhardwaj.passkey.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based authenticated encryption for backup files.
 *
 * AES-256-GCM over a key derived with PBKDF2-HMAC-SHA512. Argon2id would be stronger against
 * GPU attack and is what [BackupFormat.kdfId] exists to allow; it needs a native dependency and
 * is planned alongside the 5.7.0 key work, at which point new backups get kdfId 2 and this
 * reader keeps handling kdfId 1.
 */
internal object BackupCrypto {

    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA512"

    private val secureRandom = SecureRandom()

    sealed interface DecryptResult {
        data class Success(val plaintext: ByteArray) : DecryptResult
        /**
         * A wrong password and a corrupted file are deliberately indistinguishable: GCM cannot
         * tell them apart, and pretending otherwise would leak whether a guess was "close".
         */
        data object WrongPasswordOrCorrupt : DecryptResult
        data class UnsupportedFormat(val reason: String) : DecryptResult
    }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(BackupFormat.SALT_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(BackupFormat.NONCE_BYTES).also(secureRandom::nextBytes)
        val iterations = BackupFormat.DEFAULT_PBKDF2_ITERATIONS

        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(BackupFormat.GCM_TAG_BITS, nonce))

            // The header is authenticated but not encrypted, so the KDF parameters cannot be
            // tampered with. Its ciphertextLength is known only after encryption, so build the
            // header first with the final length.
            val ciphertextLength = cipher.getOutputSize(plaintext.size)
            val header = BackupFormat.write(
                BackupFormat.Header(
                    formatVersion = BackupFormat.FORMAT_VERSION,
                    kdfId = BackupFormat.KDF_PBKDF2_HMAC_SHA512,
                    iterations = iterations,
                    memoryKiB = 0,
                    parallelism = 0,
                    salt = salt,
                    nonce = nonce,
                    ciphertextLength = ciphertextLength
                )
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            return header + ciphertext
        } finally {
            key.destroySafely()
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): DecryptResult {
        val header = BackupFormat.parse(bytes)
            ?: return DecryptResult.UnsupportedFormat("not a PassKey backup")
        if (header.formatVersion > BackupFormat.FORMAT_VERSION) {
            return DecryptResult.UnsupportedFormat(
                "made by a newer version of PassKey (format ${header.formatVersion})"
            )
        }
        if (header.kdfId != BackupFormat.KDF_PBKDF2_HMAC_SHA512) {
            return DecryptResult.UnsupportedFormat("unsupported key derivation (${header.kdfId})")
        }
        if (header.iterations !in 1..10_000_000) {
            return DecryptResult.UnsupportedFormat("implausible iteration count")
        }
        val ciphertext = bytes.copyOfRange(BackupFormat.HEADER_BYTES, bytes.size)
        if (ciphertext.isEmpty()) return DecryptResult.UnsupportedFormat("truncated file")

        val key = deriveKey(password, header.salt, header.iterations)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(BackupFormat.GCM_TAG_BITS, header.nonce)
            )
            cipher.updateAAD(bytes.copyOfRange(0, BackupFormat.HEADER_BYTES))
            DecryptResult.Success(cipher.doFinal(ciphertext))
        } catch (_: AEADBadTagException) {
            DecryptResult.WrongPasswordOrCorrupt
        } catch (_: GeneralSecurityException) {
            DecryptResult.WrongPasswordOrCorrupt
        } finally {
            key.destroySafely()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val derived = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
            try {
                return SecretKeySpec(derived, "AES")
            } finally {
                Arrays.fill(derived, 0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun SecretKeySpec.destroySafely() {
        // SecretKeySpec.destroy() throws DestroyFailedException on the Android provider; the
        // key material is short-lived and unreferenced after this, so failure is not actionable.
        runCatching { destroy() }
    }
}
