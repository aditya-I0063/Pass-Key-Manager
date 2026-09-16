package com.bhardwaj.passkey.data.security

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the vault's data encryption key (DEK) and the slots that wrap it.
 *
 * Replaces a single build-time constant shared by every install. The DEK is 32 random bytes
 * generated once per install and never stored bare; each slot holds it wrapped under a different
 * unlock factor, so any one of them opens the vault.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val slotStore: KeySlotStore,
    private val keyStore: KeyStoreWrapper,
    private val keyDerivation: KeyDerivation,
    private val biometrics: BiometricAuthenticator
) {
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()

    sealed interface UnlockResult {
        data class Success(val dek: ByteArray) : UnlockResult
        /** Both Keystore slots are gone; only the recovery password remains. */
        data object NeedsRecoveryPassword : UnlockResult
        /** The device has no lock screen, so no auth-bound key can exist. */
        data object NeedsDeviceLock : UnlockResult
        data object Cancelled : UnlockResult
        data object Lockout : UnlockResult
        data class Error(val cause: Throwable) : UnlockResult
    }

    sealed interface ProvisionResult {
        data class Success(val dek: ByteArray) : ProvisionResult
        data class Error(val cause: Throwable) : ProvisionResult
    }

    fun isProvisioned(): Boolean = slotStore.exists()

    fun isDeviceSecure(): Boolean =
        context.getSystemService<KeyguardManager>()?.isDeviceSecure == true

    suspend fun migrationState(): MigrationState =
        slotStore.load()?.migrationState ?: MigrationState.NONE

    /**
     * Creates a fresh DEK and all three slots. The recovery password is mandatory: it is the
     * only slot that survives the user removing their device lock, which destroys every
     * auth-bound Keystore key. Without it there is a reachable state with permanent data loss.
     */
    suspend fun provision(recoveryPassword: CharArray): ProvisionResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val dek = ByteArray(DEK_BYTES).also(secureRandom::nextBytes)
                val slots = buildList {
                    // Recovery first, and unconditionally: it is the only slot that does not
                    // depend on the device having a lock screen, and KeySlotFile refuses to
                    // persist without it.
                    add(wrapWithPassword(SlotId.REC, dek, recoveryPassword))

                    keyStore.delete(KeyStoreWrapper.ALIAS_BIO)
                    keyStore.delete(KeyStoreWrapper.ALIAS_CRED)

                    // Auth-bound Keystore keys cannot be generated at all on a device with no
                    // lock screen, and the biometric variant additionally needs an enrolment.
                    // Each is attempted independently so a device with a PIN but no fingerprint
                    // still gets CRED, and a device with neither still gets a usable vault
                    // protected by the recovery password alone.
                    if (isDeviceSecure()) {
                        runCatching {
                            keyStore.createBiometricKey()
                            wrapWithKeystore(SlotId.BIO, KeyStoreWrapper.ALIAS_BIO, dek)
                        }.onSuccess { add(it) }
                            .onFailure { keyStore.delete(KeyStoreWrapper.ALIAS_BIO) }

                        runCatching {
                            keyStore.createCredentialKey()
                            wrapWithKeystore(SlotId.CRED, KeyStoreWrapper.ALIAS_CRED, dek)
                        }.onSuccess { add(it) }
                            .onFailure { keyStore.delete(KeyStoreWrapper.ALIAS_CRED) }
                    }
                }
                slotStore.save(
                    KeySlotFile(
                        migrationState = MigrationState.PENDING,
                        dekCheck = dekCheck(dek),
                        slots = slots
                    )
                )
                ProvisionResult.Success(dek)
            }.getOrElse { ProvisionResult.Error(it) }
        }
    }

    /**
     * Unlock chain: BIO, then CRED, then recovery.
     *
     * KeyPermanentlyInvalidatedException is thrown by Cipher.init, before any prompt appears, so
     * an invalidated enrollment is detected silently and the user never sees a broken prompt.
     */
    suspend fun unlock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String
    ): UnlockResult = mutex.withLock {
        val file = slotStore.load() ?: return@withLock UnlockResult.NeedsRecoveryPassword

        if (!isDeviceSecure()) {
            // Removing the lock screen destroys every auth-bound Keystore key. Re-adding it does
            // not bring them back, so recovery is the only remaining door.
            return@withLock UnlockResult.NeedsRecoveryPassword
        }

        tryBiometricSlot(file, activity, title, subtitle, negativeButton)?.let { return@withLock it }
        tryCredentialSlot(file, activity, title, subtitle)?.let { return@withLock it }
        UnlockResult.NeedsRecoveryPassword
    }

    private suspend fun tryBiometricSlot(
        file: KeySlotFile,
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String
    ): UnlockResult? {
        val slot = file.slot(SlotId.BIO) as? KeySlot.Keystore ?: return null
        val cipher = try {
            keyStore.initDecryptCipher(slot.alias, slot.ivB64.decodeB64())
        } catch (_: KeyPermanentlyInvalidatedException) {
            return null   // enrollment changed - fall through to CRED
        } catch (_: Exception) {
            return null
        }

        return when (val outcome = biometrics.authenticateWithCrypto(
            activity, cipher, title, subtitle, negativeButton
        )) {
            is BiometricAuthenticator.Outcome.Success -> runCatching {
                val dek = outcome.cipher!!.doFinal(slot.wrappedKeyB64.decodeB64())
                UnlockResult.Success(dek)
            }.getOrElse { UnlockResult.Error(it) }

            BiometricAuthenticator.Outcome.UserCancelled -> UnlockResult.Cancelled
            BiometricAuthenticator.Outcome.Lockout -> UnlockResult.Lockout
            BiometricAuthenticator.Outcome.NoneEnrolled -> null
            is BiometricAuthenticator.Outcome.Failed -> null
        }
    }

    private suspend fun tryCredentialSlot(
        file: KeySlotFile,
        activity: FragmentActivity,
        title: String,
        subtitle: String
    ): UnlockResult? {
        val slot = file.slot(SlotId.CRED) as? KeySlot.Keystore ?: return null
        if (!keyStore.exists(slot.alias)) return null

        when (biometrics.authenticateForTimeBoundKey(activity, title, subtitle)) {
            is BiometricAuthenticator.Outcome.Success -> Unit
            BiometricAuthenticator.Outcome.UserCancelled -> return UnlockResult.Cancelled
            BiometricAuthenticator.Outcome.Lockout -> return UnlockResult.Lockout
            else -> return null
        }

        return try {
            // Must happen inside the key's validity window opened by the prompt above.
            val cipher = keyStore.initDecryptCipher(slot.alias, slot.ivB64.decodeB64())
            val dek = cipher.doFinal(slot.wrappedKeyB64.decodeB64())
            repairBiometricSlot(file, dek)
            UnlockResult.Success(dek)
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Unwraps via the recovery password and rebuilds both Keystore slots. */
    suspend fun unlockWithRecoveryPassword(password: CharArray): UnlockResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = slotStore.load()
                ?: return@withContext UnlockResult.Error(IllegalStateException("no key slots"))
            val slot = file.slot(SlotId.REC) as? KeySlot.Password
                ?: return@withContext UnlockResult.Error(IllegalStateException("no recovery slot"))

            val wrappingKey = keyDerivation.derive(password, slot.kdf)
            try {
                val dek = AesGcm.decrypt(
                    key = wrappingKey,
                    iv = slot.ivB64.decodeB64(),
                    ciphertext = slot.wrappedKeyB64.decodeB64()
                ) ?: return@withContext UnlockResult.Cancelled

                if (dekCheck(dek) != file.dekCheck) {
                    return@withContext UnlockResult.Error(IllegalStateException("key mismatch"))
                }
                if (isDeviceSecure()) rebuildKeystoreSlots(file, dek)
                UnlockResult.Success(dek)
            } finally {
                Arrays.fill(wrappingKey, 0)
            }
        }
    }

    suspend fun changeRecoveryPassword(dek: ByteArray, newPassword: CharArray) =
        mutex.withLock {
            val file = slotStore.load() ?: return@withLock
            slotStore.save(file.withSlot(wrapWithPassword(SlotId.REC, dek, newPassword)))
        }

    suspend fun markMigrationState(state: MigrationState) = mutex.withLock {
        slotStore.load()?.let { slotStore.save(it.copy(migrationState = state)) }
    }

    private suspend fun repairBiometricSlot(file: KeySlotFile, dek: ByteArray) {
        if (!isDeviceSecure()) return
        runCatching {
            keyStore.delete(KeyStoreWrapper.ALIAS_BIO)
            keyStore.createBiometricKey()
            slotStore.save(file.withSlot(wrapWithKeystore(SlotId.BIO, KeyStoreWrapper.ALIAS_BIO, dek)))
        }
    }

    private suspend fun rebuildKeystoreSlots(file: KeySlotFile, dek: ByteArray) {
        if (!isDeviceSecure()) return
        runCatching {
            var updated = file
            keyStore.delete(KeyStoreWrapper.ALIAS_CRED)
            keyStore.createCredentialKey()
            updated = updated.withSlot(
                wrapWithKeystore(SlotId.CRED, KeyStoreWrapper.ALIAS_CRED, dek)
            )
            runCatching {
                keyStore.delete(KeyStoreWrapper.ALIAS_BIO)
                keyStore.createBiometricKey()
                wrapWithKeystore(SlotId.BIO, KeyStoreWrapper.ALIAS_BIO, dek)
            }.onSuccess { updated = updated.withSlot(it) }
            slotStore.save(updated)
        }
    }

    private fun wrapWithKeystore(id: SlotId, alias: String, dek: ByteArray): KeySlot.Keystore {
        val cipher: Cipher = keyStore.initEncryptCipher(alias)
        val wrapped = cipher.doFinal(dek)
        return KeySlot.Keystore(
            id = id,
            alias = alias,
            ivB64 = cipher.iv.encodeB64(),
            wrappedKeyB64 = wrapped.encodeB64()
        )
    }

    private fun wrapWithPassword(
        id: SlotId,
        dek: ByteArray,
        password: CharArray
    ): KeySlot.Password {
        val spec = KdfSpec.Argon2id(saltB64 = keyDerivation.newSalt().encodeB64())
        val wrappingKey = keyDerivation.derive(password, spec)
        try {
            val (iv, ciphertext) = AesGcm.encrypt(wrappingKey, dek)
            return KeySlot.Password(
                id = id,
                kdf = spec,
                ivB64 = iv.encodeB64(),
                wrappedKeyB64 = ciphertext.encodeB64()
            )
        } finally {
            Arrays.fill(wrappingKey, 0)
        }
    }

    private fun dekCheck(dek: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(dek + DEK_CHECK_CONTEXT.toByteArray(Charsets.US_ASCII))
            .take(16)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEK_BYTES = 32
        const val DEK_CHECK_CONTEXT = "passkey-dek-v1"
    }
}

private fun ByteArray.encodeB64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
private fun String.decodeB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
