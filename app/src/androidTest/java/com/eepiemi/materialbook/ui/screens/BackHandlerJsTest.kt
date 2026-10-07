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
 * Tests for backHandlerNB in scripts.js against a real WebView, same harness
 * approach as PipHandoffJsTest. The fixture mimics the desktop site
 * (html#facebook on www.facebook.com/) with a Messenger chat window: a
 * fixed-position box with a header row of buttons and a message composer,
 * not marked role="dialog".
 */
@RunWith(AndroidJUnit4::class)
class BackHandlerJsTest {

    private fun String.unquoted() = removeSurrounding("\"")

    private class Harness {
        lateinit var webView: WebView
            private set

        fun load(body: String) {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    // A real viewport, so document.elementFromPoint (which the
                    // handler uses to skip covered buttons) can hit anything.
                    layout(0, 0, 1080, 2000)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://www.facebook.com/",
                        "<html id='facebook'><body>$body</body></html>",
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            eval(backHandlerScript())
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

        // The desktop/feed identifiers and the back handler, i.e. scripts.js up
        // to the first block that doesn't belong to them.
        private fun backHandlerScript(): String {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val all = resources.openRawResource(R.raw.scripts).bufferedReader().use { it.readText() }
            val end = all.indexOf("// Enable press and hold")
            assertTrue("back handler block not found in scripts.js", end > 0)
            return all.substring(0, end)
        }
    }

    private val chatWindow = """
        <div id="chat" style="position:fixed; left:0; top:0; width:300px; height:400px;">
          <div style="height:48px;">
            <div role="button" id="settings" style="display:inline-block; width:80px; height:28px;"
                 onclick="window.__clicked='settings'"></div>
            <div role="button" id="minimize" style="display:inline-block; width:28px; height:28px;"
                 onclick="window.__clicked='minimize'"></div>
            <div role="button" id="close" style="display:inline-block; width:28px; height:28px;"
                 onclick="window.__clicked='close'; document.getElementById('chat').remove();"></div>
          </div>
          <div style="height:280px; padding-top:20px;">
            <div role="button" style="width:24px; height:24px; margin-left:250px;"
                 onclick="window.__clicked='message'"></div>
          </div>
          <div role="region"><div role="textbox" contenteditable="true" style="height:20px;"></div></div>
        </div>
    """.trimIndent()

    @Test
    fun openChatWindow_isClosed_andBackIsHandled() {
        val h = Harness()
        h.load(chatWindow)

        assertEquals("true", h.eval("backHandlerNB()").unquoted())
        assertEquals("close", h.eval("window.__clicked").unquoted())
    }

    @Test
    fun buttonCoveredByTheHeader_isNotClicked() {
        val h = Harness()
        // A message scrolled up under the header leaves its action button in the
        // header row, right of "Close chat", but covered.
        h.load(
            chatWindow.replace(
                "<div role=\"region\">",
                """
                <div role="button" style="position:absolute; top:10px; left:280px; width:16px; height:16px;"
                     onclick="window.__clicked='covered-message'"></div>
                <div style="position:absolute; top:0; left:270px; width:30px; height:40px; z-index:5;"></div>
                <div role="region">
                """.trimIndent()
            )
        )

        assertEquals("true", h.eval("backHandlerNB()").unquoted())
        assertEquals("close", h.eval("window.__clicked").unquoted())
    }

    @Test
    fun secondBack_afterChatClosed_fallsThroughToNormalHandling() {
        val h = Harness()
        h.load(chatWindow)
        h.eval("backHandlerNB()")
        h.eval("window.__clicked = null;")

        // No chat left and no dialog: the page handles Back as before.
        assertEquals("false", h.eval("backHandlerNB()").unquoted())
        assertEquals("null", h.eval("window.__clicked"))
    }

    @Test
    fun composerInsideDialog_isLeftToTheDialogHandling() {
        val h = Harness()
        h.load(
            """
            <div role="dialog" style="position:fixed; inset:0;">
              <div role="button" style="width:28px; height:28px;" onclick="window.__clicked='dialog-button'"></div>
              <div contenteditable="true" style="height:20px;"></div>
            </div>
            """.trimIndent()
        )

        h.eval("backHandlerNB()")

        assertEquals("null", h.eval("window.__clicked ?? null"))
    }

    @Test
    fun feedCommentBox_isNotTreatedAsChatWindow() {
        val h = Harness()
        h.load(
            """
            <div style="height:3000px;">
              <div contenteditable="true" style="height:20px;"></div>
              <div role="button" style="width:28px; height:28px;" onclick="window.__clicked='post-button'"></div>
            </div>
            """.trimIndent()
        )

        h.eval("backHandlerNB()")

        assertEquals("null", h.eval("window.__clicked ?? null"))
    }
}
