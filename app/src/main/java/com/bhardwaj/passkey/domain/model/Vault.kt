package com.bhardwaj.passkey.domain.model

/**
 * The entry categories. The enum *name* is the persisted value (Room column and backup files),
 * so it must not change. A display label is a presentation concern and lives in the UI.
 */
enum class Category {
    BANKS,
    MAILS,
    APPS,
    OTHERS;

    companion object {
        /**
         * Never [valueOf]: it throws on unknown input, and an imported file is untrusted.
         */
        fun fromNameOrOther(name: String?): Category =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: OTHERS
    }
}

/**
 * A group of related entries.
 *
 * Deliberately not annotated @Immutable: Kotlin 2.x strong skipping already treats a data class
 * of primitives and enums as stable, so the annotation would only couple the domain layer to the
 * Compose runtime for nothing.
 *
 * [id] is non-null here even though the Room column is nullable. The entity is deliberately left
 * alone - changing its nullability would alter Room's identity hash and require a migration for
 * no functional gain - and the mapper asserts instead, which is always safe because Room
 * populates the primary key on every read. That is what removes `previewId!!` from every
 * call site above the data layer.
 */
data class Preview(
    val id: Long,
    val heading: String,
    val category: Category,
    val sequence: Long = 0
)

data class Detail(
    val id: Long,
    val previewId: Long,
    val question: String,
    val answer: String,
    val sequence: Long = 0,
    val isSecret: Boolean = false
)
