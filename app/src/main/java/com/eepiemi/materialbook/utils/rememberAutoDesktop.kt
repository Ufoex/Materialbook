package com.eepiemi.materialbook.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

// Device-class check only: smallestScreenWidthDp doesn't change with rotation,
// so a phone launched sideways is no longer treated as a large screen (that
// used to get it stuck in the desktop layout).
fun isAutoDesktopScreen(smallestScreenWidthDp: Int): Boolean = smallestScreenWidthDp >= 600

// The page is in desktop mode if the user turned it on, or the screen is
// large enough. Computed at runtime; the automatic part is never persisted.
fun effectiveDesktop(userDesktopSetting: Boolean, isAutoDesktop: Boolean): Boolean =
    userDesktopSetting || isAutoDesktop

@Composable
fun rememberAutoDesktop(): Boolean {
    val configuration = LocalConfiguration.current
    return remember { isAutoDesktopScreen(configuration.smallestScreenWidthDp) }
}
