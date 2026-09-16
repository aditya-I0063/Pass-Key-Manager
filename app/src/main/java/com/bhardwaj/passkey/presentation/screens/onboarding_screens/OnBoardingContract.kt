package com.bhardwaj.passkey.presentation.screens.onboarding_screens

import com.bhardwaj.passkey.presentation.navigation.NavRoute

/** Like the splash screen, this one carries no state of its own - the pager owns its page. */
sealed interface OnBoardingIntent {
    data object Finished : OnBoardingIntent
}

sealed interface OnBoardingEffect {
    data class Navigate(val route: NavRoute) : OnBoardingEffect
}
