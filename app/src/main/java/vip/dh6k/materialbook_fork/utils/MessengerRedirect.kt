package vip.dh6k.materialbook_fork.utils

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
