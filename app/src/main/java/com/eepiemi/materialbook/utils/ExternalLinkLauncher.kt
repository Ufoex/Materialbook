package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.net.URI

/** Schemes the WebView renders itself; never hand these to another app. */
private val WEBVIEW_SCHEMES = setOf("about", "data", "blob", "file", "javascript")

private val WEB_SCHEMES = setOf("http", "https")

/** Facebook's own short-link domains; they redirect to facebook.com. */
private val SHORT_HOSTS = setOf("fb.com", "www.fb.com", "fb.me", "www.fb.me")

private const val MOBILE = "https://m.facebook.com/"

// fb://<kind>/<id> -> mobile web path. Kinds the Facebook app registers; the rest open the feed.
private val DEEP_LINK_PATHS = mapOf<String, (String) -> String>(
    "profile" to { "profile.php?id=$it" },
    "user" to { "profile.php?id=$it" },
    "page" to { it },
    "group" to { "groups/$it" },
    "event" to { "events/$it" },
    "post" to { it },
    "story" to { it },
    "photo" to { "photo.php?fbid=$it" },
    "video" to { "watch/?v=$it" },
    "reel" to { "reel/$it" },
    "marketplace" to { "marketplace/" },
    "notifications" to { "notifications/" },
    "friends" to { "friends/" },
    "watch" to { "watch/" },
)

/**
 * Where a VIEW intent's data should load. fb:// and facebook:// app deep links become
 * m.facebook.com pages (the WebView can't open them); web links are kept as they are.
 */
fun intentUrl(data: String): String {
    val uri = runCatching { URI(data) }.getOrNull() ?: return data
    if (uri.scheme?.lowercase() !in setOf("fb", "facebook")) return data
    val kind = uri.host?.lowercase().orEmpty()
    val id = uri.path?.trim('/')?.substringBefore('/').orEmpty()
    val path = DEEP_LINK_PATHS[kind]
    return if (path == null || (id.isEmpty() && kind in ID_KINDS)) MOBILE else MOBILE + path(id)
}

private val ID_KINDS = setOf("profile", "user", "page", "group", "event", "post", "story", "photo", "video", "reel")

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
    return host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch" ||
        host in SHORT_HOSTS
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