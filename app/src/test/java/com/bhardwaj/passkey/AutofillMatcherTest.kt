package com.bhardwaj.passkey

import com.bhardwaj.passkey.domain.autofill.AutofillMatcher
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.Preview
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutofillMatcherTest {

    private fun preview(id: Long, heading: String) =
        Preview(id = id, heading = heading, category = Category.APPS)

    private fun detail(
        id: Long,
        previewId: Long,
        question: String,
        answer: String,
        sequence: Long = 0,
        isSecret: Boolean = false
    ) = Detail(id, previewId, question, answer, sequence, isSecret)

    @Test
    fun `a package name and a domain reduce to the same identifying token`() {
        assertThat(AutofillMatcher.tokensOf("com.google.android.gm", null)).contains("google")
        assertThat(AutofillMatcher.tokensOf(null, "accounts.google.com")).contains("google")
    }

    @Test
    fun `boilerplate parts of a name identify nobody and are dropped`() {
        val tokens = AutofillMatcher.tokensOf("com.example.android.app", "www.login.example.com")
        assertThat(tokens).containsExactly("example", "wwwloginexamplecom")
    }

    @Test
    fun `the full host survives, so an entry headed with one still matches`() {
        val tokens = AutofillMatcher.tokensOf(null, "https://mail.proton.me/login")
        val previews = listOf(preview(1, "mail.proton.me"))
        val details = mapOf(1L to listOf(detail(10, 1, "Password", "pw", isSecret = true)))

        assertThat(AutofillMatcher.match(previews, details, tokens).map { it.label })
            .containsExactly("mail.proton.me")
    }

    @Test
    fun `no identifying token means no suggestions rather than every entry`() {
        val previews = listOf(preview(1, "Gmail"))
        val details = mapOf(1L to listOf(detail(10, 1, "Password", "pw", isSecret = true)))

        assertThat(AutofillMatcher.match(previews, details, emptySet())).isEmpty()
    }

    @Test
    fun `an entry with no secret is not offered`() {
        // Filling a form with a note would be worse than offering nothing.
        val previews = listOf(preview(1, "Google"))
        val details = mapOf(1L to listOf(detail(10, 1, "Note", "some text")))

        assertThat(AutofillMatcher.match(previews, details, setOf("google"))).isEmpty()
    }

    @Test
    fun `a secret with an empty value is not offered`() {
        val previews = listOf(preview(1, "Google"))
        val details = mapOf(1L to listOf(detail(10, 1, "Password", "   ", isSecret = true)))

        assertThat(AutofillMatcher.match(previews, details, setOf("google"))).isEmpty()
    }

    @Test
    fun `the username is the first non-secret value, not a keyword match`() {
        val previews = listOf(preview(1, "Google"))
        val details = mapOf(
            1L to listOf(
                detail(10, 1, "correo", "alice@example.com", sequence = 0),
                detail(11, 1, "contraseña", "s3cret", sequence = 1, isSecret = true)
            )
        )

        val credential = AutofillMatcher.match(previews, details, setOf("google")).single()
        assertThat(credential.username).isEqualTo("alice@example.com")
        assertThat(credential.password).isEqualTo("s3cret")
    }

    @Test
    fun `an older entry with no isSecret flag falls back to keyword matching`() {
        // Rows written before the isSecret column existed default to false.
        val previews = listOf(preview(1, "Google"))
        val details = mapOf(
            1L to listOf(
                detail(10, 1, "Username", "alice", sequence = 0),
                detail(11, 1, "Password", "s3cret", sequence = 1)
            )
        )

        val credential = AutofillMatcher
            .match(previews, details, setOf("google"), secretKeywords = setOf("password"))
            .single()
        assertThat(credential.password).isEqualTo("s3cret")
        assertThat(credential.username).isEqualTo("alice")
    }

    @Test
    fun `matching is case and punctuation insensitive in both directions`() {
        val previews = listOf(preview(1, "G-Mail"), preview(2, "Gmail Work Account"))
        val details = (1L..2L).associateWith {
            listOf(detail(it * 10, it, "Password", "pw", isSecret = true))
        }

        assertThat(AutofillMatcher.match(previews, details, setOf("gmail")).map { it.previewId })
            .containsExactly(1L, 2L)
    }

    @Test
    fun `an unrelated entry is not offered`() {
        val previews = listOf(preview(1, "Netflix"))
        val details = mapOf(1L to listOf(detail(10, 1, "Password", "pw", isSecret = true)))

        assertThat(AutofillMatcher.match(previews, details, setOf("google"))).isEmpty()
    }

    @Test
    fun `a one or two letter heading cannot match everything`() {
        val previews = listOf(preview(1, "X"))
        val details = mapOf(1L to listOf(detail(10, 1, "Password", "pw", isSecret = true)))

        assertThat(AutofillMatcher.match(previews, details, setOf("example"))).isEmpty()
    }
}
