package com.bhardwaj.passkey.utils

import java.security.SecureRandom
import java.util.Collections
import java.util.Random

object PasswordGenerator {

    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    /**
     * Deliberately [SecureRandom] and not `SecureRandom.getInstanceStrong()`, which can block on
     * `/dev/random`. `setSeed` is never called: on API 26+ the platform provider is already seeded
     * from the kernel, and seeding it ourselves would only reduce entropy.
     */
    private val secureRandom: Random = SecureRandom()

    /**
     * Generates a password of [length] characters.
     *
     * Guarantees at least one character from every selected class, which the previous
     * implementation did not — a 16-character "password with symbols" could legitimately contain
     * no symbol at all. If no class is selected it falls back to lowercase.
     *
     * @param random injectable purely so tests can seed it deterministically; production always
     *   uses the [SecureRandom] default.
     */
    fun generate(
        length: Int,
        includeUpper: Boolean,
        includeLower: Boolean,
        includeNumbers: Boolean,
        includeSpecial: Boolean,
        random: Random = secureRandom
    ): String {
        val pools = buildList {
            if (includeUpper) add(UPPER)
            if (includeLower) add(LOWER)
            if (includeNumbers) add(DIGITS)
            if (includeSpecial) add(SPECIAL)
        }.ifEmpty { listOf(LOWER) }

        // Cannot satisfy "one per class" in fewer characters than there are classes.
        val size = maxOf(length, pools.size)
        val union = pools.joinToString(separator = "")

        val characters = ArrayList<Char>(size)
        // nextInt(bound) performs rejection sampling internally, so it is unbiased; a manual
        // modulo would reintroduce bias.
        pools.forEach { pool -> characters.add(pool[random.nextInt(pool.length)]) }
        repeat(size - pools.size) { characters.add(union[random.nextInt(union.length)]) }

        // Collections.shuffle(list, random) — NOT Kotlin's shuffled(), which uses kotlin.random.
        Collections.shuffle(characters, random)
        return characters.joinToString(separator = "")
    }
}
