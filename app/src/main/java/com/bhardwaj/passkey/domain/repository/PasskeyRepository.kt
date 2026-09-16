package com.bhardwaj.passkey.domain.repository

import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview
import kotlinx.coroutines.flow.Flow

interface PasskeyRepository {
    // Details
    fun getDetails(): Flow<List<Details>>

    fun getDetailsByPreviewId(previewId: Long): Flow<List<Details>>

    suspend fun getDetailById(detailId: Long): Details?

    suspend fun getDetailByContent(previewId: Long, question: String, answer: String): Details?

    suspend fun upsertDetails(details: Details): Long

    suspend fun deleteDetail(details: Details)

    suspend fun deleteDetailByPreviewId(previewId: Long)

    suspend fun updateDetailSequence(detailId: Long, sequence: Long)

    // Preview
    fun getPreviews(): Flow<List<Preview>>

    suspend fun getPreviewById(previewId: Long): Preview?

    suspend fun getPreviewByHeading(previewHeading: String, categoryName: String): Preview?

    suspend fun upsertPreview(previews: Preview): Long

    suspend fun deletePreview(previews: Preview)

    suspend fun updatePreviewSequence(previewId: Long, sequence: Long)

    // Cross-cutting

    /**
     * Runs [block] inside a single database transaction.
     *
     * Import previously wrote row by row with no transaction, so a malformed row part-way
     * through a file left the vault half-populated with no way back. Reordering had the same
     * problem: N separate sequence writes that could be interrupted between any two.
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R

    /** Removes every entry. Only meaningful inside [runInTransaction]. */
    suspend fun deleteAll()
}