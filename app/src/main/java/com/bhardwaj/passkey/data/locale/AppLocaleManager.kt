package com.bhardwaj.passkey.data.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies and persists the per-app language.
 *
 * Extracted from SettingsViewModel, which was reaching for LocaleManager and AppCompatDelegate
 * directly. The API fork matters: on API 33+ the platform LocaleManager updates the process
 * configuration, while below that AppCompatDelegate applies the override per Activity only -
 * which is why resolving strings against the Application context returned the previous language.
 */
@Singleton
class AppLocaleManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository
) {
    suspend fun apply(languageTag: String) {
        preferences.setSelectedLanguageTag(languageTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag)
            )
        }
    }
}
