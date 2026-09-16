package com.bhardwaj.passkey.presentation.screens.detail_screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.screens.common.PassKeyButton
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailState
import com.bhardwaj.passkey.utils.ButtonType

/**
 * Takes either the `otpauth://` link behind a QR code or the Base32 secret printed beside it -
 * the two things a two-factor setup page actually offers.
 *
 * No camera. Scanning the code itself would mean a camera permission in an app whose whole
 * proposition is that it asks for almost none, and every setup page that shows a QR code also
 * offers the same secret as text.
 */
@Composable
fun AuthenticatorDialog(
    editor: DetailState.TotpEditor,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_authenticator)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editor.input,
                    onValueChange = onInputChange,
                    isError = editor.isInvalid,
                    singleLine = false,
                    label = { Text(text = stringResource(R.string.authenticator_input_hint)) }
                )
                if (editor.isInvalid) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(R.string.authenticator_invalid),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            PassKeyButton(
                onClick = onSave,
                text = stringResource(R.string.add),
                buttonType = ButtonType.DEFAULT
            )
        },
        dismissButton = {
            PassKeyButton(
                onClick = onDismiss,
                text = stringResource(R.string.cancel),
                buttonType = ButtonType.OUTLINED
            )
        }
    )
}
