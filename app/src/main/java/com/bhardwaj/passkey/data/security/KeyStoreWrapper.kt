package com.bhardwaj.passkey.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and uses the AndroidKeyStore keys that wrap the vault's data encryption key.
 *
 * These are **RSA key pairs, not AES keys**, and that is the whole point. An auth-bound symmetric
 * key requires authentication for *every* operation including encryption, so wrapping the vault
 * key at provisioning time would throw UserNotAuthenticatedException - there is nothing to
 * authenticate against yet, and prompting during setup would be the wrong moment. With a key
 * pair, the public key encrypts with no authentication at all, while the private key requires it
 * to decrypt. Wrapping is free; unwrapping is gated.
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
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val KEY_BITS = 2048

        /** Seconds the credential-backed key stays usable after a successful auth. */
        const val CRED_VALIDITY_SECONDS = 30
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    fun exists(alias: String): Boolean = runCatching { keyStore.containsAlias(alias) }
        .getOrDefault(false)

    fun delete(alias: String) {
        runCatching { keyStore.deleteEntry(alias) }
    }

    fun createBiometricKey() = generate(ALIAS_BIO, biometricOnly = true)

    fun createCredentialKey() = generate(ALIAS_CRED, biometricOnly = false)

    /**
     * Encryption uses the public key, which is never auth-gated, so this can be called at
     * provisioning time without a prompt.
     */
    fun initEncryptCipher(alias: String): Cipher {
        val certificate = keyStore.getCertificate(alias)
            ?: error("Keystore alias $alias is missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, certificate.publicKey, oaepSpec())
        }
    }

    /**
     * Decryption uses the private key and therefore requires authentication.
     *
     * @throws android.security.keystore.KeyPermanentlyInvalidatedException when the enrollment
     *   backing the key has changed. Thrown here, *before* any prompt is shown, which is what
     *   lets the caller fall through to another slot without the user seeing a broken prompt.
     */
    fun initDecryptCipher(alias: String): Cipher {
        val privateKey = keyStore.getKey(alias, null)
            ?: error("Keystore alias $alias is missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, privateKey, oaepSpec())
        }
    }

    /**
     * MGF1 is pinned to SHA-1 even though the digest is SHA-256.
     *
     * AndroidKeyStore only records a single digest for the key and applies it to OAEP itself;
     * passing MGF1ParameterSpec.SHA256 here makes init fail with an unsupported-MGF error on
     * several OEM implementations. This is the combination the platform actually accepts.
     */
    private fun oaepSpec() = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

    private fun generate(alias: String, biometricOnly: Boolean) {
        // StrongBox can throw at generation even when the feature flag is advertised, and it
        // frequently does not support RSA at all, so it is attempted and then abandoned.
        runCatching { generate(alias, biometricOnly, strongBox = supportsStrongBox()) }
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

    private fun generate(alias: String, biometricOnly: Boolean, strongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_BITS)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
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

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).apply {
            initialize(builder.build())
        }.generateKeyPair()
    }

    private fun supportsStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
