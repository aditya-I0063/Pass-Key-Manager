package com.bhardwaj.passkey.domain.model

/** Which character classes a generated password may draw from. */
enum class PasswordCharacterClass { UPPERCASE, LOWERCASE, DIGITS, SYMBOLS }

/**
 * Settings for the password generator.
 *
 * Replaces five loose `mutableStateOf` fields on the ViewModel plus a toggle event that
 * dispatched on the magic strings "Upper", "Lower", "Number" and "Special".
 */
data class PasswordPolicy(
    val length: Int = DEFAULT_LENGTH,
    val classes: Set<PasswordCharacterClass> = DEFAULT_CLASSES
) {
    val includeUppercase: Boolean get() = PasswordCharacterClass.UPPERCASE in classes
    val includeLowercase: Boolean get() = PasswordCharacterClass.LOWERCASE in classes
    val includeDigits: Boolean get() = PasswordCharacterClass.DIGITS in classes
    val includeSymbols: Boolean get() = PasswordCharacterClass.SYMBOLS in classes

    /**
     * Toggling the last remaining class falls back to lowercase rather than leaving an empty
     * pool. The generator has the same guard, but keeping the state itself always valid means
     * the UI can never show every switch off.
     */
    fun toggle(characterClass: PasswordCharacterClass, enabled: Boolean): PasswordPolicy {
        val updated = if (enabled) classes + characterClass else classes - characterClass
        return copy(
            classes = updated.ifEmpty { setOf(PasswordCharacterClass.LOWERCASE) }
        )
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 32
        const val DEFAULT_LENGTH = 12

        val DEFAULT_CLASSES = setOf(
            PasswordCharacterClass.UPPERCASE,
            PasswordCharacterClass.LOWERCASE,
            PasswordCharacterClass.DIGITS
        )
    }
}
