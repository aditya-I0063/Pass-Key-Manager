package com.bhardwaj.passkey

import android.net.Uri
import app.cash.turbine.test
import com.bhardwaj.passkey.data.AppInfo
import com.bhardwaj.passkey.data.analysis.SecretKeywordProvider
import com.bhardwaj.passkey.data.backup.BackupError
import com.bhardwaj.passkey.data.backup.BackupException
import com.bhardwaj.passkey.data.backup.BackupRepository
import com.bhardwaj.passkey.data.backup.ImportMode
import com.bhardwaj.passkey.data.locale.AppLocaleManager
import com.bhardwaj.passkey.data.security.DatabaseKeyManager
import com.bhardwaj.passkey.domain.model.AutoLockTimeout
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Language
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.presentation.screens.settings_screen.RecoveryChangeStep
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsEffect
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsIntent
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsState
import com.bhardwaj.passkey.presentation.screens.settings_screen.SettingsViewModel
import com.bhardwaj.passkey.utils.AlertBy
import com.bhardwaj.passkey.utils.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FakePasskeyRepository
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var keyManager: DatabaseKeyManager
    private lateinit var localeManager: AppLocaleManager
    private lateinit var secretKeywords: SecretKeywordProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePasskeyRepository()
        preferences = FakePreferencesRepository()
        backupRepository = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)
        localeManager = mockk(relaxed = true)
        secretKeywords = mockk()
        every { secretKeywords.keywords() } returns setOf("password")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        repository = repository,
        preferences = preferences,
        backupRepository = backupRepository,
        keyManager = keyManager,
        localeManager = localeManager,
        secretKeywords = secretKeywords,
        defaultDispatcher = dispatcher,
        appInfo = AppInfo()
    )

    @Test
    fun `dismissing the language sheet leaves no sheet showing`() = runTest(dispatcher) {
        // The old pair of fields disagreed here: isSheetOpen went false while
        // bottomSheetOpenedBy still held a topic, so closing the language picker revealed
        // whichever legal text had been opened last.
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(SettingsIntent.PrivacyClicked)
            assertThat(expectMostRecentItem().sheet)
                .isEqualTo(SettingsState.Sheet.Info(AlertBy.PRIVACY))

            vm.onIntent(SettingsIntent.LanguageClicked)
            assertThat(expectMostRecentItem().sheet).isEqualTo(SettingsState.Sheet.Language)

            vm.onIntent(SettingsIntent.SheetDismissed)
            assertThat(expectMostRecentItem().sheet).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a language applies the tag and closes the sheet`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(SettingsIntent.LanguageClicked)
            vm.onIntent(
                SettingsIntent.LanguageSelected(
                    Language(languageId = "hi", languageName = "हिन्दी", languageNameInEnglish = "Hindi")
                )
            )
            assertThat(expectMostRecentItem().sheet).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { localeManager.apply("hi") }
    }

    @Test
    fun `analysis opens a sheet and tapping an entry navigates to it`() = runTest(dispatcher) {
        val previewId = repository.createPreview("Gmail", Category.MAILS, sequence = 0)
        repository.createDetail(previewId = previewId, question = "Password", answer = "abc")
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(SettingsIntent.AnalyzeClicked)
            val analysis = expectMostRecentItem().analysis
            assertThat(analysis).isNotNull()
            assertThat(analysis!!.totalPasswords).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }

        vm.effects.test {
            vm.onIntent(SettingsIntent.AnalysisItemClicked(previewId))
            assertThat(awaitItem())
                .isEqualTo(SettingsEffect.Navigate(NavRoute.Details(previewId)))
        }
        assertThat(vm.state.value.analysis).isNull()
    }

    @Test
    fun `the recovery change runs in two steps and reports failure`() = runTest(dispatcher) {
        coEvery { keyManager.changeRecoveryPassword(any(), any()) } returns false
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(SettingsIntent.ChangeRecoveryPasswordClicked)
            assertThat(expectMostRecentItem().recoveryChange)
                .isEqualTo(RecoveryChangeStep.CURRENT_PASSWORD)

            vm.onIntent(SettingsIntent.CurrentRecoveryPasswordEntered("old".toCharArray()))
            assertThat(expectMostRecentItem().recoveryChange)
                .isEqualTo(RecoveryChangeStep.NEW_PASSWORD)
            cancelAndIgnoreRemainingEvents()
        }

        vm.effects.test {
            vm.onIntent(SettingsIntent.NewRecoveryPasswordEntered("new".toCharArray()))
            assertThat(awaitItem()).isEqualTo(
                SettingsEffect.ShowSnackbar(
                    UiText.StringResource(R.string.recovery_change_failed)
                )
            )
        }
        assertThat(vm.state.value.recoveryChange).isNull()
    }

    @Test
    fun `the entered passwords are zeroed rather than left in memory`() = runTest(dispatcher) {
        coEvery { keyManager.changeRecoveryPassword(any(), any()) } returns true
        val vm = viewModel()
        val current = "old-secret".toCharArray()
        val new = "new-secret".toCharArray()

        vm.effects.test {
            vm.onIntent(SettingsIntent.CurrentRecoveryPasswordEntered(current))
            vm.onIntent(SettingsIntent.NewRecoveryPasswordEntered(new))
            awaitItem()
        }

        assertThat(current.concatToString()).isEqualTo("\u0000".repeat(current.size))
        assertThat(new.concatToString()).isEqualTo("\u0000".repeat(new.size))
    }

    @Test
    fun `abandoning the recovery flow wipes the password it was holding`() = runTest(dispatcher) {
        val vm = viewModel()
        val current = "old-secret".toCharArray()

        vm.onIntent(SettingsIntent.CurrentRecoveryPasswordEntered(current))
        vm.onIntent(SettingsIntent.RecoveryChangeDismissed)

        assertThat(current.concatToString()).isEqualTo("\u0000".repeat(current.size))
        assertThat(vm.state.value.recoveryChange).isNull()
    }

    @Test
    fun `choosing an auto-lock timeout persists it and closes the dialog`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(SettingsIntent.AutoLockClicked)
            assertThat(expectMostRecentItem().isAutoLockDialogOpen).isTrue()

            vm.onIntent(SettingsIntent.AutoLockTimeoutSelected(AutoLockTimeout.MINUTES_5))
            val state = expectMostRecentItem()
            assertThat(state.isAutoLockDialogOpen).isFalse()
            assertThat(state.autoLockTimeout).isEqualTo(AutoLockTimeout.MINUTES_5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rating the app is an effect, not an intent started from the ViewModel`() =
        runTest(dispatcher) {
            // Opening the store needs a visible Activity; doing it here used to mean an
            // Application context, FLAG_ACTIVITY_NEW_TASK and an unhandled crash with no Play.
            val vm = viewModel()
            vm.effects.test {
                vm.onIntent(SettingsIntent.RateAppClicked)
                assertThat(awaitItem()).isEqualTo(SettingsEffect.OpenStoreListing)
            }
        }

    @Test
    fun `each import failure maps to its own message`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val vm = viewModel()

        val cases = mapOf(
            BackupError.WrongPasswordOrCorrupt to R.string.import_wrong_password,
            BackupError.FileTooLarge to R.string.import_file_too_large,
            BackupError.UnsupportedVersion("v9") to R.string.import_unsupported_version,
            BackupError.ReadFailed to R.string.import_failed
        )

        vm.effects.test {
            cases.forEach { (error, expected) ->
                coEvery { backupRepository.import(any(), any(), any()) } returns
                    Result.failure(BackupException(error))
                vm.onIntent(
                    SettingsIntent.ImportFileChosen(uri, "pw".toCharArray(), ImportMode.MERGE)
                )
                assertThat(awaitItem())
                    .isEqualTo(SettingsEffect.ShowSnackbar(UiText.StringResource(expected)))
            }
        }
    }
}

/** DataStore-free stand-in; the ViewModel only needs the values, not the storage. */
private class FakePreferencesRepository : PreferencesRepository {
    private val onboarding = MutableStateFlow(false)
    private val languageTag = MutableStateFlow<String?>(null)
    private val timeout = MutableStateFlow(AutoLockTimeout.DEFAULT)

    override val onboardingCompleted: Flow<Boolean> = onboarding
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboarding.value = completed
    }

    override val selectedLanguageTag: Flow<String?> = languageTag
    override suspend fun setSelectedLanguageTag(tag: String) {
        languageTag.value = tag
    }

    override val autoLockTimeout: Flow<AutoLockTimeout> = timeout
    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        this.timeout.value = timeout
    }
}
