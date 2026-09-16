package com.bhardwaj.passkey.data.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters used to turn a user password into a key-wrapping key.
 *
 * Stored alongside the wrapped key so parameters can be raised later without breaking existing
 * slots: an old slot keeps its own spec and is re-wrapped with the current one next time the
 * password is verified.
 */
@Serializable
sealed interface KdfSpec {

    val saltB64: String

    @Serializable
    @SerialName("argon2id")
    data class Argon2id(
        override val saltB64: String,
        val memoryKiB: Int = DEFAULT_MEMORY_KIB,
        val iterations: Int = DEFAULT_ITERATIONS,
        val parallelism: Int = DEFAULT_PARALLELISM
    ) : KdfSpec {
        companion object {
            /**
             * Tuned to stay usable on low-end devices: 32 MiB is a compromise, since the vault
             * has to unlock on whatever phone the user has. Backup files, which leave the
             * device and face offline attack, use higher settings.
             */
            const val DEFAULT_MEMORY_KIB = 32 * 1024
            const val DEFAULT_ITERATIONS = 3
            const val DEFAULT_PARALLELISM = 2
        }
    }

    @Serializable
    @SerialName("pbkdf2-hmac-sha512")
    data class Pbkdf2HmacSha512(
        override val saltB64: String,
        val iterations: Int = DEFAULT_ITERATIONS
    ) : KdfSpec {
        companion object {
            const val DEFAULT_ITERATIONS = 600_000
        }
    }
}
