package com.eepiemi.materialbook.utils.jsBridge

import android.util.Log
import android.webkit.JavascriptInterface

private const val TAG = "AstryxbookPiP"

/**
 * Reports the currently active video state so the activity can decide when to
 * enter PiP and how to size the floating window.
 */
class PipBridge(
    private val onVideoStateChanged: (Boolean, Int, Int) -> Unit,
    private val onPageVisibleCallback: () -> Unit = {},
) {
    @JavascriptInterface
    fun setVideoPlaying(isPlaying: Boolean) {
        Log.d(TAG, "PipBridge.setVideoPlaying: isPlaying=$isPlaying")
        onVideoStateChanged(isPlaying, 0, 0)
    }

    @JavascriptInterface
    fun setVideoState(isPlaying: Boolean, videoWidth: Int, videoHeight: Int) {
        Log.d(TAG, "PipBridge.setVideoState: isPlaying=$isPlaying, ${videoWidth}x$videoHeight")
        onVideoStateChanged(isPlaying, videoWidth, videoHeight)
    }

    // Permanent diagnostic, not debug-only scaffolding: PIP_FOCUS_MODE_JS's
    // own after-the-fact sanity check calls this only when it finds an
    // element still visibly on-screen after hiding everything it knows to
    // hide. Silent/free in the normal case; when it does fire, it gives the
    // exact tag/id/class of the leaking element from an ordinary field
    // logcat capture, no live DevTools session required - useful for
    // intermittent, hard-to-reproduce leaks (page chrome, a stray control)
    // that vary by which video/page layout Facebook happens to render.
    @JavascriptInterface
    fun logPipAnomaly(message: String) {
        Log.w(TAG, "PiP focus mode: unexpected visible element(s): $message")
    }

    // Called by PIP_HANDOFF_ARM_JS when the page becomes visible again while
    // a lock-screen audio handoff is active (the user unlocked): one of the
    // handback signals. Runs on the JS bridge thread, not the main thread.
    @JavascriptInterface
    fun onPageVisible() {
        Log.d(TAG, "PipBridge.onPageVisible")
        onPageVisibleCallback()
    }
}
