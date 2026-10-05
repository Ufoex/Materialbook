package com.eepiemi.materialbook

import android.content.pm.ActivityInfo
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DESKTOP_LAYOUT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.LEGACY_REVERT_DESKTOP
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.migrateLegacyAutoDesktop
import com.eepiemi.materialbook.utils.appOrientation
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

    // ── Orientation: phones portrait except fullscreen video ─────────────────

    @Test
    fun phone_browsing_isPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, appOrientation(384, isFullscreen = false))
    }

    @Test
    fun phone_fullscreenVideo_followsAutoRotate() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, appOrientation(384, isFullscreen = true))
    }

    @Test
    fun largeScreen_isNeverLocked() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, appOrientation(600, isFullscreen = false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, appOrientation(800, isFullscreen = true))
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
