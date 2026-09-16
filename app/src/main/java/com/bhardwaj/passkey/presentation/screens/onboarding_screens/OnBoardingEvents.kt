package com.bhardwaj.passkey.presentation.screens.onboarding_screens

sealed interface OnBoardingEvents {
    data object OnBoardingComplete : OnBoardingEvents
}