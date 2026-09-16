package com.bhardwaj.passkey.presentation.screens.detail_screen

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.bhardwaj.passkey.domain.model.Detail

sealed interface DetailEvents {
    // Dialogs.
    data class OnTitleChange(val newTitle: String) : DetailEvents
    data class OnDescriptionChange(val newDescription: String) : DetailEvents
    data object OnSaveClick : DetailEvents
    data object OnCancelClick : DetailEvents
    data object OnDeleteClick : DetailEvents
    data object OnDismissBottomSheet : DetailEvents
    data object OnDismissAlertDialog : DetailEvents

    // Single Item.
    data class OnLongPress(val detailsDescription: String) : DetailEvents
    data class OnReorderDetails(val from: LazyListItemInfo, val to: LazyListItemInfo) : DetailEvents
    data class OnChangeClick(val details: Detail) : DetailEvents
    data class OnSwipedLeft(val details: Detail) : DetailEvents

    // Floating Action Button
    data object OnAddDetailClick : DetailEvents

    // Search
    data class OnSearchTextUpdate(val newText: String) : DetailEvents
    data object OnGeneratePasswordClick : DetailEvents
    data object OnPasswordSettingsClick : DetailEvents
    data object OnDismissPasswordSettings : DetailEvents
    data class OnPasswordLengthChange(val length: Float) : DetailEvents
    data class OnTogglePasswordOption(val option: String, val value: Boolean) : DetailEvents
}