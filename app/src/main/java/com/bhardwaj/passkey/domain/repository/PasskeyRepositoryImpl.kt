package com.bhardwaj.passkey.domain.repository

import androidx.room.withTransaction
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.bhardwaj.passkey.data.local.PassKeyDatabase
import com.bhardwaj.passkey.data.local.dao.DetailsDao
import com.bhardwaj.passkey.data.local.dao.PreviewDao
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyRepositoryImpl(
    private val vault: VaultDatabaseProvider
) : PasskeyRepository {

    private suspend fun previewDao(): PreviewDao = vault.requireDb().previewDao
    private suspend fun detailsDao(): DetailsDao = vault.requireDb().detailsDao

    /**
     * Room Flows are resolved through the vault's current database rather than captured once.
     *
     * This is load-bearing for locking: flatMapLatest detaches every collector when the database
     * closes and re-subscribes when it reopens. Holding a DAO captured at construction would
     * leave collectors on a closed database and throw
     * "attempt to re-open an already-closed object" on the next emission.
     */
    private fun <T> vaultFlow(block: (PassKeyDatabase) -> Flow<List<T>>): Flow<List<T>> =
        vault.database.flatMapLatest { db -> db?.let(block) ?: flowOf(emptyList()) }

    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        vault.requireDb().withTransaction { block() }

    override suspend fun deleteAll() {
        detailsDao().deleteAllDetails()
        previewDao().deleteAllPreviews()
    }
    override fun getDetails(): Flow<List<Details>> = vaultFlow { it.detailsDao.getDetails() }

    override fun getDetailsByPreviewId(previewId: Long): Flow<List<Details>> =
        vaultFlow { it.detailsDao.getDetailsByPreviewId(previewId) }

    override suspend fun getDetailById(detailId: Long): Details? {
        return detailsDao().getDetailById(detailId)
    }

    override suspend fun getDetailByContent(
        previewId: Long,
        question: String,
        answer: String
    ): Details? {
        return detailsDao().getDetailByContent(previewId, question, answer)
    }

    override suspend fun upsertDetails(details: Details): Long {
        return detailsDao().upsertDetails(details)
    }

    override suspend fun deleteDetail(details: Details) {
        return detailsDao().deleteDetail(details)
    }

    override suspend fun deleteDetailByPreviewId(previewId: Long) {
        return detailsDao().deleteDetailByPreviewId(previewId)
    }

    override suspend fun updateDetailSequence(detailId: Long, sequence: Long) {
        return detailsDao().updateDetailSequence(detailId, sequence)
    }

    override fun getPreviews(): Flow<List<Preview>> = vaultFlow { it.previewDao.getPreviews() }

    override suspend fun getPreviewById(previewId: Long): Preview? {
        return previewDao().getPreviewById(previewId)
    }

    override suspend fun getPreviewByHeading(
        previewHeading: String,
        categoryName: String
    ): Preview? {
        return previewDao().getPreviewByHeading(previewHeading, categoryName)
    }

    override suspend fun upsertPreview(previews: Preview): Long {
        return previewDao().upsertPreview(previews)
    }

    override suspend fun deletePreview(previews: Preview) {
        return previewDao().deletePreview(previews)
    }

    override suspend fun updatePreviewSequence(previewId: Long, sequence: Long) {
        return previewDao().updatePreviewSequence(previewId, sequence)
    }
}