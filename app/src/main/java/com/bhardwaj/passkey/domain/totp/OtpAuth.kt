package com.bhardwaj.passkey.domain.totp

/**
 * Base32 as RFC 4648 defines it, and as every authenticator prints it.
 *
 * Deliberately forgiving on input: secrets are shown to people in groups of four with spaces,
 * sometimes lowercase, often without padding. Rejecting any of those would only mean the user
 * retypes what they already copied correctly.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(encoded: String): ByteArray? {
        val cleaned = encoded.trim()
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
            .uppercase()
        if (cleaned.isEmpty()) return null

        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>(cleaned.length * 5 / 8 + 1)
        for (character in cleaned) {
            val value = ALPHABET.indexOf(character)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out += ((buffer shr bitsLeft) and 0xff).toByte()
            }
        }
        return if (out.isEmpty()) null else out.toByteArray()
    }

    fun encode(bytes: ByteArray): String {
        val builder = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                builder.append(ALPHABET[(buffer shr bitsLeft) and 0x1f])
            }
        }
        if (bitsLeft > 0) builder.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
        return builder.toString()
    }
}

/**
 * Reads the `otpauth://totp/...` URI behind every authenticator QR code.
 *
 * Parsed by hand rather than with android.net.Uri so it stays testable on the JVM, and because
 * the label half of the path is percent-encoded in a way Uri's own splitting does not help with.
 */
object OtpAuthUri {

    private const val SCHEME = "otpauth://"
    private const val TOTP_TYPE = "totp"

    /**
     * Returns null for anything that is not a well-formed TOTP URI - including the HOTP variant,
     * which counts events rather than time and would silently produce codes that never verify.
     */
    fun parse(raw: String): TotpConfig? {
        val text = raw.trim()
        if (!text.startsWith(SCHEME, ignoreCase = true)) return null
        val body = text.substring(SCHEME.length)

        val type = body.substringBefore('/').substringBefore('?').lowercase()
        if (type != TOTP_TYPE) return null

        val afterType = body.substringAfter('/', missingDelimiterValue = "")
        val label = afterType.substringBefore('?').decodePercent()
        val query = afterType.substringAfter('?', missingDelimiterValue = "")
        val params = query.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val key = pair.substringBefore('=').lowercase()
                val value = pair.substringAfter('=', missingDelimiterValue = "").decodePercent()
                key to value
            }
            .toMap()

        val secret = params["secret"]?.let { Base32.decode(it) } ?: return null

        // "Issuer:account" in the label, with the issuer= parameter winning when both exist.
        val labelIssuer = label.substringBefore(':', missingDelimiterValue = "").trim()
        val account = label.substringAfter(':', missingDelimiterValue = label).trim()

        return TotpConfig(
            secret = secret,
            issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: labelIssuer.takeIf { it.isNotEmpty() },
            account = account.takeIf { it.isNotEmpty() },
            algorithm = TotpAlgorithm.fromNameOrDefault(params["algorithm"]),
            digits = params["digits"]?.toIntOrNull()
                ?.takeIf { it in TotpConfig.DIGIT_RANGE }
                ?: TotpConfig.DEFAULT_DIGITS,
            periodSeconds = params["period"]?.toIntOrNull()
                ?.takeIf { it in TotpConfig.PERIOD_RANGE }
                ?: TotpConfig.DEFAULT_PERIOD_SECONDS
        )
    }

    private fun String.decodePercent(): String {
        if (!contains('%') && !contains('+')) return this
        val bytes = ArrayList<Byte>(length)
        var index = 0
        while (index < length) {
            val character = this[index]
            when {
                character == '%' && index + 2 < length -> {
                    val hex = substring(index + 1, index + 3).toIntOrNull(16)
                    if (hex == null) {
                        bytes += character.code.toByte()
                        index++
                    } else {
                        bytes += hex.toByte()
                        index += 3
                    }
                }

                character == '+' -> {
                    bytes += ' '.code.toByte()
                    index++
                }

                else -> {
                    // Non-ASCII characters can appear unencoded in a hand-edited URI.
                    character.toString().toByteArray(Charsets.UTF_8).forEach { bytes += it }
                    index++
                }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
