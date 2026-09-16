package com.bhardwaj.passkey.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailScreen
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.OnBoardingScreen
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewScreen
import com.bhardwaj.passkey.presentation.screens.security_screen.VaultGateScreen
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsScreen
import com.bhardwaj.passkey.presentation.screens.splash_screen.SplashPage
import com.bhardwaj.passkey.utils.Categories

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(route = NavScreens.SplashPage.route) {
            SplashPage(
                onNavigate = {
                    navController.popBackStack()
                    navController.navigate(it.route)
                }
            )
        }
        composable(route = NavScreens.OnboardingPage.route) {
            OnBoardingScreen(
                onNavigate = {
                    navController.popBackStack()
                    navController.navigate(it.route)
                }
            )
        }
        composable(
            route = NavScreens.PreviewPage.route + "?categoryName={categoryName}",
            arguments = listOf(navArgument(name = "categoryName") {
                type = NavType.StringType
                defaultValue = Categories.BANKS.name
            }),
        ) {
            PreviewScreen(onNavigate = { navController.navigate(it.route) })
        }
        composable(
            route = NavScreens.DetailsPage.route + "?previewId={previewId}",
            arguments = listOf(navArgument(name = "previewId") {
                type = NavType.LongType
                defaultValue = -1
            })
        ) {
            DetailScreen(onPopBackStack = { navController.popBackStack() })
        }
        composable(route = NavScreens.SettingPage.route) {
            SettingsScreen(
                onPopBackStack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) })
        }
        composable(route = NavScreens.SecurityPage.route) {
            VaultGateScreen(
                onUnlocked = {
                    navController.navigate(NavScreens.PreviewPage.route) {
                        // Nothing behind the gate should survive it.
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}