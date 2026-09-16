package com.bhardwaj.passkey.presentation.screens.settings_screen

import android.net.Uri
import com.bhardwaj.passkey.data.backup.ImportMode
import com.bhardwaj.passkey.domain.model.AutoLockTimeout
import com.bhardwaj.passkey.domain.model.AppLanguage
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.utils.AlertBy
import com.bhardwaj.passkey.utils.PasswordAnalysisResult
import com.bhardwaj.passkey.utils.UiText

data class SettingsState(
    val appVersion: String = "",
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.DEFAULT,
    /** Non-null means the bottom sheet is open, and says what it is showing. */
    val sheet: Sheet? = null,
    val isAutoLockDialogOpen: Boolean = false,
    /** Non-null means the analysis sheet is showing this result. */
    val analysis: PasswordAnalysisResult? = null,
    val recoveryChange: RecoveryChangeStep? = null
) {
    /**
     * The screen has one bottom sheet with two possible bodies. Modelling it as a single
     * nullable value replaces an `isSheetOpen` boolean and a separate `bottomSheetOpenedBy`
     * that could disagree: dismissing the language picker used to leave the sheet expanded
     * showing whichever legal text had been opened last.
     */
    sealed interface Sheet {
        data object Language : Sheet
        data class Info(val topic: AlertBy) : Sheet
    }
}

/** Replaces a `Boolean?` whose three values had to be decoded at every use site. */
enum class RecoveryChangeStep { CURRENT_PASSWORD, NEW_PASSWORD }

sealed interface SettingsIntent {
    data object LanguageClicked : SettingsIntent
    data class LanguageSelected(val language: AppLanguage) : SettingsIntent

    data object PrivacyClicked : SettingsIntent
    data object TermsClicked : SettingsIntent
    data object AboutClicked : SettingsIntent
    data object SheetDismissed : SettingsIntent

    data object RateAppClicked : SettingsIntent
    data object AutofillClicked : SettingsIntent

    data class ExportFileChosen(val uri: Uri, val password: CharArray) : SettingsIntent {
        override fun equals(other: Any?) = this === other ||
            (other is ExportFileChosen && uri == other.uri && password.contentEquals(other.password))

        override fun hashCode() = 31 * uri.hashCode() + password.contentHashCode()
    }

    /** [password] is null for a legacy plaintext .passkey CSV, which needs none. */
    data class ImportFileChosen(
        val uri: Uri,
        val password: CharArray?,
        val mode: ImportMode
    ) : SettingsIntent {
        override fun equals(other: Any?) = this === other ||
            (other is ImportFileChosen && uri == other.uri &&
                password.contentEquals(other.password) && mode == other.mode)

        override fun hashCode(): Int {
            var result = uri.hashCode()
            result = 31 * result + (password?.contentHashCode() ?: 0)
            return 31 * result + mode.hashCode()
        }
    }

    data object AnalyzeClicked : SettingsIntent
    data object AnalysisDismissed : SettingsIntent
    data class AnalysisItemClicked(val previewId: Long) : SettingsIntent

    data object AutoLockClicked : SettingsIntent
    data object AutoLockDismissed : SettingsIntent
    data class AutoLockTimeoutSelected(val timeout: AutoLockTimeout) : SettingsIntent

    data object ChangeRecoveryPasswordClicked : SettingsIntent
    data object RecoveryChangeDismissed : SettingsIntent
    data class CurrentRecoveryPasswordEntered(val password: CharArray) : SettingsIntent {
        override fun equals(other: Any?) = this === other ||
            (other is CurrentRecoveryPasswordEntered && password.contentEquals(other.password))

        override fun hashCode() = password.contentHashCode()
    }

    data class NewRecoveryPasswordEntered(val password: CharArray) : SettingsIntent {
        override fun equals(other: Any?) = this === other ||
            (other is NewRecoveryPasswordEntered && password.contentEquals(other.password))

        override fun hashCode() = password.contentHashCode()
    }
}

sealed interface SettingsEffect {
    data class ShowSnackbar(val message: UiText) : SettingsEffect
    data class Navigate(val route: NavRoute) : SettingsEffect

    /**
     * Opening the store listing needs a visible Activity, so the UI performs it. The old code
     * started the intent from the Application context with FLAG_ACTIVITY_NEW_TASK and never
     * handled the device having no Play Store, which threw ActivityNotFoundException.
     */
    data object OpenStoreListing : SettingsEffect

    /** Android owns the autofill service picker; the app can only ask for it to be shown. */
    data object OpenAutofillSettings : SettingsEffect
}
