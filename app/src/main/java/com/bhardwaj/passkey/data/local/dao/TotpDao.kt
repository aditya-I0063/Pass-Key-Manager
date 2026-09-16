package com.bhardwaj.passkey.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.bhardwaj.passkey.data.local.entity.TotpEntity
import com.bhardwaj.passkey.utils.Constants.Companion.TOTP_TABLE
import kotlinx.coroutines.flow.Flow

@Dao
interface TotpDao {
    @Query("SELECT * FROM $TOTP_TABLE WHERE previewId=:previewId ORDER BY totpId ASC")
    fun getByPreviewId(previewId: Long): Flow<List<TotpEntity>>

    @Upsert
    suspend fun upsert(totp: TotpEntity): Long

    @Delete
    suspend fun delete(totp: TotpEntity)

    @Query("DELETE FROM $TOTP_TABLE")
    suspend fun deleteAll()
}
