package com.bhardwaj.passkey

import android.app.KeyguardManager
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bhardwaj.passkey.data.security.KeyStoreWrapper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises real AndroidKeyStore key generation.
 *
 * These only mean anything on a device with a lock screen, so they report what the device can
 * actually do rather than assuming. On a device with no credential, auth-bound keys cannot be
 * generated at all and the app is expected to fall back to a recovery-password-only vault.
 */
@RunWith(AndroidJUnit4::class)
class KeyStoreWrapperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrapper = KeyStoreWrapper(context)

    private val isDeviceSecure: Boolean
        get() = context.getSystemService<KeyguardManager>()?.isDeviceSecure == true

    @Before
    fun setUp() = cleanUp()

    @After
    fun tearDown() = cleanUp()

    private fun cleanUp() {
        wrapper.delete(KeyStoreWrapper.ALIAS_BIO)
        wrapper.delete(KeyStoreWrapper.ALIAS_CRED)
    }

    @Test
    fun report_device_capabilities() {
        // Printed so a failing slot on CI or a given emulator can be diagnosed, since the
        // production code deliberately swallows these failures and falls back.
        println("KEYSTOREDIAG isDeviceSecure=$isDeviceSecure")
        val bio = runCatching { wrapper.createBiometricKey() }
        println("KEYSTOREDIAG createBiometricKey -> " + (bio.exceptionOrNull()?.toString() ?: "OK"))
        val cred = runCatching { wrapper.createCredentialKey() }
        println("KEYSTOREDIAG createCredentialKey -> " + (cred.exceptionOrNull()?.toString() ?: "OK"))
    }

    @Test
    fun credential_key_is_creatable_when_the_device_has_a_lock() {
        if (!isDeviceSecure) return   // nothing meaningful to assert
        wrapper.createCredentialKey()
        assertThat(wrapper.exists(KeyStoreWrapper.ALIAS_CRED)).isTrue()
        // The property this whole design rests on: encryption uses the public key and needs no
        // authentication, so the vault key can be wrapped during setup without a prompt. An
        // auth-bound *symmetric* key would throw UserNotAuthenticatedException here.
        val cipher = wrapper.initEncryptCipher(KeyStoreWrapper.ALIAS_CRED)
        assertThat(cipher.doFinal(ByteArray(32))).isNotEmpty()
    }

    @Test
    fun deleting_an_alias_removes_it() {
        if (!isDeviceSecure) return
        wrapper.createCredentialKey()
        assertThat(wrapper.exists(KeyStoreWrapper.ALIAS_CRED)).isTrue()
        wrapper.delete(KeyStoreWrapper.ALIAS_CRED)
        assertThat(wrapper.exists(KeyStoreWrapper.ALIAS_CRED)).isFalse()
    }
}
