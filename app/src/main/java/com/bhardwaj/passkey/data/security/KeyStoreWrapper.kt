package com.bhardwaj.passkey.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and uses the AndroidKeyStore keys that wrap the vault's data encryption key.
 *
 * Two aliases exist, deliberately with different auth properties:
 *
 *  - [ALIAS_BIO] requires per-use strong biometric auth and is bound to the current biometric
 *    enrollment, so adding or removing a fingerprint invalidates it. That is the desired
 *    property for the everyday unlock path.
 *  - [ALIAS_CRED] accepts device credential or biometrics with a short validity window and is
 *    *not* invalidated by biometric enrollment, which is what lets the app self-heal after the
 *    user changes their fingerprints without ever creating an unauthenticated path to the key.
 */
@Singleton
class KeyStoreWrapper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val ALIAS_BIO = "passkey_dek_wrap_bio"
        const val ALIAS_CRED = "passkey_dek_wrap_cred"

        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/" +
                "${KeyProperties.ENCRYPTION_PADDING_NONE}"
        private const val GCM_TAG_BITS = 128
        private const val KEY_BITS = 256

        /** Seconds the credential-backed key stays usable after a successful auth. */
        const val CRED_VALIDITY_SECONDS = 30
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    fun exists(alias: String): Boolean = runCatching { keyStore.containsAlias(alias) }
        .getOrDefault(false)

    fun getOrNull(alias: String): SecretKey? = runCatching {
        keyStore.getKey(alias, null) as? SecretKey
    }.getOrNull()

    fun delete(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    fun createBiometricKey(): SecretKey = generate(ALIAS_BIO, biometricOnly = true)

    fun createCredentialKey(): SecretKey = generate(ALIAS_CRED, biometricOnly = false)

    /**
     * @throws android.security.keystore.KeyPermanentlyInvalidatedException when the enrollment
     *   backing the key has changed. Thrown by init, *before* any prompt is shown, which is what
     *   lets the caller fall through to another slot without the user seeing a broken prompt.
     */
    fun initEncryptCipher(alias: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, requireKey(alias))
        }

    /** @throws android.security.keystore.KeyPermanentlyInvalidatedException — see above. */
    fun initDecryptCipher(alias: String, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, requireKey(alias), GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun requireKey(alias: String): SecretKey =
        getOrNull(alias) ?: error("Keystore alias $alias is missing")

    private fun generate(alias: String, biometricOnly: Boolean): SecretKey {
        // StrongBox can throw at generateKey() even when the feature flag is advertised, so it
        // is attempted and then abandoned rather than trusted.
        return runCatching { generate(alias, biometricOnly, strongBox = supportsStrongBox()) }
            .recoverCatching { error ->
                if (error is StrongBoxUnavailableException) {
                    delete(alias)
                    generate(alias, biometricOnly, strongBox = false)
                } else {
                    throw error
                }
            }
            .getOrThrow()
    }

    private fun generate(alias: String, biometricOnly: Boolean, strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            // API 28 exactly, which is this app's minSdk: the key is unusable while the device
            // is locked, so a stolen unlocked-but-idle phone is not a bypass.
            .setUnlockedDeviceRequired(true)

        if (biometricOnly) {
            // Invalidating on enrollment change is the point of this slot; the CRED slot exists
            // precisely so that invalidation is recoverable.
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authTypes = if (biometricOnly) {
                KeyProperties.AUTH_BIOMETRIC_STRONG
            } else {
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            }
            builder.setUserAuthenticationParameters(
                if (biometricOnly) 0 else CRED_VALIDITY_SECONDS,
                authTypes
            )
        } else {
            // Deprecated at API 30 but the only option on 28-29. -1 means per-use auth.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(
                if (biometricOnly) -1 else CRED_VALIDITY_SECONDS
            )
        }

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(builder.build())
        }.generateKey()
    }

    private fun supportsStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
