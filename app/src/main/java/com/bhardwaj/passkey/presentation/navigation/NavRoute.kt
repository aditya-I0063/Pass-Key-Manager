package com.bhardwaj.passkey.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 *
 * Replaces string constants that callers concatenated by hand - `Routes.DETAILS_PAGE +
 * "?previewId=$id"` appeared in two ViewModels as independently maintained copies of one URL
 * format, and nothing checked that the argument name matched the graph.
 */
sealed interface NavRoute {

    @Serializable
    data object Splash : NavRoute

    @Serializable
    data object Onboarding : NavRoute

    @Serializable
    data object Security : NavRoute

    @Serializable
    data object Previews : NavRoute

    @Serializable
    data class Details(val previewId: Long) : NavRoute

    @Serializable
    data object Settings : NavRoute
}
