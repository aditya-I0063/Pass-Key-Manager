package com.bhardwaj.passkey.presentation.screens.preview_screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_CATEGORY_NAME
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    // BUFFERED: with lifecycle-aware collection a backgrounded screen has no active collector,
    // and a rendezvous channel would suspend whoever emitted the effect.
    private val _effects = Channel<PreviewEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Survives process death, unlike the rest of the UI state. */
    private val category = savedStateHandle.getStateFlow(PREVIEW_CATEGORY_NAME, Category.BANKS.name)

    private val query = MutableStateFlow("")

    /** Editor and pending-delete only; the rest of the state is derived. */
    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(
        val editor: PreviewState.Editor? = null,
        val pendingDelete: Preview? = null
    )

    /**
     * State is *derived* from independent sources rather than accumulated into one mutable
     * object. Combining the public state back into itself would be a self-feeding loop that only
     * terminates because StateFlow deduplicates equal values.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PreviewState> = combine(
        category,
        query,
        transient,
        category.flatMapLatest { repository.getPreviewsByCategory(Category.valueOf(it)) }
    ) { categoryName, currentQuery, ui, items ->
        PreviewState(
            category = Category.valueOf(categoryName),
            query = currentQuery,
            items = items
                .filter { currentQuery.isBlank() || it.heading.contains(currentQuery, true) }
                // Hidden rather than deleted while the confirmation is up.
                .filterNot { it.id == ui.pendingDelete?.id },
            isLoading = false,
            editor = ui.editor,
            pendingDelete = ui.pendingDelete
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewState())

    fun onIntent(intent: PreviewIntent) {
        when (intent) {
            is PreviewIntent.CategorySelected -> {
                if (category.value != intent.category.name) {
                    savedStateHandle[PREVIEW_CATEGORY_NAME] = intent.category.name
                    query.value = ""
                }
            }

            is PreviewIntent.QueryChanged -> query.value = intent.query

            PreviewIntent.AddClicked -> {
                query.value = ""
                transient.update { it.copy(editor = PreviewState.Editor()) }
            }

            is PreviewIntent.EditClicked -> {
                query.value = ""
                transient.update {
                    it.copy(
                        editor = PreviewState.Editor(
                            heading = intent.preview.heading,
                            editingId = intent.preview.id
                        )
                    )
                }
            }

            is PreviewIntent.HeadingChanged ->
                transient.update { it.copy(editor = it.editor?.copy(heading = intent.heading)) }

            PreviewIntent.EditorDismissed -> transient.update { it.copy(editor = null) }

            PreviewIntent.SaveClicked -> save()

            is PreviewIntent.SwipedToDelete ->
                // Nothing is written yet. The previous implementation deleted the row here and
                // re-inserted it on cancel, so a process death while the dialog was open lost it.
                transient.update { it.copy(pendingDelete = intent.preview) }

            PreviewIntent.DeleteCancelled -> transient.update { it.copy(pendingDelete = null) }

            PreviewIntent.DeleteConfirmed -> {
                val target = transient.value.pendingDelete ?: return
                viewModelScope.launch {
                    repository.runInTransaction {
                        repository.deleteDetailsByPreviewId(target.id)
                        repository.deletePreview(target)
                    }
                    transient.update { it.copy(pendingDelete = null) }
                }
            }

            is PreviewIntent.Moved -> move(intent.fromIndex, intent.toIndex)

            is PreviewIntent.ItemClicked -> {
                query.value = ""
                emit(PreviewEffect.Navigate(NavRoute.Details(intent.previewId)))
            }

            is PreviewIntent.ItemLongPressed ->
                // A heading is not a secret, so it is not marked sensitive and is not auto-cleared.
                emit(PreviewEffect.CopyToClipboard(intent.heading, isSensitive = false))

            PreviewIntent.SettingsClicked -> {
                query.value = ""
                emit(PreviewEffect.Navigate(NavRoute.Settings))
            }
        }
    }

    private fun save() {
        val editor = transient.value.editor ?: return
        val heading = editor.heading.trim()
        if (heading.isBlank()) {
            transient.update { it.copy(editor = null) }
            emit(PreviewEffect.ShowSnackbar(UiText.StringResource(R.string.enter_valid_heading)))
            return
        }
        viewModelScope.launch {
            val currentCategory = Category.valueOf(category.value)
            val existing = repository.getPreviewByHeading(heading, currentCategory)
            // When editing, the lookup finds the very row being edited. Treating that as a clash
            // meant saving an edit without renaming it reported "heading exists" and silently
            // discarded the edit.
            if (existing != null && existing.id != editor.editingId) {
                transient.update { it.copy(editor = null) }
                emit(PreviewEffect.ShowSnackbar(UiText.StringResource(R.string.heading_exists)))
                return@launch
            }

            if (editor.editingId != null) {
                repository.getPreviewById(editor.editingId)?.let { existingRow ->
                    repository.updatePreview(existingRow.copy(heading = heading))
                }
            } else {
                repository.createPreview(heading = heading, category = currentCategory)
            }
            transient.update { it.copy(editor = null) }
        }
    }

    private fun move(fromIndex: Int, toIndex: Int) {
        val current = state.value.items
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val reordered = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        viewModelScope.launch {
            // One transaction, not N separate writes: an interrupted reorder used to leave the
            // list partially renumbered.
            repository.runInTransaction {
                reordered.forEachIndexed { index, preview ->
                    repository.updatePreviewSequence(preview.id, index.toLong())
                }
            }
        }
    }

    private fun emit(effect: PreviewEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

/** Local helper so intents read as transformations rather than assignments. */
private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
