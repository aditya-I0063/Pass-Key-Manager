package com.bhardwaj.passkey.domain.model

/**
 * User-selectable idle period before the vault re-locks.
 *
 * Carries no string resource: a display label is an Android concern, and having one here made
 * domain depend on generated R, which is the layering this release is untangling. The UI maps
 * each value to text.
 */
enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    SECONDS_15(15_000L),
    SECONDS_30(30_000L),
    MINUTE_1(60_000L),
    MINUTES_5(300_000L),

    /** Deliberately last, and confirmed before it can be chosen. */
    NEVER(Long.MAX_VALUE);

    companion object {
        val DEFAULT = SECONDS_30

        fun fromMillis(millis: Long?): AutoLockTimeout =
            entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}
