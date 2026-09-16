package com.bhardwaj.passkey.data.repository

import androidx.room.withTransaction
import com.bhardwaj.passkey.data.local.PasskeyDatabase
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import com.bhardwaj.passkey.data.mapper.toDomain
import com.bhardwaj.passkey.data.mapper.toEntity
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyRepositoryImpl(
    private val vault: VaultDatabaseProvider
) : PasskeyRepository {

    /**
     * Room Flows are resolved through the vault's *current* database rather than a DAO captured
     * at construction.
     *
     * This is load-bearing for locking: flatMapLatest detaches every collector when the database
     * closes and re-subscribes when it reopens. A captured DAO would leave collectors on a closed
     * database and throw "attempt to re-open an already-closed object" on the next emission.
     */
    private fun <E, T> vaultFlow(
        query: (PasskeyDatabase) -> Flow<List<E>>,
        mapper: (E) -> T
    ): Flow<List<T>> = vault.database.flatMapLatest { db ->
        db?.let { database -> query(database).map { rows -> rows.map(mapper) } } ?: flowOf(emptyList())
    }

    override fun getPreviews(): Flow<List<Preview>> =
        vaultFlow({ it.previewDao.getPreviews() }) { it.toDomain() }

    override fun getPreviewsByCategory(category: Category): Flow<List<Preview>> =
        vaultFlow({ it.previewDao.getPreviewsByCategory(category.name) }) { it.toDomain() }

    override suspend fun getPreviewById(previewId: Long): Preview? =
        vault.requireDb().previewDao.getPreviewById(previewId)?.toDomain()

    override suspend fun getPreviewByHeading(heading: String, category: Category): Preview? =
        vault.requireDb().previewDao.getPreviewByHeading(heading, category.name)?.toDomain()

    override suspend fun createPreview(heading: String, category: Category, sequence: Long): Long =
        vault.requireDb().previewDao.upsertPreview(
            Preview(id = 0, heading = heading, category = category, sequence = sequence).toEntity()
        )

    override suspend fun updatePreview(preview: Preview) {
        vault.requireDb().previewDao.upsertPreview(preview.toEntity())
    }

    override suspend fun deletePreview(preview: Preview) {
        vault.requireDb().previewDao.deletePreview(preview.toEntity())
    }

    override suspend fun updatePreviewSequence(previewId: Long, sequence: Long) {
        vault.requireDb().previewDao.updatePreviewSequence(previewId, sequence)
    }

    override fun getDetails(): Flow<List<Detail>> =
        vaultFlow({ it.detailsDao.getDetails() }) { it.toDomain() }

    override fun getDetailsByPreviewId(previewId: Long): Flow<List<Detail>> =
        vaultFlow({ it.detailsDao.getDetailsByPreviewId(previewId) }) { it.toDomain() }

    override suspend fun getDetailByContent(
        previewId: Long,
        question: String,
        answer: String
    ): Detail? = vault.requireDb().detailsDao
        .getDetailByContent(previewId, question, answer)?.toDomain()

    override suspend fun createDetail(
        previewId: Long,
        question: String,
        answer: String,
        sequence: Long,
        isSecret: Boolean
    ): Long = vault.requireDb().detailsDao.upsertDetails(
        Detail(
            id = 0,
            previewId = previewId,
            question = question,
            answer = answer,
            sequence = sequence,
            isSecret = isSecret
        ).toEntity()
    )

    override suspend fun updateDetail(detail: Detail) {
        vault.requireDb().detailsDao.upsertDetails(detail.toEntity())
    }

    override suspend fun deleteDetail(detail: Detail) {
        vault.requireDb().detailsDao.deleteDetail(detail.toEntity())
    }

    override suspend fun deleteDetailsByPreviewId(previewId: Long) {
        vault.requireDb().detailsDao.deleteDetailByPreviewId(previewId)
    }

    override suspend fun updateDetailSequence(detailId: Long, sequence: Long) {
        vault.requireDb().detailsDao.updateDetailSequence(detailId, sequence)
    }

    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        vault.requireDb().withTransaction { block() }

    override suspend fun deleteAll() {
        val db = vault.requireDb()
        db.detailsDao.deleteAllDetails()
        db.previewDao.deleteAllPreviews()
    }
}
