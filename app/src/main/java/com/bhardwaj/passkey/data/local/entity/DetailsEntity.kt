package com.bhardwaj.passkey.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bhardwaj.passkey.utils.Constants.Companion.DETAILS_TABLE

@Keep
/**
 * [previewId] is indexed: every lookup of an entry's details filters on it, and without an index
 * each one was a full table scan.
 *
 * No foreign key, unlike the two tables added in v4. Adding one to an existing table means
 * rebuilding it, and this is the table holding every password the user owns; the cascade is
 * enforced in a single transaction by the repository instead.
 */
@Entity(tableName = DETAILS_TABLE, indices = [Index("previewId")])
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