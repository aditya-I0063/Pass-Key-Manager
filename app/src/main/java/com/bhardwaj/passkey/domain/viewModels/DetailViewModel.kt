package com.bhardwaj.passkey.domain.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.events.DetailEvents
import com.bhardwaj.passkey.utils.Constants.Companion.BOTTOM_SHEET_HEADING
import com.bhardwaj.passkey.utils.PasswordGenerator
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
class DetailViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    // Deliberately NOT in SavedStateHandle. That is serialized into the saved-instance-state
    // bundle, which the system writes to disk under /data/system_ce/<user>/ for task
    // persistence - so the in-progress secret, including a freshly generated password, would be
    // stored in cleartext outside the encrypted database. Losing a half-typed entry to process
    // death is the correct trade here.
    private val _detailTitle = MutableStateFlow("")
    val detailTitle = _detailTitle.asStateFlow()

    private val _detailResponse = MutableStateFlow("")
    val detailResponse = _detailResponse.asStateFlow()
    /** true = editing an existing row, false = adding. Resolved to text by the UI. */
    val isEditingSheet = savedStateHandle.getStateFlow(BOTTOM_SHEET_HEADING, false)
    val previewId = savedStateHandle.get<Long>("previewId") ?: -1

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    val details: StateFlow<List<Details>> = repository.getDetailsByPreviewId(previewId = previewId)
        .combine(searchText) { details, text ->
            if (text.isBlank()) {
                details
            } else {
                details.filter {
                    it.question.contains(
                        text,
                        ignoreCase = true
                    ) or it.answer.contains(text, ignoreCase = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var isSheetOpen by mutableStateOf(false)
        private set

    var isAlertOpen by mutableStateOf(false)
        private set

    var detail by mutableStateOf<Details?>(null)
        private set

    var isPasswordSettingsOpen by mutableStateOf(false)
        private set

    var passwordLength by mutableFloatStateOf(12f)
        private set
    var includeUpper by mutableStateOf(true)
        private set
    var includeLower by mutableStateOf(true)
        private set
    var includeNumbers by mutableStateOf(true)
        private set
    var includeSpecial by mutableStateOf(false)
        private set

    private var deletedDetail: Details? = null

    fun onEvent(event: DetailEvents) {
        when (event) {
            DetailEvents.OnAddDetailClick -> {
                savedStateHandle[BOTTOM_SHEET_HEADING] = false
                isSheetOpen = true
                _searchText.value = ""
            }

            DetailEvents.OnDismissBottomSheet -> {
                _detailTitle.value = ""
                _detailResponse.value = ""
                isSheetOpen = false
            }

            is DetailEvents.OnChangeClick -> {
                viewModelScope.launch {
                    _searchText.value = ""
                    repository.getDetailById(event.details.detailsId!!)?.let { detail ->
                        isSheetOpen = true
                        savedStateHandle[BOTTOM_SHEET_HEADING] = true
                        _detailTitle.value = event.details.question
                        _detailResponse.value = event.details.answer
                        this@DetailViewModel.detail = detail
                    }
                }
            }

            is DetailEvents.OnTitleChange -> {
                _detailTitle.value = event.newTitle
            }

            is DetailEvents.OnDescriptionChange -> {
                _detailResponse.value = event.newDescription
            }

            DetailEvents.OnSaveClick -> {
                viewModelScope.launch {
                    if (detailTitle.value.isBlank() or detailResponse.value.isBlank()) {
                        isSheetOpen = false
                        _detailTitle.value = ""
                        _detailResponse.value = ""
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.enter_valid_title_n_response)
                            )
                        )
                        return@launch
                    }
                    if (previewId.toInt() == -1) {
                        isSheetOpen = false
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.something_went_wrong)
                            )
                        )
                        return@launch
                    }
                    val newDetail = Details(
                        previewId = previewId,
                        question = detailTitle.value.trim(),
                        answer = detailResponse.value.trim(),
                    )

                    detail?.let {
                        repository.upsertDetails(
                            it.copy(
                                previewId = previewId,
                                // Trimmed to match the create path above. A trailing space in a
                                // stored password fails silently wherever it is pasted.
                                question = newDetail.question,
                                answer = newDetail.answer,
                            )
                        )
                    } ?: repository.upsertDetails(newDetail)

                    detail = null
                    _detailTitle.value = ""
                    _detailResponse.value = ""
                    isSheetOpen = false
                }
            }

            is DetailEvents.OnLongPress -> {
                sendUiEvents(UiEvents.CopyToClipboard(value = event.detailsDescription, isSensitive = true))
            }

            is DetailEvents.OnSwipedLeft -> {
                viewModelScope.launch {
                    isAlertOpen = true
                    repository.deleteDetail(event.details)
                    deletedDetail = event.details
                }
            }

            DetailEvents.OnDismissAlertDialog -> {
                viewModelScope.launch {
                    isAlertOpen = false
                    deletedDetail?.let { repository.upsertDetails(it) }
                    deletedDetail = null
                }
            }

            DetailEvents.OnCancelClick -> {
                viewModelScope.launch {
                    isAlertOpen = false
                    deletedDetail?.let { repository.upsertDetails(it) }
                    deletedDetail = null
                }
            }

            is DetailEvents.OnDeleteClick -> {
                isAlertOpen = false
                deletedDetail = null
            }

            is DetailEvents.OnSearchTextUpdate -> {
                _searchText.value = event.newText
            }

            is DetailEvents.OnReorderDetails -> {
                viewModelScope.launch {
                    val newList = details.value.toMutableList().apply {
                        add(event.to.index, removeAt(event.from.index))
                    }

                    val updatedList = newList.mapIndexed { index, detail ->
                        detail.copy(sequence = index.toLong())
                    }

                    repository.runInTransaction {
                        updatedList.forEach { detail ->
                            repository.updateDetailSequence(
                                detailId = detail.detailsId!!,
                                sequence = detail.sequence
                            )
                        }
                    }
                }
            }

            DetailEvents.OnGeneratePasswordClick -> {
                val newPassword = PasswordGenerator.generate(
                    length = passwordLength.toInt(),
                    includeUpper = includeUpper,
                    includeLower = includeLower,
                    includeNumbers = includeNumbers,
                    includeSpecial = includeSpecial
                )
                _detailResponse.value = newPassword
            }

            DetailEvents.OnPasswordSettingsClick -> {
                isPasswordSettingsOpen = true
            }

            DetailEvents.OnDismissPasswordSettings -> {
                isPasswordSettingsOpen = false
            }

            is DetailEvents.OnPasswordLengthChange -> {
                passwordLength = event.length
            }

            is DetailEvents.OnTogglePasswordOption -> {
                when (event.option) {
                    "Upper" -> includeUpper = event.value
                    "Lower" -> includeLower = event.value
                    "Number" -> includeNumbers = event.value
                    "Special" -> includeSpecial = event.value
                }
                if (!includeUpper && !includeLower && !includeNumbers && !includeSpecial) {
                    includeLower = true
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