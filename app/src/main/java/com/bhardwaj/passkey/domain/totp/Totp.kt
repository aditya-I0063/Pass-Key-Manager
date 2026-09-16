package com.bhardwaj.passkey.domain.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class TotpAlgorithm(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromNameOrDefault(name: String?): TotpAlgorithm =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: SHA1
    }
}

/**
 * One authenticator entry, exactly as an `otpauth://` URI describes it.
 *
 * [secret] is the decoded key, not the Base32 text: nothing above this layer should have to
 * know how it was encoded, and the encoded form is another copy of the secret to keep track of.
 */
data class TotpConfig(
    val secret: ByteArray,
    val issuer: String? = null,
    val account: String? = null,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = DEFAULT_DIGITS,
    val periodSeconds: Int = DEFAULT_PERIOD_SECONDS
) {
    override fun equals(other: Any?): Boolean = this === other || (
        other is TotpConfig &&
            secret.contentEquals(other.secret) &&
            issuer == other.issuer &&
            account == other.account &&
            algorithm == other.algorithm &&
            digits == other.digits &&
            periodSeconds == other.periodSeconds
        )

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + (issuer?.hashCode() ?: 0)
        result = 31 * result + (account?.hashCode() ?: 0)
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        return 31 * result + periodSeconds
    }

    companion object {
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_PERIOD_SECONDS = 30
        val DIGIT_RANGE = 6..8
        val PERIOD_RANGE = 15..120
    }
}

/**
 * RFC 6238 time-based one-time passwords.
 *
 * Entirely offline and entirely standard: the same counter, HMAC and dynamic truncation every
 * authenticator implements, so a code generated here matches the one a phone across the room
 * shows for the same secret.
 */
object TotpGenerator {

    /** [epochSeconds] is a parameter rather than a clock read, which is what makes this testable. */
    fun generate(config: TotpConfig, epochSeconds: Long): String {
        val counter = epochSeconds / config.periodSeconds
        return generate(config.secret, counter, config.algorithm, config.digits)
    }

    fun secondsRemaining(config: TotpConfig, epochSeconds: Long): Int =
        (config.periodSeconds - epochSeconds.mod(config.periodSeconds.toLong())).toInt()

    private fun generate(
        secret: ByteArray,
        counter: Long,
        algorithm: TotpAlgorithm,
        digits: Int
    ): String {
        val message = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            message[i] = (value and 0xff).toByte()
            value = value ushr 8
        }

        val mac = Mac.getInstance(algorithm.macName).apply {
            init(SecretKeySpec(secret, algorithm.macName))
        }
        val hash = mac.doFinal(message)

        // Dynamic truncation: the low nibble of the last byte picks where to read from.
        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        val modulus = 10.0.pow(digits).toInt()
        return (binary % modulus).toString().padStart(digits, '0')
    }
}
