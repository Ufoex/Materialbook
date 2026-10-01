package com.eepiemi.materialbook

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DESKTOP_LAYOUT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.LEGACY_REVERT_DESKTOP
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.migrateLegacyAutoDesktop
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.isAutoDesktopScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDesktopTest {

    // ── Effective desktop rule ───────────────────────────────────────────────

    @Test
    fun userOff_phone_isMobile() {
        assertFalse(effectiveDesktop(userDesktopSetting = false, isAutoDesktop = false))
    }

    @Test
    fun userOn_phone_isDesktop() {
        assertTrue(effectiveDesktop(userDesktopSetting = true, isAutoDesktop = false))
    }

    @Test
    fun userOff_largeScreen_isDesktop() {
        assertTrue(effectiveDesktop(userDesktopSetting = false, isAutoDesktop = true))
    }

    // ── Auto-desktop is a device-class check only ────────────────────────────

    @Test
    fun phoneSmallestWidth_isNotAutoDesktop() {
        // ~384dp phone, regardless of orientation (smallestScreenWidthDp is rotation-invariant).
        assertFalse(isAutoDesktopScreen(384))
    }

    @Test
    fun tabletSmallestWidth_isAutoDesktop() {
        assertTrue(isAutoDesktopScreen(600))
        assertTrue(isAutoDesktopScreen(800))
    }

    // ── Migration of the old persisted auto decision ─────────────────────────

    @Test
    fun legacyAutoDesktop_isResetAndRevertKeyCleared() {
        val prefs = mutablePreferencesOf(DESKTOP_LAYOUT to true, LEGACY_REVERT_DESKTOP to true)
        migrateLegacyAutoDesktop(prefs)
        assertEquals(false, prefs[DESKTOP_LAYOUT])
        assertNull(prefs[LEGACY_REVERT_DESKTOP])
    }

    @Test
    fun userChosenDesktop_isKept() {
        val prefs = mutablePreferencesOf(DESKTOP_LAYOUT to true, LEGACY_REVERT_DESKTOP to false)
        migrateLegacyAutoDesktop(prefs)
        assertEquals(true, prefs[DESKTOP_LAYOUT])
        assertNull(prefs[LEGACY_REVERT_DESKTOP])
    }

    @Test
    fun userChosenDesktop_withoutLegacyKey_isKept() {
        val prefs = mutablePreferencesOf(DESKTOP_LAYOUT to true)
        migrateLegacyAutoDesktop(prefs)
        assertEquals(true, prefs[DESKTOP_LAYOUT])
    }
}
