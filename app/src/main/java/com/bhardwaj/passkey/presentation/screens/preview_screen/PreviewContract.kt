package com.bhardwaj.passkey.presentation.screens.preview_screen

import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.utils.UiText

/**
 * The reference MVI contract for this app: one immutable state object, one intent type, one
 * effect type.
 *
 * Previously the screen read from a bag of separate sources - three SavedStateHandle flows, a
 * MutableStateFlow, three `mutableStateOf` properties and a plain `var` - which meant no single
 * value described what the screen was showing, and impossible combinations were representable.
 */
data class PreviewState(
    val category: Category = Category.BANKS,
    val query: String = "",
    val items: List<Preview> = emptyList(),
    /**
     * Distinguishes "still loading" from "genuinely empty". `stateIn` emits its initial value
     * first, so without this the empty-list illustration flashed on every entry to the screen.
     */
    val isLoading: Boolean = true,
    /** Non-null means the add/edit sheet is open. */
    val editor: Editor? = null,
    /**
     * Non-null means the delete confirmation is showing. The row is hidden from [items] but is
     * **not** deleted until confirmed - see PreviewViewModel.
     */
    val pendingDelete: Preview? = null
) {
    /**
     * Collapses four previously separate fields: a sheet-open boolean, the sheet's title, the
     * heading being typed, and the row being edited. The title was stored as an already-resolved
     * English string, which was both a localization bug and a modelling one.
     */
    data class Editor(
        val heading: String = "",
        val editingId: Long? = null
    ) {
        val isEdit: Boolean get() = editingId != null
    }

    val showEmptyState: Boolean get() = !isLoading && items.isEmpty() && query.isBlank()
}

sealed interface PreviewIntent {
    data class CategorySelected(val category: Category) : PreviewIntent
    data class QueryChanged(val query: String) : PreviewIntent

    data object AddClicked : PreviewIntent
    data class EditClicked(val preview: Preview) : PreviewIntent
    data class HeadingChanged(val heading: String) : PreviewIntent
    data object SaveClicked : PreviewIntent
    data object EditorDismissed : PreviewIntent

    data class SwipedToDelete(val preview: Preview) : PreviewIntent
    data object DeleteConfirmed : PreviewIntent
    data object DeleteCancelled : PreviewIntent

    /** Plain indices: the old event carried a Compose `LazyListItemInfo`. */
    data class Moved(val fromIndex: Int, val toIndex: Int) : PreviewIntent

    data class ItemClicked(val previewId: Long) : PreviewIntent
    data class ItemLongPressed(val heading: String) : PreviewIntent
    data object SettingsClicked : PreviewIntent
}

sealed interface PreviewEffect {
    data class ShowSnackbar(val message: UiText) : PreviewEffect
    data class Navigate(val route: NavRoute) : PreviewEffect
    data class CopyToClipboard(val value: String, val isSensitive: Boolean) : PreviewEffect
}
