package com.bhardwaj.passkey.presentation.screens.security_screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.events.SecurityEvents
import com.bhardwaj.passkey.domain.viewModels.SecurityViewModel
import com.bhardwaj.passkey.presentation.theme.Poppins
import com.bhardwaj.passkey.utils.asString
import com.bhardwaj.passkey.utils.UiEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    navController: NavController,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvents.Navigate -> {
                    navController.popBackStack()
                    navController.navigate(event.route)
                }

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

    val activity = LocalActivity.current as FragmentActivity
    val startForResult =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == FragmentActivity.RESULT_CANCELED) {
                viewModel.onEvent(SecurityEvents.AddPasswordToTheDeviceFirst)
            }
            viewModel.onEvent(SecurityEvents.OnAuthenticationError)
        }

    val title = stringResource(id = R.string.app_name)
    val subTitle = stringResource(id = R.string.welcome_to_pass_key)

    LaunchedEffect(key1 = Unit) {
        biometricPrompt(
            title = title,
            subTitle = subTitle,
            context = context,
            activity = activity,
            viewModel = viewModel,
            scope = scope,
            startForResult = startForResult
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (viewModel.needToOpenAlertDialog) {
                Dialog(
                    onDismissRequest = {}, properties = DialogProperties(
                        securePolicy = SecureFlagPolicy.SecureOn,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                modifier = Modifier.padding(16.dp),
                                text = stringResource(id = R.string.security_heading),
                                fontSize = 16.sp,
                                fontStyle = FontStyle.Normal,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 24.dp
                                ),
                                text = stringResource(id = R.string.security_description),
                                fontSize = 16.sp,
                                fontStyle = FontStyle.Normal,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        },
                                        onClick = {
                                            scope.launch {
                                                biometricPrompt(
                                                    title = title,
                                                    subTitle = subTitle,
                                                    context = context,
                                                    activity = activity,
                                                    viewModel = viewModel,
                                                    scope = scope,
                                                    startForResult = startForResult
                                                )
                                            }
                                        }
                                    )
                                    .padding(8.dp),
                                text = stringResource(id = R.string.security_button),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontStyle = FontStyle.Normal,
                                fontFamily = Poppins,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

fun biometricPrompt(
    title: String,
    subTitle: String,
    context: Context,
    activity: FragmentActivity,
    viewModel: SecurityViewModel,
    scope: CoroutineScope,
    startForResult: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricManager by lazy { BiometricManager.from(context) }
    val allowedAuthenticators = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subTitle)
        .setAllowedAuthenticators(allowedAuthenticators)
        .build()

    val biometricPrompt = BiometricPrompt(
        activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                viewModel.onEvent(SecurityEvents.OnAuthenticationError)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.onEvent(SecurityEvents.OnAuthenticationSucceeded)
            }
        }
    )
    when (biometricManager.canAuthenticate(allowedAuthenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            biometricPrompt.authenticate(promptInfo)
        }

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        allowedAuthenticators
                    )
                }
                scope.launch {
                    startForResult.launch(intent)
                }
            } else {
                viewModel.onEvent(SecurityEvents.AddPasswordToTheDeviceFirst)
            }
        }

        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
            viewModel.onEvent(SecurityEvents.OnAuthenticationError)
        }
    }
}