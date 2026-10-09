package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R

private class Shortcut(val id: String, val label: Int, val icon: Int, val url: String)

// Long-press menu of the launcher icon. Dynamic (not shortcuts.xml) so the intent targets
// whatever package this build has (the debug build has a suffix). Launchers show the first few.
private val SHORTCUTS = listOf(
    Shortcut("messages", R.string.shortcut_messages, R.drawable.ic_shortcut_messages, "https://m.facebook.com/messages/"),
    Shortcut("search", R.string.shortcut_search, R.drawable.ic_shortcut_search, "https://m.facebook.com/search/"),
    Shortcut("marketplace", R.string.shortcut_marketplace, R.drawable.ic_shortcut_marketplace, "https://m.facebook.com/marketplace/"),
    Shortcut("reels", R.string.shortcut_reels, R.drawable.ic_shortcut_reels, "https://m.facebook.com/reel/"),
    Shortcut("notifications", R.string.shortcut_notifications, R.drawable.ic_shortcut_notifications, "https://m.facebook.com/notifications/"),
)

fun publishShortcuts(context: Context) {
    val list = SHORTCUTS.map {
        ShortcutInfoCompat.Builder(context, it.id)
            .setShortLabel(context.getString(it.label))
            .setIcon(IconCompat.createWithResource(context, it.icon))
            .setIntent(Intent(Intent.ACTION_VIEW, it.url.toUri(), context, MainActivity::class.java))
            .build()
    }
    // add (not set): the chat shortcuts for the share sheet live in the same list.
    runCatching { ShortcutManagerCompat.addDynamicShortcuts(context, list) }
}

private const val SHARE_CATEGORY = "com.eepiemi.materialbook.category.SHARE_CHAT"

/**
 * The first chats of the inbox ([json] from share_into_chat.js: [{id, name, img}]) as sharing
 * shortcuts: they appear as contacts in Android's share sheet. Picking one opens that chat
 * with the shared content typed in.
 */
fun publishChatShortcuts(context: Context, json: String) {
    val chats = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return
    kotlin.concurrent.thread {
        val shortcuts = (0 until chats.length()).mapNotNull { i ->
            val c = chats.getJSONObject(i)
            val id = c.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val avatar = runCatching {
                java.net.URL(c.optString("img")).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
            ShortcutInfoCompat.Builder(context, "chat_$id")
                .setShortLabel(c.optString("name"))
                .setLongLived(true)
                .setRank(10 + i)
                .setCategories(setOf(SHARE_CATEGORY))
                .setIcon(
                    if (avatar != null) IconCompat.createWithAdaptiveBitmap(avatar)
                    else IconCompat.createWithResource(context, R.drawable.ic_shortcut_messages)
                )
                .setIntent(
                    Intent(Intent.ACTION_VIEW, "https://www.facebook.com/messages/t/$id/".toUri(), context, MainActivity::class.java)
                )
                .build()
        }
        runCatching {
            val keep = shortcuts.map { it.id }.toSet()
            val stale = ShortcutManagerCompat.getDynamicShortcuts(context)
                .map { it.id }.filter { it.startsWith("chat_") && it !in keep }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
        }
    }
}
