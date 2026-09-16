package com.bhardwaj.passkey.presentation.screens.detail_screen

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
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
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.events.DetailEvents
import com.bhardwaj.passkey.domain.viewModels.DetailViewModel
import com.bhardwaj.passkey.presentation.screens.common.PassKeyButton
import com.bhardwaj.passkey.presentation.screens.common.PasskeySearchBar
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.DetailsBottomSheet
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.DetailsItem
import com.bhardwaj.passkey.presentation.screens.detail_screen.components.PasswordSettingsSheet
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.utils.ButtonType
import com.bhardwaj.passkey.utils.asString
import com.bhardwaj.passkey.utils.SecureClipboard
import com.bhardwaj.passkey.utils.UiText
import com.bhardwaj.passkey.utils.UiEvents
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    onPopBackStack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val detailTitle by viewModel.detailTitle.collectAsState()
    val detailResponse by viewModel.detailResponse.collectAsState()
    val isEditingSheet by viewModel.isEditingSheet.collectAsState()
    // Resolved here rather than in the ViewModel so it follows the app locale.
    val bottomSheetHeading =
        stringResource(if (isEditingSheet) R.string.edit else R.string.add)
    val searchText by viewModel.searchText.collectAsState()
    val details by viewModel.details.collectAsState(initial = emptyList())

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onEvent(DetailEvents.OnReorderDetails(from, to))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }
    val isPasswordSettingsOpen = viewModel.isPasswordSettingsOpen

    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvents.PopBackStack -> onPopBackStack()
                is UiEvents.ShowSnackBar -> {
                    scope.launch {
                        snackBarHostState.showSnackbar(
                            message = event.message.asString(context),
                            actionLabel = event.action?.asString(context)
                        )
                    }
                }
                is UiEvents.CopyToClipboard -> {
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


                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onEvent(DetailEvents.OnAddDetailClick)
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

                if (searchText.isNotBlank() || details.isNotEmpty()) {
                    PasskeySearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        query = searchText,
                        onQueryChange = { viewModel.onEvent(DetailEvents.OnSearchTextUpdate(newText = it)) },
                        onTrailingIconClick = {
                            viewModel.onEvent(DetailEvents.OnSearchTextUpdate(newText = ""))
                        }
                    )
                }

                if (details.isEmpty()) {
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
                            items = details,
                            key = { item -> "${item.detailsId}" }
                        ) { detail ->
                            if (searchText.isNotBlank()) {
                                DetailsItem(details = detail, onEvent = viewModel::onEvent)
                            } else {
                                val state = rememberSwipeToDismissBoxState(
                                    initialValue = SwipeToDismissBoxValue.Settled,
                                    positionalThreshold = { totalDistance -> totalDistance * 0.6f }
                                )

                                LaunchedEffect(state.currentValue) {
                                    if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.onEvent(DetailEvents.OnSwipedLeft(detail))
                                        state.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                }
                                ReorderableItem(
                                    reorderableLazyColumnState,
                                    "${detail.detailsId}"
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
                                                details = detail,
                                                onEvent = viewModel::onEvent
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
            if (viewModel.isSheetOpen) {
                DetailsBottomSheet(
                    modifier = Modifier,
                    bottomSheetHeading = bottomSheetHeading,
                    detailTitle = detailTitle,
                    detailResponse = detailResponse,
                    onTitleChange = {
                        viewModel.onEvent(DetailEvents.OnTitleChange(it))
                    },
                    onDescriptionChange = {
                        viewModel.onEvent(DetailEvents.OnDescriptionChange(it))
                    },
                    onDismiss = {
                        viewModel.onEvent(DetailEvents.OnDismissBottomSheet)
                    },
                    onSave = {
                        viewModel.onEvent(DetailEvents.OnSaveClick)
                    },
                    onGeneratePasswordClicked = {
                        viewModel.onEvent(DetailEvents.OnGeneratePasswordClick)
                    },
                    onPasswordSettingsClicked = {
                        viewModel.onEvent(DetailEvents.OnPasswordSettingsClick)
                    }
                )
            }
        }
        if (isPasswordSettingsOpen) {
            PasswordSettingsSheet(
                length = viewModel.passwordLength,
                includeUpper = viewModel.includeUpper,
                includeLower = viewModel.includeLower,
                includeNumbers = viewModel.includeNumbers,
                includeSpecial = viewModel.includeSpecial,
                onPasswordLengthChange = {
                    viewModel.onEvent(DetailEvents.OnPasswordLengthChange(it))
                },
                onDismissPasswordSettings = {
                    viewModel.onEvent(DetailEvents.OnDismissPasswordSettings)
                },
                onTogglePasswordOption = { type, checked ->
                    viewModel.onEvent(DetailEvents.OnTogglePasswordOption(type, checked))
                }
            )
        }
        if (viewModel.isAlertOpen) {
            AlertDialog(
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                containerColor = MaterialTheme.colorScheme.background,
                onDismissRequest = {
                    viewModel.onEvent(DetailEvents.OnDismissAlertDialog)
                },
                title = { Text(text = stringResource(id = R.string.confirm_delete_title)) },
                text = { Text(text = stringResource(id = R.string.confirm_delete_description_question)) },
                confirmButton = {
                    PassKeyButton(
                        onClick = {
                            viewModel.onEvent(DetailEvents.OnDeleteClick)
                        },
                        text = stringResource(id = R.string.delete),
                        buttonType = ButtonType.DEFAULT
                    )
                },
                dismissButton = {
                    PassKeyButton(
                        onClick = {
                            viewModel.onEvent(DetailEvents.OnCancelClick)
                        },
                        text = stringResource(id = R.string.cancel),
                        buttonType = ButtonType.OUTLINED
                    )
                },
            )
        }
    }
}