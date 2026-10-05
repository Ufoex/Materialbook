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
 * JVM unit test validating `Theme.Astryxbook` XML definitions across all variants.
 *
 * Runs quickly in JVM unit tests without needing an Android device.
 *
 * Prevents regressions across the 4 configuration-qualified theme files:
 * - values/themes.xml
 * - values-v31/themes.xml
 * - values-night/themes.xml
 * - values-night-v31/themes.xml
 *
 * Specifically guards against:
 * 1. The -v31/-night-v31 variants re-introducing dynamic system accents
 *    (`@android:color/system_accent1_*`), which previously caused the theme to
 *    silently revert to Material You dynamic theming on API 31+ devices.
 * 2. `colorControlActivated` drifting away from Facebook Blue (`#1877F2`).
 * 3. `windowBackground` deviating from `@android:color/background_dark`.
 */
class ThemeXmlTest {

    private val resDir: File by lazy {
        listOf(
            File("src/main/res"),
            File("app/src/main/res")
        ).firstOrNull { it.isDirectory }
            ?: error("Could not locate app res directory from ${File(".").absolutePath}")
    }

    private val themeQualifiers = listOf(
        "",
        "-v31",
        "-night",
        "-night-v31"
    )

    private data class StyleDefinition(
        val name: String,
        val parent: String?,
        val items: Map<String, String>
    )

    private fun parseStyles(file: File): Map<String, StyleDefinition> {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(file)
        val styleNodes = doc.getElementsByTagName("style")
        val result = mutableMapOf<String, StyleDefinition>()

        for (i in 0 until styleNodes.length) {
            val styleElement = styleNodes.item(i) as Element
            val styleName = styleElement.getAttribute("name")
            val parent = styleElement.getAttribute("parent").ifEmpty { null }

            val itemNodes = styleElement.getElementsByTagName("item")
            val items = mutableMapOf<String, String>()
            for (j in 0 until itemNodes.length) {
                val itemElement = itemNodes.item(j) as Element
                val itemName = itemElement.getAttribute("name")
                val itemValue = itemElement.textContent.trim()
                items[itemName] = itemValue
            }
            result[styleName] = StyleDefinition(styleName, parent, items)
        }
        return result
    }

    @Test
    fun themeAstryxbookExistsInAllVariants() {
        themeQualifiers.forEach { qualifier ->
            val themeFile = File(resDir, "values$qualifier/themes.xml")
            assertTrue(
                "Missing themes.xml at ${themeFile.path}",
                themeFile.exists() && themeFile.isFile
            )
            val styles = parseStyles(themeFile)
            assertTrue(
                "Theme.Astryxbook must be defined in values$qualifier/themes.xml",
                styles.containsKey("Theme.Astryxbook")
            )
        }
    }

    @Test
    fun colorControlActivatedIsFacebookBlueInAllVariants() {
        themeQualifiers.forEach { qualifier ->
            val themeFile = File(resDir, "values$qualifier/themes.xml")
            val styles = parseStyles(themeFile)
            val astryxTheme = styles["Theme.Astryxbook"]
            assertNotNull("Theme.Astryxbook missing in values$qualifier/themes.xml", astryxTheme)

            val color = astryxTheme!!.items["android:colorControlActivated"]
            assertEquals(
                "android:colorControlActivated in values$qualifier/themes.xml must be '#1877F2' (Facebook Blue)",
                "#1877F2",
                color
            )
        }
    }

    @Test
    fun windowBackgroundIsBackgroundDarkInAllVariants() {
        themeQualifiers.forEach { qualifier ->
            val themeFile = File(resDir, "values$qualifier/themes.xml")
            val styles = parseStyles(themeFile)
            val astryxTheme = styles["Theme.Astryxbook"]
            assertNotNull("Theme.Astryxbook missing in values$qualifier/themes.xml", astryxTheme)

            val background = astryxTheme!!.items["android:windowBackground"]
            assertEquals(
                "android:windowBackground in values$qualifier/themes.xml must be '@android:color/background_dark'",
                "@android:color/background_dark",
                background
            )
        }
    }

    @Test
    fun v31VariantsDoNotUseDynamicSystemAccents() {
        listOf("-v31", "-night-v31").forEach { qualifier ->
            val themeFile = File(resDir, "values$qualifier/themes.xml")
            val content = themeFile.readText()
            assertFalse(
                "values$qualifier/themes.xml must not reference dynamic system_accent colors",
                content.contains("system_accent")
            )
        }
    }
}
