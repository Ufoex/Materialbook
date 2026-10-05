package com.eepiemi.materialbook.utils

import androidx.annotation.RawRes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode


const val SCRIPT_SRC = "https://raw.githubusercontent.com/dh6k/Materialbook_fork/refs/heads/main/app/src/main/res/raw/"

data class Script(
    val isEnabled: Boolean,
    @param:RawRes val resourceId:  Int,
    val scriptTitle: String
)

// Debug use only: force bundled scripts so local JS edits verify on-device
// without pushing to GitHub first. Release keeps remote fetch + fallback.
const val USE_LOCAL_SCRIPTS = false

suspend fun fetchScripts(
    scripts: List<Script>,
    fallbackContent: (Int) -> String
): String {
    return HttpClient(OkHttp).use { httpClient ->
        buildString {
        // ponytail: FB is an SPA, same document lives across feed -> post -> back,
        // and MaterialbookWV re-evaluates this bundle on every Finished. Without
        // this guard each navigation stacks ~20 more MutationObservers on the
        // same document, so response gets slower the more posts you tap.
        // window-flags reset on real reload, so refresh() still reinstalls cleanly.
        append("if(!window._mbBundleInjected){window._mbBundleInjected=true;")
        scripts.filter { it.isEnabled }.forEach { script ->
            val content =
                if (USE_LOCAL_SCRIPTS) {
                    fallbackContent(script.resourceId)
                } else {
                    runCatching {
                        val res = httpClient.get(SCRIPT_SRC + script.scriptTitle)
                        if (res.status == HttpStatusCode.OK) {
                            res.body() as String
                        } else {
                            throw Exception()
                        }
                    }.getOrElse {
                        fallbackContent(script.resourceId)
                    }
                }
            append('\n')
            append(content)
        }
        append('\n')
        append("}")
        }
    }
}
