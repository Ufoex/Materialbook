package com.eepiemi.materialbook.utils

import android.content.res.Resources
import com.eepiemi.materialbook.R
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

/**
 * Applies a curated subset of Brave's adblock lists at the network layer.
 *
 * Combines with the existing JS-based [adblock.js] instead of replacing it:
 * adblock.js hides sponsored DOM nodes, this rejects the underlying
 * ad/tracker requests before they load. It only ever *rejects more*, and
 * never rejects the main Facebook navigation, so existing features stay intact.
 *
 * ponytail: parses network rules + `@@` exceptions only. Cosmetic (`##`),
 * CSS (`#$#`), scriptlet (`#%#`) and regex rules are skipped — none apply to
 * a Facebook-only wrapper, and skipping them keeps the parser tiny and safe.
 */
object BraveBlockList {

    private val httpClient by lazy { HttpClient(OkHttp) }

    @Volatile private var blocked: List<Rule> = emptyList()
    @Volatile private var allowed: List<Rule> = emptyList()
    @Volatile private var enabled = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isLoaded(): Boolean = blocked.isNotEmpty() || allowed.isNotEmpty()

    /** Synchronous bootstrap from the bundled resource so blocking works on first load. */
    fun loadInitial(resources: Resources) {
        if (isLoaded()) return
        runCatching { parse(readRaw(resources, R.raw.brave_blocklist)) }
            .onSuccess { (b, a) -> blocked = b; allowed = a }
    }

    /** Async refresh from the remote copy, falling back to the bundled resource. */
    suspend fun refresh(resources: Resources) {
        val text = runCatching {
            val res = httpClient.get(SCRIPT_SRC + "brave_blocklist.txt")
            if (res.status == HttpStatusCode.OK) res.body() as String else throw Exception()
        }.getOrElse { readRaw(resources, R.raw.brave_blocklist) }
        runCatching { parse(text) }.onSuccess { (b, a) -> blocked = b; allowed = a }
    }

    /** Test-only entry point that avoids needing a [Resources] instance. */
    internal fun loadFromText(text: String) {
        val (b, a) = parse(text)
        blocked = b
        allowed = a
    }

    fun shouldBlock(url: String, isMainFrame: Boolean): Boolean {
        if (!enabled || !isLoaded()) return false
        val host = hostOf(url)
        val path = pathOf(url)
        // ponytail: never risk breaking the app's own document load.
        if (isMainFrame && isFacebookish(host)) return false
        if (allowed.any { it.matches(host, path) }) return false
        return blocked.any { rule ->
            if (!rule.matches(host, path)) false
            // Whole-domain blocks of first-party Facebook hosts are skipped: a
            // bundled/remote list could otherwise nuke facebook.com assets.
            else if (rule.pathPrefix == null && isFacebookish(host)) false
            else true
        }
    }

    private fun readRaw(resources: Resources, resId: Int): String =
        resources.openRawResource(resId).bufferedReader().use { it.readText() }

    private data class Rule(val host: String, val pathPrefix: String?)

    private fun Rule.matches(host: String, path: String): Boolean {
        if (host != this.host && !host.endsWith("." + this.host)) return false
        if (pathPrefix == null) return true
        return path.startsWith(pathPrefix)
    }

    private fun parse(text: String): Pair<List<Rule>, List<Rule>> {
        val blocked = mutableListOf<Rule>()
        val allowed = mutableListOf<Rule>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            // Skip cosmetic / scriptlet / CSS-injection rules.
            if (line.startsWith("#") || line.contains("##")) return@forEach

            val isException = line.startsWith("@@")
            val body = if (isException) line.removePrefix("@@") else line

            // Ignore site-specific rules ($domain=...); this app is Facebook-only
            // and applying another site's allow/deny here would misfire.
            val optStart = body.indexOf('$')
            if (optStart >= 0) {
                if (body.substring(optStart + 1).contains("domain")) return@forEach
                val pattern = body.substring(0, optStart)
                buildRule(pattern)?.let { (if (isException) allowed else blocked).add(it) }
                return@forEach
            }
            buildRule(body)?.let { (if (isException) allowed else blocked).add(it) }
        }
        return blocked to allowed
    }

    private fun buildRule(pattern: String): Rule? {
        // Inline regex rules (e.g. /ads/) can't be matched by host; skip.
        if (pattern.startsWith("/") && pattern.endsWith("/")) return null

        var p = pattern
        if (p.startsWith("||")) {
            p = p.removePrefix("||")
            val cut = p.indexOfFirst { it == '^' }
            if (cut >= 0) p = p.substring(0, cut)
        } else if (p.startsWith("|")) {
            p = p.removePrefix("|")
            p = p.removePrefix("https://").removePrefix("http://").removePrefix("//")
        } else if (p.startsWith("*.")) {
            p = p.removePrefix("*.")
        }

        val slash = p.indexOf('/')
        val host = if (slash >= 0) p.substring(0, slash) else p
        val pathPrefix = if (slash >= 0) p.substring(slash) else null
        if (host.isEmpty()) return null
        return Rule(host.trimEnd('^', '*', '.'), pathPrefix)
    }

    private fun hostOf(url: String): String {
        val rest = if (url.contains("://")) url.substringAfter("://") else url
        val authority = if (rest.contains("/")) rest.substringBefore("/") else rest
        val host = if (authority.contains("@")) authority.substringAfter("@") else authority
        return if (host.contains(":")) host.substringBefore(":") else host
    }

    private fun pathOf(url: String): String {
        val rest = if (url.contains("://")) url.substringAfter("://") else url
        return if (rest.contains("/")) "/" + rest.substringAfter("/") else "/"
    }

    private fun isFacebookish(host: String): Boolean =
        host.endsWith("facebook.com") || host.endsWith("messenger.com") ||
            host.endsWith("fbcdn.net") || host.endsWith("fbsbx.com") ||
            host.endsWith("facebook.net") || host.endsWith("instagram.com") ||
            host.endsWith("whatsapp.net") || host.endsWith("oculus.com")
}
