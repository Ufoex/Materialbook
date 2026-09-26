package com.eepiemi.materialbook.ui.screens

import android.content.Intent
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberSaveableWebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.components.NetworkErrorDialog
import com.eepiemi.materialbook.ui.components.settings.SettingsDialog
import com.eepiemi.materialbook.ui.viewmodel.MainViewModel
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.DESKTOP_USER_AGENT
import com.eepiemi.materialbook.utils.ExternalRequestInterceptor
import com.eepiemi.materialbook.utils.fileChooserWebViewParams
import com.eepiemi.materialbook.utils.jsBridge.ClipboardBridge
import com.eepiemi.materialbook.utils.jsBridge.DownloadBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialbookSettings
import com.eepiemi.materialbook.utils.jsBridge.ThemeChange
import com.eepiemi.materialbook.utils.jsBridge.MaterialYouBridge
import com.eepiemi.materialbook.utils.jsBridge.PipBridge
import com.eepiemi.materialbook.utils.rememberAutoDesktop
import com.eepiemi.materialbook.utils.rememberImeHeight
import kotlinx.coroutines.delay

internal const val PIP_TOGGLE_JS = """
(function() {
  // Reuse whatever focus mode already locked in as THE pip video, instead of
  // re-deriving "the active video" independently. Re-deriving it here is what
  // caused the toggle to sometimes resume a different reel than the one
  // actually shown in the PiP window (e.g. when focus mode's hide-everything
  // -else CSS didn't fully collapse some other video's layout box).
  var best = document.querySelector('[data-astryx-pip-video]');
  if (!best) {
    best = window.__astryxLastActiveVideo;
    if (best && !document.documentElement.contains(best)) best = null;
  }
  if (!best) {
    // Last resort: focus mode never ran (e.g. page wasn't Finished loading
    // yet) - fall back to the old by-area heuristic.
    var videos = document.querySelectorAll('video');
    var bestArea = 0;
    for (var i = 0; i < videos.length; i++) {
      var r = videos[i].getBoundingClientRect();
      var area = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0)) *
                 Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      if (area > bestArea) { bestArea = area; best = videos[i]; }
    }
  }
  if (!best) return;

  // Reverted: dispatching a synthetic pointer/click sequence was tried here to
  // get Facebook's own handler to toggle playback in sync with its internal
  // state (see history), but it did nothing at all - Facebook's player almost
  // certainly checks event.isTrusted and ignores non-trusted synthetic events,
  // so no play()/pause() ever fired. Back to calling the media element
  // directly, which does work (mediaPlaybackRequiresUserGesture is disabled),
  // even though Facebook's own logic re-pauses it shortly after - see
  // PIP_FOCUS_MODE_JS's debug hook / logDebug output for that separate issue.
  if (best.paused) { best.play(); } else { best.pause(); }
})();
"""

