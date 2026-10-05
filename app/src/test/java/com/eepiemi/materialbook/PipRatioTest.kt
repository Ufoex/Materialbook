package com.eepiemi.materialbook

import android.util.Rational
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PipRatioTest {

    private fun parse(stored: String): Rational {
        return SettingsViewModel.parsedPipRational(stored)
    }

    // ── Shipped presets ──────────────────────────────────────────────────────

    @Test
    fun preset_4_7_parsesCorrectly() {
        assertEquals(Rational(4, 7), parse("4:7"))
    }

    @Test
    fun preset_2_3_parsesCorrectly() {
        assertEquals(Rational(2, 3), parse("2:3"))
    }

    @Test
    fun preset_3_4_parsesCorrectly() {
        assertEquals(Rational(3, 4), parse("3:4"))
    }

    @Test
    fun preset_9_16_parsesCorrectly() {
        assertEquals(Rational(9, 16), parse("9:16"))
    }

    // ── Default & error handling ─────────────────────────────────────────────

    @Test
    fun defaultRatioIs_4_7() {
        assertEquals(Rational(4, 7), parse("4:7"))
    }

    @Test
    fun landscapeVideoUsesDetectedSourceRatio() {
        assertEquals(
            Rational(4, 3),
            SettingsViewModel.calculatePipRational(1920, 1440, Rational(4, 7))
        )
    }

    @Test
    fun extremeLandscapeRatioIsClampedToAndroidLimit() {
        assertEquals(
            Rational(239, 100),
            SettingsViewModel.calculatePipRational(1000, 100, Rational(4, 7))
        )
    }

    @Test
    fun unknownVideoDimensionsFallBackTo16By9() {
        assertEquals(
            Rational(16, 9),
            SettingsViewModel.calculatePipRational(0, 0, Rational(4, 7))
        )
    }

    @Test
    fun portraitVideoStillUsesSelectedRatio() {
        assertEquals(
            Rational(2, 3),
            SettingsViewModel.calculatePipRational(1080, 1920, Rational(2, 3))
        )
    }

    @Test
    fun corruptValue_fallsBackTo_4_7() {
        assertEquals(Rational(4, 7), parse("garbage"))
    }

    @Test
    fun emptyString_fallsBackTo_4_7() {
        assertEquals(Rational(4, 7), parse(""))
    }

    @Test
    fun missingDenominator_fallsBackTo_4_7() {
        assertEquals(Rational(4, 7), parse("4:"))
    }

    @Test
    fun floatValue_fallsBackTo_4_7() {
        assertEquals(Rational(4, 7), parse("0.5625"))
    }
}