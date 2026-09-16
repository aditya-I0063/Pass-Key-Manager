package com.bhardwaj.passkey.presentation.screens.settings_screen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.events.SettingsEvents
import com.bhardwaj.passkey.domain.viewModels.SettingsViewModel
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.AnalysisBottomSheet
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.FaqItem
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.LanguageBottomSheet
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.SettingsText
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import com.bhardwaj.passkey.utils.Constants.Companion.FILE_TYPE
import com.bhardwaj.passkey.utils.asString
import com.bhardwaj.passkey.utils.UiEvents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onPopBackStack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    val context = LocalContext.current
    val invalidFileMessage = stringResource(R.string.invalid_file_selected)

    val importDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { document ->
        document?.let { uri ->
            if (isPasskeyFile(uri, context)) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().use { text -> text.readText() }
                    viewModel.onEvent(SettingsEvents.OnImportDataClick(content))
                }
            } else {
                scope.launch {
                    snackBarHostState.showSnackbar(
                        message = invalidFileMessage
                    )
                }
            }
        }
    }

    val isAnalysisSheetOpen = viewModel.isAnalysisSheetOpen
    val analysisResult = viewModel.analysisResult

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvents.PopBackStack -> onPopBackStack()
                is UiEvents.Navigate -> onNavigate(event.route)
                is UiEvents.ShowSnackBar -> {
                    scope.launch {
                        snackBarHostState.showSnackbar(
                            message = event.message.asString(context),
                            actionLabel = event.action?.asString(context)
                        )
                    }
                }

                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { paddingValues ->
        BottomSheetScaffold(
            modifier = Modifier.padding(paddingValues),
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetShape = RectangleShape,
            sheetSwipeEnabled = true,
            sheetDragHandle = {},
            sheetContent = {
                if (viewModel.isSheetOpen) {
                    val sheetState = rememberModalBottomSheetState()
                    ModalBottomSheet(
                        properties = ModalBottomSheetProperties(
                            securePolicy = SecureFlagPolicy.SecureOn
                        ),
                        sheetState = sheetState,
                        onDismissRequest = { viewModel.onEvent(SettingsEvents.OnDismissBottomSheet) },
                        dragHandle = {},
                        shape = RectangleShape,
                    ) {
                        LanguageBottomSheet(
                            onLanguageChange = {
                                viewModel.onEvent(
                                    SettingsEvents.OnLanguageChange(
                                        newLanguage = it
                                    )
                                )
                            },
                            onBackIconClick = {
                                viewModel.onEvent(SettingsEvents.OnDismissBottomSheet)
                            }
                        )
                    }
                } else {
                    FaqItem(openedBy = viewModel.bottomSheetOpenedBy)
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.icon_logo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(id = R.string.more),
                            fontFamily = BebasNeue,
                            fontSize = 64.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        ) {
                            SettingsText(text = stringResource(id = R.string.change_language)) {
                                viewModel.onEvent(SettingsEvents.OnLanguageClick)
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                            SettingsText(text = stringResource(id = R.string.analyze_passwords)) {
                                viewModel.onEvent(SettingsEvents.OnAnalyzePasswordsClick)
                            }
                            SettingsText(text = stringResource(id = R.string.rate_app)) {
                                viewModel.onEvent(SettingsEvents.OnRateAppClick)
                            }
                            SettingsText(text = stringResource(id = R.string.import_data)) {
                                importDataLauncher.launch(arrayOf("*/*"))
                            }
                            SettingsText(text = stringResource(id = R.string.export_data)) {
                                viewModel.onEvent(SettingsEvents.OnExportDataClick)
                            }
                            SettingsText(text = stringResource(id = R.string.privacy)) {
                                viewModel.onEvent(SettingsEvents.OnPrivacyClick)
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                            SettingsText(text = stringResource(id = R.string.terms_n_condition)) {
                                viewModel.onEvent(SettingsEvents.OnTermsAndConditionClick)
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                            SettingsText(text = stringResource(id = R.string.about)) {
                                viewModel.onEvent(SettingsEvents.OnAboutClick)
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
                        }
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.app_version, viewModel.appVersion),
                        fontFamily = Poppins,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    if (isAnalysisSheetOpen) {
        AnalysisBottomSheet(
            result = analysisResult,
            onDismiss = { viewModel.onEvent(SettingsEvents.OnDismissAnalysisSheet) },
            onDetailClick = { detail ->
                viewModel.onEvent(SettingsEvents.OnAnalysisItemClick(detail))
            }
        )
    }
}

private fun isPasskeyFile(uri: Uri, context: Context): Boolean {
    val contentResolver: ContentResolver = context.contentResolver
    return try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val fileName = cursor.getString(nameIndex)
                fileName.endsWith(FILE_TYPE)
            } else {
                false
            }
        } == true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}