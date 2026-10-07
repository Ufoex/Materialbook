package com.eepiemi.materialbook.ui.screens

import android.webkit.JavascriptInterface
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
 * Tests for the "File Download Script" block of scripts.js against a real
 * WebView, same harness approach as PipHandoffJsTest. Only an explicit
 * download (a clicked <a download> pointing at a blob: URL) may reach
 * DownloadBridge; blobs a page merely creates, like the decrypted photos of an
 * encrypted Messenger chat, must not.
 */
@RunWith(AndroidJUnit4::class)
class FileDownloadJsTest {

    private class Harness {
        lateinit var webView: WebView
            private set

        val downloads = mutableListOf<String>()

        inner class FakeDownloadBridge {
            @JavascriptInterface
            fun downloadBase64File(base64Data: String, mimeType: String) {
                synchronized(downloads) { downloads.add(mimeType) }
            }
        }

        fun load() {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(FakeDownloadBridge(), "DownloadBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.com/",
                        "<html><body><p id='text'>hi</p></body></html>",
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            eval(downloadScript())
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

        // FileReader and the bridge call are asynchronous; give them time to land.
        fun downloadCountAfterSettling(): Int {
            Thread.sleep(500)
            eval("1")
            return synchronized(downloads) { downloads.size }
        }

        private fun downloadScript(): String {
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            val all = resources.openRawResource(R.raw.scripts).bufferedReader().use { it.readText() }
            val start = all.indexOf("// File Download Script")
            assertTrue("File Download Script block not found in scripts.js", start >= 0)
            return all.substring(start)
        }
    }

    private val makeBlobUrl =
        "window.__url = URL.createObjectURL(new Blob(['x'], { type: 'image/png' }));"

    @Test
    fun creatingBlobUrls_doesNotDownload() {
        val h = Harness()
        h.load()

        h.eval(makeBlobUrl)
        h.eval(
            """
            var img = document.createElement('img');
            img.src = window.__url;
            document.body.appendChild(img);
            """.trimIndent()
        )

        assertEquals(0, h.downloadCountAfterSettling())
    }

    @Test
    fun detachedDownloadLinkClickedByScript_downloadsOnce() {
        val h = Harness()
        h.load()

        h.eval(makeBlobUrl)
        h.eval(
            """
            var a = document.createElement('a');
            a.href = window.__url;
            a.download = 'photo.png';
            a.click();
            """.trimIndent()
        )

        assertEquals(1, h.downloadCountAfterSettling())
        assertEquals("image/png", h.downloads.single())
    }

    @Test
    fun connectedDownloadLinkClicked_downloadsOnce() {
        val h = Harness()
        h.load()

        h.eval(makeBlobUrl)
        h.eval(
            """
            var a = document.createElement('a');
            a.href = window.__url;
            a.download = 'photo.png';
            a.textContent = 'save';
            document.body.appendChild(a);
            a.click();
            """.trimIndent()
        )

        assertEquals(1, h.downloadCountAfterSettling())
    }

    @Test
    fun blobLinkWithoutDownloadAttribute_doesNotDownload() {
        val h = Harness()
        h.load()

        h.eval(makeBlobUrl)
        h.eval(
            """
            var a = document.createElement('a');
            a.href = window.__url;
            a.addEventListener('click', function(e) { e.preventDefault(); });
            document.body.appendChild(a);
            a.click();
            """.trimIndent()
        )

        assertEquals(0, h.downloadCountAfterSettling())
    }

    @Test
    fun revokedBlobUrl_isNotDownloaded() {
        val h = Harness()
        h.load()

        h.eval(makeBlobUrl)
        h.eval(
            """
            URL.revokeObjectURL(window.__url);
            var a = document.createElement('a');
            a.href = window.__url;
            a.download = 'photo.png';
            a.click();
            """.trimIndent()
        )

        assertEquals(0, h.downloadCountAfterSettling())
    }

    @Test
    fun scriptIsIdempotent() {
        val h = Harness()
        h.load()
        h.eval(
            InstrumentationRegistry.getInstrumentation().targetContext.resources
                .openRawResource(R.raw.scripts).bufferedReader().use { it.readText() }
                .let { it.substring(it.indexOf("// File Download Script")) }
        )

        h.eval(makeBlobUrl)
        h.eval(
            """
            var a = document.createElement('a');
            a.href = window.__url;
            a.download = 'photo.png';
            a.click();
            """.trimIndent()
        )

        assertEquals(1, h.downloadCountAfterSettling())
    }
}
