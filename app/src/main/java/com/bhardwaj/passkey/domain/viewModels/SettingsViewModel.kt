package com.bhardwaj.passkey.domain.viewModels

import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.backup.BackupError
import com.bhardwaj.passkey.data.backup.BackupException
import com.bhardwaj.passkey.data.backup.BackupRepository
import com.bhardwaj.passkey.data.security.AutoLockTimeout
import com.bhardwaj.passkey.data.repository.DataStoreRepository
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.events.SettingsEvents
import com.bhardwaj.passkey.presentation.navigation.Routes
import com.bhardwaj.passkey.utils.AlertBy.ABOUT
import com.bhardwaj.passkey.utils.AlertBy.PRIVACY
import com.bhardwaj.passkey.utils.AlertBy.TERMS_N_CONDITIONS
import com.bhardwaj.passkey.utils.Categories
import com.bhardwaj.passkey.utils.PasswordAnalysisResult
import com.bhardwaj.passkey.utils.PasswordAnalyzer
import com.bhardwaj.passkey.utils.UiEvents
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val dataStoreRepository: DataStoreRepository,
    private val backupRepository: BackupRepository,
    private val appContext: Application
) : ViewModel() {
    private val _uiEvents = Channel<UiEvents>()
    val uiEvents = _uiEvents.receiveAsFlow()

    var appVersion: String by mutableStateOf(
        appContext.packageManager.getPackageInfo(
            appContext.packageName,
            0
        ).versionName ?: "1.0.0"
    )
        private set

    var bottomSheetOpenedBy by mutableStateOf(PRIVACY)
        private set

    var isSheetOpen by mutableStateOf(false)
        private set

    var isAnalysisSheetOpen by mutableStateOf(false)
        private set

    var analysisResult by mutableStateOf(PasswordAnalysisResult())
        private set

    var isAutoLockDialogOpen by mutableStateOf(false)
        private set

    val autoLockTimeout: StateFlow<AutoLockTimeout> = flow {
        emitAll(
            dataStoreRepository.readPreference(
                key = DataStoreRepository.autoLockTimeoutKey,
                defaultValue = AutoLockTimeout.DEFAULT.millis
            )
        )
    }.map { AutoLockTimeout.fromMillis(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoLockTimeout.DEFAULT)

    fun onEvent(event: SettingsEvents) {
        when (event) {
            SettingsEvents.OnLanguageClick -> {
                isSheetOpen = true
            }

            SettingsEvents.OnDismissBottomSheet -> {
                isSheetOpen = false
            }

            SettingsEvents.OnRateAppClick -> {
                val appPackageName = appContext.packageName
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$appPackageName".toUri()
                    )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            }

            SettingsEvents.OnPrivacyClick -> {
                bottomSheetOpenedBy = PRIVACY
            }

            SettingsEvents.OnTermsAndConditionClick -> {
                bottomSheetOpenedBy = TERMS_N_CONDITIONS
            }

            SettingsEvents.OnAboutClick -> {
                bottomSheetOpenedBy = ABOUT
            }

            is SettingsEvents.OnLanguageChange -> {
                isSheetOpen = false
                if (event.newLanguage.comingSoon) {
                    sendUiEvents(
                        UiEvents.ShowSnackBar(
                            message = UiText.StringResource(R.string.coming_soon)
                        )
                    )
                } else {
                    viewModelScope.launch {
                        dataStoreRepository.savePreference(
                            key = DataStoreRepository.currentLanguageKey,
                            value = event.newLanguage.languageId
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            appContext.getSystemService(LocaleManager::class.java).applicationLocales =
                                LocaleList.forLanguageTags(event.newLanguage.languageId)
                        } else {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(
                                    event.newLanguage.languageId
                                )
                            )
                        }
                    }
                }
            }

            is SettingsEvents.OnExportFileChosen -> {
                viewModelScope.launch {
                    val result = backupRepository.export(event.uri, event.password)
                    java.util.Arrays.fill(event.password, '\u0000')
                    sendUiEvents(
                        UiEvents.ShowSnackBar(
                            message = UiText.StringResource(
                                if (result.isSuccess) R.string.export_success
                                else R.string.export_failed
                            )
                        )
                    )
                }
            }

            is SettingsEvents.OnImportFileChosen -> {
                viewModelScope.launch {
                    val result = backupRepository.import(event.uri, event.password, event.mode)
                    event.password?.let { java.util.Arrays.fill(it, '\u0000') }
                    result.fold(
                        onSuccess = { summary ->
                            sendUiEvents(
                                UiEvents.ShowSnackBar(
                                    message = UiText.StringResource(
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
                            sendUiEvents(UiEvents.ShowSnackBar(UiText.StringResource(message)))
                        }
                    )
                }
            }

            SettingsEvents.OnAnalyzePasswordsClick -> {
                viewModelScope.launch {
                    val allDetails = repository.getDetails().first()
                    val keywords = secretFieldKeywords()
                    val result = withContext(Dispatchers.IO) {
                        PasswordAnalyzer.analyze(allDetails, keywords)
                    }
                    analysisResult = result
                    isAnalysisSheetOpen = true
                }
            }

            SettingsEvents.OnAutoLockClick -> {
                isAutoLockDialogOpen = true
            }

            SettingsEvents.OnDismissAutoLockDialog -> {
                isAutoLockDialogOpen = false
            }

            is SettingsEvents.OnAutoLockTimeoutChange -> {
                isAutoLockDialogOpen = false
                viewModelScope.launch {
                    dataStoreRepository.savePreference(
                        key = DataStoreRepository.autoLockTimeoutKey,
                        value = event.timeout.millis
                    )
                }
            }

            SettingsEvents.OnDismissAnalysisSheet -> {
                isAnalysisSheetOpen = false
            }

            is SettingsEvents.OnAnalysisItemClick -> {
                isAnalysisSheetOpen = false
                sendUiEvents(
                    UiEvents.Navigate(
                        Routes.DETAILS_PAGE + "?previewId=${event.detail.previewId}"
                    )
                )
            }
        }
    }

    /**
     * Union of the default-locale and current-locale keyword lists. A vault may hold entries
     * labelled before the user switched language, so matching only the current locale would
     * silently stop classifying them.
     */
    private fun secretFieldKeywords(): Set<String> {
        val current = appContext.resources.getStringArray(R.array.secret_field_keywords).toSet()
        val defaultLocaleConfig = android.content.res.Configuration(appContext.resources.configuration)
        defaultLocaleConfig.setLocale(java.util.Locale.ENGLISH)
        val fallback = appContext.createConfigurationContext(defaultLocaleConfig)
            .resources.getStringArray(R.array.secret_field_keywords).toSet()
        return current + fallback
    }

    private fun sendUiEvents(events: UiEvents) {
        viewModelScope.launch {
            _uiEvents.send(events)
        }
    }






}