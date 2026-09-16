package com.bhardwaj.passkey.utils

import com.bhardwaj.passkey.data.local.entity.Details
import java.text.Normalizer
import java.util.Locale

data class PasswordAnalysisResult(
    val totalPasswords: Int = 0,
    val weakPasswords: List<Details> = emptyList(),
    val reusedPasswords: Map<String, List<Details>> = emptyMap(),
    val strengthScore: Int = 0
)

object PasswordAnalyzer {

    /**
     * Analyses the vault.
     *
     * [secretKeywords] must be supplied by the caller from `R.array.secret_field_keywords`,
     * unioned across the default and current locale. Previously this object hardcoded the
     * English words "password", "pin", "code" and "secret", so across the app's 16 non-English
     * locales nothing matched: totalPasswords came back 0, the score came back 100, and users
     * were told their vault was perfect. That is worse than having no feature at all.
     */
    fun analyze(details: List<Details>, secretKeywords: Set<String>): PasswordAnalysisResult {
        val normalizedKeywords = secretKeywords.map { it.normalizeForMatch() }.filter { it.isNotBlank() }

        val passwordEntries = details.filter { detail ->
            detail.isSecret || normalizedKeywords.any { keyword ->
                detail.question.normalizeForMatch().contains(keyword)
            }
        }

        val weakList = passwordEntries.filter { isWeak(it.answer) }

        // Keyed by a digest rather than the plaintext: this map is held in ViewModel state and
        // rendered by the analysis sheet, and there is no reason for cleartext passwords to sit
        // in a composition-scoped object.
        val reused = passwordEntries
            .groupBy { it.answer }
            .filterValues { it.size > 1 }
            .mapKeys { (password, _) -> password.digestKey() }

        var score = 100
        if (passwordEntries.isNotEmpty()) {
            val weakPenalty = (weakList.size * 10).coerceAtMost(50)
            // Counts affected entries, not distinct duplicated values: three copies of one
            // password used to score the same as two, which understated the problem.
            val reusedEntryCount = reused.values.sumOf { it.size }
            val reusedPenalty = (reusedEntryCount * 15).coerceAtMost(50)
            score -= (weakPenalty + reusedPenalty)
        }

        return PasswordAnalysisResult(
            totalPasswords = passwordEntries.size,
            weakPasswords = weakList,
            reusedPasswords = reused,
            strengthScore = score.coerceAtLeast(0)
        )
    }

    private fun isWeak(password: String): Boolean {
        if (password.length < 8) return true
        if (password.all { it.isDigit() }) return true
        if (password.all { it.isLetter() }) return true
        if (password.toSet().size == 1) return true
        return false
    }

    /** Case-folded and accent-stripped, so "Contraseña" and "contrasena" both match. */
    private fun String.normalizeForMatch(): String =
        Normalizer.normalize(this, Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()

    private fun String.digestKey(): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
