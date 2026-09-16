package com.bhardwaj.passkey.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.bhardwaj.passkey.data.security.AppLockObserver
import com.bhardwaj.passkey.data.security.LockReason
import com.bhardwaj.passkey.presentation.navigation.Routes
import kotlinx.coroutines.delay
import com.bhardwaj.passkey.data.security.VaultSession
import com.bhardwaj.passkey.domain.viewModels.SplashViewModel
import javax.inject.Inject
import com.bhardwaj.passkey.presentation.navigation.NavGraph
import com.bhardwaj.passkey.presentation.theme.PassKeyTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhardwaj.passkey.presentation.screens.common.UpdateReadyBanner
import com.bhardwaj.passkey.presentation.screens.common.UpdateRequiredScreen
import com.bhardwaj.passkey.utils.AppUpdateController
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private companion object {
        const val IDLE_POLL_INTERVAL_MS = 5_000L
    }


    @Inject
    lateinit var vaultSession: VaultSession

    @Inject
    lateinit var appLockObserver: AppLockObserver

    /**
     * Activity.onUserInteraction is dispatched before the window handles the event, so it sees
     * touches and key presses without competing with Compose gesture consumption - far more
     * robust than wrapping the tree in a pointerInput.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        vaultSession.touch()
    }

    private val splashViewModel: SplashViewModel by viewModels()
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateController: AppUpdateController

    private val updateResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // The previous implementation ignored this result entirely, so cancelling a
            // "required" update simply continued into the vault.
            if (result.resultCode != RESULT_OK) {
                updateController.onImmediateUpdateNotCompleted()
            }
        }

    override fun onStart() {
        super.onStart()
        updateController.onStart()
    }

    override fun onStop() {
        updateController.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateController.resumeInterruptedUpdate(this, updateResultLauncher)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appUpdateManager = AppUpdateManagerFactory.create(this@MainActivity)
        updateController = AppUpdateController(
            appUpdateManager = appUpdateManager,
            remoteConfig = runCatching { FirebaseRemoteConfig.getInstance() }.getOrNull()
        )
        updateController.checkMinimumSupportedVersion()
        updateController.checkForUpdate(this@MainActivity, updateResultLauncher)
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            PassKeyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val updateState by updateController.state.collectAsStateWithLifecycle()

                    if (updateState == AppUpdateController.State.UpdateRequired) {
                        // Terminal by design: there is no path from here into the vault.
                        UpdateRequiredScreen(
                            onUpdateClick = {
                                updateController.checkForUpdate(
                                    this@MainActivity,
                                    updateResultLauncher
                                )
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()
                            val isUnlocked by vaultSession.isUnlocked
                                .collectAsStateWithLifecycle()
                            val lockReason by vaultSession.lockReason
                                .collectAsStateWithLifecycle()

                            // Foreground idle timer. onStop covers backgrounding; this covers
                            // the app being left open and untouched on an unlocked phone.
                            LaunchedEffect(isUnlocked) {
                                if (!isUnlocked) return@LaunchedEffect
                                while (true) {
                                    delay(IDLE_POLL_INTERVAL_MS)
                                    appLockObserver.lockIfIdle()
                                }
                            }

                            LaunchedEffect(isUnlocked, lockReason) {
                                // ColdStart is the initial value and means "never unlocked yet",
                                // so it must not yank the user out of onboarding.
                                if (!isUnlocked && lockReason != LockReason.ColdStart) {
                                    navController.navigate(Routes.SECURITY_PAGE) {
                                        // Drop every screen holding vault content, which also
                                        // destroys their nav-scoped ViewModels.
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }

                            NavGraph(
                                navController = navController,
                                startDestination = splashViewModel.startDestination
                            )
                            if (updateState == AppUpdateController.State.ReadyToInstall) {
                                UpdateReadyBanner(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    onRestartClick = { updateController.completeFlexibleUpdate() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}