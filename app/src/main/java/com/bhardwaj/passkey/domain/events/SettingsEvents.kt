package com.bhardwaj.passkey.domain.events

import android.net.Uri
import com.bhardwaj.passkey.data.backup.ImportMode
import com.bhardwaj.passkey.data.security.AutoLockTimeout
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.domain.models.Language

sealed interface SettingsEvents {
    data class OnLanguageChange(val newLanguage: Language) : SettingsEvents
    /** The user picked a destination and supplied a password for the encrypted backup. */
    data class OnExportFileChosen(val uri: Uri, val password: CharArray) : SettingsEvents {
        override fun equals(other: Any?) =
            this === other || (other is OnExportFileChosen && uri == other.uri &&
                password.contentEquals(other.password))

        override fun hashCode() = 31 * uri.hashCode() + password.contentHashCode()
    }

    /** [password] is null for a legacy plaintext .passkey file, which needs none. */
    data class OnImportFileChosen(
        val uri: Uri,
        val password: CharArray?,
        val mode: ImportMode
    ) : SettingsEvents {
        override fun equals(other: Any?) =
            this === other || (other is OnImportFileChosen && uri == other.uri &&
                password.contentEquals(other.password) && mode == other.mode)

        override fun hashCode(): Int {
            var result = uri.hashCode()
            result = 31 * result + (password?.contentHashCode() ?: 0)
            result = 31 * result + mode.hashCode()
            return result
        }
    }

    data class OnAnalysisItemClick(val detail: Details) : SettingsEvents
    data object OnLanguageClick : SettingsEvents
    data object OnDismissBottomSheet : SettingsEvents
    data object OnRateAppClick : SettingsEvents
    data object OnPrivacyClick : SettingsEvents
    data object OnTermsAndConditionClick : SettingsEvents
    data object OnAboutClick : SettingsEvents
    data object OnAnalyzePasswordsClick : SettingsEvents
    data object OnDismissAnalysisSheet : SettingsEvents
    data object OnAutoLockClick : SettingsEvents
    data object OnDismissAutoLockDialog : SettingsEvents
    data class OnAutoLockTimeoutChange(val timeout: AutoLockTimeout) : SettingsEvents
}