package com.bhardwaj.passkey.domain.model

import com.bhardwaj.passkey.domain.totp.TotpConfig

/** An authenticator attached to an entry. */
data class TotpEntry(
    val id: Long,
    val previewId: Long,
    val label: String,
    val config: TotpConfig
)

/**
 * A value a secret used to hold.
 *
 * [changedAt] is wall-clock because it is shown as a date. Everywhere the app measures an
 * interval - the auto-lock timer - it uses elapsedRealtime instead, which a clock change cannot
 * move.
 */
data class PasswordHistoryEntry(
    val id: Long,
    val detailId: Long,
    val answer: String,
    val changedAt: Long
)
