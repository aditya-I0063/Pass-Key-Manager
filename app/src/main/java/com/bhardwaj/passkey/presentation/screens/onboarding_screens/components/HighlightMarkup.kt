package com.bhardwaj.passkey.presentation.screens.onboarding_screens.components

/**
 * A span of [text] that should be rendered with the highlight colour.
 */
data class HighlightSpan(val start: Int, val endExclusive: Int)

/**
 * The result of stripping highlight markers out of a raw string resource.
 *
 * @param text the display text, with all markers removed
 * @param spans the ranges of [text] that were wrapped in markers
 */
data class MarkedUpText(val text: String, val spans: List<HighlightSpan>)

private const val OPEN = "[["
private const val CLOSE = "]]"

/**
 * Parses `[[ ]]` highlight markers out of a translatable string.
 *
 * The marker lives inside the string resource so that translators choose which word is
 * emphasised in their own language. Previously the highlighted word was a hardcoded English
 * literal matched with `indexOf`, which returned -1 for every translated heading and crashed
 * `AnnotatedString.addStyle`.
 *
 * This parser never throws. Unbalanced, empty or absent markers simply yield fewer spans, so a
 * translator dropping a marker degrades to unstyled text rather than a crash.
 */
fun parseHighlightMarkup(raw: String): MarkedUpText {
    val out = StringBuilder(raw.length)
    val spans = mutableListOf<HighlightSpan>()
    var i = 0
    var openAt: Int? = null

    while (i < raw.length) {
        when {
            openAt == null && raw.startsWith(OPEN, i) -> {
                openAt = out.length
                i += OPEN.length
            }

            openAt != null && raw.startsWith(CLOSE, i) -> {
                if (openAt < out.length) spans += HighlightSpan(openAt, out.length)
                openAt = null
                i += CLOSE.length
            }

            else -> {
                out.append(raw[i])
                i++
            }
        }
    }
    return MarkedUpText(text = out.toString(), spans = spans)
}
