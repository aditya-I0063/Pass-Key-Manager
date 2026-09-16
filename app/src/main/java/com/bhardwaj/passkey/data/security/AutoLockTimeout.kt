package com.bhardwaj.passkey.data.security

import androidx.annotation.StringRes
import com.bhardwaj.passkey.R

/** User-selectable idle period before the vault re-locks. */
enum class AutoLockTimeout(val millis: Long, @param:StringRes val labelRes: Int) {
    IMMEDIATELY(0L, R.string.auto_lock_immediately),
    SECONDS_15(15_000L, R.string.auto_lock_15_seconds),
    SECONDS_30(30_000L, R.string.auto_lock_30_seconds),
    MINUTE_1(60_000L, R.string.auto_lock_1_minute),
    MINUTES_5(300_000L, R.string.auto_lock_5_minutes),

    /** Deliberately last, and confirmed before it can be chosen. */
    NEVER(Long.MAX_VALUE, R.string.auto_lock_never);

    companion object {
        val DEFAULT = SECONDS_30

        fun fromMillis(millis: Long?): AutoLockTimeout =
            entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}
