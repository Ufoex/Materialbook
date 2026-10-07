package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * autoscroll_reels.js against a real WebView. The page is a minimal reel list (three
 * `div.vertically-snappable` items, each with a video); the video's playback state and
 * on-screen position are faked on the elements, and scrollIntoView is recorded, so the
 * test sees exactly which reel the script moves to.
 */
@RunWith(AndroidJUnit4::class)
class AutoScrollReelsJsTest {

    private class Harness {
        lateinit var webView: WebView
            private set

        /** [path] is the page path: the script only acts on reel viewers. */
        fun load(path: String = "/reel/111/") {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://m.facebook.com$path",
                        """
                        <html><body>
                          <div id="list">
                            <div class="vertically-snappable" id="r1"><video></video></div>
                            <div class="vertically-snappable" id="r2"><video></video></div>
                            <div class="vertically-snappable" id="r3"><video></video></div>
                          </div>
                        </body></html>
                        """.trimIndent(),
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            // A detached WebView has no size: give the page a viewport, fake the first reel's
            // video as the playing, on-screen one and record scrollIntoView on every reel.
            eval(
                """
                Object.defineProperty(window, 'innerWidth', { value: 400, configurable: true });
                Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });
                window.__scrolled = [];
                document.querySelectorAll('.vertically-snappable').forEach(function(c) {
                  c.scrollIntoView = function() { window.__scrolled.push(c.id); };
                });
                window.__video = function(id) { return document.querySelector('#' + id + ' video'); };
                window.__fake = function(id, props) {
                  var v = window.__video(id);
                  Object.keys(props).forEach(function(k) {
                    Object.defineProperty(v, k, { value: props[k], configurable: true });
                  });
                };
                // on screen: r1 only (the others are below the viewport)
                window.__fake('r1', { duration: 10, currentTime: 3, paused: false });
                window.__video('r1').getBoundingClientRect = function() {
                  return { left: 0, right: 400, top: 0, bottom: 800, width: 400, height: 800 };
                };
                ['r2', 'r3'].forEach(function(id) {
                  window.__fake(id, { duration: 10, currentTime: 0, paused: true });
                  window.__video(id).getBoundingClientRect = function() {
                    return { left: 0, right: 400, top: 900, bottom: 1700, width: 400, height: 800 };
                  };
                });
                """.trimIndent()
            )
            eval(script())
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

        /** Sets the first reel's playback position and lets the 200 ms poll see it. */
        fun playAt(time: Double) {
            eval("window.__fake('r1', { currentTime: $time }); 1")
            Thread.sleep(450)
        }

        /** Ids of the reels the script scrolled to, comma separated ("" when it never did). */
        fun scrolledTo(): String = eval("window.__scrolled.join(',')").removeSurrounding("\"")

        private fun script(): String {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            return resources.openRawResource(R.raw.autoscroll_reels).bufferedReader().use { it.readText() }
        }
    }

    @Test
    fun loopRestart_goesToTheNextReel() {
        val h = Harness().apply { load() }
        h.playAt(9.6)   // last moments of the reel
        h.playAt(0.2)   // playback wrapped around: the loop restarted
        assertEquals("r2", h.scrolledTo())
    }

    @Test
    fun endedEvent_goesToTheNextReel() {
        val h = Harness().apply { load() }
        h.eval("window.__video('r1').dispatchEvent(new Event('ended')); 1")
        Thread.sleep(300)
        assertEquals("r2", h.scrolledTo())
    }

    @Test
    fun seekingBackFromTheMiddle_isNotAnEnd() {
        val h = Harness().apply { load() }
        h.playAt(6.0)
        h.playAt(1.0)   // jumped back, but not from the end of the reel
        assertEquals("", h.scrolledTo())
    }

    @Test
    fun pausedVideo_doesNotAdvance() {
        val h = Harness().apply { load() }
        h.playAt(9.6)
        h.eval("window.__fake('r1', { paused: true }); 1")
        h.playAt(0.2)
        assertEquals("", h.scrolledTo())
    }

    @Test
    fun disabledForTheSession_doesNotAdvance() {
        val h = Harness().apply { load() }
        h.eval("window.__mbAutoScroll.enabled = false; 1")
        h.playAt(9.6)
        h.playAt(0.2)
        assertEquals("", h.scrolledTo())
        // and it works again once re-enabled
        h.eval("window.__mbAutoScroll.enabled = true; 1")
        h.playAt(9.6)
        h.playAt(0.2)
        assertEquals("r2", h.scrolledTo())
    }

    @Test
    fun reelsRemovedAsAds_areSkipped() {
        val h = Harness().apply { load() }
        // r2 was taken out of the list by the ad blocker
        h.eval("var r2 = document.getElementById('r2'); r2.dataset.adHidden = 'true'; r2.style.display = 'none'; 1")
        h.playAt(9.6)
        h.playAt(0.2)
        assertEquals("r3", h.scrolledTo())
    }

    @Test
    fun outsideAReelViewer_doesNothing() {
        val h = Harness().apply { load(path = "/") }
        h.playAt(9.6)
        h.playAt(0.2)
        assertEquals("", h.scrolledTo())
    }
}
