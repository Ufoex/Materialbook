package com.eepiemi.materialbook

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * app_name is hand-edited across 10 locale strings.xml files on every rebrand.
 * Sweeps all of them so a missed locale fails loudly here instead of shipping
 * half-branded ("Materialbook") in one language.
 */
@RunWith(AndroidJUnit4::class)
class LocaleAppNameTest {

    private val translatedLocales = listOf(
        Locale.forLanguageTag("ar"),
        Locale.forLanguageTag("bn"),
        Locale.forLanguageTag("de"),
        Locale.forLanguageTag("es"),
        Locale.forLanguageTag("fr"),
        Locale.forLanguageTag("it"),
        Locale.forLanguageTag("iw"),
        Locale.forLanguageTag("pt"),
        Locale.Builder().setLanguage("zh").setRegion("TW").build(),
    )

    private fun contextFor(locale: Locale): Context {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    @Test
    fun appNameIsAstryxbookInEveryTranslatedLocale() {
        translatedLocales.forEach { locale ->
            val name = contextFor(locale).getString(R.string.app_name)
            assertTrue(
                "app_name wrong for locale '$locale': expected to start with \"Astryxbook\" but was \"$name\"",
                name.startsWith("Astryxbook")
            )
        }
    }

    @Test
    fun appNameIsAstryxbookInDefaultFallback() {
        // A locale we have no translation for falls back to the default values/strings.xml.
        val name = contextFor(Locale.forLanguageTag("ja")).getString(R.string.app_name)
        assertTrue(
            "app_name should start with \"Astryxbook\" but was \"$name\"",
            name.startsWith("Astryxbook")
        )
    }
}
