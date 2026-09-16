package com.bhardwaj.passkey.presentation.screens.preview_screen

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.bhardwaj.passkey.domain.model.Preview

sealed interface PreviewEvents {
    // Dialogs.
    data class OnHeadingChange(val newHeading: String) : PreviewEvents
    data object OnSaveClick : PreviewEvents
    data object OnCancelClick : PreviewEvents
    data object OnDeleteClick : PreviewEvents
    data object OnDismissBottomSheet : PreviewEvents
    data object OnDismissAlertDialog : PreviewEvents

    // Single Item.
    data class OnLongPress(val previewHeading: String) : PreviewEvents
    data class OnPreviewClick(val previewId: Long) : PreviewEvents
    data class OnReorderPreview(val from: LazyListItemInfo, val to: LazyListItemInfo) :
        PreviewEvents

    data class OnChangeClick(val preview: Preview) : PreviewEvents
    data class OnSwipedLeft(val preview: Preview) : PreviewEvents

    // Floating Action Button
    data object OnAddPreviewClick : PreviewEvents

    // Search
    data class OnSearchTextUpdate(val newText: String) : PreviewEvents

    // Bottom Navigation.
    data class OnBottomNavigationClick(val newTitle: String) : PreviewEvents

    // Settings
    data object OnSettingsClick : PreviewEvents
}