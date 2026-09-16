package com.bhardwaj.passkey.data.analysis

import android.content.Context
import android.content.res.Configuration
import com.bhardwaj.passkey.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the words that identify a field as holding a secret.
 *
 * Returns the union of the current locale's list and the English one. A vault may hold entries
 * labelled before the user switched language, so matching only the current locale would silently
 * stop classifying them.
 */
@Singleton
class SecretKeywordProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun keywords(): Set<String> {
        val current = context.resources.getStringArray(R.array.secret_field_keywords).toSet()
        val englishConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val fallback = context.createConfigurationContext(englishConfig)
            .resources.getStringArray(R.array.secret_field_keywords).toSet()
        return current + fallback
    }
}
