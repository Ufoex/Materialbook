package com.eepiemi.materialbook

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eepiemi.materialbook.utils.Release
import com.eepiemi.materialbook.utils.intentUrl
import com.eepiemi.materialbook.utils.AppVisibility
import com.eepiemi.materialbook.utils.ShareBox
import com.eepiemi.materialbook.utils.sharedContentFrom
import com.eepiemi.materialbook.utils.publishShortcuts
import com.eepiemi.materialbook.utils.scheduleNotificationPoll
import com.eepiemi.materialbook.utils.UpdateDialog
import com.eepiemi.materialbook.utils.checkForUpdate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eepiemi.materialbook.audio.LockScreenAudioService
import com.eepiemi.materialbook.audio.PipHandback
import com.eepiemi.materialbook.audio.isHandoffEligible
import com.eepiemi.materialbook.audio.parseHandoffRead
import com.eepiemi.materialbook.ui.screens.MaterialbookWebView
import com.eepiemi.materialbook.utils.appOrientation
import com.eepiemi.materialbook.ui.theme.MaterialbookTheme
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

private const val TAG = "AstryxbookPiP"
private const val ACTION_PIP_TOGGLE = "com.astryx.book.PIP_TOGGLE_PLAYBACK"

// Cap on PIP_HANDOFF_READ_JS answering after screen-off (it took 5-30ms in
// the device spike); the foreground grace period leaves no room to wait.
private const val HANDOFF_READ_TIMEOUT_MS = 300L

class MainActivity : ComponentActivity() {

    // Shared with the composable tree below (passed explicitly rather than
    // relying on Compose's viewModel() default resolution) so onUserLeaveHint
    // can read the PiP setting without extra plumbing through the UI layer.
    private val settingsVM: SettingsViewModel by viewModels()

    // VIEW intents delivered to an already-running instance (singleTop) update this.
    private val urlState = mutableStateOf<String?>(null)
    private val urlNonce = mutableIntStateOf(0)

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

    // ---- Lock-screen audio (see LockScreenAudioService) ----
    // While in PiP with the setting on, locking the screen hands the video's
    // audio from the WebView to a native player; coming back hands it back.

    // Bumped at screen-off to run PIP_HANDOFF_READ_JS; the result comes back
    // through onPipHandoffRead.
    private var pipHandoffReadTrigger by mutableIntStateOf(0)

    // Latest handback request for the composable to run (pipHandbackJs).
    private var pipHandback by mutableStateOf<PipHandback?>(null)
    private var pipHandbackSeq = 0

    // Bound at PiP entry (option B: binding creates the service idle, not in
    // the foreground), used at screen-off to start playback and at handback
    // to read the position.
    private var audioController: ListenableFuture<MediaController>? = null

    // Single guard so screen-off never double-starts a handoff and handback
    // never runs twice.
    private var handoffActive = false
    private var handoffReadPending = false
    private var handoffStartMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val handoffReadTimeout = Runnable {
        if (handoffReadPending) {
            handoffReadPending = false
            Log.w(TAG, "handoff: page read timed out after ${HANDOFF_READ_TIMEOUT_MS}ms, aborting")
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            // Observed order on the device spike (SM-A566B, Android 16): the
            // page gets visibilitychange -> hidden first, Facebook pauses the
            // video 25-60ms later, then SCREEN_OFF arrives ~0.5s after that,
            // still in PiP (PiP stays open across lock and unlock), so the
            // live isInPipMode check here is reliable.
            Log.d(TAG, "handoff: SCREEN_OFF inPip=$isInPipMode enabled=${lockscreenAudioEnabled()} active=$handoffActive pending=$handoffReadPending")
            if (!isInPipMode || !lockscreenAudioEnabled() || handoffActive || handoffReadPending) return
            handoffReadPending = true
            pipHandoffReadTrigger++
            mainHandler.postDelayed(handoffReadTimeout, HANDOFF_READ_TIMEOUT_MS)
        }
    }

    private fun lockscreenAudioEnabled(): Boolean =
        settingsVM.pipEnabled.value && settingsVM.pipLockscreenAudio.value

    private fun connectAudioController(): ListenableFuture<MediaController> =
        audioController ?: MediaController.Builder(
            this, SessionToken(this, ComponentName(this, LockScreenAudioService::class.java))
        ).buildAsync().also { audioController = it }

    private fun releaseAudio() {
        audioController?.let { MediaController.releaseFuture(it) }
        audioController = null
        stopService(Intent(this, LockScreenAudioService::class.java))
    }

