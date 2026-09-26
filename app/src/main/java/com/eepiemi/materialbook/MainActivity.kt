package com.eepiemi.materialbook

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.eepiemi.materialbook.ui.screens.MaterialbookWebView
import com.eepiemi.materialbook.ui.theme.MaterialbookTheme
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val TAG = "AstryxbookPiP"
private const val ACTION_PIP_TOGGLE = "com.astryx.book.PIP_TOGGLE_PLAYBACK"

class MainActivity : ComponentActivity() {

    // Shared with the composable tree below (passed explicitly rather than
    // relying on Compose's viewModel() default resolution) so onUserLeaveHint
    // can read the PiP setting without extra plumbing through the UI layer.
    private val settingsVM: SettingsViewModel by viewModels()

    // Live playback state reported by PipBridge — not settings-backed, so it
    // isn't part of SettingsViewModel; just a plain flag read at the one
    // moment it matters (onUserLeaveHint).
    @Volatile
    private var isVideoPlaying = false

    @Volatile
    private var currentAspectRatio = Rational(16, 9)

    // Last known video dimensions — persisted so reapplyPipParams() can
    // recompute the correct portrait ratio when the user changes the setting
    // while a video is already playing (no new JS bridge event fires in that case).
    @Volatile
    private var lastVideoWidth = 0
    @Volatile
    private var lastVideoHeight = 0

    // Bumped by pipActionReceiver on each Play/Pause tap from the PiP
    // overlay; observed by the composable to trigger a one-off JS call back
    // into the WebView (the reverse direction of PipBridge, which only goes
    // JS -> native).
    private var pipToggleTrigger by mutableIntStateOf(0)

    // Drives the CSS-injection focus mode (hide page chrome, make the video
    // fill the viewport) on PiP enter/exit.
    private var isInPipMode by mutableStateOf(false)

