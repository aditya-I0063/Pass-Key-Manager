package com.bhardwaj.passkey.presentation.screens.detail_screen.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.domain.events.DetailEvents
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
fun DetailsItem(
    modifier: Modifier = Modifier,
    scope: ReorderableCollectionItemScope? = null,
    details: Details,
    onEvent: (DetailEvents) -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .background(Color.Transparent)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                modifier = scope?.let {
                    with(it) {
                        Modifier.draggableHandle(
                            onDragStarted = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
                                }
                            },
                            onDragStopped = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                }
                            },
                        )
                    }
                } ?: Modifier,
                imageVector = Icons.Default.Apps,
                contentDescription = stringResource(R.string.cd_drag_handle),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1F)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            onEvent(DetailEvents.OnLongPress(details.answer))
                        },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                text = details.answer,
                fontFamily = Poppins,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (scope != null) {
                Icon(
                    modifier = Modifier.clickable(
                        onClick = {
                            onEvent(DetailEvents.OnChangeClick(details))
                        },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.cd_edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .offset(y = (-6).dp)
                .background(MaterialTheme.colorScheme.background),
            text = details.question,
            fontFamily = BebasNeue,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}