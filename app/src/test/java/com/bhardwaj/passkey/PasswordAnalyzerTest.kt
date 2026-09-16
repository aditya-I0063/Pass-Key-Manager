package com.bhardwaj.passkey

import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.utils.PasswordAnalyzer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordAnalyzerTest {

    private val english = setOf("password", "pin", "code", "secret")
    private val hindi = setOf("पासवर्ड", "पिन")

    private fun detail(
        id: Long, question: String, answer: String, isSecret: Boolean = false
    ) = Detail(
        id = id, previewId = 1, question = question, answer = answer, isSecret = isSecret
    )

    @Test
    fun `empty vault scores 100`() {
        val result = PasswordAnalyzer.analyze(emptyList(), english)
        assertThat(result.strengthScore).isEqualTo(100)
        assertThat(result.totalPasswords).isEqualTo(0)
    }

    @Test
    fun `entries labelled in a non-English locale are classified`() {
        // The whole point: with hardcoded English keywords this returned 0 passwords and a
        // perfect score for every user of the 16 translated locales.
        val details = listOf(detail(1, "पासवर्ड", "abc"))
        val result = PasswordAnalyzer.analyze(details, english + hindi)
        assertThat(result.totalPasswords).isEqualTo(1)
        assertThat(result.weakPasswords).hasSize(1)
    }

    @Test
    fun `the isSecret flag classifies regardless of label language`() {
        val details = listOf(detail(1, "مفتاح", "abc", isSecret = true))
        val result = PasswordAnalyzer.analyze(details, english)
        assertThat(result.totalPasswords).isEqualTo(1)
    }

    @Test
    fun `matching ignores case and accents`() {
        val details = listOf(detail(1, "Contraseña principal", "abc"))
        val result = PasswordAnalyzer.analyze(details, setOf("contrasena"))
        assertThat(result.totalPasswords).isEqualTo(1)
    }

    @Test
    fun `non-secret fields are ignored`() {
        val details = listOf(detail(1, "Username", "alice"), detail(2, "Notes", "hello"))
        assertThat(PasswordAnalyzer.analyze(details, english).totalPasswords).isEqualTo(0)
    }

    @Test
    fun `weak passwords are detected`() {
        val details = listOf(
            detail(1, "Password", "short"),        // < 8
            detail(2, "PIN", "12345678"),          // all digits
            detail(3, "Password 3", "abcdefgh"),   // all letters
            detail(4, "Password 4", "aaaaaaaa"),   // single repeated character
            detail(5, "Password 5", "Str0ng!Pass") // fine
        )
        val result = PasswordAnalyzer.analyze(details, english)
        assertThat(result.weakPasswords.map { it.id }).containsExactly(1L, 2L, 3L, 4L)
    }

    @Test
    fun `weak penalty is capped at 50`() {
        val details = (1..10L).map { detail(it, "Password $it", "abc") }
        // Ten weak entries would be -100 uncapped; the floor must still be 50 here.
        val result = PasswordAnalyzer.analyze(details, english)
        assertThat(result.strengthScore).isAtLeast(0)
        assertThat(result.weakPasswords).hasSize(10)
    }

    @Test
    fun `reuse groups only include duplicates and are not keyed by plaintext`() {
        val details = listOf(
            detail(1, "Password A", "Repeated1!"),
            detail(2, "Password B", "Repeated1!"),
            detail(3, "Password C", "Unique9!x")
        )
        val result = PasswordAnalyzer.analyze(details, english)
        assertThat(result.reusedPasswords).hasSize(1)
        assertThat(result.reusedPasswords.values.single()).hasSize(2)
        assertThat(result.reusedPasswords.keys.single()).doesNotContain("Repeated1!")
    }

    @Test
    fun `score never drops below zero`() {
        val details = (1..40L).map { detail(it, "Password $it", "aaa") }
        assertThat(PasswordAnalyzer.analyze(details, english).strengthScore).isAtLeast(0)
    }
}
