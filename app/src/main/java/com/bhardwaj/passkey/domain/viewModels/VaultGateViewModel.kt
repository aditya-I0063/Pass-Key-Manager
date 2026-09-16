package com.bhardwaj.passkey.domain.viewModels

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.bhardwaj.passkey.data.backup.BackupRepository
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import com.bhardwaj.passkey.data.security.DatabaseKeyManager
import com.bhardwaj.passkey.data.security.MigrationState
import com.bhardwaj.passkey.data.security.VaultMigration
import com.bhardwaj.passkey.data.security.VaultSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Arrays
import javax.inject.Inject

/**
 * Drives everything between app launch and an open vault: first-run provisioning, the one-time
 * re-key of a pre-5.7 database, and the ordinary unlock.
 */
@HiltViewModel
class VaultGateViewModel @Inject constructor(
    private val keyManager: DatabaseKeyManager,
    private val migration: VaultMigration,
    private val backupRepository: BackupRepository,
    private val vault: VaultDatabaseProvider,
    private val session: VaultSession
) : ViewModel() {

    sealed interface State {
        data object Checking : State

        /**
         * A recovery password must be chosen before the vault can be used.
         * [isExistingVault] distinguishes an upgrading user, whose data is about to be re-keyed,
         * from a first-run user.
         */
        data class SetUpRecovery(val isExistingVault: Boolean) : State

        /** Ready to prompt for biometrics. */
        data object Locked : State
        data object Authenticating : State
        data object Migrating : State

        /** [canExportVault] is true while the legacy key still opens the original database. */
        data class MigrationFailed(val reason: String, val canExportVault: Boolean) : State

        data object NeedsRecoveryPassword : State
        data object NeedsDeviceLock : State
        data object Unlocked : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    /** null = nothing to report, true/false = the outcome of a rescue export. */
    private val _exportResult = MutableStateFlow<Boolean?>(null)
    val exportResult: StateFlow<Boolean?> = _exportResult.asStateFlow()

    /** Set when a wrong recovery password was entered, so the UI can say so. */
    private val _recoveryFailed = MutableStateFlow(false)
    val recoveryFailed: StateFlow<Boolean> = _recoveryFailed.asStateFlow()

    fun start() {
        viewModelScope.launch {
            _state.value = if (!keyManager.isProvisioned()) {
                State.SetUpRecovery(isExistingVault = migration.databaseExists())
            } else {
                // A PENDING migration state is also just Locked: the re-key is resumable
                // because the original database is untouched until the final rename.
                State.Locked
            }
        }
    }

    /** First run, or the first launch after upgrading from a pre-5.7 build. */
    fun onRecoveryPasswordChosen(password: CharArray) {
        viewModelScope.launch {
            _state.value = State.Checking
            when (val result = keyManager.provision(password)) {
                is DatabaseKeyManager.ProvisionResult.Error ->
                    _state.value = State.Error(result.cause.message ?: "provisioning failed")

                is DatabaseKeyManager.ProvisionResult.Success -> {
                    Arrays.fill(password, NUL)
                    runMigrationThenOpen(result.dek)
                }
            }
        }
    }

    fun onUnlockRequested(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String
    ) {
        viewModelScope.launch {
            _state.value = State.Authenticating
            when (val result = keyManager.unlock(activity, title, subtitle, negativeButton)) {
                is DatabaseKeyManager.UnlockResult.Success -> runMigrationThenOpen(result.dek)
                DatabaseKeyManager.UnlockResult.NeedsRecoveryPassword ->
                    _state.value = State.NeedsRecoveryPassword
                DatabaseKeyManager.UnlockResult.NeedsDeviceLock ->
                    _state.value = State.NeedsDeviceLock
                DatabaseKeyManager.UnlockResult.Cancelled -> _state.value = State.Locked
                DatabaseKeyManager.UnlockResult.Lockout -> _state.value = State.Locked
                is DatabaseKeyManager.UnlockResult.Error ->
                    _state.value = State.Error(result.cause.message ?: "unlock failed")
            }
        }
    }

    fun onRecoveryPasswordEntered(password: CharArray) {
        viewModelScope.launch {
            _state.value = State.Checking
            _recoveryFailed.value = false
            val result = keyManager.unlockWithRecoveryPassword(password)
            Arrays.fill(password, NUL)
            when (result) {
                is DatabaseKeyManager.UnlockResult.Success -> runMigrationThenOpen(result.dek)
                else -> {
                    _recoveryFailed.value = true
                    _state.value = State.NeedsRecoveryPassword
                }
            }
        }
    }

    fun onRetry() = start()

    /**
     * Exports the un-migrated vault by reading it through the legacy key.
     *
     * Offered before the re-key and again if it fails. The point is that a user is never stuck
     * behind a migration they cannot complete: the original database is untouched by a failed
     * export, so its contents can always be written out to a backup they control.
     */
    fun onExportLegacyVault(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            val previews = migration.readLegacyVault()
            val success = previews != null &&
                backupRepository.exportPreviews(uri, password, previews).isSuccess
            Arrays.fill(password, NUL)
            _exportResult.value = success
        }
    }

    fun onExportResultShown() {
        _exportResult.value = null
    }

    /**
     * The re-key runs after unlock, because the new key must be wrapped by an auth-bound
     * Keystore key before it is used to encrypt anything.
     */
    private suspend fun runMigrationThenOpen(dek: ByteArray) {
        if (keyManager.migrationState() != MigrationState.DONE && migration.databaseExists()) {
            _state.value = State.Migrating
            val result = migration.migrate(dek)
            if (result is VaultMigration.Result.Failed) {
                _state.value = State.MigrationFailed(
                    reason = result.reason.name,
                    canExportVault = migration.canOpenWithLegacyKey()
                )
                return
            }
        }
        keyManager.markMigrationState(MigrationState.DONE)
        migration.discardBackup()

        val opened = runCatching { vault.open(dek) }
        if (opened.isFailure) {
            _state.value = State.Error(
                opened.exceptionOrNull()?.message ?: "could not open the vault"
            )
            return
        }
        session.onUnlocked()
        _state.value = State.Unlocked
        // The key now lives only inside VaultDatabaseProvider's SQLCipher handle.
        Arrays.fill(dek, 0)
    }

    private companion object {
        val NUL = Char(0)
    }
}
