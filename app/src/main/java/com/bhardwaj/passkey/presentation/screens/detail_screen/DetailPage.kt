package com.bhardwaj.passkey.presentation.screens.detail_screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bhardwaj.passkey.presentation.screens.common.ObserveAsEvents
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailViewModel
import com.bhardwaj.passkey.presentation.screens.common.PassKeyButton
import com.bhardwaj.passkey.presentation.screens.common.PasskeySearchBar
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.DetailsBottomSheet
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.DetailsItem
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.AuthenticatorCard
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.AuthenticatorDialog
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.PasswordHistorySheet
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.PasswordSettingsSheet
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.utils.ButtonType
import com.bhardwaj.passkey.utils.asString
import com.bhardwaj.passkey.utils.SecureClipboard
import com.bhardwaj.passkey.utils.UiText
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    onPopBackStack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Hoisted: a bound method reference allocates a new object per recomposition, so every
    // visible row recomposed on each keystroke.
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onIntent(DetailIntent.Moved(from.index, to.index))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }

    val context = LocalContext.current

    ObserveAsEvents(viewModel.effects) { event ->
            when (event) {
                is DetailEffect.PopBackStack -> onPopBackStack()
                is DetailEffect.ShowSnackbar -> {
                    scope.launch {
                        snackBarHostState.showSnackbar(
                            message = event.message.asString(context)
                        )
                    }
                }
                is DetailEffect.CopyToClipboard -> {
                    SecureClipboard.copy(
                        context = context,
                        text = event.value,
                        isSensitive = event.isSensitive
                    )
                    // Android 13+ shows its own copy confirmation, so a second one is noise.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        scope.launch {
                            snackBarHostState.showSnackbar(
                                message = UiText.StringResource(R.string.copied).asString(context)
                            )
                        }
                    }
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallFloatingActionButton(
                    modifier = Modifier.padding(bottom = 12.dp),
                    onClick = { onIntent(DetailIntent.AddAuthenticatorClicked) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    content = {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.add_authenticator),
                        )
                    },
                )
                FloatingActionButton(
                    onClick = {
                        onIntent(DetailIntent.AddClicked)
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    content = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_detail),
                        )
                    },
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(id = R.drawable.icon_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.details),
                    fontFamily = BebasNeue,
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                state.authenticators.forEach { entry ->
                    AuthenticatorCard(
                        entry = entry,
                        onCopy = { onIntent(DetailIntent.AuthenticatorCodeCopied(it)) },
                        onDelete = { onIntent(DetailIntent.AuthenticatorDeleteClicked(entry)) }
                    )
                }

                if (state.query.isNotBlank() || state.items.isNotEmpty()) {
                    PasskeySearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        query = state.query,
                        onQueryChange = { onIntent(DetailIntent.QueryChanged(it)) },
                        onTrailingIconClick = {
                            onIntent(DetailIntent.QueryChanged(""))
                        }
                    )
                }

                if (state.showEmptyState) {
                    Image(
                        modifier = Modifier
                            .weight(1F)
                            .fillMaxWidth()
                            .padding(16.dp),
                        painter = painterResource(id = R.drawable.icon_empty_list),
                        contentDescription = stringResource(id = R.string.no_details_found)
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                    ) {
                        items(
                            items = state.items,
                            key = { item -> item.id }
                        ) { detail ->
                            if (state.query.isNotBlank()) {
                                DetailsItem(detail = detail, onIntent = onIntent)
                            } else {
                                val state = rememberSwipeToDismissBoxState(
                                    initialValue = SwipeToDismissBoxValue.Settled,
                                    positionalThreshold = { totalDistance -> totalDistance * 0.6f }
                                )

                                LaunchedEffect(state.currentValue) {
                                    if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                        onIntent(DetailIntent.SwipedToDelete(detail))
                                        state.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                }
                                ReorderableItem(
                                    reorderableLazyColumnState,
                                    "${detail.id}"
                                ) {
                                    SwipeToDismissBox(
                                        state = state,
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 6.dp)
                                                    .padding(bottom = 24.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    modifier = Modifier.align(Alignment.CenterEnd),
                                                    tint = MaterialTheme.colorScheme.background
                                                )
                                            }
                                        },
                                        content = {
                                            DetailsItem(
                                                scope = this@ReorderableItem,
                                                detail = detail,
                                                onIntent = onIntent
                                            )
                                        },
                                        enableDismissFromEndToStart = true,
                                        enableDismissFromStartToEnd = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.editor?.let { editor ->
                DetailsBottomSheet(
                    modifier = Modifier,
                    bottomSheetHeading = stringResource(
                        if (editor.isEdit) R.string.edit else R.string.add
                    ),
                    detailTitle = editor.question,
                    detailResponse = editor.answer,
                    onTitleChange = {
                        onIntent(DetailIntent.QuestionChanged(it))
                    },
                    onDescriptionChange = {
                        onIntent(DetailIntent.AnswerChanged(it))
                    },
                    onDismiss = {
                        onIntent(DetailIntent.EditorDismissed)
                    },
                    onSave = {
                        onIntent(DetailIntent.SaveClicked)
                    },
                    onGeneratePasswordClicked = {
                        onIntent(DetailIntent.GenerateClicked)
                    },
                    onPasswordSettingsClicked = {
                        onIntent(DetailIntent.PolicyClicked)
                    }
                )
            }
        }
        state.totpEditor?.let { editor ->
            AuthenticatorDialog(
                editor = editor,
                onInputChange = { onIntent(DetailIntent.AuthenticatorInputChanged(it)) },
                onDismiss = { onIntent(DetailIntent.AuthenticatorEditorDismissed) },
                onSave = { onIntent(DetailIntent.AuthenticatorSaveClicked) }
            )
        }
        state.history?.let { history ->
            PasswordHistorySheet(
                history = history,
                onCopy = { onIntent(DetailIntent.LongPressed(it)) },
                onDismiss = { onIntent(DetailIntent.HistoryDismissed) }
            )
        }
        if (state.isPolicySheetOpen) {
            PasswordSettingsSheet(
                policy = state.policy,
                onPasswordLengthChange = { onIntent(DetailIntent.LengthChanged(it)) },
                onDismissPasswordSettings = { onIntent(DetailIntent.PolicyDismissed) },
                onToggleCharacterClass = { characterClass, enabled ->
                    onIntent(DetailIntent.CharacterClassToggled(characterClass, enabled))
                }
            )
        }
        if (state.pendingDelete != null) {
            AlertDialog(
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                containerColor = MaterialTheme.colorScheme.background,
                onDismissRequest = {
                    onIntent(DetailIntent.DeleteCancelled)
                },
                title = { Text(text = stringResource(id = R.string.confirm_delete_title)) },
                text = { Text(text = stringResource(id = R.string.confirm_delete_description_question)) },
                confirmButton = {
                    PassKeyButton(
                        onClick = {
                            onIntent(DetailIntent.DeleteConfirmed)
                        },
                        text = stringResource(id = R.string.delete),
                        buttonType = ButtonType.DEFAULT
                    )
                },
                dismissButton = {
                    PassKeyButton(
                        onClick = {
                            onIntent(DetailIntent.DeleteCancelled)
                        },
                        text = stringResource(id = R.string.cancel),
                        buttonType = ButtonType.OUTLINED
                    )
                },
            )
        }
    }
}