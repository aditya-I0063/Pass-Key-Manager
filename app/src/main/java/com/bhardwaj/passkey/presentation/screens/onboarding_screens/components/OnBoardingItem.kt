package com.bhardwaj.passkey.presentation.screens.onboarding_screens.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.OnBoardingScreen
import com.bhardwaj.passkey.presentation.theme.Poppins


@Composable
fun OnBoardingItem(
    modifier: Modifier = Modifier,
    screen: OnBoardingScreen
) {
    Column(
        modifier = modifier
            .fillMaxHeight(0.8F)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon_logo),
            contentDescription = null
        )
        TextHighlighter(
            modifier = Modifier.padding(top = 72.dp),
            markedUpText = screen.title
        )
        Text(
            modifier = Modifier.padding(top = 24.dp),
            text = screen.description,
            fontFamily = Poppins,
            fontStyle = FontStyle.Normal,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}