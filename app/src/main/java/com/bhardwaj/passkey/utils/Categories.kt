package com.bhardwaj.passkey.utils

import androidx.annotation.StringRes
import com.bhardwaj.passkey.R

/**
 * The entry categories. The enum *name* is the persisted value (Room column and backup files),
 * so it must not change; [labelRes] carries the localized display name instead.
 */
enum class Categories(@param:StringRes val labelRes: Int) {
    BANKS(R.string.banks),
    MAILS(R.string.mails),
    APPS(R.string.apps),
    OTHERS(R.string.others)
}
