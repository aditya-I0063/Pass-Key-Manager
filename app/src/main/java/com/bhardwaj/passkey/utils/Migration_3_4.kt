package com.bhardwaj.passkey.utils

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bhardwaj.passkey.utils.Constants.Companion.DETAILS_TABLE
import com.bhardwaj.passkey.utils.Constants.Companion.DETAIL_HISTORY_TABLE
import com.bhardwaj.passkey.utils.Constants.Companion.PREVIEW_TABLE
import com.bhardwaj.passkey.utils.Constants.Companion.TOTP_TABLE

/**
 * Adds authenticator codes, password history, and the index details_table never had.
 *
 * Every statement here is additive: one index and two new tables. No existing row is read,
 * rewritten or moved, so there is nothing for an interrupted run to leave half-done - the tables
 * either exist or they do not, and IF NOT EXISTS makes a retry harmless.
 *
 * The statements are copied verbatim from Room's own exported schema (schemas/4.json). They have
 * to match it exactly, down to the foreign-key clause and the index names, or Room's identity
 * check fails at the first open after upgrading and every install refuses to start.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Every lookup of an entry's details filters on previewId and was a full table scan.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${DETAILS_TABLE}_previewId` " +
                "ON `$DETAILS_TABLE` (`previewId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TOTP_TABLE` (" +
                "`totpId` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`previewId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`secret` TEXT NOT NULL, " +
                "`issuer` TEXT, " +
                "`algorithm` TEXT NOT NULL, " +
                "`digits` INTEGER NOT NULL, " +
                "`periodSeconds` INTEGER NOT NULL, " +
                "FOREIGN KEY(`previewId`) REFERENCES `$PREVIEW_TABLE`(`previewId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${TOTP_TABLE}_previewId` " +
                "ON `$TOTP_TABLE` (`previewId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$DETAIL_HISTORY_TABLE` (" +
                "`historyId` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`detailsId` INTEGER NOT NULL, " +
                "`answer` TEXT NOT NULL, " +
                "`changedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`detailsId`) REFERENCES `$DETAILS_TABLE`(`detailsId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${DETAIL_HISTORY_TABLE}_detailsId` " +
                "ON `$DETAIL_HISTORY_TABLE` (`detailsId`)"
        )
    }
}
