package com.bhardwaj.passkey.presentation.screens.detail_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailState
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import java.text.DateFormat
import java.util.Date

/**
 * The values a secret used to hold, newest first.
 *
 * Read-only, and each row copies rather than restores: a password that was replaced was usually
 * replaced for a reason, and putting it back with one tap is the wrong default. Copying covers
 * the case that actually happens - a site that has not caught up with the change yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHistorySheet(
    history: DetailState.History,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    ModalBottomSheet(
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        dragHandle = {},
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.password_history),
                fontFamily = BebasNeue,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = history.detail.question,
                fontFamily = Poppins,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )

            if (history.entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.password_history_empty),
                    fontFamily = Poppins,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                LazyColumn {
                    items(history.entries, key = { it.id }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClick = { onCopy(entry.answer) },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = entry.answer,
                                fontFamily = Poppins,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = dateFormat.format(Date(entry.changedAt)),
                                fontFamily = Poppins,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
