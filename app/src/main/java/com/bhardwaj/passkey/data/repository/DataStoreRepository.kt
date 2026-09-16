package com.bhardwaj.passkey.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bhardwaj.passkey.data.local.DataStoreSource
import com.bhardwaj.passkey.utils.Constants.Companion.AUTO_LOCK_TIMEOUT
import com.bhardwaj.passkey.utils.Constants.Companion.CURRENT_LANGUAGE
import com.bhardwaj.passkey.utils.Constants.Companion.ONBOARDING_COMPLETE
import com.bhardwaj.passkey.utils.Constants.Companion.PASSKEY_PREFS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreRepository @Inject constructor(private val context: Context) : DataStoreSource {
    companion object {
        val onBoardingKey = booleanPreferencesKey(name = ONBOARDING_COMPLETE)
        val currentLanguageKey = stringPreferencesKey(name = CURRENT_LANGUAGE)
        val autoLockTimeoutKey = longPreferencesKey(name = AUTO_LOCK_TIMEOUT)
    }

    private val Context.dataStore by preferencesDataStore(PASSKEY_PREFS)
    private val dataSource = context.dataStore

    override suspend fun <T> readPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        dataSource.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            val result = preferences[key] ?: defaultValue
            result
        }

    override suspend fun <T> savePreference(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences -> preferences[key] = value }
    }

    override suspend fun <T> removePreference(key: Preferences.Key<T>) {
        dataSource.edit { it.remove(key) }
    }

    override suspend fun clearAllPreference() {
        dataSource.edit { preferences -> preferences.clear() }
    }
}