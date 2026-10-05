package com.eepiemi.materialbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM unit test validating brand strings across all translated locales.
 *
 * Runs on every `./gradlew test` in milliseconds without requiring an Android
 * emulator or connected device.
 *
 * Guards two critical architectural invariants:
 * 1. Every localized `brand_strings.xml` defines Astryx branding (`app_name` is
 *    "Astryxbook", settings and error strings mention "Astryxbook" and never
 *    "Materialbook").
 * 2. Upstream `strings.xml` files never redefine these 3 branded keys — preventing
 *    silent merge conflicts or upstream strings overriding the fork branding.
 */
class BrandStringsTest {

    private val resDir: File by lazy {
        listOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).firstOrNull { it.isDirectory }
            ?: error("Could not locate app res directory from ${File(".").absolutePath}")
    }

    private val localeQualifiers = listOf(
        "",           // default values/
        "-ar",        // Arabic
        "-bn",        // Bengali
        "-de",        // German
        "-es",        // Spanish
        "-fr",        // French
        "-it",        // Italian
        "-iw",        // Hebrew
        "-pt",        // Portuguese
        "-zh-rTW"     // Traditional Chinese
    )

    private val brandedKeyNames = setOf(
        "app_name",
        "materialbook_settings",
        "network_error_description"
    )

    private fun parseStringsXml(file: File): Map<String, String> {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val content = element.textContent.trim()
            result[name] = content
        }
        return result
    }

    @Test
    fun brandStringsExistInAllSupportedLocales() {
        localeQualifiers.forEach { qualifier ->
            val brandFile = File(resDir, "values$qualifier/brand_strings.xml")
            assertTrue(
                "Missing brand_strings.xml for qualifier 'values$qualifier' at ${brandFile.path}",
                brandFile.exists() && brandFile.isFile
            )
        }
    }

    @Test
    fun appNameIsAstryxbookInAllBrandFiles() {
        localeQualifiers.forEach { qualifier ->
            val brandFile = File(resDir, "values$qualifier/brand_strings.xml")
            val strings = parseStringsXml(brandFile)
            assertEquals(
                "app_name in values$qualifier/brand_strings.xml must be 'Astryxbook'",
                "Astryxbook",
                strings["app_name"]
            )
        }
    }

    @Test
    fun brandedStringsContainAstryxbookAndNeverMaterialbook() {
        localeQualifiers.forEach { qualifier ->
            val brandFile = File(resDir, "values$qualifier/brand_strings.xml")
            val strings = parseStringsXml(brandFile)

            val settingsTitle = strings["materialbook_settings"]
            assertNotNull(
                "materialbook_settings missing in values$qualifier/brand_strings.xml",
                settingsTitle
            )
            assertTrue(
                "materialbook_settings in values$qualifier must contain 'Astryxbook', was: '$settingsTitle'",
                settingsTitle!!.contains("Astryxbook")
            )
            assertFalse(
                "materialbook_settings in values$qualifier must not contain 'Materialbook', was: '$settingsTitle'",
                settingsTitle.contains("Materialbook", ignoreCase = true)
            )

            val networkError = strings["network_error_description"]
            assertNotNull(
                "network_error_description missing in values$qualifier/brand_strings.xml",
                networkError
            )
            assertTrue(
                "network_error_description in values$qualifier must contain 'Astryxbook', was: '$networkError'",
                networkError!!.contains("Astryxbook")
            )
            assertFalse(
                "network_error_description in values$qualifier must not contain 'Materialbook', was: '$networkError'",
                networkError.contains("Materialbook", ignoreCase = true)
            )
        }
    }

    @Test
    fun stringsXmlNeverRedefinesBrandedKeys() {
        // strings.xml is upstream Materialbook's actively-maintained translation file.
        // If an upstream sync or developer re-adds any of the branded keys into strings.xml,
        // it creates a merge-conflict magnet and can silently collide with brand_strings.xml.
        localeQualifiers.forEach { qualifier ->
            val stringsFile = File(resDir, "values$qualifier/strings.xml")
            if (stringsFile.exists()) {
                val upstreamStrings = parseStringsXml(stringsFile)
                brandedKeyNames.forEach { key ->
                    assertFalse(
                        "Upstream strings.xml at values$qualifier/strings.xml must NOT redefine '$key' (use brand_strings.xml instead)",
                        upstreamStrings.containsKey(key)
                    )
                }
            }
        }
    }
}
