package com.bhardwaj.passkey.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clipboard access hardened for secret values.
 *
 * Two problems with the previous inline `ClipData.newPlainText(...)` calls:
 *  - the clip carried no sensitivity flag, so Android 13+ rendered the copied password in the
 *    system paste preview;
 *  - nothing ever cleared it, so a password sat on the clipboard indefinitely.
 *
 * Auto-clear is best-effort by design. Since API 29 an app without window focus cannot write
 * the clipboard, so a delayed clear that fires while backgrounded is a silent no-op. The real
 * defences are [EXTRA_IS_SENSITIVE] and Android 12+'s own system-wide clipboard expiry; the
 * timer just shortens the window while the user is still in the app.
 */
object SecureClipboard {

    /**
     * Literal rather than [ClipDescription.EXTRA_IS_SENSITIVE] (API 33) on purpose: the same key
     * is honoured by Gboard and several OEM clipboard managers below 33, and is a harmless no-op
     * everywhere else.
     */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private const val CLIP_LABEL = "PassKey"
    const val DEFAULT_AUTO_CLEAR_MILLIS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    /** Timestamp of the clip we last wrote, so we never wipe something the user copied since. */
    private var ourClipTimestamp: Long = 0L

    fun copy(
        context: Context,
        text: String,
        isSensitive: Boolean,
        autoClearMillis: Long = if (isSensitive) DEFAULT_AUTO_CLEAR_MILLIS else 0L
    ) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return

        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        if (isSensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        ourClipTimestamp = clipboard.primaryClipDescription?.timestamp ?: 0L

        clearJob?.cancel()
        if (autoClearMillis > 0L) {
            clearJob = scope.launch {
                delay(autoClearMillis)
                clearIfOurs(context)
            }
        }
    }

    /** Clears the clipboard only if it still holds the clip this object wrote. */
    fun clearIfOurs(context: Context) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        val description = clipboard.primaryClipDescription ?: return
        if (description.label != CLIP_LABEL) return
        if (ourClipTimestamp != 0L && description.timestamp != ourClipTimestamp) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        ourClipTimestamp = 0L
    }
}
