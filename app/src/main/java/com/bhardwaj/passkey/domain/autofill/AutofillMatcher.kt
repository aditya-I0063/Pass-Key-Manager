package com.bhardwaj.passkey.domain.autofill

import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.Preview

/** One vault entry, reduced to what an autofill dataset can put in a form. */
data class AutofillCredential(
    val previewId: Long,
    val label: String,
    val username: String?,
    val password: String
)

/**
 * Decides which vault entries belong to the app or web page being filled.
 *
 * Kept free of Android types on purpose: the AssistStructure walk is untestable on the JVM, so
 * everything that can be a decision rather than a traversal lives here, where it can be.
 */
object AutofillMatcher {

    private const val MIN_TOKEN_LENGTH = 3

    /**
     * Parts of a package name or domain that identify nobody. "com.google.android.gm" and
     * "accounts.google.com" both have to reduce to "google" for either to find a Gmail entry.
     */
    private val NOISE = setOf(
        "com", "org", "net", "io", "co", "uk", "in", "de", "app", "apps", "android",
        "www", "mobile", "web", "login", "signin", "sign", "auth", "account", "accounts",
        "secure", "my", "id", "www2", "m"
    )

    fun tokensOf(packageName: String?, webDomain: String?): Set<String> {
        val tokens = mutableSetOf<String>()
        webDomain?.let { domain ->
            val host = domain.substringAfter("://").substringBefore('/').lowercase()
            // The whole host, so an entry literally headed "mail.proton.me" still matches.
            if (host.isNotBlank()) tokens += host
            tokens += host.split('.')
        }
        packageName?.let { tokens += it.lowercase().split('.') }
        return tokens
            .map { it.normalize() }
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in NOISE }
            .toSet()
    }

    /**
     * Entries whose heading names the same service. Matching runs both ways because a heading is
     * free text: "Google" matches the token "google", and the heading "Gmail Work Account"
     * contains it.
     */
    fun match(
        previews: List<Preview>,
        detailsByPreview: Map<Long, List<Detail>>,
        tokens: Set<String>,
        secretKeywords: Set<String> = emptySet()
    ): List<AutofillCredential> {
        if (tokens.isEmpty()) return emptyList()
        return previews
            .filter { preview -> matches(preview.heading, tokens) }
            .mapNotNull { preview ->
                credentialFor(preview, detailsByPreview[preview.id].orEmpty(), secretKeywords)
            }
    }

    private fun matches(heading: String, tokens: Set<String>): Boolean {
        val normalized = heading.normalize()
        if (normalized.length < MIN_TOKEN_LENGTH) return false
        return tokens.any { token -> normalized.contains(token) || token.contains(normalized) }
    }

    /**
     * The password is the entry's secret; the username is whatever non-secret value comes first.
     *
     * Picking the username by elimination rather than by matching words like "user" or "email"
     * is deliberate - that kind of keyword match is what made the password analyser return
     * "perfect vault" in all 16 non-English locales.
     */
    fun credentialFor(
        preview: Preview,
        details: List<Detail>,
        secretKeywords: Set<String> = emptySet()
    ): AutofillCredential? {
        val ordered = details.sortedBy { it.sequence }
        val password = ordered.firstOrNull { it.isSecret }
            ?: ordered.firstOrNull { detail -> secretKeywords.any { detail.question.normalize().contains(it.normalize()) } }
            ?: return null
        if (password.answer.isBlank()) return null
        val username = ordered.firstOrNull { it.id != password.id && it.answer.isNotBlank() }
        return AutofillCredential(
            previewId = preview.id,
            label = preview.heading,
            username = username?.answer,
            password = password.answer
        )
    }

    private fun String.normalize(): String =
        lowercase().filter { it.isLetterOrDigit() }
}
