package com.eepiemi.materialbook.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for the lock-screen audio handoff scripts (PIP_HANDOFF_ARM_JS,
 * PIP_HANDOFF_READ_JS, PIP_HANDOFF_DISARM_JS, pipHandbackJs) against a real
 * WebView with a synthetic fixture, same harness approach as
 * PipFocusModeJsTest. The page's hidden/visible transitions are simulated by
 * overriding document.visibilityState and dispatching visibilitychange.
 */
@RunWith(AndroidJUnit4::class)
class PipHandoffJsTest {

    private fun String.unquoted() = removeSurrounding("\"")

    private class Harness {
        lateinit var webView: WebView
            private set

        @Volatile
        var pageVisibleCalls = 0

        inner class FakePipBridge {
            @JavascriptInterface
            fun onPageVisible() {
                pageVisibleCalls++
            }
        }

        fun loadHtml(html: String) {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(FakePipBridge(), "PipBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL("https://example.com/", html, "text/html", "UTF-8", null)
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
        }

        fun eval(js: String): String {
            var result: String? = null
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                webView.evaluateJavascript(js) { value ->
                    result = value
                    latch.countDown()
                }
            }
            assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
            return result ?: "null"
        }
    }

    // Fixture: a marked PiP video plus a bigger unmarked one. currentSrc,
    // currentTime and paused are overridden per element so tests don't need
    // real media; play()/pause() are spied the same way PipFocusModeJsTest does.
    private fun Harness.loadFixture(pipPlaying: Boolean = true, pipMuted: Boolean = false) {
        loadHtml(
            """
            <html><body>
              <video id="pip" data-astryx-pip-video="true" style="width:10px;height:10px;"></video>
              <video id="other" style="width:500px;height:500px;"></video>
            </body></html>
            """.trimIndent()
        )
        eval(
            """
            window.__vis = 'visible';
            Object.defineProperty(document, 'visibilityState', { get: function() { return window.__vis; } });
            window.__calls = [];
            HTMLMediaElement.prototype.play = function() { window.__calls.push('play:' + this.id); this.__paused = false; return undefined; };
            HTMLMediaElement.prototype.pause = function() { window.__calls.push('pause:' + this.id); this.__paused = true; };
            ['pip', 'other'].forEach(function(id) {
              var v = document.getElementById(id);
              v.__paused = true;
              Object.defineProperty(v, 'paused', { get: function() { return this.__paused; } });
              Object.defineProperty(v, 'currentSrc', { get: function() { return 'https://video.example.net/' + id + '.mp4'; } });
              v.__time = 12.5;
              Object.defineProperty(v, 'currentTime', {
                get: function() { return this.__time; },
                set: function(t) { this.__time = t; }
              });
            });
            var pip = document.getElementById('pip');
            pip.__paused = ${!pipPlaying};
            pip.muted = $pipMuted;
            """.trimIndent()
        )
    }

    private fun Harness.setVisibility(state: String) {
        eval("window.__vis = '$state'; document.dispatchEvent(new Event('visibilitychange'));")
    }

    private fun Harness.read(): JSONObject {
        val raw = eval(PIP_HANDOFF_READ_JS)
        return JSONObject(JSONTokener(raw).nextValue() as String)
    }

    // ── Snapshot at hide ─────────────────────────────────────────────────────