// Hides everything on the page except the currently-playing video and its
// ancestor chain, forcing the video to fill the viewport — makes PiP show
// just the video, like a native player, instead of the whole shrunk page.
// Fragile by nature: fights Facebook's own page structure. Known risk: if
// any ancestor of the video uses CSS transform/filter/contain, it creates a
// new containing block and position:fixed on the video won't actually reach
// the real viewport edges — not something fixable from our side if so.
internal const val PIP_FOCUS_MODE_JS = """
(function() {
  // Android may pause the active video before PiP focus mode runs. Prefer the
  // detector's last real active video so a paused, larger reel cannot replace it.
  var best = window.__astryxLastActiveVideo;
  if (!best || !document.documentElement.contains(best)) {
    var videos = document.querySelectorAll('video');
    var bestArea = 0;
    best = null;
    for (var i = 0; i < videos.length; i++) {
      var r = videos[i].getBoundingClientRect();
      var area = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0)) *
                 Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      if (area > bestArea) { bestArea = area; best = videos[i]; }
    }
  }
  if (!best) return;

  var el = best;
  while (el && el !== document.body) {
    el.setAttribute('data-astryx-pip-keep', 'true');
    el = el.parentElement;
  }
  best.setAttribute('data-astryx-pip-video', 'true');
  document.body.setAttribute('data-astryx-pip-active', 'true');

  // download_content.js's own download button (#materialbook-global-downloader)
  // is a direct child of <body>, so our "hide everything not kept" stylesheet
  // rule below does target it - but its own stylesheet uses an ID+class
  // selector (#materialbook-global-downloader.visible { display:flex
  // !important }), which beats our tag+attribute selector on specificity
  // regardless of !important or insertion order. Skip the specificity fight:
  // set an inline override directly, which always wins over any stylesheet
  // rule. Only relevant in PiP - it's a legitimate, wanted control in the
  // normal in-app view, just useless/obstructive squeezed into a small PiP
  // window.
  var dlBtn = document.getElementById('materialbook-global-downloader');
  if (dlBtn) dlBtn.style.setProperty('display', 'none', 'important');

  // copy_to_clipboard.js's own copy-to-clipboard button
  // (#materialbook-clipboard-copier) - same specificity-beating problem, same
  // fix. This one's meant for photos/stories, not video reels, but its own
  // detection heuristics can false-positive inside a reel (seen intermittently
  // on landscape reels) - possibly triggered by our own focus-mode DOM churn
  // itself, since its MutationObserver reacts to the same attribute/class
  // changes we're making. Hiding it here doesn't depend on understanding why
  // it misfires, just makes sure it's never visible in the PiP window
  // regardless of what its own visibility logic decides.
  var cpBtn = document.getElementById('materialbook-clipboard-copier');
  if (cpBtn) cpBtn.style.setProperty('display', 'none', 'important');

  if (!document.getElementById('astryx-pip-style')) {
    var style = document.createElement('style');
    style.id = 'astryx-pip-style';
    // Root cause found via live DevTools breakpoint: Facebook's reels feed has
    // a controller that owns "which single reel is active" and force-pauses
    // any other playing video with reason "controller_pause_requested" -
    // unrelated to CSS visibility, Page Visibility, or event trust (all ruled
    // out). The controller most likely tracks "active reel" via scroll
    // position/intersection against each reel-item's own wrapper element.
    // The previous version of this stylesheet collapsed our video's own
    // ancestor chain with display:contents, which removes an element's box
    // entirely - if the controller watches that wrapper's geometry, this
    // would make it think our reel scrolled out of view. So: leave the
    // video's ancestors completely untouched (no display/style changes at
    // all) - our video still visually dominates the screen via position:fixed
    // + max z-index regardless of what its ancestors look like, so nothing is
    // lost visually by not neutralizing them.
    style.textContent =
      'body[data-astryx-pip-active] > *:not([data-astryx-pip-keep]), ' +
      'body[data-astryx-pip-active] [data-astryx-pip-keep] > *:not([data-astryx-pip-keep]) { display:none !important; }' +
      'video:not([data-astryx-pip-video]) { display:none !important; }' +
      'video[data-astryx-pip-video] { position:fixed !important; top:0 !important; left:0 !important; width:100vw !important; height:100vh !important; object-fit:cover !important; z-index:2147483647 !important; background:#000 !important; }';
    document.head.appendChild(style);
  }

  // Permanent sanity check, not a one-off debug hook: this class of bug (an
  // element leaking into the PiP window that our known hiding rules don't
  // reach) has happened three times now - the toolbar, the download button,
  // the clipboard-copy button - each only found by either live DevTools or
  // reading the injected script's own source. Catch the NEXT one from an
  // ordinary field logcat capture instead: after hiding runs, scan for
  // anything still visibly on-screen that isn't the video itself or one of
  // its known ancestors, and report exactly what it is. Silent when nothing
  // is found, so this costs nothing in the normal case.
  var leaked = [];
  var all = document.body.querySelectorAll('*');
  for (var k = 0; k < all.length; k++) {
    var el = all[k];
    if (el === best) continue;
    if (el.hasAttribute('data-astryx-pip-keep') || el.hasAttribute('data-astryx-pip-video')) continue;
    if (getComputedStyle(el).display === 'none') continue;
    var er = el.getBoundingClientRect();
    if (er.width === 0 || er.height === 0) continue;
    var desc = el.tagName +
      (el.id ? '#' + el.id : '') +
      (el.className && typeof el.className === 'string' ? '.' + el.className.replace(/\s+/g, '.') : '');
    leaked.push(desc);
    if (leaked.length >= 8) break;
  }
  if (leaked.length && window.PipBridge && window.PipBridge.logPipAnomaly) {
    window.PipBridge.logPipAnomaly(leaked.join(' | '));
  }
})();
"""

