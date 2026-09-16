package com.bhardwaj.passkey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bhardwaj.passkey.data.local.dao.DetailsDao
import com.bhardwaj.passkey.data.local.dao.PreviewDao
import com.bhardwaj.passkey.data.local.dao.DetailHistoryDao
import com.bhardwaj.passkey.data.local.dao.TotpDao
import com.bhardwaj.passkey.data.local.entity.DetailHistoryEntity
import com.bhardwaj.passkey.data.local.entity.DetailsEntity
import com.bhardwaj.passkey.data.local.entity.TotpEntity
import com.bhardwaj.passkey.data.local.entity.PreviewEntity

@Database(
    entities = [
        PreviewEntity::class,
        DetailsEntity::class,
        TotpEntity::class,
        DetailHistoryEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class PasskeyDatabase : RoomDatabase() {
    abstract val previewDao: PreviewDao
    abstract val detailsDao: DetailsDao
    abstract val totpDao: TotpDao
    abstract val historyDao: DetailHistoryDao
}