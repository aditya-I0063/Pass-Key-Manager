package com.bhardwaj.passkey.utils

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.bhardwaj.passkey.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives Play in-app updates and the minimum-supported-version gate.
 *
 * The previous implementation set AppUpdateType.IMMEDIATE and then passed an empty callback to
 * registerForActivityResult, so cancelling the "required" update - or an install failure - fell
 * straight through into the vault. A single back press bypassed the gate for the whole session.
 * It also blocked on every available update however trivial, because updatePriority was never
 * consulted, and its FLEXIBLE branch was unreachable dead code.
 */
class AppUpdateController(
    private val appUpdateManager: AppUpdateManager,
    private val remoteConfig: FirebaseRemoteConfig?
) {
    companion object {
        /**
         * Set per release through the Play Developer Publishing API. It cannot be set in the
         * Play Console UI and is immutable once a release is rolled out, so it has to be part of
         * the release checklist rather than an afterthought.
         */
        const val PRIORITY_FORCE_IMMEDIATE = 4

        /** Only nag about a low-priority update once it has been available this long. */
        const val FLEXIBLE_STALENESS_DAYS = 7

        const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
    }

    sealed interface State {
        data object Idle : State
        /** Hard stop: the running build is below the server-declared floor. */
        data object UpdateRequired : State
        /** A flexible update finished downloading and needs a restart to apply. */
        data object ReadyToInstall : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val installListener = InstallStateUpdatedListener { installState ->
        if (installState.installStatus() == InstallStatus.DOWNLOADED) {
            _state.value = State.ReadyToInstall
        }
    }

    fun onStart() = appUpdateManager.registerListener(installListener)

    fun onStop() = appUpdateManager.unregisterListener(installListener)

    fun completeFlexibleUpdate() = appUpdateManager.completeUpdate()

    /**
     * Server-side kill switch. In-app updates are client-side only and do nothing for a
     * sideloaded install or a device without Play, so this is the only mechanism that actually
     * enforces a floor - and the safety net if a release needs to be stopped.
     */
    fun checkMinimumSupportedVersion() {
        val config = remoteConfig ?: return
        config.fetchAndActivate().addOnSuccessListener {
            val minimum = config.getLong(KEY_MIN_SUPPORTED_VERSION_CODE)
            if (minimum > 0 && BuildConfig.VERSION_CODE < minimum) {
                _state.value = State.UpdateRequired
            }
        }
    }

    fun checkForUpdate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info -> onUpdateInfo(info, activity, launcher) }
            .addOnFailureListener {
                // No Play Store, no network, or a sideloaded build. Not fatal on its own; the
                // Remote Config gate is what actually blocks unsupported versions.
            }
    }

    /** Resumes an immediate update that was interrupted, e.g. by the process being killed. */
    fun resumeInterruptedUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                start(info, AppUpdateType.IMMEDIATE, activity, launcher)
            }
        }
    }

    /** The user dismissed or failed a *required* update, so we must not let them through. */
    fun onImmediateUpdateNotCompleted() {
        _state.value = State.UpdateRequired
    }

    private fun onUpdateInfo(
        info: AppUpdateInfo,
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return

        val priority = info.updatePriority()
        val staleness = info.clientVersionStalenessDays() ?: 0

        when {
            priority >= PRIORITY_FORCE_IMMEDIATE && info.isImmediateUpdateAllowed ->
                start(info, AppUpdateType.IMMEDIATE, activity, launcher)

            staleness >= FLEXIBLE_STALENESS_DAYS && info.isFlexibleUpdateAllowed ->
                start(info, AppUpdateType.FLEXIBLE, activity, launcher)

            else -> Unit // A minor update; do not interrupt the user.
        }
    }

    private fun start(
        info: AppUpdateInfo,
        type: Int,
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = runCatching {
        appUpdateManager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(type).build()
        )
    }
}
