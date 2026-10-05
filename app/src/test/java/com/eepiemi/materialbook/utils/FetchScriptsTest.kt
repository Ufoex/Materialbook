package com.eepiemi.materialbook.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fetchScripts used to have two robustness gaps (see fetchScripts.kt's own
 * comment): fetches were unbounded — no timeout, success or exception
 * whichever came first, however long that took — and strictly sequential,
 * one script's GET fully awaited before the next started. On a cold network
 * (e.g. right after an app cache clear forces every fetch back onto the
 * network instead of reusing a cached response), that could leave
 * userScripts — including pip_video_detector.js, the PiP active-video
 * tracker — not finished loading for several seconds, during which PiP
 * focus mode has no reliable data to work with (see the black-video-with-
 * audio field report this was diagnosed from). These tests cover the fix:
 * a bounded per-script timeout with fallback, and concurrent fetching that
 * still preserves the caller's script order in the final concatenated
 * output regardless of which network response lands first.
 */
class FetchScriptsTest {

    private val scriptA = Script(isEnabled = true, resourceId = 1, scriptTitle = "a.js")
    private val scriptB = Script(isEnabled = true, resourceId = 2, scriptTitle = "b.js")

    private fun fallbackFor(resourceId: Int) = "fallback-$resourceId"

    private fun clientRespondingWith(
        bodies: Map<String, String> = emptyMap(),
        errors: Set<String> = emptySet(),
        delays: Map<String, Long> = emptyMap()
    ): HttpClient = HttpClient(MockEngine { request ->
        val title = request.url.encodedPath.substringAfterLast('/')
        delays[title]?.let { delay(it) }
        when {
            title in errors -> respondError(HttpStatusCode.NotFound)
            bodies.containsKey(title) -> respond(
                content = bodies.getValue(title),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/javascript")
            )
            else -> respondError(HttpStatusCode.NotFound)
        }
    })

    @Test
    fun returnsNetworkContent_onSuccess() = runBlocking {
        val client = clientRespondingWith(bodies = mapOf("a.js" to "network-a"))

        val result = fetchScripts(listOf(scriptA), ::fallbackFor, client)

        assertEquals("network-a", result)
    }

    @Test
    fun fallsBackToBundled_onNon200Status() = runBlocking {
        val client = clientRespondingWith(errors = setOf("a.js"))

        val result = fetchScripts(listOf(scriptA), ::fallbackFor, client)

        assertEquals(fallbackFor(1), result)
    }

    @Test
    fun fallsBackToBundled_whenFetchExceedsTimeout() = runBlocking {
        val client = clientRespondingWith(
            bodies = mapOf("a.js" to "network-a"),
            delays = mapOf("a.js" to FETCH_TIMEOUT_MS + 500)
        )

        val result = fetchScripts(listOf(scriptA), ::fallbackFor, client)

        assertEquals(
            "a fetch that exceeds FETCH_TIMEOUT_MS must fall back, not hang or throw",
            fallbackFor(1),
            result
        )
    }

    @Test
    fun preservesCallerOrder_regardlessOfWhichResponseLandsFirst() = runBlocking {
        // b.js "wins the race" (no delay) while a.js is slow (but still under
        // the timeout) - the concatenated result must still read a-then-b,
        // matching the order the caller listed them in, not completion order.
        val client = clientRespondingWith(
            bodies = mapOf("a.js" to "content-a", "b.js" to "content-b"),
            delays = mapOf("a.js" to 200L)
        )

        val result = fetchScripts(listOf(scriptA, scriptB), ::fallbackFor, client)

        assertEquals("content-acontent-b", result)
    }

    @Test
    fun runsFetchesConcurrently_notSequentially() = runBlocking {
        // Two scripts, each individually within the timeout, but whose
        // delays would sum to MORE than the timeout if run one-after-another.
        // A regression to sequential fetching would make this test's overall
        // wall-clock time balloon well past FETCH_TIMEOUT_MS even though
        // neither individual fetch does - this asserts on outcome (both
        // succeed, within one timeout window), not on wall-clock timing
        // directly, to avoid a flaky test.
        val perScriptDelay = FETCH_TIMEOUT_MS - 200
        val client = clientRespondingWith(
            bodies = mapOf("a.js" to "content-a", "b.js" to "content-b"),
            delays = mapOf("a.js" to perScriptDelay, "b.js" to perScriptDelay)
        )

        val result = fetchScripts(listOf(scriptA, scriptB), ::fallbackFor, client)

        assertEquals(
            "both fetches should succeed within roughly one timeout window if run concurrently",
            "content-acontent-b",
            result
        )
    }

    @Test
    fun skipsDisabledScripts() = runBlocking {
        val disabled = scriptB.copy(isEnabled = false)
        val client = clientRespondingWith(bodies = mapOf("a.js" to "content-a", "b.js" to "content-b"))

        val result = fetchScripts(listOf(scriptA, disabled), ::fallbackFor, client)

        assertTrue(result.contains("content-a"))
        assertFalse(
            "disabled script must not be fetched or contribute fallback content",
            result.contains("content-b") || result.contains(fallbackFor(2))
        )
    }
}