internal const val PIP_RESTORE_MODE_JS = """
(function() {
  var style = document.getElementById('astryx-pip-style');
  if (style) style.remove();
  document.body.removeAttribute('data-astryx-pip-active');
  var kept = document.querySelectorAll('[data-astryx-pip-keep]');
  for (var i = 0; i < kept.length; i++) { kept[i].removeAttribute('data-astryx-pip-keep'); }
  var vids = document.querySelectorAll('[data-astryx-pip-video]');
  for (var j = 0; j < vids.length; j++) { vids[j].removeAttribute('data-astryx-pip-video'); }
  // Let download_content.js's own .visible class control this again, now
  // that we're back in the normal view where it's a wanted control.
  var dlBtn = document.getElementById('materialbook-global-downloader');
  if (dlBtn) dlBtn.style.removeProperty('display');
  var cpBtn = document.getElementById('materialbook-clipboard-copier');
  if (cpBtn) cpBtn.style.removeProperty('display');
  // Resume normal live tracking now that we're back in the foreground.
  if (window.__astryxSetPipFreeze) window.__astryxSetPipFreeze(false);
})();
"""

// Freezes the detector's window.__astryxLastActiveVideo the instant Android
// decides PiP is eligible (onUserLeaveHint), well before PIP_FOCUS_MODE_JS
// actually runs. Installs a getter/setter guard the first time it's called
// (idempotent), capturing whatever value is already tracked so nothing is
// lost, then freezes writes. Facebook's own reels controller can pause the
// current reel and autoplay a different one in the gap between
// onUserLeaveHint and onPictureInPictureModeChanged (up to ~3.5s observed) -
// without this, that later write silently wins and PIP_FOCUS_MODE_JS locks
// onto the wrong reel, one the native side never computed an aspect ratio for.
internal const val PIP_FREEZE_ACTIVE_VIDEO_JS = """
(function() {
  if (!window.__astryxPipFreezeInstalled) {
    window.__astryxPipFreezeInstalled = true;
    var real = window.__astryxLastActiveVideo || null;
    var frozen = false;
    Object.defineProperty(window, '__astryxLastActiveVideo', {
      configurable: true,
      get: function() { return real; },
      set: function(v) { if (!frozen) { real = v; } }
    });
    window.__astryxSetPipFreeze = function(v) { frozen = !!v; };
  }
  window.__astryxSetPipFreeze(true);
})();
"""

