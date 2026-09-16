package com.bhardwaj.passkey.domain.repository

import androidx.room.withTransaction
import com.bhardwaj.passkey.data.local.PassKeyDatabase
import com.bhardwaj.passkey.data.local.dao.DetailsDao
import com.bhardwaj.passkey.data.local.dao.PreviewDao
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import kotlinx.coroutines.flow.Flow

class PasskeyRepositoryImpl(
    private val database: PassKeyDatabase,
    private val previewDao: PreviewDao,
    private val detailsDao: DetailsDao
) : PasskeyRepository {

    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }

    override suspend fun deleteAll() {
        detailsDao.deleteAllDetails()
        previewDao.deleteAllPreviews()
    }
    override fun getDetails(): Flow<List<Details>> {
        return detailsDao.getDetails()
    }

    override fun getDetailsByPreviewId(previewId: Long): Flow<List<Details>> {
        return detailsDao.getDetailsByPreviewId(previewId)
    }

    override suspend fun getDetailById(detailId: Long): Details? {
        return detailsDao.getDetailById(detailId)
    }

    override suspend fun getDetailByContent(
        previewId: Long,
        question: String,
        answer: String
    ): Details? {
        return detailsDao.getDetailByContent(previewId, question, answer)
    }

    override suspend fun upsertDetails(details: Details): Long {
        return detailsDao.upsertDetails(details)
    }

    override suspend fun deleteDetail(details: Details) {
        return detailsDao.deleteDetail(details)
    }

    override suspend fun deleteDetailByPreviewId(previewId: Long) {
        return detailsDao.deleteDetailByPreviewId(previewId)
    }

    override suspend fun updateDetailSequence(detailId: Long, sequence: Long) {
        return detailsDao.updateDetailSequence(detailId, sequence)
    }

    override fun getPreviews(): Flow<List<Preview>> {
        return previewDao.getPreviews()
    }

    override suspend fun getPreviewById(previewId: Long): Preview? {
        return previewDao.getPreviewById(previewId)
    }

    override suspend fun getPreviewByHeading(
        previewHeading: String,
        categoryName: String
    ): Preview? {
        return previewDao.getPreviewByHeading(previewHeading, categoryName)
    }

    override suspend fun upsertPreview(previews: Preview): Long {
        return previewDao.upsertPreview(previews)
    }

    override suspend fun deletePreview(previews: Preview) {
        return previewDao.deletePreview(previews)
    }

    override suspend fun updatePreviewSequence(previewId: Long, sequence: Long) {
        return previewDao.updatePreviewSequence(previewId, sequence)
    }
}