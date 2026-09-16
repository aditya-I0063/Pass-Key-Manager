package com.bhardwaj.passkey.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bhardwaj.passkey.utils.Constants.Companion.DETAILS_TABLE

@Keep
@Entity(tableName = DETAILS_TABLE)
data class DetailsEntity(
    @PrimaryKey(autoGenerate = true)
    val detailsId: Long? = null,
    val previewId: Long,
    val question: String,
    val answer: String,
    val sequence: Long = 0,
    /**
     * Whether this row holds a secret (password, PIN, recovery code) rather than a username or
     * note. Set explicitly when the value comes from the generator; older rows default to false
     * and fall back to keyword matching in PasswordAnalyzer.
     */
    val isSecret: Boolean = false
)