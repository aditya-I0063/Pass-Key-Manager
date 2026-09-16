package com.bhardwaj.passkey

import com.bhardwaj.passkey.domain.totp.Base32
import com.bhardwaj.passkey.domain.totp.OtpAuthUri
import com.bhardwaj.passkey.domain.totp.TotpAlgorithm
import com.bhardwaj.passkey.domain.totp.TotpConfig
import com.bhardwaj.passkey.domain.totp.TotpGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TotpTest {

    // RFC 6238 Appendix B. The seed is repeated to the length each algorithm expects.
    private val sha1Seed = "12345678901234567890".toByteArray()
    private val sha256Seed = "12345678901234567890123456789012".toByteArray()
    private val sha512Seed =
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    private fun config(
        secret: ByteArray,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        digits: Int = 8
    ) = TotpConfig(secret = secret, algorithm = algorithm, digits = digits)

    @Test
    fun `the RFC 6238 SHA-1 test vectors all match`() {
        val expected = mapOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )
        expected.forEach { (time, code) ->
            assertThat(TotpGenerator.generate(config(sha1Seed), time)).isEqualTo(code)
        }
    }

    @Test
    fun `the SHA-256 and SHA-512 vectors match too`() {
        assertThat(TotpGenerator.generate(config(sha256Seed, TotpAlgorithm.SHA256), 59L))
            .isEqualTo("46119246")
        assertThat(TotpGenerator.generate(config(sha512Seed, TotpAlgorithm.SHA512), 59L))
            .isEqualTo("90693936")
    }

    @Test
    fun `a six digit code is the eight digit one truncated from the left`() {
        // Not a property of this implementation but of the spec: digits is a modulus.
        assertThat(TotpGenerator.generate(config(sha1Seed, digits = 6), 59L)).isEqualTo("287082")
    }

    @Test
    fun `a code is padded rather than shortened when the truncation is small`() {
        // A leading zero is significant; dropping it produces a code the server rejects.
        val codes = (0L until 2000L step 30).map {
            TotpGenerator.generate(config(sha1Seed, digits = 6), it)
        }
        assertThat(codes.all { it.length == 6 }).isTrue()
    }

    @Test
    fun `the countdown runs from the period down to one`() {
        val config = TotpConfig(secret = sha1Seed)
        assertThat(TotpGenerator.secondsRemaining(config, 0L)).isEqualTo(30)
        assertThat(TotpGenerator.secondsRemaining(config, 1L)).isEqualTo(29)
        assertThat(TotpGenerator.secondsRemaining(config, 29L)).isEqualTo(1)
        assertThat(TotpGenerator.secondsRemaining(config, 30L)).isEqualTo(30)
    }

    @Test
    fun `base32 round-trips and matches the canonical encoding`() {
        assertThat(Base32.encode(sha1Seed)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        assertThat(Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(sha1Seed)
    }

    @Test
    fun `base32 accepts a secret as it is printed for people to read`() {
        // Grouped, lowercase and unpadded - all three are how secrets actually arrive.
        assertThat(Base32.decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(sha1Seed)
        assertThat(Base32.decode("JBSWY3DP")).isEqualTo("Hello".toByteArray())
        assertThat(Base32.decode("JBSWY3DP===")).isEqualTo("Hello".toByteArray())
    }

    @Test
    fun `base32 rejects characters outside the alphabet rather than guessing`() {
        assertThat(Base32.decode("JBSWY3D!")).isNull()
        assertThat(Base32.decode("01890")).isNull()
        assertThat(Base32.decode("")).isNull()
    }

    @Test
    fun `an otpauth uri supplies every parameter`() {
        val config = OtpAuthUri.parse(
            "otpauth://totp/ACME%20Co:alice%40example.com" +
                "?secret=GEZDGNBVGY3TQOJQ&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        )
        assertThat(config).isNotNull()
        assertThat(config!!.issuer).isEqualTo("ACME Co")
        assertThat(config.account).isEqualTo("alice@example.com")
        assertThat(config.algorithm).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(config.digits).isEqualTo(8)
        assertThat(config.periodSeconds).isEqualTo(60)
    }

    @Test
    fun `the defaults apply when the uri omits them`() {
        val config = OtpAuthUri.parse("otpauth://totp/alice?secret=GEZDGNBVGY3TQOJQ")!!
        assertThat(config.algorithm).isEqualTo(TotpAlgorithm.SHA1)
        assertThat(config.digits).isEqualTo(6)
        assertThat(config.periodSeconds).isEqualTo(30)
        assertThat(config.account).isEqualTo("alice")
        assertThat(config.issuer).isNull()
    }

    @Test
    fun `out of range parameters fall back instead of producing codes nothing accepts`() {
        val config = OtpAuthUri.parse(
            "otpauth://totp/alice?secret=GEZDGNBVGY3TQOJQ&digits=99&period=0"
        )!!
        assertThat(config.digits).isEqualTo(6)
        assertThat(config.periodSeconds).isEqualTo(30)
    }

    @Test
    fun `an issuer in the label is used when there is no issuer parameter`() {
        val config = OtpAuthUri.parse("otpauth://totp/GitHub:alice?secret=GEZDGNBVGY3TQOJQ")!!
        assertThat(config.issuer).isEqualTo("GitHub")
        assertThat(config.account).isEqualTo("alice")
    }

    @Test
    fun `hotp, a missing secret and plain text are all rejected`() {
        // HOTP counts events rather than time; accepting it would generate codes that never
        // verify, with nothing to tell the user why.
        assertThat(OtpAuthUri.parse("otpauth://hotp/alice?secret=GEZDGNBVGY3TQOJQ&counter=1"))
            .isNull()
        assertThat(OtpAuthUri.parse("otpauth://totp/alice?issuer=ACME")).isNull()
        assertThat(OtpAuthUri.parse("GEZDGNBVGY3TQOJQ")).isNull()
        assertThat(OtpAuthUri.parse("")).isNull()
    }

    @Test
    fun `a uri and its hand-typed secret produce the same code`() {
        val fromUri = OtpAuthUri.parse("otpauth://totp/alice?secret=GEZDGNBVGY3TQOJQ")!!
        val fromSecret = TotpConfig(secret = Base32.decode("GEZDGNBVGY3TQOJQ")!!)
        assertThat(TotpGenerator.generate(fromUri, 1111111109L))
            .isEqualTo(TotpGenerator.generate(fromSecret, 1111111109L))
    }
}
