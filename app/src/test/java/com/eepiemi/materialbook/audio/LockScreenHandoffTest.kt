package com.eepiemi.materialbook.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json, which is only a stub in plain local unit tests.
@RunWith(RobolectricTestRunner::class)
class LockScreenHandoffTest {

    private val mp4 = "https://video.ftlv6-1.fna.fbcdn.net/o1/v/t2/f2/m412/abc.mp4?oe=1&oh=2"

    // ── isHandoffEligible ────────────────────────────────────────────────────

    @Test
    fun httpsMp4_thatWasPlaying_isEligible() {
        assertTrue(isHandoffEligible(mp4, wasPlaying = true))
    }

    @Test
    fun blobUrl_isRejected() {
        assertFalse(isHandoffEligible("blob:https://www.facebook.com/1234", wasPlaying = true))
    }

    @Test
    fun emptyOrNullSrc_isRejected() {
        assertFalse(isHandoffEligible("", wasPlaying = true))
        assertFalse(isHandoffEligible(null, wasPlaying = true))
    }

    @Test
    fun plainHttp_isRejected() {
        assertFalse(isHandoffEligible("http://example.com/a.mp4", wasPlaying = true))
    }

    @Test
    fun alreadyPausedVideo_isRejected() {
        assertFalse(isHandoffEligible(mp4, wasPlaying = false))
    }

    // ── parseHandoffRead ─────────────────────────────────────────────────────

    @Test
    fun parsesDoubleEncodedEvaluateJavascriptResult() {
        // evaluateJavascript JSON-encodes the script's return value, which is
        // itself a JSON.stringify'd object.
        val raw = "\"{\\\"src\\\":\\\"$mp4\\\",\\\"time\\\":37.545152,\\\"wasPlaying\\\":true}\""

        val read = parseHandoffRead(raw)

        assertEquals(HandoffRead(src = mp4, positionMs = 37545, wasPlaying = true), read)
    }

    @Test
    fun missingFields_defaultToNotEligible() {
        val read = parseHandoffRead("\"{}\"")

        assertEquals(HandoffRead(src = "", positionMs = 0, wasPlaying = false), read)
    }

    @Test
    fun nullOrGarbage_returnsNull() {
        assertNull(parseHandoffRead(null))
        assertNull(parseHandoffRead("null"))
        assertNull(parseHandoffRead("not json"))
    }
}
