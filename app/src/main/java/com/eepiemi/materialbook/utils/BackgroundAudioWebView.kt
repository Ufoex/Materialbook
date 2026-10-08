package com.eepiemi.materialbook.utils

import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * A WebView that can pretend its window never hides. Chromium pauses media when the window is
 * hidden (screen off), so while [keepAlive] is set the hidden notification is swallowed and the
 * reel keeps playing. The reel viewer's audio button sets it through [Bridge].
 */
class BackgroundAudioWebView(context: Context) : WebView(context) {
    @Volatile var keepAlive = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepAlive) View.VISIBLE else visibility)
    }

    inner class Bridge {
        @JavascriptInterface
        fun set(on: Boolean) {
            keepAlive = on
        }
    }
}
