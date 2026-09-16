package com.bhardwaj.passkey.presentation.screens.settings_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.AppLanguage
import com.bhardwaj.passkey.presentation.theme.Poppins

@Composable
fun LanguageItem(
    modifier: Modifier = Modifier,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onLanguageChange(language)
            }
    ) {
        Text(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            text = language.endonym,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            text = language.englishName,
            fontFamily = Poppins,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        HorizontalDivider()
    }
}

@Composable
fun LanguageBottomSheet(
    modifier: Modifier = Modifier,
    onBackIconClick: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Icon(
            modifier = Modifier
                .padding(16.dp)
                .padding(top = 16.dp)
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = { onBackIconClick() }
                ),
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back)
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = stringResource(id = R.string.change),
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = stringResource(id = R.string.language),
            fontFamily = Poppins,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyColumn {
            items(AppLanguage.entries) { single ->
                LanguageItem(language = single, onLanguageChange = onLanguageChange)
            }
        }
    }
}