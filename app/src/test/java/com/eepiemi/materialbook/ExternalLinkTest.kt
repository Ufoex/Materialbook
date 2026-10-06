package com.eepiemi.materialbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import com.eepiemi.materialbook.utils.isWebViewRenderable

class ExternalLinkTest {

    // Keeps Facebook in the app...
    @Test
    fun facebookStaysInWebView() {
        listOf(
            "https://www.facebook.com/photo/?fbid=123",
            "https://m.facebook.com/groups/99",
            "https://facebook.com/",
            "https://web.facebook.com/profile",
            // The old regex needed a trailing "/" after the host, so the bare root
            // fell through to the browser and reloaded Facebook in Brave.
            "https://www.facebook.com",
            "https://fb.watch/abc123/",
        ).forEach { assertTrue(it, isWebViewRenderable(it)) }
    }

    // ...and sends everything else to the default browser.
    @Test
    fun everythingElseLeavesTheApp() {
        listOf(
            "https://example.com/article",
            "https://news.example.org/story?id=7",
            // Host must be compared, not searched for anywhere in the string.
            "https://evil.com/facebook.com/x",
            // Short-link redirectors only ever point off-site, so they must leave too.
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fa",
            "https://lm.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fa",
        ).forEach { assertFalse(it, isWebViewRenderable(it)) }
    }

    // WebView can't render app deep links; they must reach the owning app.
    @Test
    fun deepLinksAreNotRenderableInWebView() {
        listOf(
            "fb-messenger://open",
            "market://details?id=com.example",
            "intent://example.com/#Intent;scheme=https;package=com.example;end",
            "tel:+123456",
            "mailto:someone@example.com",
        ).forEach { assertFalse(it, isWebViewRenderable(it)) }
    }

    // The old sanitizer re-encoded URL.query, so "hello%20world" came out as
    // "hello%2520world" and the browser got a broken URL.
    @Test
    fun sanitizerKeepsQueryIntact() {
        assertEquals(
            "https://example.com/search?q=hello%20world",
            fbRedirectSanitizer("https://example.com/search?q=hello%20world")
        )
        assertEquals(
            "https://example.com/search?q=hello+world",
            fbRedirectSanitizer("https://example.com/search?q=hello+world")
        )
    }

    @Test
    fun sanitizerStripsFbclidAndKeepsFragment() {
        assertEquals(
            "https://news.example.com/story?utm_source=fb",
            fbRedirectSanitizer("https://news.example.com/story?utm_source=fb&fbclid=xyz")
        )
        assertEquals(
            "https://example.com/page#section-3",
            fbRedirectSanitizer("https://example.com/page#section-3")
        )
    }

    // l.facebook.com/l.php?u= carries the real destination; unwrap it.
    @Test
    fun sanitizerUnwrapsFacebookRedirector() {
        assertEquals(
            "https://example.com/article",
            fbRedirectSanitizer(
                "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle&h=ATabc"
            )
        )
    }

    // A URL we can't parse must survive untouched rather than become a broken one.
    @Test
    fun sanitizerReturnsInputWhenUnparseable() {
        assertEquals("not a url at all", fbRedirectSanitizer("not a url at all"))
    }
}