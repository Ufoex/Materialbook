package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

const val DEFAULT_MESSENGER_PACKAGE = "com.facebook.orca"

/** True for URLs the Messenger app handles: deep links, short links, message threads. */
fun isMessengerUrl(url: String): Boolean {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return false
    when (uri.scheme?.lowercase()) {
        "fb-messenger", "fb-messenger-share" -> return true
        "intent" -> {
            // e.g. intent://...#Intent;package=com.facebook.orca;... from FB web buttons
            val pkg = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME).`package` }
                .getOrNull() ?: return false
            return ("orca" in pkg || "messenger" in pkg || "mlite" in pkg)
        }
        "http", "https" -> { /* host check below */ }
        else -> return false
    }
    val host = (uri.host ?: "").lowercase()
    if (host == "m.me" || host.endsWith(".m.me")) return true
    if (host == "messenger.com" || host.endsWith(".messenger.com")) return true
    if ("facebook" in host && (uri.path ?: "").startsWith("/messages")) return true
    return false
}

/**
 * Opens the Messenger app ([packageName], default com.facebook.orca).
 * Prefers the launcher entry (no URL resolution, no interstitial flash).
 * Falls back to plain VIEW then targeted deep link. False = app not installed.
 */
fun openMessenger(context: Context, url: String, packageName: String): Boolean {
    val pkg = packageName.ifBlank { DEFAULT_MESSENGER_PACKAGE }
    // Launcher entry first: no URL resolution, so WebView never flashes the
    // download interstitial while the app opens. (Verified on-device: stays open.)
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
    if (launch != null && runCatching { context.startActivity(launch) }.isSuccess) return true
    // fb-messenger:// deep links resolve but Messenger drops them instantly (verified
    // on-device: IntentHandlerActivity flashes then closes); m.me stays open, so use it.
    val stableUrl = if (url.startsWith("fb-messenger", ignoreCase = true)) "https://m.me/" else url
    // No NEW_TASK guesswork here: openExternalUrl owns intent:// parsing + flags, and
    // package pinning last would re-break the working launcher path above.
    if (openExternalUrl(context, stableUrl)) return true
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, stableUrl.toUri()).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.isSuccess
}

const val MESSAGES_DESKTOP_URL = "https://www.facebook.com/messages/"

private fun facebookHost(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
    val isFb = host == "facebook.com" || host.endsWith(".facebook.com")
    return isFb && !host.startsWith("l.") && !host.startsWith("lm.")
}

/** facebook.com/messages* web page (the one Facebook's mobile site refuses to render). */
fun isMessagesWebUrl(url: String): Boolean {
    if (!facebookHost(url)) return false
    return (runCatching { java.net.URI(url).path }.getOrNull() ?: "").startsWith("/messages")
}

/**
 * True when [url] is a regular Facebook page outside the messages section,
 * i.e. where the desktop-mode override should end. Login/checkpoint pages are
 * excluded because the desktop messages page may bounce through them.
 */
fun isLeavingMessages(url: String): Boolean {
    if (!facebookHost(url)) return false
    val path = runCatching { java.net.URI(url).path }.getOrNull() ?: ""
    return listOf("/messages", "/messenger", "/login", "/checkpoint").none { path.startsWith(it) }
}

/** The desktop Messages page (the one loaded with the desktop user agent). */
fun isDesktopMessagesUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    return uri.host?.lowercase() == "www.facebook.com" && (uri.path ?: "").startsWith("/messages")
}

private val THREAD_PATH = Regex("^/messages/(?:e2ee/)?t/[^/]+")

/**
 * Desktop equivalent of any Messages/Messenger link, so deep links keep their conversation:
 * `facebook.com/messages/t/<id>`, `m.me/<name>`, `messenger.com/t/<id>` and
 * `fb-messenger://user/<id>` open that thread; anything else opens the inbox.
 */
fun messagesDesktopUrl(url: String): String {
    val inbox = MESSAGES_DESKTOP_URL
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return inbox
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase() ?: ""
    val path = uri.path ?: ""
    fun thread(id: String) = if (id.isBlank()) inbox else "https://www.facebook.com/messages/t/$id"
    return when {
        scheme == "fb-messenger" && (host == "user" || host == "threads") ->
            thread(path.trim('/').substringBefore('/'))
        scheme != "http" && scheme != "https" -> inbox
        host == "m.me" || host.endsWith(".m.me") -> {
            val first = path.trim('/').substringBefore('/')
            // m.me/j/<code> is a group invite: no desktop equivalent
            if (first == "j") inbox else thread(first)
        }
        host == "messenger.com" || host.endsWith(".messenger.com") ->
            if (path.startsWith("/t/")) thread(path.removePrefix("/t/").substringBefore('/')) else inbox
        else -> THREAD_PATH.find(path)?.let { "https://www.facebook.com" + it.value } ?: inbox
    }
}
