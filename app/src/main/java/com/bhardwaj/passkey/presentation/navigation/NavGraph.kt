package com.bhardwaj.passkey.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailScreen
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.OnBoardingScreen
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewScreen
import com.bhardwaj.passkey.presentation.screens.security_screen.VaultGateScreen
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsScreen
import com.bhardwaj.passkey.presentation.screens.splash_screen.SplashPage

@Composable
fun NavGraph(navController: NavHostController) {
    // Splash is a constant start destination. It used to come from a mutable ViewModel field,
    // and changing a NavHost's start destination after first composition recreates the graph and
    // resets the back stack. Splash already routes onward with a Navigate effect.
    NavHost(navController = navController, startDestination = NavRoute.Splash) {

        composable<NavRoute.Splash> {
            SplashPage(onNavigate = { navController.replaceWith(it) })
        }

        composable<NavRoute.Onboarding> {
            OnBoardingScreen(onNavigate = { navController.replaceWith(it) })
        }

        composable<NavRoute.Security> {
            VaultGateScreen(onUnlocked = { navController.replaceWith(NavRoute.Previews) })
        }

        composable<NavRoute.Previews> {
            PreviewScreen(onNavigate = { navController.navigate(it) })
        }

        composable<NavRoute.Details> {
            DetailScreen(onPopBackStack = { navController.popBackStack() })
        }

        composable<NavRoute.Settings> {
            SettingsScreen(onNavigate = { navController.navigate(it) })
        }
    }
}

/** Navigates and drops everything behind it, for one-way transitions past a gate. */
private fun NavHostController.replaceWith(route: NavRoute) {
    navigate(route) { popUpTo(0) { inclusive = true } }
}
