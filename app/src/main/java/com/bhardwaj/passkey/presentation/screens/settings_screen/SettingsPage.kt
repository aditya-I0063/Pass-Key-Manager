package com.bhardwaj.passkey.presentation.screens.settings_screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.backup.BackupFormat
import com.bhardwaj.passkey.data.backup.ImportMode
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.presentation.screens.common.ObserveAsEvents
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.AnalysisBottomSheet
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.AutoLockDialog
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.BackupPasswordDialog
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.FaqItem
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.LanguageBottomSheet
import com.bhardwaj.passkey.presentation.screens.settings_screen.components.SettingsText
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import com.bhardwaj.passkey.utils.UiText
import com.bhardwaj.passkey.utils.asString
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (NavRoute) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A bound method reference allocates a new object on every recomposition, so the lambda
    // parameter would never be equal and the whole subtree would recompose needlessly.
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val context = LocalContext.current

    // Backup file selection. The password is collected only after a destination or source is
    // chosen, so the user is never asked for one and then cancels out of the picker.
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> pendingExportUri = uri }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (isEncryptedBackup(it, context)) {
                pendingImportUri = it
            } else {
                // A legacy plaintext .passkey CSV needs no password; restore it directly.
                onIntent(SettingsIntent.ImportFileChosen(it, password = null, mode = ImportMode.MERGE))
            }
        }
    }

    // The sheet's openness follows the state rather than each click site launching its own
    // expand(), so there is one place where the two can no longer disagree.
    LaunchedEffect(state.sheet) {
        if (state.sheet == null) {
            scaffoldState.bottomSheetState.partialExpand()
        } else {
            scaffoldState.bottomSheetState.expand()
        }
    }

    // ...and a swipe down is a dismissal like any other, so the state hears about it.
    val sheetValue = scaffoldState.bottomSheetState.currentValue
    LaunchedEffect(sheetValue) {
        if (sheetValue == SheetValue.PartiallyExpanded) {
            onIntent(SettingsIntent.SheetDismissed)
        }
    }

    // Two steps: prove the current password, then choose the new one. Reuses the same dialog.
    when (state.recoveryChange) {
        RecoveryChangeStep.CURRENT_PASSWORD -> BackupPasswordDialog(
            confirmMode = false,
            titleRes = R.string.recovery_change_current_title,
            messageRes = R.string.recovery_change_current_message,
            onDismiss = { onIntent(SettingsIntent.RecoveryChangeDismissed) },
            onConfirm = { onIntent(SettingsIntent.CurrentRecoveryPasswordEntered(it)) }
        )

        RecoveryChangeStep.NEW_PASSWORD -> BackupPasswordDialog(
            confirmMode = true,
            titleRes = R.string.recovery_change_new_title,
            messageRes = R.string.recovery_change_new_message,
            onDismiss = { onIntent(SettingsIntent.RecoveryChangeDismissed) },
            onConfirm = { onIntent(SettingsIntent.NewRecoveryPasswordEntered(it)) }
        )

        null -> Unit
    }

    if (state.isAutoLockDialogOpen) {
        AutoLockDialog(
            current = state.autoLockTimeout,
            onDismiss = { onIntent(SettingsIntent.AutoLockDismissed) },
            onSelect = { onIntent(SettingsIntent.AutoLockTimeoutSelected(it)) }
        )
    }

    pendingExportUri?.let { uri ->
        BackupPasswordDialog(
            confirmMode = true,
            onDismiss = { pendingExportUri = null },
            onConfirm = { password ->
                pendingExportUri = null
                onIntent(SettingsIntent.ExportFileChosen(uri, password))
            }
        )
    }

    pendingImportUri?.let { uri ->
        BackupPasswordDialog(
            confirmMode = false,
            onDismiss = { pendingImportUri = null },
            onConfirm = { password ->
                pendingImportUri = null
                onIntent(SettingsIntent.ImportFileChosen(uri, password, ImportMode.MERGE))
            }
        )
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is SettingsEffect.Navigate -> onNavigate(effect.route)

            is SettingsEffect.ShowSnackbar -> scope.launch {
                snackBarHostState.showSnackbar(message = effect.message.asString(context))
            }

            // Started from the Activity context, and guarded: a device without the Play Store
            // used to take an unhandled ActivityNotFoundException straight to a crash.
            SettingsEffect.OpenStoreListing -> {
                val opened = runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()
                        )
                    )
                }.isSuccess
                if (!opened) {
                    scope.launch {
                        snackBarHostState.showSnackbar(
                            UiText.StringResource(R.string.store_unavailable).asString(context)
                        )
                    }
                }
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
                // Rendered inside the scaffold's own sheet rather than in a nested
                // ModalBottomSheet: that put a second window in front of an empty expanded
                // sheet, and only the activity window carries FLAG_SECURE reliably.
                when (val sheet = state.sheet) {
                    SettingsState.Sheet.Language -> LanguageBottomSheet(
                        onLanguageChange = { onIntent(SettingsIntent.LanguageSelected(it)) },
                        onBackIconClick = { onIntent(SettingsIntent.SheetDismissed) }
                    )

                    is SettingsState.Sheet.Info -> FaqItem(openedBy = sheet.topic)

                    // A sheet needs measurable content even while collapsed.
                    null -> Spacer(modifier = Modifier.fillMaxWidth().height(1.dp))
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
                                onIntent(SettingsIntent.LanguageClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.analyze_passwords)) {
                                onIntent(SettingsIntent.AnalyzeClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.rate_app)) {
                                onIntent(SettingsIntent.RateAppClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.auto_lock)) {
                                onIntent(SettingsIntent.AutoLockClicked)
                            }
                            SettingsText(
                                text = stringResource(id = R.string.change_recovery_password)
                            ) {
                                onIntent(SettingsIntent.ChangeRecoveryPasswordClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.import_data)) {
                                importLauncher.launch(arrayOf("*/*"))
                            }
                            SettingsText(text = stringResource(id = R.string.export_data)) {
                                exportLauncher.launch(defaultBackupFileName())
                            }
                            SettingsText(text = stringResource(id = R.string.privacy)) {
                                onIntent(SettingsIntent.PrivacyClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.terms_n_condition)) {
                                onIntent(SettingsIntent.TermsClicked)
                            }
                            SettingsText(text = stringResource(id = R.string.about)) {
                                onIntent(SettingsIntent.AboutClicked)
                            }
                        }
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.app_version, state.appVersion),
                        fontFamily = Poppins,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    state.analysis?.let { analysis ->
        AnalysisBottomSheet(
            result = analysis,
            onDismiss = { onIntent(SettingsIntent.AnalysisDismissed) },
            onDetailClick = { detail ->
                onIntent(SettingsIntent.AnalysisItemClicked(detail.previewId))
            }
        )
    }
}

/**
 * Detects the encrypted `.pkbak` format by its magic bytes rather than by filename.
 *
 * The previous check matched a display name ending in "passkey", which would have rejected
 * every new backup, and trusted a user-supplied filename to decide how to parse the contents.
 */
private fun isEncryptedBackup(uri: Uri, context: Context): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val magic = ByteArray(8)
        val read = input.read(magic)
        read == magic.size && BackupFormat.hasMagic(magic)
    } == true
} catch (_: Exception) {
    false
}

private fun defaultBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "passkey_backup_$stamp.pkbak"
}
