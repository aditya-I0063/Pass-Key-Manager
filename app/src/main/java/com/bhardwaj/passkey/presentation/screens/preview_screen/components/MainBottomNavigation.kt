package com.bhardwaj.passkey.presentation.screens.preview_screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.presentation.screens.common.labelRes

data class BottomNavigationItem(
    val category: Category,
    val icon: ImageVector,
    val isVisible: Boolean = true
) {
    /** The persisted enum name, used as the navigation/selection key. */
    val title: String get() = category.name
}

val bottomNavigationList = listOf(
    BottomNavigationItem(
        category = Category.BANKS,
        icon = Icons.Filled.AccountBalance
    ),
    BottomNavigationItem(
        category = Category.APPS,
        icon = Icons.Filled.Gamepad
    ),
    // Invisible spacer that reserves room for the centre FAB notch. It is not a destination.
    BottomNavigationItem(
        category = Category.BANKS,
        icon = Icons.Filled.AccountBalance,
        isVisible = false,
    ),

    BottomNavigationItem(
        category = Category.MAILS,
        icon = Icons.Filled.Email
    ),
    BottomNavigationItem(
        category = Category.OTHERS,
        icon = Icons.AutoMirrored.Filled.Article
    )
)

@Composable
fun MainBottomNavigation(
    selectedIndex: Int,
    onItemClick: (index: Int, title: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomNavigationList.forEachIndexed { index, item ->
            Icon(
                imageVector = item.icon,
                // Previously the raw enum name ("BANKS"), announced in English in every locale.
                contentDescription = if (item.isVisible) {
                    stringResource(id = item.category.labelRes())
                } else {
                    null
                },
                modifier = Modifier
                    .then(
                        // The alpha-0 spacer stays reachable by TalkBack without this.
                        if (item.isVisible) Modifier else Modifier.clearAndSetSemantics { }
                    )
                    .size(42.dp)
                    .padding(8.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            if (item.isVisible) {
                                onItemClick(index, item.title)
                            }
                        })
                    .alpha(if (item.isVisible) 1F else 0F),
                tint = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}