package com.bhardwaj.passkey.presentation.screens.security_screen

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.viewModels.VaultGateViewModel
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.BackupPasswordDialog

/**
 * The screen standing between app launch and an open vault.
 *
 * Replaces the previous security screen, whose only job was to show a biometric prompt and then
 * navigate. It now also covers first-run key provisioning, the one-time re-key of a pre-5.7
 * database, and the recovery paths for when the device's biometric enrollment or lock screen has
 * changed underneath the app.
 */
@Composable
fun VaultGateScreen(
    onUnlocked: () -> Unit,
    viewModel: VaultGateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recoveryFailed by viewModel.recoveryFailed.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current

    val promptTitle = stringResource(R.string.app_name)
    val promptSubtitle = stringResource(R.string.security_description)
    val promptNegative = stringResource(R.string.cancel)

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(state) {
        if (state is VaultGateViewModel.State.Unlocked) onUnlocked()
    }

    // Auto-prompt on entering the locked state, so the user is not made to tap twice.
    LaunchedEffect(state, activity) {
        if (state is VaultGateViewModel.State.Locked && activity != null) {
            viewModel.onUnlockRequested(activity, promptTitle, promptSubtitle, promptNegative)
        }
    }

    when (val current = state) {
        is VaultGateViewModel.State.SetUpRecovery -> BackupPasswordDialog(
            confirmMode = true,
            titleRes = R.string.recovery_setup_title,
            messageRes = if (current.isExistingVault) {
                R.string.recovery_setup_message_upgrade
            } else {
                R.string.recovery_setup_message_new
            },
            dismissible = false,
            onDismiss = {},
            onConfirm = viewModel::onRecoveryPasswordChosen
        )

        VaultGateViewModel.State.NeedsRecoveryPassword -> BackupPasswordDialog(
            confirmMode = false,
            titleRes = R.string.recovery_unlock_title,
            messageRes = if (recoveryFailed) {
                R.string.recovery_unlock_wrong
            } else {
                R.string.recovery_unlock_message
            },
            dismissible = false,
            onDismiss = {},
            onConfirm = viewModel::onRecoveryPasswordEntered
        )

        VaultGateViewModel.State.Migrating -> GateMessage(
            title = stringResource(R.string.migration_title),
            message = stringResource(R.string.migration_message),
            busy = true
        )

        VaultGateViewModel.State.Checking,
        VaultGateViewModel.State.Authenticating -> GateMessage(busy = true)

        is VaultGateViewModel.State.MigrationFailed -> GateMessage(
            title = stringResource(R.string.migration_failed_title),
            // Deliberately does not offer "reset": the original database is intact and the only
            // safe actions are retrying or getting the data out.
            message = if (current.canExportVault) {
                stringResource(R.string.migration_failed_message_can_export)
            } else {
                stringResource(R.string.migration_failed_message)
            },
            primaryLabel = stringResource(R.string.migration_retry),
            onPrimary = viewModel::onRetry
        )

        VaultGateViewModel.State.NeedsDeviceLock -> GateMessage(
            title = stringResource(R.string.device_lock_required_title),
            message = stringResource(R.string.device_lock_required_message),
            primaryLabel = stringResource(R.string.device_lock_open_settings),
            onPrimary = {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            secondaryLabel = stringResource(R.string.migration_retry),
            onSecondary = viewModel::onRetry
        )

        is VaultGateViewModel.State.Error -> GateMessage(
            title = stringResource(R.string.something_went_wrong),
            message = current.message,
            primaryLabel = stringResource(R.string.migration_retry),
            onPrimary = viewModel::onRetry
        )

        VaultGateViewModel.State.Locked -> GateMessage(
            title = stringResource(R.string.security_heading),
            message = stringResource(R.string.security_description),
            primaryLabel = stringResource(R.string.security_button),
            onPrimary = {
                activity?.let {
                    viewModel.onUnlockRequested(it, promptTitle, promptSubtitle, promptNegative)
                }
            }
        )

        VaultGateViewModel.State.Unlocked -> Unit
    }
}

@Composable
private fun GateMessage(
    title: String? = null,
    message: String? = null,
    busy: Boolean = false,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(bottom = 24.dp))
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
        message?.let {
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
        if (primaryLabel != null && onPrimary != null) {
            Button(modifier = Modifier.padding(top = 24.dp), onClick = onPrimary) {
                Text(text = primaryLabel)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(modifier = Modifier.padding(top = 8.dp), onClick = onSecondary) {
                Text(text = secondaryLabel)
            }
        }
    }
}
