package com.bhardwaj.passkey.presentation.screens.splash_screen

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bhardwaj.passkey.presentation.screens.common.ObserveAsEvents
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.screens.splash_screen.SplashEvents
import com.bhardwaj.passkey.presentation.screens.splash_screen.SplashViewModel
import com.bhardwaj.passkey.presentation.theme.Poppins
import com.bhardwaj.passkey.utils.UiEvents
import kotlinx.coroutines.delay

@Composable
fun SplashPage(
    onNavigate: (UiEvents.Navigate) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val scale = remember {
        Animatable(0.3F)
    }

    ObserveAsEvents(viewModel.uiEvents) { event ->
            when (event) {
                is UiEvents.Navigate -> onNavigate(event)
                else -> Unit
            }
    }

    LaunchedEffect(key1 = true) {
        scale.animateTo(
            targetValue = 0.7F,
            animationSpec = tween(
                durationMillis = 700,
                easing = {
                    OvershootInterpolator(2F).getInterpolation(it)
                }
            )
        )
        delay(700)
        viewModel.onEvent(SplashEvents.OnLoadingComplete)
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale.value)
                    .clip(CircleShape)
            )
            Text(
                text = stringResource(id = R.string.app_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
                fontFamily = Poppins,
                fontSize = 12.sp,
                fontStyle = FontStyle.Normal,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}