package com.bhardwaj.passkey.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.bhardwaj.passkey.data.local.entity.PreviewEntity
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_TABLE
import kotlinx.coroutines.flow.Flow

@Dao
interface PreviewDao {
    @Query("SELECT * FROM $PREVIEW_TABLE ORDER BY sequence ASC")
    fun getPreviews(): Flow<List<PreviewEntity>>

    @Query("SELECT * FROM $PREVIEW_TABLE WHERE previewId=:previewId")
    suspend fun getPreviewById(previewId: Long): PreviewEntity?

    /**
     * COLLATE NOCASE: duplicate detection was case-sensitive, so "Gmail" and "gmail" coexisted
     * as separate entries and re-importing a differently-cased backup created duplicates.
     */
    @Query(
        "SELECT * FROM $PREVIEW_TABLE " +
            "WHERE heading=:previewHeading COLLATE NOCASE AND categoryName=:categoryName"
    )
    suspend fun getPreviewByHeading(previewHeading: String, categoryName: String): PreviewEntity?

    @Query("SELECT * FROM $PREVIEW_TABLE WHERE categoryName=:categoryName ORDER BY sequence ASC")
    fun getPreviewsByCategory(categoryName: String): Flow<List<PreviewEntity>>

    @Upsert
    suspend fun upsertPreview(previews: PreviewEntity): Long

    @Delete
    suspend fun deletePreview(previews: PreviewEntity)

    @Query("UPDATE $PREVIEW_TABLE SET sequence=:sequence WHERE previewId=:previewId")
    suspend fun updatePreviewSequence(previewId: Long, sequence: Long)

    @Query("DELETE FROM $PREVIEW_TABLE")
    suspend fun deleteAllPreviews()
}