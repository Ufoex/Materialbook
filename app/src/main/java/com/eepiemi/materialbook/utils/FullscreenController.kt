package com.eepiemi.materialbook.utils

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback

/**
 * State for HTML5 fullscreen (element.requestFullscreen()), which Android
 * WebView routes through WebChromeClient.onShowCustomView/onHideCustomView.
 * Kept free of Activity/window code so the show/hide contract is unit
 * testable; the [Host] does the actual attaching and system-bar handling.
 */
class FullscreenController(private val host: Host) {

    interface Host {
        fun attach(view: View)
        fun detach(view: View)
    }

    private var view: View? = null
    private var callback: CustomViewCallback? = null

    val isFullscreen: Boolean get() = view != null

    /**
     * Returns false if a custom view is already showing; per the
     * onShowCustomView contract the new one is then dismissed immediately.
     */
    fun show(view: View, callback: CustomViewCallback): Boolean {
        if (this.view != null) {
            callback.onCustomViewHidden()
            return false
        }
        this.view = view
        this.callback = callback
        host.attach(view)
        return true
    }

    /**
     * Leaves fullscreen. Safe to call from Back, from onHideCustomView (which
     * WebView fires after we tell it the view is hidden), and on disposal:
     * whichever comes first does the work, the rest are no-ops, and the
     * WebView callback runs exactly once.
     */
    fun hide(): Boolean {
        val shown = view ?: return false
        val cb = callback
        view = null
        callback = null
        host.detach(shown)
        cb?.onCustomViewHidden()
        return true
    }
}
