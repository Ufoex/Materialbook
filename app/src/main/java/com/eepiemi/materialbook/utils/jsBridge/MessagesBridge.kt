package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.JavascriptInterface

/**
 * Called by messages_layer.js when the desktop site in the Messages layer navigates
 * inside the page (pushState / popstate) to somewhere outside Messages: WebView never
 * reports those as page loads, so the request interceptor can't see them.
 */
class MessagesBridge(private val onLeftMessages: (String) -> Unit) {
    @JavascriptInterface
    fun onLeftMessages(url: String) = onLeftMessages.invoke(url)
}
