package com.bhardwaj.passkey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bhardwaj.passkey.data.local.dao.DetailsDao
import com.bhardwaj.passkey.data.local.dao.PreviewDao
import com.bhardwaj.passkey.data.local.entity.Details
import com.bhardwaj.passkey.data.local.entity.Preview

@Database(
    entities = [Preview::class, Details::class],
    version = 3,
    exportSchema = true
)
abstract class PasskeyDatabase : RoomDatabase() {
    abstract val previewDao: PreviewDao
    abstract val detailsDao: DetailsDao
}