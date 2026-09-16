package com.bhardwaj.passkey.presentation.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bhardwaj.passkey.R

/**
 * Terminal screen shown when the running build is below the supported floor, or when the user
 * dismissed a required update.
 *
 * There is deliberately no way past this composable. Previously a required update could be
 * cancelled and the app carried on into the vault regardless, which made "immediate" updates
 * advisory in practice.
 */
@Composable
fun UpdateRequiredScreen(onUpdateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.update_required_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = stringResource(R.string.update_required_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onUpdateClick
        ) {
            Text(text = stringResource(R.string.update_now))
        }
    }
}

/**
 * Non-blocking prompt for a *flexible* update that has finished downloading.
 *
 * Deliberately not the blocking screen: a low-priority update that is merely ready should not
 * lock the user out of their vault, and calling completeUpdate() unprompted would restart the
 * app from under them.
 */
@Composable
fun UpdateReadyBanner(
    modifier: Modifier = Modifier,
    onRestartClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1F),
                text = stringResource(R.string.update_ready_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            androidx.compose.material3.TextButton(onClick = onRestartClick) {
                Text(text = stringResource(R.string.update_restart))
            }
        }
    }
}
