package com.bhardwaj.passkey.data.security

import android.util.Base64
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Derives a 256-bit key-wrapping key from a user password. */
interface KeyDerivation {
    fun derive(password: CharArray, spec: KdfSpec): ByteArray
    fun newSalt(): ByteArray
}

@Singleton
class DefaultKeyDerivation @Inject constructor() : KeyDerivation {

    private val secureRandom = SecureRandom()
    private val argon2 by lazy { Argon2Kt() }

    override fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)

    override fun derive(password: CharArray, spec: KdfSpec): ByteArray {
        val salt = Base64.decode(spec.saltB64, Base64.NO_WRAP)
        return when (spec) {
            is KdfSpec.Argon2id -> deriveArgon2(password, salt, spec)
            is KdfSpec.Pbkdf2HmacSha512 -> derivePbkdf2(password, salt, spec.iterations)
        }
    }

    private fun deriveArgon2(
        password: CharArray,
        salt: ByteArray,
        spec: KdfSpec.Argon2id
    ): ByteArray {
        val passwordBytes = password.toUtf8Bytes()
        try {
            return argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = salt,
                tCostInIterations = spec.iterations,
                mCostInKibibyte = spec.memoryKiB,
                parallelism = spec.parallelism,
                hashLengthInBytes = KEY_BYTES
            ).rawHashAsByteArray()
        } finally {
            Arrays.fill(passwordBytes, 0)
        }
    }

    private fun derivePbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                .generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    /** Avoids String, which would leave an immutable copy of the password on the heap. */
    private fun CharArray.toUtf8Bytes(): ByteArray {
        val buffer = java.nio.CharBuffer.wrap(this)
        val encoded = Charsets.UTF_8.encode(buffer)
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        return bytes
    }

    private companion object {
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
    }
}
