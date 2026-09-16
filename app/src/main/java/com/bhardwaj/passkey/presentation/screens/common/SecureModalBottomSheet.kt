package com.bhardwaj.passkey.presentation.screens.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * A bottom sheet that always carries FLAG_SECURE.
 *
 * Every sheet in this app shows vault contents, and each one is a separate window. The Material 3
 * default is SecureFlagPolicy.Inherit, which does currently inherit the flag from MainActivity -
 * so this is not a fix for a live leak. It is a guard against one: a default that changes, or a
 * sheet hosted off a window that is not secure, would turn screenshots back on silently and
 * nothing would say so.
 *
 * `scripts/check-modal-bottom-sheet.sh` fails the build if ModalBottomSheet is called anywhere
 * but here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    // Defaults mirror ModalBottomSheet's own, so adopting the wrapper changes nothing visually.
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        content = content
    )
}
