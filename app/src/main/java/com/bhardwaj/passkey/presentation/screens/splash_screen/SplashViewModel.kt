package com.bhardwaj.passkey.presentation.screens.splash_screen

import com.bhardwaj.passkey.presentation.navigation.NavRoute
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferences: PreferencesRepository
) : ViewModel() {

    // BUFFERED, not the RENDEZVOUS default: with lifecycle-aware collection a backgrounded
    // screen has no active collector, and a rendezvous channel would suspend the coroutine
    // that emitted the effect until the user came back.
    private val _effects = Channel<SplashEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: SplashIntent) {
        when (intent) {
            SplashIntent.LoadingFinished -> {
                viewModelScope.launch {
                    // first(), not collect(): a DataStore flow never completes, so collecting
                    // it re-sent a Navigate effect on every later preference change - including a
                    // language switch.
                    val completed = preferences.onboardingCompleted.first()
                    _effects.send(
                        SplashEffect.Navigate(
                            if (completed) NavRoute.Security else NavRoute.Onboarding
                        )
                    )
                }
            }
        }
    }
}