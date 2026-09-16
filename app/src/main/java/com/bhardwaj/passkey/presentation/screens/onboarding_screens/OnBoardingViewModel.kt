package com.bhardwaj.passkey.presentation.screens.onboarding_screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.OnBoardingEvents
import com.bhardwaj.passkey.presentation.navigation.Routes
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

    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun onEvent(event: OnBoardingEvents) {
        when (event) {
            is OnBoardingEvents.OnBoardingComplete -> {
                viewModelScope.launch {
                    preferences.setOnboardingCompleted(true)
                    _uiEvents.send(UiEvents.Navigate(Routes.SECURITY_PAGE))
                }
            }
        }
    }
}