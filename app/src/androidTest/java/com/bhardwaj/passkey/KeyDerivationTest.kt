package com.bhardwaj.passkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhardwaj.passkey.data.security.DefaultKeyDerivation
import com.bhardwaj.passkey.data.security.KdfSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Base64

/**
 * Exercises the real Argon2 native binding. Instrumented rather than JVM because argon2kt is a
 * JNI library; these tests are what would catch the binding being stripped or renamed.
 */
@RunWith(AndroidJUnit4::class)
class KeyDerivationTest {

    private val derivation = DefaultKeyDerivation()

    private fun argon2(salt: ByteArray) = KdfSpec.Argon2id(
        saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
        // Deliberately small so the test suite stays quick; production uses the defaults.
        memoryKiB = 1024,
        iterations = 1,
        parallelism = 1
    )

    @Test
    fun argon2_is_deterministic_for_the_same_password_and_salt() {
        val salt = derivation.newSalt()
        val a = derivation.derive("correct horse".toCharArray(), argon2(salt))
        val b = derivation.derive("correct horse".toCharArray(), argon2(salt))
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(32)
    }

    @Test
    fun argon2_differs_for_a_different_password() {
        val salt = derivation.newSalt()
        val a = derivation.derive("correct horse".toCharArray(), argon2(salt))
        val b = derivation.derive("incorrect horse".toCharArray(), argon2(salt))
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun argon2_differs_for_a_different_salt() {
        val password = "correct horse".toCharArray()
        val a = derivation.derive(password.copyOf(), argon2(derivation.newSalt()))
        val b = derivation.derive(password.copyOf(), argon2(derivation.newSalt()))
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun pbkdf2_path_also_works() {
        // Kept alive in the sealed hierarchy so KDF parameters can move without a format break.
        val spec = KdfSpec.Pbkdf2HmacSha512(
            saltB64 = Base64.encodeToString(derivation.newSalt(), Base64.NO_WRAP),
            iterations = 1_000
        )
        val a = derivation.derive("pw".toCharArray(), spec)
        val b = derivation.derive("pw".toCharArray(), spec)
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(32)
    }

    @Test
    fun salts_are_unique() {
        val salts = List(50) { derivation.newSalt().toList() }
        assertThat(salts.toSet()).hasSize(50)
    }

    @Test
    fun non_ascii_passwords_round_trip() {
        // The password is converted to UTF-8 bytes without going through String, so a
        // multi-byte passphrase must still derive consistently.
        val salt = derivation.newSalt()
        val password = "pÿssw0rd-éü-中文".toCharArray()
        val a = derivation.derive(password.copyOf(), argon2(salt))
        val b = derivation.derive(password.copyOf(), argon2(salt))
        assertThat(a).isEqualTo(b)
    }
}
