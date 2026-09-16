package com.bhardwaj.passkey.presentation.screens.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.presentation.theme.Poppins

@Composable
fun PasskeySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onTrailingIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(48.dp)
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )

        BasicTextField(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .weight(1F),
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                fontFamily = Poppins,
                fontSize = 16.sp,
                fontStyle = FontStyle.Normal,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            maxLines = 1,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.search),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        if (query.isNotEmpty()) {
            Icon(
                modifier = Modifier.clickable(
                    onClick = { onTrailingIconClick() },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_clear_search),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}