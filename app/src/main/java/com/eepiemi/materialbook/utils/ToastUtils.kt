package com.eepiemi.materialbook.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Shows a Toast safely from any thread. JS-bridge methods run on a WebView
 * background thread, so calling Toast.makeText(...).show() directly there is
 * unsafe — dispatch to the main looper instead.
 */
fun Context.showToast(text: String) {
    val ctx = this
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }
}
