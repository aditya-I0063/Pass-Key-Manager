package com.bhardwaj.passkey.presentation.screens.settings_screen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.AutoLockTimeout

/** Display label for a timeout. Lives here because text is a presentation concern. */
@androidx.annotation.StringRes
private fun AutoLockTimeout.labelRes(): Int = when (this) {
    AutoLockTimeout.IMMEDIATELY -> R.string.auto_lock_immediately
    AutoLockTimeout.SECONDS_15 -> R.string.auto_lock_15_seconds
    AutoLockTimeout.SECONDS_30 -> R.string.auto_lock_30_seconds
    AutoLockTimeout.MINUTE_1 -> R.string.auto_lock_1_minute
    AutoLockTimeout.MINUTES_5 -> R.string.auto_lock_5_minutes
    AutoLockTimeout.NEVER -> R.string.auto_lock_never
}

@Composable
fun AutoLockDialog(
    current: AutoLockTimeout,
    onDismiss: () -> Unit,
    onSelect: (AutoLockTimeout) -> Unit
) {
    var selected by remember(current) { mutableStateOf(current) }

    AlertDialog(
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_lock)) },
        text = {
            Column {
                AutoLockTimeout.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { selected = option }
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(option.labelRes())
                        )
                    }
                }
                // "Never" is a real footgun for a password manager, so it is spelled out
                // rather than hidden behind a label.
                if (selected == AutoLockTimeout.NEVER) {
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = stringResource(R.string.auto_lock_never_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) {
                Text(stringResource(R.string.backup_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
