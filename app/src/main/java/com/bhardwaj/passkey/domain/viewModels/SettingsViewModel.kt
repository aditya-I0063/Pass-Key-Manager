package com.bhardwaj.passkey.domain.viewModels

import android.app.Application
import android.app.LocaleManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview
import com.bhardwaj.passkey.data.repository.DataStoreRepository
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.events.SettingsEvents
import com.bhardwaj.passkey.presentation.navigation.Routes
import com.bhardwaj.passkey.utils.AlertBy.ABOUT
import com.bhardwaj.passkey.utils.AlertBy.PRIVACY
import com.bhardwaj.passkey.utils.AlertBy.TERMS_N_CONDITIONS
import com.bhardwaj.passkey.utils.Categories
import com.bhardwaj.passkey.utils.Constants.Companion.FILE_HEADER
import com.bhardwaj.passkey.utils.Constants.Companion.FILE_NAME
import com.bhardwaj.passkey.utils.Constants.Companion.FILE_PICKER_TYPE
import com.bhardwaj.passkey.utils.Constants.Companion.FILE_TYPE
import com.bhardwaj.passkey.utils.PasswordAnalysisResult
import com.bhardwaj.passkey.utils.PasswordAnalyzer
import com.bhardwaj.passkey.utils.UiEvents
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.StringReader
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    private val dataStoreRepository: DataStoreRepository,
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

            SettingsEvents.OnExportDataClick -> {
                viewModelScope.launch {
                    val fileName = "${FILE_NAME}_${System.currentTimeMillis()}.${FILE_TYPE}"

                    if (exportDataToStorage(fileName)) {
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.export_download)
                            )
                        )
                    } else {
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.something_went_wrong)
                            )
                        )
                    }
                }
            }

            is SettingsEvents.OnImportDataClick -> {
                viewModelScope.launch {
                    if (importDateFromStorage(event.content)) {
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.import_success)
                            )
                        )
                    } else {
                        sendUiEvents(
                            UiEvents.ShowSnackBar(
                                message = UiText.StringResource(R.string.import_failed)
                            )
                        )
                    }
                }
            }

            SettingsEvents.OnAnalyzePasswordsClick -> {
                viewModelScope.launch {
                    val allDetails = repository.getDetails().first()
                    val result = withContext(Dispatchers.IO) {
                        PasswordAnalyzer.analyze(allDetails)
                    }
                    analysisResult = result
                    isAnalysisSheetOpen = true
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

    private fun sendUiEvents(events: UiEvents) {
        viewModelScope.launch {
            _uiEvents.send(events)
        }
    }

    private fun replaceCommasWithUnderscores(text: String): String {
        return text.replace(",", "____")
    }

    private fun replaceUnderscoresWithCommas(text: String): String {
        return text.replace("____", ",")
    }

    private suspend fun exportDataToStorage(fileName: String): Boolean {
        return try {
            val allPreviews = repository.getPreviews().first()

            val csvBuilder = StringBuilder()
            csvBuilder.append(FILE_HEADER)
            allPreviews.forEach { preview ->
                val previewId = preview.previewId!!
                val details = repository.getDetailsByPreviewId(previewId).first()
                details.forEach {
                    val categoryName = preview.categoryName
                    val heading = replaceCommasWithUnderscores(preview.heading)
                    val question = replaceCommasWithUnderscores(it.question)
                    val answer = replaceCommasWithUnderscores(it.answer)

                    csvBuilder.append("$categoryName,$heading,$question,$answer\n")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadsApi29AndAbove(csvBuilder.toString(), fileName)
            } else {
                saveToDownloadsLegacy(csvBuilder.toString(), fileName)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveToDownloadsApi29AndAbove(data: String, filename: String) {
        withContext(Dispatchers.IO) {
            val contentResolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, FILE_PICKER_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri: Uri? =
                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                try {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(data.toByteArray())
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } ?: throw IOException("Failed to create new MediaStore record.")
        }
    }

    private fun saveToDownloadsLegacy(data: String, filename: String) {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, filename)
        file.writeText(data)
    }

    private suspend fun importDateFromStorage(content: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val reader = BufferedReader(StringReader(content))
                reader.readLine()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val values = line!!.split(",")
                    if (values.size != 4) {
                        continue
                    }

                    val category = replaceUnderscoresWithCommas(values[0].trim())
                    val heading = replaceUnderscoresWithCommas(values[1].trim())
                    val question = replaceUnderscoresWithCommas(values[2].trim())
                    val answer = replaceUnderscoresWithCommas(values[3].trim())

                    val existingPreview = repository.getPreviewByHeading(heading, category)

                    val previewId: Long = if (existingPreview != null) {
                        existingPreview.previewId!!
                    } else {
                        repository.upsertPreview(
                            Preview(
                                heading = heading,
                                categoryName = Categories.valueOf(category)
                            )
                        )
                    }

                    val existingDetail = repository.getDetailByContent(previewId, question, answer)
                    if (existingDetail == null) {
                        repository.upsertDetails(
                            Details(
                                previewId = previewId,
                                question = question,
                                answer = answer
                            )
                        )
                    }
                }
                reader.close()
                return@withContext true
            }
        } catch (_: Exception) {
            return false
        }
    }
}