    // Bumped in onUserLeaveHint, the earliest moment we know PiP is about to
    // engage — observed by the composable to freeze the detector's "last
    // active video" tracking immediately. Without this, Facebook's own
    // controller can pause reel A and autoplay a different reel B in the gap
    // (up to ~3.5s observed) between onUserLeaveHint and
    // onPictureInPictureModeChanged, and PIP_FOCUS_MODE_JS would then lock
    // onto whichever video is "last active" *at focus-mode-run time* — by
    // then already B, not the A the aspect ratio was computed from.
    private var pipEnteringTrigger by mutableIntStateOf(0)

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) {
                Log.d(TAG, "pipActionReceiver: toggle requested")
                pipToggleTrigger++
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(ACTION_PIP_TOGGLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
        }

        setContent {
            val intentUrl = intent?.data?.toString()
            MaterialbookTheme {
                MaterialbookWebView(
                    url = intentUrl
                        ?: "https://facebook.com/",
                    settingsVM = settingsVM,
                    pipToggleTrigger = pipToggleTrigger,
                    pipEnteringTrigger = pipEnteringTrigger,
                    isInPipMode = isInPipMode,
                    onVideoPlayingChanged = { isPlaying, videoWidth, videoHeight ->
                        updateVideoPlaybackState(isPlaying, videoWidth, videoHeight)
                    }
                )
            }
        }

        // Re-push PictureInPictureParams whenever the user toggles PiP or
        // changes the portrait ratio — no app restart required.
        lifecycleScope.launch {
            settingsVM.pipEnabled.collect { reapplyPipParams() }
        }
        lifecycleScope.launch {
            settingsVM.pipPortraitRatio.collect { reapplyPipParams() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipActionReceiver)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(TAG, "onPictureInPictureModeChanged: $isInPictureInPictureMode")
        isInPipMode = isInPictureInPictureMode

        // Chromium pauses the video the instant PiP starts, but our JS
        // detector only reports state on its own schedule — without this,
        // the Play/Pause button can briefly show the wrong icon (still
        // "Pause" right when it should already say "Play"). Flip + rebuild
        // the action immediately rather than waiting for the next JS report.
        if (isInPictureInPictureMode && isVideoPlaying) {
            isVideoPlaying = false
            Log.d(TAG, "onPictureInPictureModeChanged: immediate icon flip to Play")
            setPictureInPictureParams(
                pipParamsBuilder()
                    .setAspectRatio(currentAspectRatio)
                    .setActions(listOf(buildPlayPauseAction(false)))
                    .build()
            )
        }
    }

    private fun buildPlayPauseAction(isPlaying: Boolean): RemoteAction {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val icon = Icon.createWithResource(this, iconRes)
        val intent = Intent(ACTION_PIP_TOGGLE).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = if (isPlaying) "Pause" else "Play"
        return RemoteAction(icon, label, label, pendingIntent)
    }

    private fun pipParamsBuilder(): PictureInPictureParams.Builder {
        return PictureInPictureParams.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The WebView fills the activity, so its visible bounds are the
                // appropriate source rectangle for Android 12+ PiP transitions.
                val decorView = window.decorView
                setSourceRectHint(
                    Rect(
                        0,
                        0,
                        decorView.width.coerceAtLeast(1),
                        decorView.height.coerceAtLeast(1)
                    )
                )
            }
        }
    }

    /**
     * Re-pushes [PictureInPictureParams] to the system using the current settings
     * and the last known video dimensions. Called whenever [SettingsViewModel.pipEnabled]
     * or [SettingsViewModel.pipPortraitRatio] changes so the new values take effect
     * immediately — without waiting for the next JS bridge event or an app restart.
     */
    private fun reapplyPipParams() {
        // Recompute ratio from stored dimensions + current portrait setting
        if (lastVideoWidth > 0 && lastVideoHeight > 0) {
            currentAspectRatio = settingsVM.pipRationalForVideo(
                lastVideoWidth,
                lastVideoHeight
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = isVideoPlaying && settingsVM.pipEnabled.value
            Log.d(TAG, "reapplyPipParams: autoEnter=$autoEnter, ratio=$currentAspectRatio")
            setPictureInPictureParams(
                pipParamsBuilder()
                    .setAspectRatio(currentAspectRatio)
                    .setAutoEnterEnabled(autoEnter)
                    .setActions(listOf(buildPlayPauseAction(isVideoPlaying)))
                    .build()
            )
        }
    }

    private fun updateVideoPlaybackState(
        isPlaying: Boolean,
        videoWidth: Int,
        videoHeight: Int
    ) {
        isVideoPlaying = isPlaying
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight
        if (videoWidth > 0 && videoHeight > 0) {
            currentAspectRatio = settingsVM.pipRationalForVideo(videoWidth, videoHeight)
        }
        Log.d(TAG, "updateVideoPlaybackState: isPlaying=$isPlaying, ${videoWidth}x$videoHeight, pipEnabled=${settingsVM.pipEnabled.value}, aspectRatio=$currentAspectRatio")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = isVideoPlaying && settingsVM.pipEnabled.value
            Log.d(TAG, "setPictureInPictureParams: autoEnter=$autoEnter")
            val pipParams = pipParamsBuilder()
                .setAspectRatio(currentAspectRatio)
                .setAutoEnterEnabled(autoEnter)
                .setActions(listOf(buildPlayPauseAction(isPlaying)))
                .build()
            setPictureInPictureParams(pipParams)
        }
    }

    // Called right before the user leaves via Home or the recents switcher.
    // Kept as a universal fallback across ALL API levels — not just pre-S —
    // rather than relying solely on setAutoEnterEnabled above. That API is
    // primarily documented/tested for gesture-navigation swipe transitions;
    // its behavior on a plain Home-button press, on a specific OEM skin, on a
    // specific nav mode, isn't something we can verify without a real device
    // (and OEM skins like Samsung's OneUI layer their own windowing
    // customizations on top of AOSP). A redundant explicit call here when
    // auto-enter already handled it is harmless.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val eligible = isVideoPlaying &&
            settingsVM.pipEnabled.value
        Log.d(TAG, "onUserLeaveHint: sdkInt=${Build.VERSION.SDK_INT}, isVideoPlaying=$isVideoPlaying, pipEnabled=${settingsVM.pipEnabled.value}, eligible=$eligible")
        if (eligible) {
            pipEnteringTrigger++
            try {
                enterPictureInPictureMode(
                    pipParamsBuilder()
                        .setAspectRatio(currentAspectRatio)
                        .setActions(listOf(buildPlayPauseAction(isVideoPlaying)))
                        .build()
                )
                Log.d(TAG, "enterPictureInPictureMode called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "enterPictureInPictureMode failed", e)
            }
        }
    }
}
