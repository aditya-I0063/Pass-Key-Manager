package com.bhardwaj.passkey.presentation.screens.splash_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import com.bhardwaj.passkey.presentation.screens.splash_screen.SplashEvents
import com.bhardwaj.passkey.presentation.navigation.NavScreens
import com.bhardwaj.passkey.utils.UiEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferences: PreferencesRepository
) : ViewModel() {

    var startDestination by mutableStateOf(NavScreens.SplashPage.route)
        private set

    // BUFFERED, not the RENDEZVOUS default: with lifecycle-aware collection a backgrounded
    // screen has no active collector, and a rendezvous channel would suspend the coroutine
    // that emitted the effect until the user came back.
    private val _uiEvents = Channel<UiEvents>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun onEvent(event: SplashEvents) {
        when (event) {
            SplashEvents.OnLoadingComplete -> {
                viewModelScope.launch {
                    // first(), not collect(): a DataStore flow never completes, so collecting
                    // it re-sent a Navigate effect on every later preference change - including a
                    // language switch.
                    val completed = preferences.onboardingCompleted.first()
                    startDestination = if (completed) {
                        NavScreens.SecurityPage.route
                    } else {
                        NavScreens.OnboardingPage.route
                    }
                    _uiEvents.send(UiEvents.Navigate(startDestination))
                }
            }
        }
    }
}