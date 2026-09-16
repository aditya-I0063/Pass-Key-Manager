package com.bhardwaj.passkey.presentation.screens.preview_screen

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FabPosition
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.events.PreviewEvents
import com.bhardwaj.passkey.domain.viewModels.PreviewViewModel
import com.bhardwaj.passkey.presentation.screens.common.PassKeyButton
import com.bhardwaj.passkey.presentation.screens.common.PasskeySearchBar
import com.bhardwaj.passkey.presentation.screens.preview_screen.components.MainBottomNavigation
import com.bhardwaj.passkey.presentation.screens.preview_screen.components.PreviewBottomSheet
import com.bhardwaj.passkey.presentation.screens.preview_screen.components.PreviewItem
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.utils.ButtonType
import com.bhardwaj.passkey.utils.Categories
import com.bhardwaj.passkey.utils.asString
import com.bhardwaj.passkey.utils.SecureClipboard
import com.bhardwaj.passkey.utils.UiText
import com.bhardwaj.passkey.utils.UiEvents
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewScreen(
    onNavigate: (UiEvents.Navigate) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val categoryName by viewModel.categoryName.collectAsState()
    // Localized display names come from Categories.labelRes, which the bottom navigation
    // also uses, so the two can no longer drift apart.
    val categoryNameMap = Categories.entries.associate { it.name to stringResource(it.labelRes) }

    val previewHeading by viewModel.previewHeading.collectAsState()
    val isEditingSheet by viewModel.isEditingSheet.collectAsState()
    // Resolved here rather than in the ViewModel so it follows the app locale.
    val bottomSheetHeading =
        stringResource(if (isEditingSheet) R.string.edit else R.string.add)
    val searchText by viewModel.searchText.collectAsState()
    val previews by viewModel.previews.collectAsState()

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onEvent(PreviewEvents.OnReorderPreview(from, to))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }

    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvents.Navigate -> onNavigate(event)
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
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.background
            ) {
                MainBottomNavigation(
                    selectedIndex = selectedIndex,
                    onItemClick = { newIndex, newTitle ->
                        viewModel.onEvent(PreviewEvents.OnBottomNavigationClick(newTitle))
                        selectedIndex = newIndex
                    }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.offset(y = 64.dp),
                onClick = { viewModel.onEvent(PreviewEvents.OnAddPreviewClick) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                content = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_add_entry),
                    )
                }
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.icon_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { viewModel.onEvent(PreviewEvents.OnSettingsClick) }
                            )
                    )
                }
                categoryNameMap[categoryName]?.let {
                    Text(
                        text = it,
                        fontFamily = BebasNeue,
                        fontSize = 64.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (searchText.isNotBlank() || previews.isNotEmpty()) {
                    PasskeySearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        query = searchText,
                        onQueryChange = { viewModel.onEvent(PreviewEvents.OnSearchTextUpdate(newText = it)) },
                        onTrailingIconClick = {
                            viewModel.onEvent(PreviewEvents.OnSearchTextUpdate(newText = ""))
                        }
                    )
                }

                if (previews.isEmpty()) {
                    Image(
                        modifier = Modifier
                            .weight(1F)
                            .fillMaxWidth()
                            .padding(16.dp),
                        painter = painterResource(id = R.drawable.icon_empty_list),
                        contentDescription = stringResource(id = R.string.no_previews_found)
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                    ) {
                        items(
                            items = previews,
                            key = { item -> "${item.previewId}" }
                        ) { preview ->
                            if (searchText.isNotBlank()) {
                                PreviewItem(
                                    preview = preview,
                                    onEvent = viewModel::onEvent
                                )
                            } else {
                                val state = rememberSwipeToDismissBoxState(
                                    initialValue = SwipeToDismissBoxValue.Settled,
                                    positionalThreshold = { totalDistance -> totalDistance * 0.6f }
                                )

                                LaunchedEffect(state.currentValue) {
                                    if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.onEvent(PreviewEvents.OnSwipedLeft(preview))
                                        state.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                }
                                ReorderableItem(
                                    reorderableLazyColumnState,
                                    "${preview.previewId}"
                                ) {
                                    SwipeToDismissBox(
                                        state = state,
                                        backgroundContent = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
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
                                            PreviewItem(
                                                scope = this@ReorderableItem,
                                                preview = preview,
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
                PreviewBottomSheet(
                    bottomSheetHeading = bottomSheetHeading,
                    previewHeading = previewHeading,
                    onDismiss = {
                        viewModel.onEvent(PreviewEvents.OnDismissBottomSheet)
                    },
                    onHeadingChange = {
                        viewModel.onEvent(PreviewEvents.OnHeadingChange(it))
                    },
                    onSave = {
                        viewModel.onEvent(PreviewEvents.OnSaveClick)
                    }
                )
            }
        }
        if (viewModel.isAlertOpen) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.background,
                onDismissRequest = {
                    viewModel.onEvent(PreviewEvents.OnDismissAlertDialog)
                },
                title = { Text(text = stringResource(id = R.string.confirm_delete_title)) },
                text = { Text(text = stringResource(id = R.string.confirm_delete_description_heading)) },
                confirmButton = {
                    PassKeyButton(
                        onClick = {
                            viewModel.onEvent(PreviewEvents.OnDeleteClick)
                        },
                        text = stringResource(id = R.string.delete),
                        buttonType = ButtonType.DEFAULT
                    )
                },
                dismissButton = {
                    PassKeyButton(
                        onClick = {
                            viewModel.onEvent(PreviewEvents.OnCancelClick)
                        },
                        text = stringResource(id = R.string.cancel),
                        buttonType = ButtonType.OUTLINED
                    )
                }
            )
        }
    }
}