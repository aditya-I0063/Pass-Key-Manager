package com.bhardwaj.passkey.data.local.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_TABLE
import com.bhardwaj.passkey.utils.Constants.Companion.TOTP_TABLE

/**
 * An authenticator secret belonging to an entry.
 *
 * Unlike details, this table is new, so it can carry the foreign key the older tables cannot
 * gain without a full rebuild: deleting an entry takes its authenticator with it, enforced by
 * SQLite rather than by remembering to.
 *
 * The secret is stored Base32-encoded - the form every authenticator prints - inside a database
 * that is already encrypted as a whole.
 */
@Keep
@Entity(
    tableName = TOTP_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = PreviewEntity::class,
            parentColumns = ["previewId"],
            childColumns = ["previewId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("previewId")]
)
data class TotpEntity(
    @PrimaryKey(autoGenerate = true)
    val totpId: Long? = null,
    val previewId: Long,
    val label: String,
    val secret: String,
    val issuer: String? = null,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val periodSeconds: Int = 30
)
