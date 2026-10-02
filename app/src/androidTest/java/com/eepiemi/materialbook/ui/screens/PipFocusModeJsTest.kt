package com.eepiemi.materialbook.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression tests for the PiP JS scripts (PIP_TOGGLE_JS, PIP_FOCUS_MODE_JS,
 * PIP_RESTORE_MODE_JS, PIP_FREEZE_ACTIVE_VIDEO_JS), driven against a real
 * android.webkit.WebView loaded with small synthetic HTML fixtures rather
 * than live Facebook — these scripts' job is "given this DOM shape, pick/
 * hide/mark the right elements", which is exactly what's testable and
 * exactly what broke each time; Facebook's own player behavior (e.g. its
 * reels controller re-pausing a resumed video) is external, unfixable from
 * here, and deliberately NOT covered — see FORK_CHANGES.md's Known
 * limitations.
 */
@RunWith(AndroidJUnit4::class)
class PipFocusModeJsTest {

    private fun String.unquoted() = removeSurrounding("\"")

    /** Drives a fresh WebView synchronously from the test thread. */
    private class Harness {
        lateinit var webView: WebView
            private set

        // Mirrors PipBridge.logPipAnomaly's shape so PIP_FOCUS_MODE_JS's real
        // sanity-check scan can be exercised end-to-end without needing the
        // production PipBridge/Log.w wiring in this test.
        @Volatile
        var lastAnomaly: String? = null

