package com.bhardwaj.passkey.presentation.screens.splash_screen

import com.bhardwaj.passkey.presentation.navigation.NavRoute

/**
 * No state type: the splash screen holds none, and an empty one would be ceremony. What it does
 * have is one intent and one effect, which is the whole screen.
 */
sealed interface SplashIntent {
    data object LoadingFinished : SplashIntent
}

sealed interface SplashEffect {
    data class Navigate(val route: NavRoute) : SplashEffect
}
