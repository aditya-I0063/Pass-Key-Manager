package com.bhardwaj.passkey

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bhardwaj.passkey.utils.AppUpdateController
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the update gate against Play's own fake.
 *
 * This is the one part of the app that cannot be exercised for real: in-app updates do nothing
 * for a locally installed build. It is also the mechanism a hotfix depends on, and the bug it
 * replaced was precisely a silent fall-through - an empty registerForActivityResult callback, so
 * cancelling a "required" update walked straight into the vault. A test that only checked the
 * happy path would not have caught that, so the assertions below are mostly about refusal.
 */
@RunWith(AndroidJUnit4::class)
class AppUpdateControllerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private lateinit var fake: FakeAppUpdateManager
    private lateinit var controller: AppUpdateController

    /** The controller only ever hands this to Play; nothing here needs a real Activity result. */
    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: androidx.core.app.ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<IntentSenderRequest, *>
            get() = ActivityResultContracts.StartIntentSenderForResult()
    }

    /**
     * Never attached, and never used: the controller hands it to Play, which for the launcher
     * overload ignores it. Constructed on the main thread because Activity builds a Handler.
     */
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        instrumentation.runOnMainSync { activity = Activity() }
        fake = FakeAppUpdateManager(context)
        // Remote Config is null: these tests are about the Play flow. The version floor is a
        // separate mechanism and is asserted through onImmediateUpdateNotCompleted below.
        controller = AppUpdateController(fake, remoteConfig = null)
    }

    private fun check() = instrumentation.runOnMainSync {
        controller.checkForUpdate(activity, launcher)
    }

    /**
     * The controller reacts to Play through addOnSuccessListener, so asserting straight after
     * the call is a race - one this test lost only under the full suite, not on its own.
     */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    @Test
    fun a_high_priority_update_blocks_immediately() {
        fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1)
        fake.setUpdatePriority(AppUpdateController.PRIORITY_FORCE_IMMEDIATE)

        check()

        awaitUntil("the immediate flow to be shown") { fake.isImmediateFlowVisible }
        assertThat(fake.typeForUpdateInProgress).isEqualTo(AppUpdateType.IMMEDIATE)
    }

    @Test
    fun rejecting_a_required_update_does_not_let_the_user_through() {
        // The actual bug: the result callback was empty, so a back press dismissed Play's
        // dismissible "immediate" UI and the app carried on into the vault.
        fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1)
        fake.setUpdatePriority(AppUpdateController.PRIORITY_FORCE_IMMEDIATE)
        check()
        awaitUntil("the immediate flow to be shown") { fake.isImmediateFlowVisible }

        fake.userRejectsUpdate()
        instrumentation.runOnMainSync { controller.onImmediateUpdateNotCompleted() }

        assertThat(controller.state.value).isEqualTo(AppUpdateController.State.UpdateRequired)
    }

    @Test
    fun a_low_priority_update_does_not_interrupt_anyone() {
        fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1)
        fake.setUpdatePriority(0)
        fake.setClientVersionStalenessDays(0)

        check()
        settle()

        assertThat(fake.isImmediateFlowVisible).isFalse()
        assertThat(fake.isConfirmationDialogVisible).isFalse()
        assertThat(controller.state.value).isEqualTo(AppUpdateController.State.Idle)
    }

    @Test
    fun a_stale_low_priority_update_is_offered_flexibly_instead() {
        fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.FLEXIBLE)
        fake.setUpdatePriority(0)
        fake.setClientVersionStalenessDays(AppUpdateController.FLEXIBLE_STALENESS_DAYS)

        check()

        awaitUntil("the flexible flow to start") {
            fake.typeForUpdateInProgress == AppUpdateType.FLEXIBLE
        }
        // Flexible means the user keeps using the app; nothing is blocked.
        assertThat(controller.state.value).isEqualTo(AppUpdateController.State.Idle)
    }

    @Test
    fun a_downloaded_flexible_update_asks_for_a_restart_rather_than_forcing_one() {
        fake.setUpdateAvailable(BuildConfig.VERSION_CODE + 1, AppUpdateType.FLEXIBLE)
        fake.setUpdatePriority(0)
        fake.setClientVersionStalenessDays(AppUpdateController.FLEXIBLE_STALENESS_DAYS)

        instrumentation.runOnMainSync {
            controller.onStart()
            controller.checkForUpdate(activity, launcher)
        }
        awaitUntil("the flexible flow to start") {
            fake.typeForUpdateInProgress == AppUpdateType.FLEXIBLE
        }
        instrumentation.runOnMainSync {
            fake.userAcceptsUpdate()
            fake.downloadStarts()
            fake.downloadCompletes()
        }

        awaitUntil("the restart prompt") {
            controller.state.value == AppUpdateController.State.ReadyToInstall
        }
        instrumentation.runOnMainSync { controller.onStop() }
    }

    @Test
    fun no_update_available_changes_nothing() {
        fake.setUpdateNotAvailable()

        check()
        settle()

        assertThat(fake.isImmediateFlowVisible).isFalse()
        assertThat(controller.state.value).isEqualTo(AppUpdateController.State.Idle)
    }

    /**
     * Asserting that nothing happened needs the listener to have had its chance to make it
     * happen, otherwise the test passes for the wrong reason.
     */
    private fun settle() {
        Thread.sleep(500)
        instrumentation.runOnMainSync { }
    }
}