    private fun onPipHandoffRead(raw: String?) {
        if (!handoffReadPending) {
            // Timed out already; still undo the mute the read applied.
            Log.w(TAG, "handoff: late page read ignored, restoring WebView video")
            requestHandback(positionMs = null, play = false)
            return
        }
        handoffReadPending = false
        mainHandler.removeCallbacks(handoffReadTimeout)

        val read = parseHandoffRead(raw)
        if (read == null || !isHandoffEligible(read.src, read.wasPlaying)) {
            Log.d(TAG, "handoff: not eligible (src=${read?.src?.take(40)}, wasPlaying=${read?.wasPlaying}), aborting")
            requestHandback(positionMs = null, play = false)
            return
        }

        handoffActive = true
        handoffStartMs = read.positionMs
        val item = MediaItem.Builder()
            .setUri(read.src)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(read.src.toUri()).build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.lockscreen_audio_media_title))
                    .build()
            )
            .build()
        val future = connectAudioController()
        // Nothing slow before play(): the system only allows the service's
        // promotion to the foreground for a short grace period after the PiP
        // window stops being visible (~0.6s after screen-off in the spike).
        future.addListener({
            try {
                val controller = future.get()
                // Start position goes in with the item: a separate seekTo()
                // was applied before the item was set and then reset to 0
                // (seen on device: playback always started from 0:00).
                controller.setMediaItem(item, read.positionMs)
                controller.prepare()
                controller.play()
                Log.d(TAG, "handoff: native playback started at ${read.positionMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "handoff: starting native playback failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Ends an active handoff. Called from whichever signal comes first; the
     * others are no-ops. ACTION_USER_PRESENT isn't used: the system sent it
     * on unlock in the spike, but this receiver never got it.
     */
    private fun handBack(signal: String) {
        if (!handoffActive) return
        handoffActive = false
        val controller = audioController
            ?.takeIf { it.isDone && !it.isCancelled }
            ?.let { runCatching { it.get() }.getOrNull() }
            ?.takeIf { it.isConnected }
        // After a player error (e.g. an expired URL) the player is stopped
        // but keeps its last position, so this is still the right place to
        // resume from.
        val positionMs = controller?.currentPosition ?: handoffStartMs
        // Paused from the lock-screen controls (or stopped by an error):
        // hand back without resuming.
        val play = controller?.playWhenReady == true &&
            (controller.playbackState == Player.STATE_READY ||
                controller.playbackState == Player.STATE_BUFFERING)
        Log.d(TAG, "handback: signal=$signal positionMs=$positionMs play=$play")
        controller?.stop()
        // An empty timeline makes Media3 drop the notification and call
        // stopForeground right away. Without this the service stays in the
        // foreground (Media3 keeps it there for up to 10 minutes after
        // playback stops) because the reconnect below keeps it bound and
        // alive, so stopService alone never destroys it.
        controller?.clearMediaItems()
        releaseAudio()
        requestHandback(positionMs, play)
        // Still in PiP: get ready for the next lock.
        if (isInPipMode && lockscreenAudioEnabled()) connectAudioController()
    }

    private fun requestHandback(positionMs: Long?, play: Boolean) {
        pipHandback = PipHandback(
            seq = ++pipHandbackSeq,
            positionMs = positionMs,
            play = play,
            rearm = isInPipMode && lockscreenAudioEnabled(),
        )
    }
    // ---- end lock-screen audio ----

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        publishShortcuts(this)
        scheduleNotificationPoll(this, settingsVM.notifyPoll.value)
        askNotificationsOnce()

        // Phones browse in portrait; only HTML5 fullscreen video rotates (see
        // appOrientation and the fullscreen host in MaterialbookWebView).
        requestedOrientation =
            appOrientation(resources.configuration.smallestScreenWidthDp, isFullscreen = false)

        // Unconditional on purpose (not gated behind BuildConfig.DEBUG): keeps
        // chrome://inspect available on release-type builds for field
        // debugging. Most PiP fixes in this fork were found through live
        // DevTools sessions against the installed release build. Not
        // leftover scaffolding; don't remove it in a cleanup.
        WebView.setWebContentsDebuggingEnabled(true)

        val filter = IntentFilter(ACTION_PIP_TOGGLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
        }
        // SCREEN_OFF can only be received by a context-registered receiver.
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        urlState.value = intent?.data?.toString()?.let(::intentUrl)
        receiveShare(intent)

        setContent {
            val intentUrl = urlState.value
            var update by remember { mutableStateOf<Release?>(null) }
            LaunchedEffect(Unit) {
                if (settingsVM.autoUpdateCheck.value) update = checkForUpdate()
            }
            MaterialbookTheme {
                update?.let { UpdateDialog(it) { update = null } }
                MaterialbookWebView(
                    url = intentUrl
                        ?: "https://facebook.com/",
                    urlNonce = urlNonce.intValue,
                    settingsVM = settingsVM,
                    pipToggleTrigger = pipToggleTrigger,
                    pipEnteringTrigger = pipEnteringTrigger,
                    isInPipMode = isInPipMode,
                    pipHandoffReadTrigger = pipHandoffReadTrigger,
                    pipHandback = pipHandback,
                    onPipHandoffRead = ::onPipHandoffRead,
                    onPipPageVisible = { runOnUiThread { handBack("page visible") } },
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

    // Something shared to the app (a link, text, pictures): the page host asks what to do with it.
    private fun receiveShare(intent: Intent?) {
        val content = sharedContentFrom(intent) ?: return
        if (content.threadId != null) ShareBox.inChat = content else ShareBox.pending = content
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Android 13+: ask for notifications the first time the app opens. Later changes are in
    // Settings (which opens the system notification settings).
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            if (settingsVM.takeFirstNotificationAsk()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handback signals besides the page's own visibilitychange: the activity
    // becoming visible again (unlock while in PiP, or back to full screen).
    override fun onStart() {
        super.onStart()
        AppVisibility.background = false
        handBack("onStart")
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.background = true
    }

    override fun onResume() {
        super.onResume()
        handBack("onResume")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipActionReceiver)
        unregisterReceiver(screenOffReceiver)
        mainHandler.removeCallbacks(handoffReadTimeout)
        handoffActive = false
        releaseAudio()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(TAG, "onPictureInPictureModeChanged: $isInPictureInPictureMode")
        isInPipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            if (lockscreenAudioEnabled()) connectAudioController()
        } else if (handoffActive) {
            handBack("PiP exit")
        } else {
            releaseAudio()
        }

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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
        intent.data?.toString()?.let {
            urlState.value = intentUrl(it)
            urlNonce.intValue++
        }
    }

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
