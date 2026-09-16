package com.bhardwaj.passkey

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bhardwaj.passkey.domain.model.AppLanguage
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * The regression guard for the bug this whole release started from.
 *
 * Lint's MissingTranslation catches a key absent from a `values-*` folder. It cannot catch a
 * language the picker offers that has no folder at all, a folder the picker never offers, or a
 * locale missing from locales_config - which is exactly what happened to Arabic: it was in the
 * picker and in `values-ar/`, absent from locales_config, and choosing it silently did nothing.
 *
 * So these assertions are about the three lists agreeing with [AppLanguage], and about each
 * language actually resolving to its own strings at runtime.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationCoverageTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Keys chosen because they are short, present since before this work, and unmistakably
     * different once translated - not because they are the only ones that matter.
     */
    private val representativeKeys = listOf(
        R.string.skip,
        R.string.next,
        R.string.change_language,
        R.string.privacy,
        R.string.delete
    )

    /**
     * Translations that legitimately match the English word.
     *
     * Kept as an explicit list rather than by weakening the assertion: a new coincidence should
     * make someone look at it and decide, not pass silently.
     */
    private val sameAsEnglish = setOf(AppLanguage.ITALIAN to R.string.privacy)

    private fun resourcesFor(language: AppLanguage) = context
        .createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.tag))
            }
        )
        .resources

    @Test
    fun every_offered_language_has_its_own_translations() {
        val english = resourcesFor(AppLanguage.ENGLISH)

        AppLanguage.entries
            .filter { it != AppLanguage.ENGLISH }
            .forEach { language ->
                val translated = resourcesFor(language)
                val untranslated = representativeKeys.filter { key ->
                    translated.getString(key) == english.getString(key) &&
                        (language to key) !in sameAsEnglish
                }
                // A language whose strings all fall back to English has no values- folder, or
                // one that was never filled in. Either way the picker is lying about it.
                assertWithMessage(
                    "keys still English in ${language.englishName} (${language.tag})"
                ).that(untranslated.map { english.getString(it) }).isEmpty()
            }
    }

    @Test
    fun locales_config_lists_exactly_the_languages_the_picker_offers() {
        val declared = mutableSetOf<String>()
        context.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val name = parser.getAttributeValue(ANDROID_NS, "name")
                    if (name != null) declared += name
                }
            }
        }

        // Both directions: a missing entry breaks per-app language selection silently, and a
        // stray one offers a language that has nothing behind it.
        assertThat(declared).containsExactlyElementsIn(AppLanguage.entries.map { it.tag })
    }

    @Test
    fun the_app_name_is_deliberately_not_translated() {
        // It is a brand, and the only string left marked translatable="false". If it ever starts
        // differing per locale, that attribute was removed by accident.
        AppLanguage.entries.forEach { language ->
            assertThat(resourcesFor(language).getString(R.string.app_name))
                .isEqualTo(resourcesFor(AppLanguage.ENGLISH).getString(R.string.app_name))
        }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
