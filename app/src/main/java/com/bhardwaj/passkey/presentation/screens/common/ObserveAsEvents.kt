package com.bhardwaj.passkey.presentation.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects one-off effects only while the screen is at least STARTED.
 *
 * Replaces `LaunchedEffect(key1 = true) { flow.collect { ... } }`, which is not lifecycle-aware:
 * it kept collecting while the app was backgrounded, so a snackbar could be consumed and lost
 * with no window to show it in.
 *
 * The `Dispatchers.Main.immediate` hop matters. Without it an effect emitted just as the
 * lifecycle drops below STARTED can be dispatched into a composition that is already gone.
 *
 * The producing side stays a Channel rather than a SharedFlow on purpose: when repeatOnLifecycle
 * cancels this collector on STOP, undelivered items remain buffered in the channel and are
 * redelivered on restart. A SharedFlow would drop them.
 */
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, key: Any? = null, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner, key) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) { flow.collect(onEvent) }
        }
    }
}
