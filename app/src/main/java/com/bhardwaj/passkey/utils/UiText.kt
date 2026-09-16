package com.bhardwaj.passkey.utils

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A piece of user-facing text that is resolved as late as possible.
 *
 * ViewModels emit these unresolved. Resolving in a ViewModel means resolving against the
 * Application context, whose Resources do not track the per-app locale override applied by
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales] below API 33 - so on this
 * app's minSdk 28..32 range every such string came back in the wrong language after the user
 * switched language in Settings.
 *
 * These are data classes on purpose: tests assert on emitted effects by value, which an
 * identity-based class (or a `vararg` array field) would silently break.
 */
sealed interface UiText {

    data class DynamicString(val value: String) : UiText

    data class StringResource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class PluralResource(
        @param:PluralsRes val resId: Int,
        val count: Int,
        val args: List<Any> = emptyList()
    ) : UiText
}

/** Resolves against [context]. Prefer the `@Composable` overload inside composition. */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> context.getString(resId, *args.toTypedArray())
    is UiText.PluralResource ->
        context.resources.getQuantityString(resId, count, *args.toTypedArray())
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> stringResource(resId, *args.toTypedArray())
    is UiText.PluralResource -> pluralStringResource(resId, count, *args.toTypedArray())
}
