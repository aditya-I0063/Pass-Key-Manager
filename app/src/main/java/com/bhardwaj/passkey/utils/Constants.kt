package com.bhardwaj.passkey.utils

class Constants {
    companion object {
        const val DETAILS_TABLE = "details_table"
        const val PREVIEW_TABLE = "preview_table"
        const val TEMP_DETAILS_TABLE = "temp_details_table"
        const val TEMP_PREVIEW_TABLE = "temp_preview_table"
        const val PASS_KEY_DATABASE = "passkey_database"
        const val FILE_NAME = "passkey_backup"
        const val FILE_TYPE = "passkey"
        const val FILE_PICKER_TYPE = "application/passkey"
        const val FILE_HEADER = "category,heading,question,answer\n"

        // SaveState Constants.
        const val PREVIEW_HEADING = "preview_heading"
        const val PREVIEW_CATEGORY_NAME = "preview_category_name"
        const val BOTTOM_SHEET_HEADING = "bottom_sheet_heading"

        // DataStore Prefs Constants.
        const val PASSKEY_PREFS = "passkey_pref"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val CURRENT_LANGUAGE = "current_language"
    }
}