package com.eepiemi.materialbook.utils

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

// Device-class check only: smallestScreenWidthDp doesn't change with rotation,
// so a phone launched sideways is no longer treated as a large screen (that
// used to get it stuck in the desktop layout).
fun isAutoDesktopScreen(smallestScreenWidthDp: Int): Boolean = smallestScreenWidthDp >= 600

/**
 * Requested orientation for the activity. Phones stay portrait while
 * browsing and may rotate (following the user's auto-rotate setting) only
 * while a video is in HTML5 fullscreen: Facebook's mobile site handles a real
 * landscape page badly (it skips the reel on rotation, and leaving fullscreen
 * in landscape leaves its viewer showing an oversized still frame instead of
 * the video, same in Chrome). Large screens (the auto-desktop ones) rotate
 * freely as before.
 */
fun appOrientation(smallestScreenWidthDp: Int, isFullscreen: Boolean): Int = when {
    isAutoDesktopScreen(smallestScreenWidthDp) -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    isFullscreen -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    else -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
}

// The page is in desktop mode if the user turned it on, or the screen is
// large enough. Computed at runtime; the automatic part is never persisted.
fun effectiveDesktop(userDesktopSetting: Boolean, isAutoDesktop: Boolean): Boolean =
    userDesktopSetting || isAutoDesktop

@Composable
fun rememberAutoDesktop(): Boolean {
    val configuration = LocalConfiguration.current
    return remember { isAutoDesktopScreen(configuration.smallestScreenWidthDp) }
}
