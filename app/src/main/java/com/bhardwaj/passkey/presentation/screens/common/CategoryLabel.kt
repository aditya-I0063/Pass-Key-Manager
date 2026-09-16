package com.bhardwaj.passkey.presentation.screens.common

import androidx.annotation.StringRes
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.Category

/**
 * Display label for a [Category].
 *
 * Lives in the presentation layer rather than on the enum: the enum's *name* is persisted in the
 * database and in backup files, so it must stay stable, while its label is localized and is
 * purely a UI concern. Keeping the mapping here also stops the domain layer depending on
 * generated R.
 */
@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.BANKS -> R.string.banks
    Category.MAILS -> R.string.mails
    Category.APPS -> R.string.apps
    Category.OTHERS -> R.string.others
}
