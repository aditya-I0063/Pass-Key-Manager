package com.bhardwaj.passkey.presentation.screens.onboarding_screens

import com.bhardwaj.passkey.presentation.navigation.NavRoute
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.OnBoardingEvents
import com.bhardwaj.passkey.utils.UiEvents
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
    private val _uiEvents = Channel<UiEvents>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun onEvent(event: OnBoardingEvents) {
        when (event) {
            is OnBoardingEvents.OnBoardingComplete -> {
                viewModelScope.launch {
                    preferences.setOnboardingCompleted(true)
                    _uiEvents.send(UiEvents.Navigate(NavRoute.Security))
                }
            }
        }
    }
}