package com.bhardwaj.passkey.presentation.screens.onboarding_screens

import com.bhardwaj.passkey.presentation.navigation.NavRoute
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository
) : ViewModel() {

    // BUFFERED, not the RENDEZVOUS default: with lifecycle-aware collection a backgrounded
    // screen has no active collector, and a rendezvous channel would suspend the coroutine
    // that emitted the effect until the user came back.
    private val _effects = Channel<OnBoardingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: OnBoardingIntent) {
        when (intent) {
            OnBoardingIntent.Finished -> {
                viewModelScope.launch {
                    preferences.setOnboardingCompleted(true)
                    _effects.send(OnBoardingEffect.Navigate(NavRoute.Security))
                }
            }
        }
    }
}