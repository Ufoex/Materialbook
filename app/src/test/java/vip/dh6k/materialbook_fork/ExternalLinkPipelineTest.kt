package vip.dh6k.materialbook_fork

import org.junit.Assert.assertEquals
import org.junit.Test
import vip.dh6k.materialbook_fork.utils.fbRedirectSanitizer
import vip.dh6k.materialbook_fork.utils.isWebViewRenderable

/**
 * Exercises the real classification + sanitize pair the interceptor runs, in the same
 * order, so a regression in either step shows up here instead of on a user's phone.
 */
class ExternalLinkPipelineTest {

    private fun handOff(url: String): String =
        if (isWebViewRenderable(url)) "in-webview" else fbRedirectSanitizer(url)

    @Test
    fun externalLinkReachesBrowserIntact() {
        assertEquals("https://example.com/search?q=hello%20world",
            handOff("https://example.com/search?q=hello%20world"))
        assertEquals("https://example.com/page#section-3",
            handOff("https://example.com/page#section-3"))
    }

    // Facebook's own l.php redirector: the destination is what should reach the browser.
    @Test
    fun fbRedirectorHandsOffUnwrappedDestination() {
        assertEquals("https://example.com/article", handOff(
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle&h=ATabc&fbclid=Qz"
        ))
    }

    @Test
    fun facebookStaysPut() {
        assertEquals("in-webview", handOff("https://www.facebook.com/story/?id=1"))
        assertEquals("in-webview", handOff("https://m.facebook.com/"))
    }

    @Test
    fun trackingParamsAreStripped() {
        assertEquals("https://news.example.com/p?id=7",
            handOff("https://news.example.com/p?id=7&fbclid=abc123"))
    }
}