package com.bhardwaj.passkey.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bhardwaj.passkey.data.local.entity.DetailHistoryEntity
import com.bhardwaj.passkey.utils.Constants.Companion.DETAIL_HISTORY_TABLE
import kotlinx.coroutines.flow.Flow

@Dao
interface DetailHistoryDao {
    @Query("SELECT * FROM $DETAIL_HISTORY_TABLE WHERE detailsId=:detailId ORDER BY changedAt DESC")
    fun getForDetail(detailId: Long): Flow<List<DetailHistoryEntity>>

    @Query("SELECT changedAt FROM $DETAIL_HISTORY_TABLE WHERE detailsId=:detailId ORDER BY changedAt DESC LIMIT 1")
    suspend fun lastChangedAt(detailId: Long): Long?

    @Insert
    suspend fun insert(entry: DetailHistoryEntity): Long

    /**
     * Keeps the newest [keep] versions of one detail. History is a convenience, not an archive:
     * an unbounded one would grow forever inside a vault the user never prunes.
     */
    @Query(
        "DELETE FROM $DETAIL_HISTORY_TABLE WHERE detailsId=:detailId AND historyId NOT IN " +
            "(SELECT historyId FROM $DETAIL_HISTORY_TABLE WHERE detailsId=:detailId " +
            "ORDER BY changedAt DESC LIMIT :keep)"
    )
    suspend fun trim(detailId: Long, keep: Int)

    @Query("DELETE FROM $DETAIL_HISTORY_TABLE")
    suspend fun deleteAll()
}
