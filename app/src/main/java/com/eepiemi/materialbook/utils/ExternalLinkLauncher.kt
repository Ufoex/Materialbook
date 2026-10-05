package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.net.URI

/** Schemes the WebView renders itself; never hand these to another app. */
private val WEBVIEW_SCHEMES = setOf("about", "data", "blob", "file", "javascript")

private val WEB_SCHEMES = setOf("http", "https")

private fun schemeOf(url: String): String? =
    runCatching { URI(url).scheme?.lowercase() }.getOrNull()

/**
 * True when the WebView should keep the URL to itself. False means "leave the app":
 * either an app deep link (fb-messenger:, market:, intent:, tel:) or a site that isn't
 * Facebook.
 */
fun isWebViewRenderable(url: String): Boolean {
    val scheme = schemeOf(url) ?: return false
    if (scheme in WEBVIEW_SCHEMES) return true
    if (scheme !in WEB_SCHEMES) return false
    // Facebook proper, any subdomain, plus fb.watch short links. The l./lm.facebook.com
    // redirectors are excluded on purpose: /l.php only ever points somewhere else, and
    // they are *subdomains* of facebook.com, so a plain suffix test would keep them here.
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
    if (host == "l.facebook.com" || host == "lm.facebook.com") return false
    return host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch"
}

/**
 * Hands a URL to whatever the user set as default browser/app (Brave, Chrome, ...).
 * Returns false when nothing on the device can open it.
 */
fun openExternalUrl(context: Context, url: String): Boolean {
    val intent = if (url.startsWith("intent:", ignoreCase = true)) {
        // intent://...#Intent;scheme=…;package=…;end — ACTION_VIEW on the raw string
        // has no handler and throws.
        runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return false
    } else {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        Intent(Intent.ACTION_VIEW, uri)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}