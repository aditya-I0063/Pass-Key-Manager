package com.bhardwaj.passkey.presentation.screens.preview_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewEvents
import com.bhardwaj.passkey.presentation.navigation.Routes
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.utils.Constants.Companion.BOTTOM_SHEET_HEADING
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_CATEGORY_NAME
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_HEADING
import com.bhardwaj.passkey.utils.UiEvents
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val categoryName = savedStateHandle.getStateFlow(PREVIEW_CATEGORY_NAME, Category.BANKS.name)
    val previewHeading = savedStateHandle.getStateFlow(PREVIEW_HEADING, "")
    /** true = editing an existing row, false = adding. Resolved to text by the UI. */
    val isEditingSheet = savedStateHandle.getStateFlow(BOTTOM_SHEET_HEADING, false)

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val previews: StateFlow<List<Preview>> = categoryName
        // Filtered in SQL now, so reordering renumbers within a single category instead of a
        // list that had already been narrowed in memory.
        .flatMapLatest { name -> repository.getPreviewsByCategory(Category.valueOf(name)) }
        .combine(searchText) { previews, text ->
            if (text.isBlank()) previews
            else previews.filter { it.heading.contains(text, ignoreCase = true) }
        }
        // WhileSubscribed, not Lazily: Lazily keeps the Room invalidation observer alive for the
        // ViewModel's entire life even while the screen is backgrounded.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isSheetOpen by mutableStateOf(false)
        private set

    var isAlertOpen by mutableStateOf(false)
        private set

    var preview by mutableStateOf<Preview?>(null)
        private set

    private var deletedPreview: Preview? = null

    fun onEvent(event: PreviewEvents) {
        when (event) {
            is PreviewEvents.OnBottomNavigationClick -> {
                if (categoryName.value != event.newTitle) {
                    savedStateHandle[PREVIEW_CATEGORY_NAME] = event.newTitle
                    _searchText.value = ""
                }
            }

            PreviewEvents.OnAddPreviewClick -> {
                savedStateHandle[BOTTOM_SHEET_HEADING] = false
                isSheetOpen = true
                _searchText.value = ""
            }

            PreviewEvents.OnDismissBottomSheet -> {
                savedStateHandle[PREVIEW_HEADING] = ""
                isSheetOpen = false
            }

            is PreviewEvents.OnChangeClick -> {
                viewModelScope.launch {
                    _searchText.value = ""
                    repository.getPreviewById(event.preview.id)?.let { preview ->
                        isSheetOpen = true
                        savedStateHandle[BOTTOM_SHEET_HEADING] = true
                        savedStateHandle[PREVIEW_HEADING] = event.preview.heading
                        this@PreviewViewModel.preview = preview
                    }
                }
            }

            is PreviewEvents.OnHeadingChange -> {
                savedStateHandle[PREVIEW_HEADING] = event.newHeading
            }

            is PreviewEvents.OnSaveClick -> {
                viewModelScope.launch {
                    if (previewHeading.value.isBlank()) {
                        isSheetOpen = false
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.enter_valid_heading)
                            )
                        )
                        return@launch
                    }

                    val heading = previewHeading.value.trim()
                    val category = Category.valueOf(categoryName.value)

                    val existingPreview = repository.getPreviewByHeading(heading, category)
                    // When editing, the lookup finds the very row being edited. Treating that as
                    // a clash meant saving an edit without renaming it reported "heading exists"
                    // and silently discarded the edit.
                    val isClashWithAnotherRow =
                        existingPreview != null && existingPreview.id != preview?.id
                    if (isClashWithAnotherRow) {
                        preview = null
                        savedStateHandle[PREVIEW_HEADING] = ""
                        isSheetOpen = false
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.heading_exists)
                            )
                        )
                        return@launch
                    }

                    preview?.let {
                        repository.updatePreview(it.copy(heading = heading, category = category))
                    } ?: repository.createPreview(heading = heading, category = category)

                    preview = null
                    savedStateHandle[PREVIEW_HEADING] = ""
                    isSheetOpen = false
                }
            }

            is PreviewEvents.OnPreviewClick -> {
                _searchText.value = ""
                sendUiEvents(UiEvents.Navigate(Routes.DETAILS_PAGE + "?previewId=${event.previewId}"))
            }

            is PreviewEvents.OnLongPress -> {
                sendUiEvents(UiEvents.CopyToClipboard(value = event.previewHeading, isSensitive = false))
            }

            PreviewEvents.OnSettingsClick -> {
                _searchText.value = ""
                sendUiEvents(UiEvents.Navigate(Routes.SETTINGS_PAGE))
            }

            is PreviewEvents.OnSwipedLeft -> {
                viewModelScope.launch {
                    isAlertOpen = true
                    repository.deletePreview(event.preview)
                    deletedPreview = event.preview
                }
            }

            PreviewEvents.OnDismissAlertDialog -> {
                viewModelScope.launch {
                    isAlertOpen = false
                    deletedPreview?.let { repository.updatePreview(it) }
                    deletedPreview = null
                }
            }

            PreviewEvents.OnCancelClick -> {
                viewModelScope.launch {
                    isAlertOpen = false
                    deletedPreview?.let { repository.updatePreview(it) }
                    deletedPreview = null
                }
            }

            is PreviewEvents.OnDeleteClick -> {
                viewModelScope.launch {
                    deletedPreview?.let { repository.deleteDetailsByPreviewId(it.id) }
                    isAlertOpen = false
                    deletedPreview = null
                }
            }

            is PreviewEvents.OnSearchTextUpdate -> {
                _searchText.value = event.newText
            }

            is PreviewEvents.OnReorderPreview -> {
                viewModelScope.launch {
                    val newList = previews.value.toMutableList().apply {
                        add(event.to.index, removeAt(event.from.index))
                    }

                    val updatedList = newList.mapIndexed { index, preview ->
                        preview.copy(sequence = index.toLong())
                    }

                    // One transaction, not N separate writes: a reorder interrupted midway
                    // used to leave the list in a partially renumbered state.
                    repository.runInTransaction {
                        updatedList.forEach { preview ->
                            repository.updatePreviewSequence(
                                previewId = preview.id,
                                sequence = preview.sequence
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendUiEvents(events: UiEvents) {
        viewModelScope.launch {
            _uiEvents.send(events)
        }
    }
}