package com.bhardwaj.passkey.presentation.screens.settings_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.AppInfo
import com.bhardwaj.passkey.data.analysis.SecretKeywordProvider
import com.bhardwaj.passkey.data.backup.BackupError
import com.bhardwaj.passkey.data.backup.BackupException
import com.bhardwaj.passkey.data.backup.BackupRepository
import com.bhardwaj.passkey.data.locale.AppLocaleManager
import com.bhardwaj.passkey.data.security.DatabaseKeyManager
import com.bhardwaj.passkey.di.DefaultDispatcher
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.repository.PreferencesRepository
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.utils.AlertBy
import com.bhardwaj.passkey.utils.PasswordAnalysisResult
import com.bhardwaj.passkey.utils.PasswordAnalyzer
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val preferences: PreferencesRepository,
    private val backupRepository: BackupRepository,
    private val keyManager: DatabaseKeyManager,
    private val localeManager: AppLocaleManager,
    private val secretKeywords: SecretKeywordProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    appInfo: AppInfo
) : ViewModel() {

    // BUFFERED, not the RENDEZVOUS default: with lifecycle-aware collection a backgrounded
    // screen has no active collector, and a rendezvous channel would suspend the coroutine
    // that emitted the effect until the user came back.
    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(
        val sheet: SettingsState.Sheet? = null,
        val isAutoLockDialogOpen: Boolean = false,
        val analysis: PasswordAnalysisResult? = null,
        val recoveryChange: RecoveryChangeStep? = null
    )

    /**
     * Held only between the two recovery dialog steps, then zeroed - never in [SettingsState],
     * which the composition retains.
     */
    private var pendingCurrentRecoveryPassword: CharArray? = null

    private val appVersion = appInfo.versionName

    val state: StateFlow<SettingsState> = combine(
        transient,
        preferences.autoLockTimeout
    ) { ui, timeout ->
        SettingsState(
            appVersion = appVersion,
            autoLockTimeout = timeout,
            sheet = ui.sheet,
            isAutoLockDialogOpen = ui.isAutoLockDialogOpen,
            analysis = ui.analysis,
            recoveryChange = ui.recoveryChange
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsState(appVersion = appVersion)
    )

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.LanguageClicked ->
                transient.update { it.copy(sheet = SettingsState.Sheet.Language) }

            SettingsIntent.PrivacyClicked -> openInfo(AlertBy.PRIVACY)
            SettingsIntent.TermsClicked -> openInfo(AlertBy.TERMS_N_CONDITIONS)
            SettingsIntent.AboutClicked -> openInfo(AlertBy.ABOUT)

            SettingsIntent.SheetDismissed -> transient.update { it.copy(sheet = null) }

            is SettingsIntent.LanguageSelected -> {
                transient.update { it.copy(sheet = null) }
                viewModelScope.launch { localeManager.apply(intent.language.tag) }
            }

            SettingsIntent.RateAppClicked -> emit(SettingsEffect.OpenStoreListing)

            SettingsIntent.AutofillClicked -> emit(SettingsEffect.OpenAutofillSettings)

            is SettingsIntent.ExportFileChosen -> viewModelScope.launch {
                val result = backupRepository.export(intent.uri, intent.password)
                Arrays.fill(intent.password, Char(0))
                emit(
                    SettingsEffect.ShowSnackbar(
                        UiText.StringResource(
                            if (result.isSuccess) R.string.export_success
                            else R.string.export_failed
                        )
                    )
                )
            }

            is SettingsIntent.ImportFileChosen -> viewModelScope.launch {
                val result = backupRepository.import(intent.uri, intent.password, intent.mode)
                intent.password?.let { Arrays.fill(it, Char(0)) }
                result.fold(
                    onSuccess = { summary ->
                        emit(
                            SettingsEffect.ShowSnackbar(
                                UiText.StringResource(
                                    R.string.import_summary,
                                    listOf(summary.previewsAdded, summary.detailsAdded)
                                )
                            )
                        )
                    },
                    onFailure = { error ->
                        val message = when ((error as? BackupException)?.error) {
                            BackupError.WrongPasswordOrCorrupt -> R.string.import_wrong_password
                            BackupError.FileTooLarge -> R.string.import_file_too_large
                            is BackupError.UnsupportedVersion -> R.string.import_unsupported_version
                            else -> R.string.import_failed
                        }
                        emit(SettingsEffect.ShowSnackbar(UiText.StringResource(message)))
                    }
                )
            }

            SettingsIntent.AnalyzeClicked -> viewModelScope.launch {
                val allDetails = repository.getDetails().first()
                val keywords = secretKeywords.keywords()
                // Analysis walks the whole vault, so it is moved off the main thread.
                val result = withContext(defaultDispatcher) {
                    PasswordAnalyzer.analyze(allDetails, keywords)
                }
                transient.update { it.copy(analysis = result) }
            }

            SettingsIntent.AnalysisDismissed -> transient.update { it.copy(analysis = null) }

            is SettingsIntent.AnalysisItemClicked -> {
                transient.update { it.copy(analysis = null) }
                emit(SettingsEffect.Navigate(NavRoute.Details(previewId = intent.previewId)))
            }

            SettingsIntent.AutoLockClicked ->
                transient.update { it.copy(isAutoLockDialogOpen = true) }

            SettingsIntent.AutoLockDismissed ->
                transient.update { it.copy(isAutoLockDialogOpen = false) }

            is SettingsIntent.AutoLockTimeoutSelected -> {
                transient.update { it.copy(isAutoLockDialogOpen = false) }
                viewModelScope.launch { preferences.setAutoLockTimeout(intent.timeout) }
            }

            SettingsIntent.ChangeRecoveryPasswordClicked ->
                transient.update { it.copy(recoveryChange = RecoveryChangeStep.CURRENT_PASSWORD) }

            SettingsIntent.RecoveryChangeDismissed -> {
                clearPendingRecoveryPassword()
                transient.update { it.copy(recoveryChange = null) }
            }

            is SettingsIntent.CurrentRecoveryPasswordEntered -> {
                clearPendingRecoveryPassword()
                pendingCurrentRecoveryPassword = intent.password
                transient.update { it.copy(recoveryChange = RecoveryChangeStep.NEW_PASSWORD) }
            }

            is SettingsIntent.NewRecoveryPasswordEntered -> {
                val current = pendingCurrentRecoveryPassword
                pendingCurrentRecoveryPassword = null
                transient.update { it.copy(recoveryChange = null) }
                viewModelScope.launch {
                    val changed = current != null &&
                        keyManager.changeRecoveryPassword(current, intent.password)
                    current?.let { Arrays.fill(it, Char(0)) }
                    Arrays.fill(intent.password, Char(0))
                    emit(
                        SettingsEffect.ShowSnackbar(
                            UiText.StringResource(
                                if (changed) R.string.recovery_change_done
                                else R.string.recovery_change_failed
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Abandoning the flow half-way used to leave the entered password in memory for the
     * ViewModel's remaining life.
     */
    override fun onCleared() {
        clearPendingRecoveryPassword()
        super.onCleared()
    }

    private fun clearPendingRecoveryPassword() {
        pendingCurrentRecoveryPassword?.let { Arrays.fill(it, Char(0)) }
        pendingCurrentRecoveryPassword = null
    }

    private fun openInfo(topic: AlertBy) {
        transient.update { it.copy(sheet = SettingsState.Sheet.Info(topic)) }
    }

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