@Composable
fun MaterialbookWebView(
    url: String,
    settingsVM: SettingsViewModel = viewModel(),
    pipToggleTrigger: Int = 0,
    pipEnteringTrigger: Int = 0,
    isInPipMode: Boolean = false,
    onVideoPlayingChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current

    val state = rememberSaveableWebViewState(url)
    val navigator = rememberWebViewNavigator(
        requestInterceptor = ExternalRequestInterceptor { externalUrl ->
            val intent = Intent(Intent.ACTION_VIEW, externalUrl.toUri())
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(
                    context,
                    resources.getString(R.string.not_supported),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    LaunchedEffect(navigator) {
        val bundle = state.viewState
        if (bundle == null) {
            navigator.loadUrl(url)
        }
    }

    // Fired from the PiP overlay's native Play/Pause button (see MainActivity's
    // pipActionReceiver) — the only reverse (native -> JS) channel we have,
    // vs PipBridge which only goes JS -> native.
    LaunchedEffect(pipToggleTrigger) {
        if (pipToggleTrigger > 0) {
            navigator.evaluateJavaScript(PIP_TOGGLE_JS) {}
        }
    }

    // Fired from onUserLeaveHint, the earliest moment PiP is known to be
    // eligible — freezes the detector's "last active video" before
    // Facebook's own reels controller gets a chance to pause it and autoplay
    // a different one first. Deliberately separate from the isInPipMode
    // effect below, which only fires once PiP has visually engaged (up to
    // ~3.5s later) — too late to close this race.
    LaunchedEffect(pipEnteringTrigger) {
        if (pipEnteringTrigger > 0) {
            navigator.evaluateJavaScript(PIP_FREEZE_ACTIVE_VIDEO_JS) {}
        }
    }

    LaunchedEffect(isInPipMode, state.loadingState) {
        if (state.loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript(
                if (isInPipMode) PIP_FOCUS_MODE_JS else PIP_RESTORE_MODE_JS
            ) {}
        }
    }

    // allow exiting while scrolling to top.
    var exitScroll by remember { mutableStateOf(false) }
    BackHandler {
        if (exitScroll) {
            activity?.finish()
        } else {
            navigator.evaluateJavaScript("backHandlerNB();") {
                val backHandled = it.removeSurrounding("\"")
                when (backHandled) {
                    "false" -> {
                        if (navigator.canGoBack) {
                            navigator.navigateBack()
                        } else {
                            activity?.finish()
                        }
                    }
                    "exit" -> activity?.finish()
                    "scrolling" -> exitScroll = true
                }
            }
        }
    }

    LaunchedEffect(exitScroll) {
        if (exitScroll) {
            delay(800)
            exitScroll = false
        }
    }

    val isDesktop by settingsVM.desktopLayout.collectAsState()
    val isAutoRevert by settingsVM.isRevertDesktop.collectAsState()
    val isAutoDesktop = rememberAutoDesktop()

    LaunchedEffect(Unit) {
        if (isAutoDesktop && !isDesktop) {
            settingsVM.setRevertDesktop(true)
            settingsVM.setDesktopLayout(true)
        }
        else if (!isAutoDesktop && isAutoRevert) {
            settingsVM.setRevertDesktop(false)
            settingsVM.setDesktopLayout(false)
        }
    }

    var isLoading by rememberSaveable { mutableStateOf(true) }
    val isError = state.errorsForCurrentRequest.lastOrNull()?.isFromMainFrame == true

    val viewModel: MainViewModel = viewModel {
        MainViewModel(
            resources = resources,
            settings = settingsVM
        )
    }

    val themeColor by viewModel.themeColor
    // Manual handling to fix visual & padding bug on settings dialog.
    var isImmersiveMode by rememberSaveable { mutableStateOf(settingsVM.immersiveMode.value) }

    fun setWindow(immersive: Boolean) {
        val window = activity?.window ?: return
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        if (immersive) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            val isLight = ColorUtils.calculateLuminance(themeColor.toArgb()) > 0.5
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.isAppearanceLightStatusBars = isLight
            windowInsetsController.isAppearanceLightNavigationBars = isLight
        }
        isImmersiveMode = immersive
    }

    LaunchedEffect(isImmersiveMode, themeColor.value) {
        setWindow(isImmersiveMode)
    }

    val userScripts by viewModel.scripts
    val loadingState = state.loadingState

    LaunchedEffect(loadingState, userScripts) {
        if (loadingState is LoadingState.Finished) {
            userScripts?.let { scripts ->
                navigator.evaluateJavaScript(scripts) {
                    isLoading = false
                    // Correlates against PipBridge/PiP logcat timestamps to confirm
                    // or rule out a cold-start race: userScripts (including
                    // pip_video_detector.js, fetched over the network - see
                    // fetchScripts.kt) may not finish loading/evaluating before the
                    // user tries PiP, especially right after an app cache clear
                    // forces every script fetch back onto the network instead of a
                    // cached response.
                    Log.d("AstryxbookPiP", "userScripts finished evaluating")
                }
            }
        }
    }

    if (isError && isLoading) {
        NetworkErrorDialog { activity?.finish() }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val originalColor = remember { mutableStateOf(themeColor) }

    var settingsToggle by rememberSaveable { mutableStateOf(false) }
    if (settingsToggle) {
        setWindow(false)
        SettingsDialog(
            onDismiss = {
                setWindow(settingsVM.immersiveMode.value)
                viewModel.setThemeColor(originalColor.value)
                settingsToggle = false
            },
            onReload = {
                isLoading = true
                viewModel.setThemeColor(Color.Transparent)
                setWindow(settingsVM.immersiveMode.value)
                viewModel.refresh(
                    resources = resources,
                    settings = settingsVM
                )
                navigator.reload()
            }
        )
    }

    LaunchedEffect(settingsToggle) {
        if (settingsToggle) {
            originalColor.value = themeColor
            viewModel.setThemeColor(colorScheme.background)
        }
    }

    if (isLoading) {
        SplashLoading(
            if (loadingState is LoadingState.Loading) {
                loadingState.progress
            } else {
                0.8F
            }
        )
    }


    LaunchedEffect(isDesktop) {
        val userAgent = if (isDesktop) DESKTOP_USER_AGENT else ""
        state.nativeWebView.settings.userAgentString = userAgent
    }

    // needed to consume extra padding when keyboard is open
    val barsInsets = WindowInsets.systemBars.asPaddingValues()
    val imeHeight = rememberImeHeight()

    val primaryColor = colorScheme.primary.toArgb()
    val onPrimaryColor  = colorScheme.onPrimary.toArgb()

    WebView(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColor)
            .then(
                if (isImmersiveMode) {
                    Modifier.padding(bottom = imeHeight)
                } else {
                    Modifier.padding(
                        top = barsInsets.calculateTopPadding(),
                        bottom = maxOf(barsInsets.calculateBottomPadding(), imeHeight)
                    )
                }
            ),
        state = state,
        navigator = navigator,
        platformWebViewParams = fileChooserWebViewParams(),
        captureBackPresses = false,
        onCreated = { webView ->

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            cookieManager.flush()

            state.webSettings.apply {
                isJavaScriptEnabled = true

                androidWebSettings.apply {
                    //isDebugInspectorInfoEnabled = true
                    domStorageEnabled = true
                    hideDefaultVideoPoster = true
                    mediaPlaybackRequiresUserGesture = false
                }
            }

            webView.apply {
                addJavascriptInterface(
                    MaterialbookSettings { settingsToggle = true },
                    "SettingsBridge"
                )
                addJavascriptInterface(
                    ThemeChange { if (!settingsToggle) viewModel.setThemeColor(Color(it)) },
                    "ThemeBridge"
                )
                addJavascriptInterface(
                    DownloadBridge(context),
                    "DownloadBridge"
                )
                addJavascriptInterface(
                    ClipboardBridge(context),
                    "ClipboardBridge"
                )
                addJavascriptInterface(
                    MaterialYouBridge(primaryColor, onPrimaryColor),
                    "MaterialYouBridge"
                )
                addJavascriptInterface(
                    PipBridge(onVideoPlayingChanged),
                    "PipBridge"
                )

                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        }
    )
}