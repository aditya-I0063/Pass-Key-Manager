package com.bhardwaj.passkey

import com.bhardwaj.passkey.utils.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PasswordGeneratorTest {

    private companion object {
        const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    }

    private fun generate(
        length: Int = 16,
        upper: Boolean = true,
        lower: Boolean = true,
        numbers: Boolean = true,
        special: Boolean = true,
        random: Random = Random(20260916)
    ) = PasswordGenerator.generate(length, upper, lower, numbers, special, random)

    @Test
    fun `produces a password of the requested length`() {
        for (length in 4..32) {
            assertEquals(length, generate(length = length).length)
        }
    }

    @Test
    fun `includes at least one character from every selected class`() {
        // The old implementation sampled uniformly from the union, so a password could contain
        // no symbol despite symbols being requested. Run many trials to catch a regression.
        repeat(500) { seed ->
            val password = generate(length = 8, random = Random(seed.toLong()))
            assertTrue("no uppercase in $password", password.any { it in UPPER })
            assertTrue("no lowercase in $password", password.any { it in LOWER })
            assertTrue("no digit in $password", password.any { it in DIGITS })
            assertTrue("no symbol in $password", password.any { it in SPECIAL })
        }
    }

    @Test
    fun `never emits characters from a deselected class`() {
        repeat(200) { seed ->
            val password = generate(
                length = 12, upper = true, lower = true,
                numbers = false, special = false, random = Random(seed.toLong())
            )
            assertTrue("digit leaked into $password", password.none { it in DIGITS })
            assertTrue("symbol leaked into $password", password.none { it in SPECIAL })
        }
    }

    @Test
    fun `falls back to lowercase when no class is selected`() {
        val password = generate(
            length = 10, upper = false, lower = false, numbers = false, special = false
        )
        assertEquals(10, password.length)
        assertTrue("expected lowercase only, got $password", password.all { it in LOWER })
    }

    @Test
    fun `length shorter than the number of selected classes is widened to fit them`() {
        // Four classes requested but only two characters asked for: honouring "one per class"
        // has to win, otherwise the guarantee above is a lie.
        val password = generate(length = 2)
        assertEquals(4, password.length)
    }

    @Test
    fun `consecutive calls with the real generator differ`() {
        val results = List(1_000) {
            PasswordGenerator.generate(
                length = 16,
                includeUpper = true, includeLower = true,
                includeNumbers = true, includeSpecial = true
            )
        }
        assertEquals("generated passwords repeated", results.size, results.toSet().size)
    }

    @Test
    fun `first character is not always from the same class`() {
        // Regression guard for emitting the per-class characters in a fixed order without
        // shuffling, which would make the first character's class fully predictable.
        val firstClasses = (0 until 200)
            .map { generate(length = 12, random = Random(it.toLong())).first() }
            .map { c ->
                when (c) {
                    in UPPER -> "upper"; in LOWER -> "lower"; in DIGITS -> "digit"; else -> "special"
                }
            }
            .toSet()
        assertTrue("first character class never varied: $firstClasses", firstClasses.size > 1)
    }
}
