package com.bhardwaj.passkey.presentation.screens.settings_screen.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.presentation.theme.BebasNeue
import com.bhardwaj.passkey.presentation.theme.Poppins
import com.bhardwaj.passkey.utils.PasswordAnalysisResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisBottomSheet(
    result: PasswordAnalysisResult,
    onDismiss: () -> Unit,
    onDetailClick: (Detail) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        sheetState = sheetState,
        onDismissRequest = { onDismiss() },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.security_analysis),
                fontFamily = BebasNeue,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            ScoreCircularIndicator(score = result.strengthScore)

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (result.weakPasswords.isNotEmpty()) {
                    item {
                        AnalysisSectionHeader(
                            title = stringResource(R.string.weak_passwords),
                            icon = Icons.Rounded.Warning,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(result.weakPasswords) { detail ->
                        SimpleAnalysisItem(
                            detail = detail,
                            onClick = { onDetailClick(detail) }
                        )
                    }
                }

                if (result.reusedPasswords.isNotEmpty()) {
                    item {
                        AnalysisSectionHeader(
                            title = stringResource(R.string.reused_passwords),
                            icon = Icons.Rounded.Loop,
                            color = Color(0xFFFF9800) // Orange
                        )
                    }
                    items(result.reusedPasswords.entries.toList()) { (password, details) ->
                        ReusedAnalysisItem(
                            password = password,
                            count = details.size,
                            examples = details,
                            onDetailClick = onDetailClick
                        )
                    }
                }

                if (result.weakPasswords.isEmpty() && result.reusedPasswords.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.no_issues),
                                    fontFamily = Poppins,
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreCircularIndicator(
    score: Int,
    size: Dp = 160.dp,
    strokeWidth: Dp = 10.dp
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val curPercentage = animateFloatAsState(
        targetValue = if (animationPlayed) score / 100f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 200),
        label = "score"
    )

    val color = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when {
        score >= 80 -> stringResource(R.string.excellent)
        score >= 50 -> stringResource(R.string.needs_attention)
        else -> stringResource(R.string.critical)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360 * curPercentage.value,
                useCenter = false,
                style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.security_score_percent, score),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Poppins,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontFamily = Poppins,
                color = color
            )
        }
    }
}

@Composable
fun AnalysisSectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontFamily = Poppins,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun SimpleAnalysisItem(detail: Detail, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = detail.question,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = detail.answer,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Poppins
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.cd_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ReusedAnalysisItem(
    password: String,
    count: Int,
    examples: List<Detail>,
    onDetailClick: (Detail) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = pluralStringResource(R.plurals.password_used_times, count, count),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = password,
                    fontFamily = Poppins,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            examples.forEach { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDetailClick(detail) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = detail.question,
                        fontFamily = Poppins,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}