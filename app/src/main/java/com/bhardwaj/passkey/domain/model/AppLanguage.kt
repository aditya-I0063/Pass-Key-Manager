package com.bhardwaj.passkey.domain.model

/**
 * Every language the app offers, and the single place that decides so.
 *
 * There used to be three hand-maintained lists: a 17-entry `arrayListOf(Language(...))` declared
 * inline inside the picker composable, `res/xml/locales_config.xml`, and the `values-*` folders.
 * They drifted - Arabic was in the picker and in `values-ar/` but missing from locales_config, so
 * choosing it silently did nothing. That bug was fixed by adding the missing line; this removes
 * the shape that allowed it, and LocalizationCoverageTest fails if the three disagree again.
 *
 * [endonym] is the language's own name for itself, which is what a language picker must show:
 * someone who cannot read the current language has to find their own.
 */
enum class AppLanguage(
    val tag: String,
    val endonym: String,
    val englishName: String
) {
    ARABIC("ar", "عربي", "Arabic"),
    BENGALI("bn", "বাংলা", "Bengali"),
    CHINESE("zh", "中文", "Chinese"),
    ENGLISH("en", "English", "English"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    HINDI("hi", "हिन्दी", "Hindi"),
    ITALIAN("it", "Italiano", "Italian"),
    JAPANESE("ja", "日本語", "Japanese"),
    KOREAN("ko", "한국어", "Korean"),
    MARATHI("mr", "मराठी", "Marathi"),
    PORTUGUESE("pt", "Português", "Portuguese"),
    RUSSIAN("ru", "Русский", "Russian"),
    SPANISH("es", "Español", "Spanish"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu");

    companion object {
        /** The base `values/` folder, and the fallback for a tag the app does not offer. */
        val DEFAULT = ENGLISH

        fun fromTagOrDefault(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
