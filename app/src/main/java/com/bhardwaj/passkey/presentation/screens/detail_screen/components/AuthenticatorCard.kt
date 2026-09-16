package com.bhardwaj.passkey.presentation.screens.detail_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.TotpEntry
import com.bhardwaj.passkey.domain.totp.TotpGenerator
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import kotlinx.coroutines.delay

/**
 * One authenticator, with its code and the time left before it rolls.
 *
 * The clock lives here rather than in the ViewModel on purpose: a code is derived from the
 * secret and the time, not state anyone edits, and a per-second emission in DetailState would
 * recompose the whole entry list once a second for a value only this row shows.
 */
@Composable
fun AuthenticatorCard(
    modifier: Modifier = Modifier,
    entry: TotpEntry,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit
) {
    val epochSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(1_000)
        }
    }

    val code = remember(entry, epochSeconds / entry.config.periodSeconds) {
        TotpGenerator.generate(entry.config, epochSeconds)
    }
    val remaining = TotpGenerator.secondsRemaining(entry.config, epochSeconds)

    Box(modifier = modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    onClick = { onCopy(code) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1F)) {
                Text(
                    // Grouped in halves, the way every authenticator shows a code, because it
                    // is read off the screen and typed somewhere else.
                    text = code.grouped(),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = entry.label,
                    fontFamily = Poppins,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                progress = { remaining.toFloat() / entry.config.periodSeconds },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Icon(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clickable(
                        onClick = onDelete,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_remove_authenticator),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.background),
            text = stringResource(R.string.authenticator),
            fontFamily = BebasNeue,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun String.grouped(): String {
    val half = (length + 1) / 2
    return "${substring(0, half)} ${substring(half)}"
}
