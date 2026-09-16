package com.bhardwaj.passkey.domain.repository

import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.PasswordHistoryEntry
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.model.TotpEntry
import com.bhardwaj.passkey.domain.totp.TotpConfig
import kotlinx.coroutines.flow.Flow

/**
 * The vault, in domain terms. Room entities no longer cross this boundary.
 */
interface PasskeyRepository {

    // Previews

    fun getPreviews(): Flow<List<Preview>>

    /**
     * Filtering in SQL rather than in the ViewModel. Reordering used to renumber a list that had
     * already been filtered by category *and* search text, while the query ordered globally, so
     * sequences collided across categories and the order was quietly unstable.
     */
    fun getPreviewsByCategory(category: Category): Flow<List<Preview>>

    suspend fun getPreviewById(previewId: Long): Preview?

    suspend fun getPreviewByHeading(heading: String, category: Category): Preview?

    /** Returns the new row id. */
    suspend fun createPreview(heading: String, category: Category, sequence: Long = 0): Long

    suspend fun updatePreview(preview: Preview)

    suspend fun deletePreview(preview: Preview)

    suspend fun updatePreviewSequence(previewId: Long, sequence: Long)

    // Details

    fun getDetails(): Flow<List<Detail>>

    fun getDetailsByPreviewId(previewId: Long): Flow<List<Detail>>

    suspend fun getDetailByContent(previewId: Long, question: String, answer: String): Detail?

    suspend fun createDetail(
        previewId: Long,
        question: String,
        answer: String,
        sequence: Long = 0,
        isSecret: Boolean = false
    ): Long

    suspend fun updateDetail(detail: Detail)

    suspend fun deleteDetail(detail: Detail)

    suspend fun deleteDetailsByPreviewId(previewId: Long)

    suspend fun updateDetailSequence(detailId: Long, sequence: Long)

    // Authenticator codes

    fun getTotpByPreviewId(previewId: Long): Flow<List<TotpEntry>>

    suspend fun createTotp(previewId: Long, label: String, config: TotpConfig): Long

    suspend fun deleteTotp(entry: TotpEntry)

    // Password history

    fun getHistoryForDetail(detailId: Long): Flow<List<PasswordHistoryEntry>>

    /** When this secret last changed, or null if it has never been edited. */
    suspend fun lastChangedAt(detailId: Long): Long?

    // Cross-cutting

    /**
     * Runs [block] inside a single database transaction.
     *
     * Import previously wrote row by row with no transaction, so a malformed row part-way
     * through a file left the vault half-populated with no way back.
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R

    /** Removes every entry. Only meaningful inside [runInTransaction]. */
    suspend fun deleteAll()
}
