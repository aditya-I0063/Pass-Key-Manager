package com.bhardwaj.passkey.presentation.screens.onboarding_screens.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bhardwaj.passkey.presentation.theme.BebasNeue

/**
 * Renders an onboarding heading, colouring the words the translator wrapped in `[[ ]]`.
 *
 * The heading auto-sizes between 36sp and 64sp: the strings are laid out on three lines and
 * translations of "Generate\nSecure\nPasswords." are considerably longer in several supported
 * languages, which would clip at a fixed 64sp on small screens.
 */
@Composable
fun TextHighlighter(
    markedUpText: String,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val parsed = remember(markedUpText) { parseHighlightMarkup(markedUpText) }

    val annotatedString = remember(parsed, highlightColor) {
        buildAnnotatedString {
            append(parsed.text)
            parsed.spans.forEach { span ->
                // Defensive: the parser cannot currently emit out-of-bounds spans, but clamping
                // here means a future change can never reintroduce the addStyle crash.
                val start = span.start.coerceIn(0, parsed.text.length)
                val end = span.endExclusive.coerceIn(start, parsed.text.length)
                if (end > start) {
                    addStyle(
                        style = SpanStyle(color = highlightColor),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    Text(
        modifier = modifier,
        text = annotatedString,
        autoSize = TextAutoSize.StepBased(minFontSize = 36.sp, maxFontSize = 64.sp),
        fontFamily = BebasNeue,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        // Relative to the resolved font size, so it tracks auto-sizing.
        lineHeight = 1.1.em,
        color = MaterialTheme.colorScheme.secondary
    )
}
