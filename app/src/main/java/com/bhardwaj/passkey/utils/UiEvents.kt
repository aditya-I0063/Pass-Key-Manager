package com.bhardwaj.passkey.utils

sealed interface UiEvents {
    data object PopBackStack : UiEvents

    data class Navigate(val route: String) : UiEvents

    /**
     * Carries [UiText] rather than a resolved String so the message is localized at the call
     * site, using the Activity context that actually honours the per-app locale.
     */
    data class ShowSnackBar(
        val message: UiText,
        val action: UiText? = null
    ) : UiEvents

    /**
     * Copying is a UI concern and needs an Activity context to satisfy Android 10+'s rule that
     * only a focused app may write the clipboard, so it is an effect rather than something the
     * ViewModel does itself.
     */
    data class CopyToClipboard(
        val value: String,
        val isSensitive: Boolean
    ) : UiEvents
}
