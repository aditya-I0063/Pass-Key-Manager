package com.bhardwaj.passkey.domain.repository

import com.bhardwaj.passkey.domain.model.AutoLockTimeout
import kotlinx.coroutines.flow.Flow

/**
 * App preferences, expressed in the app's own terms.
 *
 * Replaces the generic DataStoreSource, whose `Preferences.Key<T>` surface leaked DataStore all
 * the way into the ViewModels - callers had to know about `DataStoreRepository.onBoardingKey` to
 * read a boolean. That interface also existed but was bypassed at the injection site, so the
 * concrete class was wired directly and the abstraction bought nothing.
 */
interface PreferencesRepository {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    val selectedLanguageTag: Flow<String?>
    suspend fun setSelectedLanguageTag(tag: String)

    val autoLockTimeout: Flow<AutoLockTimeout>
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout)
}
