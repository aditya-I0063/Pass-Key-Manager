package com.bhardwaj.passkey.presentation.screens.detail_screen

import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.PasswordCharacterClass
import com.bhardwaj.passkey.domain.model.PasswordPolicy
import com.bhardwaj.passkey.utils.UiText

data class DetailState(
    val query: String = "",
    val items: List<Detail> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null means the add/edit sheet is open. */
    val editor: Editor? = null,
    /** Non-null means the delete confirmation is showing; the row is hidden but not deleted. */
    val pendingDelete: Detail? = null,
    val policy: PasswordPolicy = PasswordPolicy(),
    val isPolicySheetOpen: Boolean = false
) {
    data class Editor(
        val question: String = "",
        val answer: String = "",
        val editingId: Long? = null,
        /**
         * Whether the current answer came out of the generator. A generated value is
         * unambiguously a secret, so the analyser never has to guess for it.
         */
        val wasGenerated: Boolean = false
    ) {
        val isEdit: Boolean get() = editingId != null
    }

    val showEmptyState: Boolean get() = !isLoading && items.isEmpty() && query.isBlank()
}

sealed interface DetailIntent {
    data class QueryChanged(val query: String) : DetailIntent

    data object AddClicked : DetailIntent
    data class EditClicked(val detail: Detail) : DetailIntent
    data class QuestionChanged(val question: String) : DetailIntent
    data class AnswerChanged(val answer: String) : DetailIntent
    data object SaveClicked : DetailIntent
    data object EditorDismissed : DetailIntent

    data class SwipedToDelete(val detail: Detail) : DetailIntent
    data object DeleteConfirmed : DetailIntent
    data object DeleteCancelled : DetailIntent

    data class Moved(val fromIndex: Int, val toIndex: Int) : DetailIntent
    data class LongPressed(val value: String) : DetailIntent
    data object BackClicked : DetailIntent

    // Generator
    data object PolicyClicked : DetailIntent
    data object PolicyDismissed : DetailIntent
    data class LengthChanged(val length: Int) : DetailIntent
    /** Typed, unlike the old event which dispatched on a raw String. */
    data class CharacterClassToggled(
        val characterClass: PasswordCharacterClass,
        val enabled: Boolean
    ) : DetailIntent
    data object GenerateClicked : DetailIntent
}

sealed interface DetailEffect {
    data class ShowSnackbar(val message: UiText) : DetailEffect
    data object PopBackStack : DetailEffect
    data class CopyToClipboard(val value: String, val isSensitive: Boolean) : DetailEffect
}
