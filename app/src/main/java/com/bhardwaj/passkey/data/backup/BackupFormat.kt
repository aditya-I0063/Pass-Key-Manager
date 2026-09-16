package com.bhardwaj.passkey.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary layout of a `.pkbak` file.
 *
 * ```
 * off  len  field
 *   0    8  magic "PKBACKUP"
 *   8    1  formatVersion
 *   9    1  kdfId            1 = PBKDF2-HMAC-SHA512
 *  10    4  kdfIterations    (big endian)
 *  14    4  kdfMemoryKiB     (big endian, 0 for PBKDF2)
 *  18    1  kdfParallelism   (0 for PBKDF2)
 *  19   16  salt
 *  35   12  nonce (GCM IV)
 *  47    4  ciphertextLength (big endian)
 *  51    N  AES-256-GCM ciphertext with the 128-bit tag appended
 * ```
 *
 * The whole 51-byte header is fed to GCM as additional authenticated data, so the KDF
 * identifier and its parameters are covered by the auth tag. Without that, an attacker could
 * rewrite `kdfId` to point at a weaker KDF and the file would still verify.
 */
internal object BackupFormat {

    val MAGIC = "PKBACKUP".toByteArray(Charsets.US_ASCII)

    /**
     * 3 carries payload schema 2 (isSecret and authenticators). Bumped alongside the payload so
     * an older build says "made by a newer version of PassKey" instead of failing to parse an
     * unknown key and blaming the password.
     */
    const val FORMAT_VERSION = 3
    const val KDF_PBKDF2_HMAC_SHA512 = 1

    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val HEADER_BYTES = 51

    /** 600k iterations of PBKDF2-HMAC-SHA512, the current OWASP guidance for this primitive. */
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000

    data class Header(
        val formatVersion: Int,
        val kdfId: Int,
        val iterations: Int,
        val memoryKiB: Int,
        val parallelism: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertextLength: Int
    ) {
        // Generated equals/hashCode would compare the arrays by identity.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Header) return false
            return formatVersion == other.formatVersion &&
                kdfId == other.kdfId &&
                iterations == other.iterations &&
                memoryKiB == other.memoryKiB &&
                parallelism == other.parallelism &&
                salt.contentEquals(other.salt) &&
                nonce.contentEquals(other.nonce) &&
                ciphertextLength == other.ciphertextLength
        }

        override fun hashCode(): Int {
            var result = formatVersion
            result = 31 * result + kdfId
            result = 31 * result + iterations
            result = 31 * result + memoryKiB
            result = 31 * result + parallelism
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + ciphertextLength
            return result
        }
    }

    fun write(header: Header): ByteArray {
        require(header.salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        require(header.nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC)
            put(header.formatVersion.toByte())
            put(header.kdfId.toByte())
            putInt(header.iterations)
            putInt(header.memoryKiB)
            put(header.parallelism.toByte())
            put(header.salt)
            put(header.nonce)
            putInt(header.ciphertextLength)
        }.array()
    }

    /** Returns null when [bytes] is not a PassKey backup at all, so callers can fall back. */
    fun parse(bytes: ByteArray): Header? {
        if (bytes.size < HEADER_BYTES) return null
        if (!hasMagic(bytes)) return null
        val buffer = ByteBuffer.wrap(bytes, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
        buffer.position(MAGIC.size)
        val formatVersion = buffer.get().toInt()
        val kdfId = buffer.get().toInt()
        val iterations = buffer.getInt()
        val memoryKiB = buffer.getInt()
        val parallelism = buffer.get().toInt()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        val ciphertextLength = buffer.getInt()
        return Header(
            formatVersion = formatVersion,
            kdfId = kdfId,
            iterations = iterations,
            memoryKiB = memoryKiB,
            parallelism = parallelism,
            salt = salt,
            nonce = nonce,
            ciphertextLength = ciphertextLength
        )
    }

    /** Cheap sniff used to choose between the encrypted format and the legacy CSV reader. */
    fun hasMagic(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        return MAGIC.indices.all { bytes[it] == MAGIC[it] }
    }
}
