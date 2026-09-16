package com.bhardwaj.passkey

import com.bhardwaj.passkey.presentation.screens.onboarding_screens.components.HighlightSpan
import com.bhardwaj.passkey.presentation.screens.onboarding_screens.components.parseHighlightMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the onboarding crash.
 *
 * The previous implementation looked up a hardcoded English word with `indexOf` and passed the
 * result straight to `AnnotatedString.addStyle`. For any translated heading `indexOf` returned
 * -1 and `addStyle(start = -1, ...)` threw. Every case below must therefore produce in-bounds
 * spans and never throw.
 */
class HighlightMarkupTest {

    @Test
    fun `strips markers and reports the highlighted range`() {
        val result = parseHighlightMarkup("Generate\n[[Secure]]\nPasswords.")

        assertEquals("Generate\nSecure\nPasswords.", result.text)
        assertEquals(listOf(HighlightSpan(9, 15)), result.spans)
        assertEquals("Secure", result.text.substring(9, 15))
    }

    @Test
    fun `text with no markers yields no spans`() {
        val result = parseHighlightMarkup("All Your Passwords Are Here.")

        assertEquals("All Your Passwords Are Here.", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `unclosed marker is dropped rather than throwing`() {
        val result = parseHighlightMarkup("Generate [[Secure Passwords.")

        assertEquals("Generate Secure Passwords.", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `stray closing marker is dropped rather than throwing`() {
        val result = parseHighlightMarkup("Generate Secure]] Passwords.")

        assertEquals("Generate Secure]] Passwords.", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `empty marker pair produces no span`() {
        val result = parseHighlightMarkup("Generate [[]] Passwords.")

        assertEquals("Generate  Passwords.", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `supports multiple highlighted spans`() {
        val result = parseHighlightMarkup("[[All]] Your [[Passwords]]")

        assertEquals("All Your Passwords", result.text)
        assertEquals(listOf(HighlightSpan(0, 3), HighlightSpan(9, 18)), result.spans)
        assertEquals("All", result.text.substring(0, 3))
        assertEquals("Passwords", result.text.substring(9, 18))
    }

    @Test
    fun `handles markers at both string boundaries`() {
        val result = parseHighlightMarkup("[[Autofill]]")

        assertEquals("Autofill", result.text)
        assertEquals(listOf(HighlightSpan(0, 8)), result.spans)
    }

    @Test
    fun `empty input is handled`() {
        val result = parseHighlightMarkup("")

        assertEquals("", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `every shipped heading parses to in-bounds spans`() {
        // Representative of the real translated resources, including RTL and CJK.
        val shipped = listOf(
            "Generate\n[[Secure]]\nPasswords.",
            "बनाएँ\n[[सुरक्षित]]\nपासवर्ड।",
            "أنشئ\nكلمات مرور\n[[آمنة]].",
            "[[安全な]]\nパスワードを\n作成。",
            "Créez des\nmots de passe\n[[sécurisés]]."
        )
        shipped.forEach { raw ->
            val result = parseHighlightMarkup(raw)
            assertEquals("one highlight expected in: $raw", 1, result.spans.size)
            result.spans.forEach { span ->
                assertTrue("start in bounds: $raw", span.start in 0..result.text.length)
                assertTrue("end in bounds: $raw", span.endExclusive in 0..result.text.length)
                assertTrue("non-empty span: $raw", span.endExclusive > span.start)
            }
        }
    }
}
