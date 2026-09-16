package com.bhardwaj.passkey.presentation.screens.splash_screen

sealed interface SplashEvents {
    data object OnLoadingComplete : SplashEvents
}