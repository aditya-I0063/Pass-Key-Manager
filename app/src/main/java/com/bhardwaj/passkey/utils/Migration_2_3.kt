package com.bhardwaj.passkey.utils

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bhardwaj.passkey.utils.Constants.Companion.DETAILS_TABLE

/**
 * Adds Details.isSecret.
 *
 * Purely additive with a default, so no table rebuild and nothing to roll back. Existing rows
 * stay 0 and keep being classified by keyword matching; only newly created or edited rows carry
 * an explicit flag. A SQL backfill was considered and rejected: the only heuristic expressible
 * here is English keyword matching, which is exactly the bug being fixed.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $DETAILS_TABLE ADD COLUMN isSecret INTEGER NOT NULL DEFAULT 0")
    }
}
