package com.bhardwaj.passkey.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bhardwaj.passkey.domain.model.AutoLockTimeout
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import com.bhardwaj.passkey.utils.Constants.Companion.AUTO_LOCK_TIMEOUT
import com.bhardwaj.passkey.utils.Constants.Companion.CURRENT_LANGUAGE
import com.bhardwaj.passkey.utils.Constants.Companion.ONBOARDING_COMPLETE
import com.bhardwaj.passkey.utils.Constants.Companion.PASSKEY_PREFS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `preferencesDataStore` delegate is declared at file scope on purpose.
 *
 * It was previously a *member* extension property on the repository class. That delegate is
 * documented as creating a single instance per file, and per-instance use risks
 * "There are multiple DataStores active for the same file". It only worked because the
 * repository happened to be a singleton.
 */
private val Context.dataStore by preferencesDataStore(PASSKEY_PREFS)

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PreferencesRepository {

    private val onboardingKey = booleanPreferencesKey(ONBOARDING_COMPLETE)
    private val languageKey = stringPreferencesKey(CURRENT_LANGUAGE)
    private val autoLockKey = longPreferencesKey(AUTO_LOCK_TIMEOUT)

    private val preferences: Flow<androidx.datastore.preferences.core.Preferences> =
        context.dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    override val onboardingCompleted: Flow<Boolean> =
        preferences.map { it[onboardingKey] == true }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[onboardingKey] = completed }
    }

    override val selectedLanguageTag: Flow<String?> = preferences.map { it[languageKey] }

    override suspend fun setSelectedLanguageTag(tag: String) {
        context.dataStore.edit { it[languageKey] = tag }
    }

    override val autoLockTimeout: Flow<AutoLockTimeout> =
        preferences.map { AutoLockTimeout.fromMillis(it[autoLockKey]) }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        context.dataStore.edit { it[autoLockKey] = timeout.millis }
    }
}
