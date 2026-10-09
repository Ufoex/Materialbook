package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.net.toUri

/** What is being shared into the app (from another app, or from Facebook's own share sheet). */
data class SharedContent(val text: String?, val files: List<Uri>, val threadId: String? = null)

object ShareBox {
    /** Just arrived: waiting for the user to pick where it goes. */
    var pending by mutableStateOf<SharedContent?>(null)

    /** Being typed into a chat in the Messages layer (done as soon as that chat is open). */
    var inChat by mutableStateOf<SharedContent?>(null)
}

/** The content of an ACTION_SEND / SEND_MULTIPLE intent, or null for anything else. */
fun sharedContentFrom(intent: Intent?): SharedContent? {
    if (intent == null) return null
    val files = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> return null
    }
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    if (text == null && files.isEmpty()) return null
    // A direct-share target (a chat in the share sheet): its shortcut id is "chat_<thread id>".
    val thread = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)?.removePrefix("chat_")?.takeIf { it.all(Char::isDigit) }
    return SharedContent(text, files, thread)
}

/** The link in Facebook's "Send as message" (intent://share/?link=...#Intent;scheme=fb-messenger...). */
fun shareLinkOf(url: String): String? {
    if (!url.startsWith("intent://share/")) return null
    return runCatching { url.substringAfter("?").substringBefore("#").split("&").first { it.startsWith("link=") } }
        .getOrNull()?.removePrefix("link=")?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
}

/** The Messenger app's own contact picker, with this content. False when it is not installed. */
fun sendWithMessengerApp(context: Context, content: SharedContent, packageName: String): Boolean {
    val pkg = packageName.ifBlank { DEFAULT_MESSENGER_PACKAGE }
    if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return false
    val intent = Intent(if (content.files.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
        setPackage(pkg)
        content.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        when (content.files.size) {
            0 -> type = "text/plain"
            1 -> { type = context.contentResolver.getType(content.files[0]) ?: "*/*"; putExtra(Intent.EXTRA_STREAM, content.files[0]) }
            else -> { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(content.files)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** A link inside [text] (the part to hand to Facebook's own share page), if there is one. */
fun linkIn(text: String?): String? =
    text?.let { Regex("https?://\\S+").find(it)?.value }?.takeIf { runCatching { it.toUri().host != null }.getOrDefault(false) }
