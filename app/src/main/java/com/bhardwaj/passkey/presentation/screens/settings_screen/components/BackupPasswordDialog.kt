package com.bhardwaj.passkey.presentation.screens.settings_screen.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.bhardwaj.passkey.R

private const val MIN_BACKUP_PASSWORD_LENGTH = 8

/**
 * Collects the password protecting a backup file.
 *
 * In [confirmMode] (export) the password is entered twice, because there is no recovery path:
 * the file is useless without it. On restore a single field is shown.
 */
@Composable
fun BackupPasswordDialog(
    confirmMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    @StringRes titleRes: Int? = null,
    @StringRes messageRes: Int? = null,
    /** False for gate dialogs that the user must not be able to escape. */
    dismissible: Boolean = true
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showValidation by remember { mutableStateOf(false) }

    val tooShort = password.length < MIN_BACKUP_PASSWORD_LENGTH
    val mismatch = confirmMode && password != confirmation
    val error = when {
        !showValidation -> null
        confirmMode && tooShort -> stringResource(R.string.backup_password_too_short)
        mismatch -> stringResource(R.string.backup_password_mismatch)
        else -> null
    }

    AlertDialog(
        // The dialog is its own window, so it needs FLAG_SECURE in its own right.
        properties = DialogProperties(
            securePolicy = SecureFlagPolicy.SecureOn,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        ),
        onDismissRequest = { if (dismissible) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    titleRes ?: if (confirmMode) R.string.backup_password_title
                    else R.string.restore_password_title
                )
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        messageRes ?: if (confirmMode) R.string.backup_password_message
                        else R.string.restore_password_message
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    modifier = Modifier.padding(top = 16.dp),
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = error != null,
                    label = { Text(stringResource(R.string.backup_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (confirmMode) ImeAction.Next else ImeAction.Done
                    )
                )
                if (confirmMode) {
                    OutlinedTextField(
                        modifier = Modifier.padding(top = 8.dp),
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        isError = error != null,
                        label = { Text(stringResource(R.string.backup_password_confirm_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )
                }
                error?.let {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showValidation = true
                val valid = if (confirmMode) !tooShort && !mismatch else password.isNotEmpty()
                if (valid) onConfirm(password.toCharArray())
            }) { Text(stringResource(R.string.backup_confirm)) }
        },
        dismissButton = {
            if (dismissible) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