    @Test
    fun read_returnsSnapshotTakenWhenHidden_evenIfPausedAfterwards() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)

        h.setVisibility("hidden")
        // Facebook pauses the video right after the page is hidden.
        h.eval("document.getElementById('pip').pause();")

        val read = h.read()
        assertEquals("https://video.example.net/pip.mp4", read.getString("src"))
        assertEquals(12.5, read.getDouble("time"), 0.001)
        assertTrue("wasPlaying must reflect the state at hide", read.getBoolean("wasPlaying"))
    }

    @Test
    fun read_reportsNotPlaying_whenVideoWasAlreadyPausedAtHide() {
        val h = Harness()
        h.loadFixture(pipPlaying = false)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        assertEquals(false, h.read().getBoolean("wasPlaying"))
    }

    @Test
    fun read_withoutSnapshot_returnsEmptySrc_andLeavesVideoAlone() {
        val h = Harness()
        h.loadFixture()
        h.eval(PIP_HANDOFF_ARM_JS) // armed, but the page was never hidden

        val read = h.read()

        assertEquals("", read.getString("src"))
        assertEquals("false", h.eval("document.getElementById('pip').muted"))
        assertEquals(
            "false",
            h.eval("document.getElementById('pip').hasAttribute('data-astryx-handoff-muted')")
        )
    }

    @Test
    fun read_mutesAndPausesTheSnapshottedVideo_andSavesPriorMute() {
        val h = Harness()
        h.loadFixture(pipPlaying = true, pipMuted = false)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        h.read()

        assertEquals("true", h.eval("document.getElementById('pip').muted"))
        assertEquals(
            "false",
            h.eval("document.getElementById('pip').getAttribute('data-astryx-handoff-muted')").unquoted()
        )
        assertTrue(h.eval("window.__calls.join(',')").contains("pause:pip"))
        assertTrue(
            "the other video must not be touched",
            !h.eval("window.__calls.join(',')").contains("other")
        )
    }

    // ── Visible signal ───────────────────────────────────────────────────────

    @Test
    fun visibleAgain_notifiesBridge_onlyWhileHandoffActive() {
        val h = Harness()
        h.loadFixture()
        h.eval(PIP_HANDOFF_ARM_JS)

        h.setVisibility("hidden")
        h.setVisibility("visible") // no handoff happened yet
        h.eval("1") // flush
        assertEquals(0, h.pageVisibleCalls)

        h.setVisibility("hidden")
        h.read()
        h.setVisibility("visible")
        h.eval("1")
        assertEquals(1, h.pageVisibleCalls)
    }

    // ── Handback ─────────────────────────────────────────────────────────────

    @Test
    fun handback_setsTime_restoresMute_removesMarker_andPlaysWhenAsked() {
        val h = Harness()
        h.loadFixture(pipPlaying = true, pipMuted = false)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        h.read()
        h.eval("window.__calls = [];")

        h.eval(pipHandbackJs(positionMs = 45_250, play = true, rearm = false))

        assertEquals(45.25, h.eval("document.getElementById('pip').currentTime").toDouble(), 0.001)
        assertEquals("false", h.eval("document.getElementById('pip').muted"))
        assertEquals(
            "false",
            h.eval("document.getElementById('pip').hasAttribute('data-astryx-handoff-muted')")
        )
        assertEquals("play:pip", h.eval("window.__calls.join(',')").unquoted())
    }

    @Test
    fun handback_doesNotPlay_whenNativePlayerWasPaused() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        h.read()
        h.eval("window.__calls = [];")

        h.eval(pipHandbackJs(positionMs = 20_000, play = false, rearm = false))

        assertEquals("", h.eval("window.__calls.join(',')").unquoted())
    }

    @Test
    fun handback_restoresMutedTrue_whenVideoWasMutedBefore() {
        val h = Harness()
        h.loadFixture(pipPlaying = true, pipMuted = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        h.read()

        h.eval(pipHandbackJs(positionMs = 1_000, play = false, rearm = false))

        assertEquals("true", h.eval("document.getElementById('pip').muted"))
    }

    @Test
    fun abortedHandback_withoutPosition_leavesTimeAlone() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        h.read()

        h.eval(pipHandbackJs(positionMs = null, play = false, rearm = false))

        assertEquals(12.5, h.eval("document.getElementById('pip').currentTime").toDouble(), 0.001)
        assertEquals("false", h.eval("document.getElementById('pip').muted"))
    }

    @Test
    fun handback_clearsSnapshotAndListener_andRearmsWhenAsked() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        h.read()

        h.eval(pipHandbackJs(positionMs = 1_000, play = false, rearm = false))
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
        assertEquals("null", h.eval("window.__astryxHandoffListener"))
        h.setVisibility("hidden")
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))

        h.eval(pipHandbackJs(positionMs = null, play = false, rearm = true))
        h.setVisibility("visible")
        h.eval("document.getElementById('pip').__paused = false;")
        h.setVisibility("hidden")
        assertEquals("true", h.eval("window.__astryxHandoffSnapshot.wasPlaying"))
    }

    @Test
    fun disarm_removesListenerAndSnapshot() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        h.eval(PIP_HANDOFF_DISARM_JS)

        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
        h.setVisibility("visible")
        h.setVisibility("hidden")
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
    }

    @Test
    fun arm_isIdempotent() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        val first = h.eval("String(window.__astryxHandoffListener)")
        h.eval(PIP_HANDOFF_ARM_JS)

        assertEquals(first, h.eval("String(window.__astryxHandoffListener)"))
    }
}
