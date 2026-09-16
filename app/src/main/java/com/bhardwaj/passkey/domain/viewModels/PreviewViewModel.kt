package com.bhardwaj.passkey.domain.viewModels

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.local.entity.Preview
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.events.PreviewEvents
import com.bhardwaj.passkey.presentation.navigation.Routes
import com.bhardwaj.passkey.utils.Categories
import com.bhardwaj.passkey.utils.Constants.Companion.BOTTOM_SHEET_HEADING
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_CATEGORY_NAME
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_HEADING
import com.bhardwaj.passkey.utils.UiEvents
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val appContext: Application,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    val categoryName = savedStateHandle.getStateFlow(PREVIEW_CATEGORY_NAME, Categories.BANKS.name)
    val previewHeading = savedStateHandle.getStateFlow(PREVIEW_HEADING, "")
    val bottomSheetHeading = savedStateHandle.getStateFlow(BOTTOM_SHEET_HEADING, "")

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    val previews: StateFlow<List<Preview>> = repository.getPreviews()
        .combine(categoryName) { previews, categoryName ->
            previews.filter { it.categoryName == Categories.valueOf(categoryName) }
        }.combine(searchText) { previews, text ->
            if (text.isBlank()) {
                previews
            } else {
                previews.filter { it.heading.contains(text, ignoreCase = true) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
                savedStateHandle[BOTTOM_SHEET_HEADING] =
                    UiText.StringResource(R.string.add).asString(context = appContext)
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
                    repository.getPreviewById(event.preview.previewId!!)?.let { preview ->
                        isSheetOpen = true
                        savedStateHandle[BOTTOM_SHEET_HEADING] =
                            UiText.StringResource(R.string.edit).asString(context = appContext)
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
                                    .asString(context = appContext)
                            )
                        )
                        return@launch
                    }

                    val newPreview = Preview(
                        heading = previewHeading.value.trim(),
                        categoryName = Categories.valueOf(categoryName.value)
                    )

                    val existingPreview = repository.getPreviewByHeading(
                        newPreview.heading,
                        newPreview.categoryName.toString()
                    )
                    // When editing, the lookup finds the very row being edited. Treating that as
                    // a clash meant saving an edit without renaming it reported "heading exists"
                    // and silently discarded the edit.
                    val isClashWithAnotherRow =
                        existingPreview != null && existingPreview.previewId != preview?.previewId
                    if (isClashWithAnotherRow) {
                        preview = null
                        savedStateHandle[PREVIEW_HEADING] = ""
                        isSheetOpen = false
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.heading_exists)
                                    .asString(context = appContext)
                            )
                        )
                        return@launch
                    }

                    preview?.let {
                        repository.upsertPreview(
                            it.copy(
                                heading = newPreview.heading,
                                categoryName = newPreview.categoryName
                            )
                        )
                    } ?: repository.upsertPreview(newPreview)

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
                val clipboardManager =
                    appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData =
                    ClipData.newPlainText("Text Copied Successfully.", event.previewHeading)
                clipboardManager.setPrimaryClip(clipData)
                sendUiEvents(
                    UiEvents.ShowSnackBar(
                        message = UiText.StringResource(R.string.copied)
                            .asString(context = appContext)
                    )
                )
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
                    deletedPreview?.let { repository.upsertPreview(it) }
                    deletedPreview = null
                }
            }

            PreviewEvents.OnCancelClick -> {
                viewModelScope.launch {
                    isAlertOpen = false
                    deletedPreview?.let { repository.upsertPreview(it) }
                    deletedPreview = null
                }
            }

            is PreviewEvents.OnDeleteClick -> {
                viewModelScope.launch {
                    deletedPreview?.let { repository.deleteDetailByPreviewId(it.previewId!!) }
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

                    updatedList.forEach { preview ->
                        repository.updatePreviewSequence(
                            previewId = preview.previewId!!,
                            sequence = preview.sequence
                        )
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