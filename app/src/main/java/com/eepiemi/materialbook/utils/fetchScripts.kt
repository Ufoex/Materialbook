package com.eepiemi.materialbook.utils

import androidx.annotation.RawRes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode


const val SCRIPT_SRC = "https://raw.githubusercontent.com/ofirc73/AstryxBook/refs/heads/main/app/src/main/res/raw/"

// Fetches were previously unbounded (no timeout at all - success or
// exception, whichever came first, however long that took) and strictly
// sequential (one script's GET awaited before the next started). On a cold
// network - e.g. right after an app cache clear forces every fetch back onto
// the network instead of reusing a cached response - this could leave
// userScripts (including pip_video_detector.js, the PiP active-video
// tracker) not finished loading for several seconds, during which PiP focus
// mode has no reliable data to work with. Bounding each fetch and running
// them concurrently turns that into a small, predictable worst case: falling
// straight back to the bundled resource, same as any other fetch failure
// already did.
internal const val FETCH_TIMEOUT_MS = 1000L

data class Script(
    val isEnabled: Boolean,
    @param:RawRes val resourceId:  Int,
    val scriptTitle: String
)

// TEMP DIAGNOSTIC REVERT: back to the original sequential, unbounded fetch —
// testing whether the timeout+concurrency change itself (not the timing gap
// it was meant to fix) is what's producing a garbled non-PiP reel layout in
// production. If this fixes the layout, the fix needs a different approach
// (later network availability without a hard timeout cutting scripts off
// mid-hydration); if it doesn't, this reverts back to the timeout+concurrent
// version. NOTE: with this reverted, FetchScriptsTest's
// fallsBackToBundled_whenFetchExceedsTimeout will fail — expected/temporary,
// there's no timeout to trigger it right now.
suspend fun fetchScripts(
    scripts: List<Script>,
    fallbackContent: (Int) -> String,
    httpClient: HttpClient = HttpClient(OkHttp)
): String {
    return scripts.filter { it.isEnabled }.joinToString("") { script ->
        runCatching {
            val res = httpClient.get(SCRIPT_SRC + script.scriptTitle)
            if (res.status == HttpStatusCode.OK) {
                res.body() as String
            } else {
                throw Exception()
            }
        }.getOrElse { fallbackContent(script.resourceId) }
    }
}
