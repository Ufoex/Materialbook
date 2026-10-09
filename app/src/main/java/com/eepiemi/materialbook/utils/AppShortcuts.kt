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
    runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
}