        inner class FakePipBridge {
            @JavascriptInterface
            fun logPipAnomaly(message: String) {
                lastAnomaly = message
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
                    loadDataWithBaseURL(
                        "https://example.com/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
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

    // ── Nested-DOM chrome leak (yesterday's "toolbar visible in PiP" bug) ────
    // The toolbar sits inside a wrapper that's on the video's ancestor path,
    // not as a direct child of <body> — the old selector only ever checked
    // body's direct children and missed exactly this shape.

    @Test
    fun focusMode_hidesNonKeptSiblings_atEveryAncestorLevel_notJustBody() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <div id="wrapper">
                <div id="toolbar">TOOLBAR</div>
                <div id="reelContainer">
                  <video id="myVideo" muted></video>
                </div>
              </div>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertEquals(
            "none",
            h.eval("getComputedStyle(document.getElementById('toolbar')).display").unquoted()
        )
        assertEquals(
            "fixed",
            h.eval("getComputedStyle(document.getElementById('myVideo')).position").unquoted()
        )
    }

    @Test
    fun focusMode_leavesVideosOwnAncestors_unhidden() {
        // The video's own ancestor chain (wrapper, reelContainer) must stay
        // visible/untouched — a past attempt at display:contents on these
        // broke Facebook's own reel-visibility tracking (see MaterialbookWV.kt
        // history). Regression guard against reintroducing that.
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <div id="wrapper">
                <div id="reelContainer">
                  <video id="myVideo" muted></video>
                </div>
              </div>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertFalse(
            h.eval("getComputedStyle(document.getElementById('wrapper')).display").unquoted() == "none"
        )
        assertFalse(
            h.eval("getComputedStyle(document.getElementById('reelContainer')).display").unquoted() == "none"
        )
    }

    // ── download_content.js's button must hide only in PiP ──────────────────

    @Test
    fun focusMode_hidesDownloadButton_viaInlineOverride() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="myVideo" muted></video>
              <button id="materialbook-global-downloader" class="visible"></button>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertEquals(
            "none",
            h.eval(
                "document.getElementById('materialbook-global-downloader').style.display"
            ).unquoted()
        )
    }

    @Test
    fun focusMode_downloadButtonOverride_beatsItsOwnVisibleClass() {
        // The button's own stylesheet uses #id.visible { display:flex
        // !important }, higher CSS specificity than our tag/attribute
        // selectors - this is why an inline override (not another stylesheet
        // rule) is required. Regression guard against reverting to a plain
        // CSS-only approach that would silently lose this specificity fight.
        val h = Harness()
        h.loadHtml(
            """
            <html><head><style>
              #materialbook-global-downloader { display: none; }
              #materialbook-global-downloader.visible { display: flex !important; }
            </style></head><body>
              <video id="myVideo" muted></video>
              <button id="materialbook-global-downloader" class="visible"></button>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertEquals(
            "none",
            h.eval(
                "getComputedStyle(document.getElementById('materialbook-global-downloader')).display"
            ).unquoted()
        )
    }

    @Test
    fun restoreMode_clearsDownloadButtonOverride() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="myVideo" muted></video>
              <button id="materialbook-global-downloader" class="visible"></button>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")
        h.eval(PIP_FOCUS_MODE_JS)

        h.eval(PIP_RESTORE_MODE_JS)

        assertEquals(
            "",
            h.eval(
                "document.getElementById('materialbook-global-downloader').style.display"
            ).unquoted()
        )
    }

    @Test
    fun focusMode_doesNotErrorWhenDownloadButtonAbsent() {
        // download_content.js may not have run/injected the button yet -
        // focus mode must not throw and must still handle the video normally.
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="myVideo" muted></video>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertEquals(
            "fixed",
            h.eval("getComputedStyle(document.getElementById('myVideo')).position").unquoted()
        )
    }

    // ── copy_to_clipboard.js's button must also hide only in PiP ────────────
    // Same leak, same fix, found by reading the script's own source (a
    // #id.visible !important rule) rather than live DevTools - it can false-
    // positive inside a reel even though it's meant for photos/stories.

    @Test
    fun focusMode_hidesClipboardCopyButton_viaInlineOverride() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="myVideo" muted></video>
              <button id="materialbook-clipboard-copier" class="visible"></button>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertEquals(
            "none",
            h.eval(
                "document.getElementById('materialbook-clipboard-copier').style.display"
            ).unquoted()
        )
    }

    @Test
    fun restoreMode_clearsClipboardCopyButtonOverride() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="myVideo" muted></video>
              <button id="materialbook-clipboard-copier" class="visible"></button>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")
        h.eval(PIP_FOCUS_MODE_JS)

        h.eval(PIP_RESTORE_MODE_JS)

        assertEquals(
            "",
            h.eval(
                "document.getElementById('materialbook-clipboard-copier').style.display"
            ).unquoted()
        )
    }

    // ── Anomaly scan: catches the NEXT leak from an ordinary field logcat ───

    @Test
    fun focusMode_reportsNoAnomaly_whenPageIsFullyHidden() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <div id="wrapper"><video id="myVideo" muted></video></div>
              <div id="otherStuff">not on the video's ancestor path</div>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertNull("expected no anomaly to be reported", h.lastAnomaly)
    }

    @Test
    fun focusMode_reportsAnomaly_whenSomethingSlipsThroughKnownHiding() {
        // Simulates the exact failure mode this scan exists for: an element
        // that ends up visible despite our known hiding rules (here, forced
        // via an inline style the page itself set with !important, the same
        // trick that caused the download/clipboard button leaks).
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <div id="wrapper"><video id="myVideo" muted></video></div>
              <div id="stray" style="display:block !important; width:50px; height:50px;">
                mystery element
              </div>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")

        h.eval(PIP_FOCUS_MODE_JS)

        assertTrue(
            "expected the leaked element to be named in the anomaly report, got: ${h.lastAnomaly}",
            h.lastAnomaly?.contains("stray") == true
        )
    }

    // ── Toggle reuses the locked-in video (yesterday's "wrong reel resumed" bug) ─

    @Test
    fun toggle_targetsLockedInVideo_notTheLargerOne() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="small" muted data-astryx-pip-video="true"
                     style="width:10px;height:10px;"></video>
              <video id="big" muted style="width:500px;height:500px;"></video>
            </body></html>
            """.trimIndent()
        )
        // Spy on play()/pause() instead of asserting real playback state —
        // WebView's media stack needs a valid source to actually transition
        // out of paused, which isn't the point of this test; what matters is
        // which element our selection logic targets.
        h.eval(
            """
            window.__played = null;
            var origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
              window.__played = this.id;
              try { return origPlay.apply(this, arguments); } catch (e) { return undefined; }
            };
            """.trimIndent()
        )

        h.eval(PIP_TOGGLE_JS)

        assertEquals("small", h.eval("window.__played").unquoted())
    }

    @Test
    fun toggle_fallsBackToLastActiveVideo_whenNoneMarked() {
        // Focus mode never ran (e.g. page wasn't Finished loading yet) - the
        // detector's last-known video should still be used over the by-area
        // heuristic when available.
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <video id="tracked" muted style="width:10px;height:10px;"></video>
              <video id="bigger" muted style="width:500px;height:500px;"></video>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('tracked');")
        h.eval(
            """
            window.__played = null;
            var origPlay = HTMLMediaElement.prototype.play;
            HTMLMediaElement.prototype.play = function() {
              window.__played = this.id;
              try { return origPlay.apply(this, arguments); } catch (e) { return undefined; }
            };
            """.trimIndent()
        )

        h.eval(PIP_TOGGLE_JS)

        assertEquals("tracked", h.eval("window.__played").unquoted())
    }

    // ── Freeze mechanism (yesterday's "landscape reel swapped for portrait" bug) ─

    @Test
    fun freeze_blocksWritesToLastActiveVideo_untilUnfrozen() {
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        h.eval("window.__astryxLastActiveVideo = 'A';")

        h.eval(PIP_FREEZE_ACTIVE_VIDEO_JS)
        h.eval("window.__astryxLastActiveVideo = 'B';") // simulates FB's controller autoplaying a different reel

        assertEquals("A", h.eval("window.__astryxLastActiveVideo").unquoted())

        h.eval(PIP_RESTORE_MODE_JS) // unfreezes as a side effect, same as real exit-PiP flow
        h.eval("window.__astryxLastActiveVideo = 'C';")

        assertEquals("C", h.eval("window.__astryxLastActiveVideo").unquoted())
    }

    @Test
    fun freeze_preservesValueAlreadyTracked_atInstallTime() {
        // The guard must capture whatever's already there before it starts
        // intercepting writes - not silently reset to null/undefined.
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        h.eval("window.__astryxLastActiveVideo = 'preExisting';")

        h.eval(PIP_FREEZE_ACTIVE_VIDEO_JS)

        assertEquals("preExisting", h.eval("window.__astryxLastActiveVideo").unquoted())
    }

    // ── Screen pin (rotation during PiP made Facebook drop the reel) ─────────
    // The test WebView's screen can't actually rotate, so these check that the
    // pin replaces the native getters with the values from pin time, and that
    // unpinning puts the native getters back.

    @Test
    fun pinScreen_keepsScreenValuesFromPinTime() {
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        val before = h.eval("JSON.stringify([screen.width, screen.height, screen.orientation.type, window.orientation])")

        h.eval(PIP_PIN_SCREEN_JS)

        assertEquals(
            before,
            h.eval("JSON.stringify([screen.width, screen.height, screen.orientation.type, window.orientation])")
        )
        assertTrue(
            "width must now come from the pin, not the native getter",
            !h.eval("Object.getOwnPropertyDescriptor(Screen.prototype, 'width').get.toString()")
                .contains("native code")
        )
    }

    @Test
    fun pinScreen_isIdempotent() {
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        h.eval(PIP_PIN_SCREEN_JS)
        val first = h.eval("String(Object.getOwnPropertyDescriptor(Screen.prototype, 'width').get)")

        h.eval(PIP_PIN_SCREEN_JS)

        assertEquals(first, h.eval("String(Object.getOwnPropertyDescriptor(Screen.prototype, 'width').get)"))
        assertEquals("true", h.eval("Array.isArray(window.__astryxScreenPin)"))
    }

    @Test
    fun unpinScreen_restoresNativeGetters_andFiresOneResize() {
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        val before = h.eval("JSON.stringify([screen.width, screen.height, screen.orientation.type, window.orientation])")
        h.eval(PIP_PIN_SCREEN_JS)
        h.eval("window.__resizes = 0; window.addEventListener('resize', function() { window.__resizes++; });")

        h.eval(PIP_UNPIN_SCREEN_JS)

        assertTrue(
            h.eval("Object.getOwnPropertyDescriptor(Screen.prototype, 'width').get.toString()")
                .contains("native code")
        )
        assertTrue(
            h.eval("Object.getOwnPropertyDescriptor(ScreenOrientation.prototype, 'type').get.toString()")
                .contains("native code")
        )
        assertEquals(
            before,
            h.eval("JSON.stringify([screen.width, screen.height, screen.orientation.type, window.orientation])")
        )
        assertEquals("1", h.eval("window.__resizes"))
        assertEquals("null", h.eval("window.__astryxScreenPin"))
    }

    @Test
    fun unpinScreen_withoutPin_isNoOp() {
        val h = Harness()
        h.loadHtml("<html><body></body></html>")
        h.eval("window.__resizes = 0; window.addEventListener('resize', function() { window.__resizes++; });")

        h.eval(PIP_UNPIN_SCREEN_JS)

        assertEquals("0", h.eval("window.__resizes"))
    }

    // ── Restore cleanup ───────────────────────────────────────────────────

    @Test
    fun restoreMode_removesAllFocusModeState() {
        val h = Harness()
        h.loadHtml(
            """
            <html><body>
              <div id="wrapper"><video id="myVideo" muted></video></div>
            </body></html>
            """.trimIndent()
        )
        h.eval("window.__astryxLastActiveVideo = document.getElementById('myVideo');")
        h.eval(PIP_FOCUS_MODE_JS)

        h.eval(PIP_RESTORE_MODE_JS)

        assertNull(h.eval("document.getElementById('astryx-pip-style')").takeIf { it != "null" })
        assertEquals(
            "false",
            h.eval("document.body.hasAttribute('data-astryx-pip-active')")
        )
        assertEquals(
            "false",
            h.eval("document.getElementById('myVideo').hasAttribute('data-astryx-pip-video')")
        )
        assertEquals(
            "false",
            h.eval("document.getElementById('wrapper').hasAttribute('data-astryx-pip-keep')")
        )
    }
}
