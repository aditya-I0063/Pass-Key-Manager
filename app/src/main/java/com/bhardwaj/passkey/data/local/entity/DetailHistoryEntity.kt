package com.bhardwaj.passkey.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bhardwaj.passkey.utils.Constants.Companion.DETAILS_TABLE
import com.bhardwaj.passkey.utils.Constants.Companion.DETAIL_HISTORY_TABLE

/**
 * A value a detail used to hold, kept so a password change can be undone and so the app can say
 * how long the current one has been in use.
 *
 * Written by the repository whenever a secret's value changes, which is what stops it from being
 * something a call site has to remember.
 */
@Keep
@Entity(
    tableName = DETAIL_HISTORY_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = DetailsEntity::class,
            parentColumns = ["detailsId"],
            childColumns = ["detailsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("detailsId")]
)
data class DetailHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long? = null,
    val detailsId: Long,
    val answer: String,
    /** Wall-clock: this is shown to the user as a date, not used to measure an interval. */
    val changedAt: Long
)
