package com.eepiemi.materialbook.ui.screens

import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.FrameLayout
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.ColorUtils
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
import com.eepiemi.materialbook.utils.BraveBlockList
import com.eepiemi.materialbook.utils.ExternalRequestInterceptor
import com.eepiemi.materialbook.utils.FullscreenController
import com.eepiemi.materialbook.utils.appOrientation
import com.eepiemi.materialbook.utils.appWebViewParams
import com.eepiemi.materialbook.utils.jsBridge.ClipboardBridge
import com.eepiemi.materialbook.utils.jsBridge.DownloadBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialbookSettings
import com.eepiemi.materialbook.utils.jsBridge.ThemeChange
import com.eepiemi.materialbook.utils.jsBridge.MaterialYouBridge
import com.eepiemi.materialbook.utils.jsBridge.PipBridge
import com.eepiemi.materialbook.audio.PipHandback
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import com.eepiemi.materialbook.utils.openMessenger
import com.eepiemi.materialbook.utils.openExternalUrl
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
    // Confirmed via live DevTools: Facebook's own screen-navigation system
    // (the data-comp-id="MScreen" structure) can set display:none directly
    // on an ancestor of the current video when the app backgrounds - treating
    // PiP entry the same as "user left this screen", independent of anything
    // this script does. display:none on ANY ancestor removes the whole
    // subtree from rendering; position:fixed on a descendant doesn't escape
    // that, it only affects positioning once an element is actually in the
    // render tree. This actively fights that, forcing kept ancestors back to
    // a renderable display - risk: unlike the other fixes here, this pushes
    // back against an ongoing Facebook process, not just passive residue, so
    // it may have side effects we haven't seen yet.
    if (getComputedStyle(el).display === 'none') {
      if (!el.hasAttribute('data-astryx-orig-style')) {
        el.setAttribute('data-astryx-orig-style', el.getAttribute('style') || '');
      }
      el.style.setProperty('display', 'block', 'important');
    }
    el = el.parentElement;
  }

  // A one-time override above wasn't enough - Facebook's own teardown can
  // re-apply display:none after we've already forced it back, same pattern
  // as the reels controller repeatedly re-pausing a resumed video elsewhere
  // in this file. Keep re-asserting for as long as we're in PiP instead of
  // setting it once; disconnected in PIP_RESTORE_MODE_JS on exit.
  if (!window.__astryxKeepVisibleObserver) {
    window.__astryxKeepVisibleObserver = new MutationObserver(function(mutations) {
      for (var mi = 0; mi < mutations.length; mi++) {
        var t = mutations[mi].target;
        if (t.hasAttribute && t.hasAttribute('data-astryx-pip-keep') && getComputedStyle(t).display === 'none') {
          t.style.setProperty('display', 'block', 'important');
        }
      }
    });
  }
  window.__astryxKeepVisibleObserver.disconnect();
  var keptNow = document.querySelectorAll('[data-astryx-pip-keep]');
  for (var m = 0; m < keptNow.length; m++) {
    window.__astryxKeepVisibleObserver.observe(keptNow[m], { attributes: true, attributeFilter: ['style', 'class'] });
  }
  best.setAttribute('data-astryx-pip-video', 'true');
  document.body.setAttribute('data-astryx-pip-active', 'true');

  // Root cause found via live DevTools mid-bug (two separate rounds): the
  // stylesheet rule below sets position/width/height/etc together, but a
  // live check showed computed position had won (fixed, top/left 0) while
  // width/height had NOT - Facebook's own !important rule was winning the
  // specificity fight for just those two properties (see the button-hiding
  // comments below for the same pattern). Fixed by setting sizing directly
  // as inline !important styles, which always wins regardless of
  // specificity. A LATER round found a second, different issue even with
  // that fix in place: position:fixed with top:0 correctly applied, but the
  // element rendered at boundingRect.top roughly -(its own height), while
  // window.scrollY was non-zero at that exact moment - a Chromium quirk
  // where a position:fixed mutation applied via script while the page has
  // non-zero scroll can render one paint cycle behind, still anchored to the
  // stale scrolled position, before the fixed-position layer tree catches
  // up. Resetting scroll to 0,0 as part of applying the fixed positioning
  // removes the stale offset, so there's nothing left for the render to lag
  // behind. Regression found in the field: on the main feed wall (not the
  // Reels tab), scrollY is the user's actual reading position, not
  // incidental state - zeroing it outright threw that away, so returning
  // from PiP always landed back at the top of the feed. Save it first and
  // restore it in PIP_RESTORE_MODE_JS instead of leaving it at zero.
  // Root cause found in the field: PIP_RESTORE_MODE_JS below used to remove
  // our overridden properties one at a time (position/width/height/etc) via
  // style.removeProperty. That doesn't "restore" anything - inline style is
  // one flat collection, not layers, so removing a property we overwrote
  // deletes it outright rather than bringing back whatever Facebook's own
  // inline style had there before us (confirmed via live DevTools: Facebook
  // sets its own inline position/object-fit/width/height on this element).
  // Landscape reels showing "out of scale" on return from PiP was that data
  // loss. Save the entire original inline style string here instead, and
  // restore that exact string on exit.
  if (!best.hasAttribute('data-astryx-orig-style')) {
    best.setAttribute('data-astryx-orig-style', best.getAttribute('style') || '');
  }

  if (window.__astryxPreScrollX === undefined) {
    window.__astryxPreScrollX = window.scrollX;
    window.__astryxPreScrollY = window.scrollY;
  }
  window.scrollTo(0, 0);
  best.style.setProperty('position', 'fixed', 'important');
  best.style.setProperty('top', '0', 'important');
  best.style.setProperty('left', '0', 'important');
  // Sized to the visual viewport (the part of the page actually visible in
  // the window), in px, and re-fitted on every visualViewport resize (the
  // PiP window settling, rotation). Found via live DevTools, two cases that
  // need different CSS units: on the Reels tab, Facebook's reel wrappers
  // (the ancestors we leave untouched, see the stylesheet comment below) stay
  // at their full-screen width inside the PiP window, so Chromium zooms the
  // page out (visualViewport.scale 0.31) and a 100vw video covered only the
  // window's top-left corner. For a feed video the page isn't zoomed, but the
  // fixed-position containing block kept its full-screen size (384x694 in a
  // 120x210 window), so 100% made the video full-screen-sized and the window
  // showed a zoomed-in crop. The visual viewport is the visible area in both
  // cases. Resizing the wrappers instead also fixed the zoom but made
  // Facebook pause the reel. The listener removes itself once the video is no
  // longer the PiP video (PIP_RESTORE_MODE_JS removes the marker).
  if (window.__astryxPipFit && window.visualViewport) {
    window.visualViewport.removeEventListener('resize', window.__astryxPipFit);
  }
  window.__astryxPipFit = function fit() {
    if (!best.hasAttribute('data-astryx-pip-video')) {
      if (window.visualViewport) window.visualViewport.removeEventListener('resize', fit);
      if (window.__astryxPipFit === fit) window.__astryxPipFit = null;
      return;
    }
    var vv = window.visualViewport;
    best.style.setProperty('width', (vv ? vv.width : window.innerWidth) + 'px', 'important');
    best.style.setProperty('height', (vv ? vv.height : window.innerHeight) + 'px', 'important');
  };
  window.__astryxPipFit();
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', window.__astryxPipFit);
  }
  best.style.setProperty('object-fit', 'cover', 'important');
  best.style.setProperty('z-index', '2147483647', 'important');
  best.style.setProperty('background', '#000', 'important');
  // Belt-and-suspenders re-assertion on the next frame, in case the first
  // application still landed mid-paint-cycle despite the scroll reset above.
  requestAnimationFrame(function() {
    if (window.scrollY !== 0 || document.documentElement.scrollTop !== 0) {
      window.scrollTo(0, 0);
    }
    best.style.setProperty('top', '0', 'important');
    best.style.setProperty('left', '0', 'important');
  });

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
  if (window.__astryxKeepVisibleObserver) window.__astryxKeepVisibleObserver.disconnect();
  var kept = document.querySelectorAll('[data-astryx-pip-keep]');
  for (var i = 0; i < kept.length; i++) {
    var k = kept[i];
    k.removeAttribute('data-astryx-pip-keep');
    // Undo the display:none override above, same save/restore-exact-string
    // pattern as the video element below (see its comment).
    if (k.hasAttribute('data-astryx-orig-style')) {
      var kOrigStyle = k.getAttribute('data-astryx-orig-style');
      if (kOrigStyle) {
        k.setAttribute('style', kOrigStyle);
      } else {
        k.removeAttribute('style');
      }
      k.removeAttribute('data-astryx-orig-style');
    }
  }
  var vids = document.querySelectorAll('[data-astryx-pip-video]');
  for (var j = 0; j < vids.length; j++) {
    var v = vids[j];
    v.removeAttribute('data-astryx-pip-video');
    // Restore Facebook's own original inline style exactly as it was, saved
    // by PIP_FOCUS_MODE_JS before we touched anything (see its comment for
    // why removeProperty alone silently destroyed it instead).
    if (v.hasAttribute('data-astryx-orig-style')) {
      var origStyle = v.getAttribute('data-astryx-orig-style');
      if (origStyle) {
        v.setAttribute('style', origStyle);
      } else {
        v.removeAttribute('style');
      }
      v.removeAttribute('data-astryx-orig-style');
    }
  }
  // Let download_content.js's own .visible class control this again, now
  // that we're back in the normal view where it's a wanted control.
  var dlBtn = document.getElementById('materialbook-global-downloader');
  if (dlBtn) dlBtn.style.removeProperty('display');
  var cpBtn = document.getElementById('materialbook-clipboard-copier');
  if (cpBtn) cpBtn.style.removeProperty('display');
  // Restore whatever scroll position PIP_FOCUS_MODE_JS saved before
  // resetting it to 0,0 (see that comment) - on the main feed wall this is
  // the user's actual reading position, not incidental state.
  if (window.__astryxPreScrollX !== undefined) {
    window.scrollTo(window.__astryxPreScrollX, window.__astryxPreScrollY);
    window.__astryxPreScrollX = undefined;
    window.__astryxPreScrollY = undefined;
  }
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

// Second attempt at the black-pip-with-audio-playing bug (see
// setLayerType(LAYER_TYPE_NONE/HARDWARE) below, the first attempt) - that one
// operates on the Android View's own bitmap cache, which doesn't necessarily
// touch Chromium's independent internal GPU compositor (video decoding/
// painting lives in Chromium's own rendering engine, not the hosting
// Android View system), and field testing after that fix showed the black
// screen could still happen, just less often. This operates one level lower,
// directly on the video element from inside the page: a temporary 3D
// transform forces Chromium to allocate a fresh compositing layer for the
// element (discarding whatever stale one it was holding), a synchronous
// reflow (reading offsetHeight) forces layout to actually happen before the
// transform is reverted, and the revert itself happens on the next animation
// frame so the new layer has a chance to actually paint first. Applied to
// the same element PIP_FOCUS_MODE_JS already selected, not re-derived.
internal const val PIP_NUDGE_COMPOSITOR_JS = """
(function() {
  var el = document.querySelector('[data-astryx-pip-video]') || window.__astryxLastActiveVideo;
  if (!el || !document.documentElement.contains(el)) return;
  var originalTransform = el.style.transform;
  el.style.transform = 'translateZ(0.001px)';
  void el.offsetHeight;
  requestAnimationFrame(function() {
    el.style.transform = originalTransform;
  });
})();
"""

// Rotating the phone while a reel is in PiP made Facebook drop it: found via
// a live probe on device, the PiP window itself doesn't change size, but the
// page gets resize/orientation events in which screen.width/height and
// screen.orientation now read landscape. Facebook's reels page reacts by
// emptying the video and switching to /watch/, built at the PiP window's
// size, so returning to the app then showed that layout squeezed into the
// left ~120px. Pinning the screen values the page reads to what they were
// at PiP entry keeps Facebook on the reel (verified: same reel kept playing
// through landscape and back, and the full-size layout came back intact).
// The events themselves still reach the page.
internal const val PIP_PIN_SCREEN_JS = """
(function() {
  if (window.__astryxScreenPin) return;
  var saved = [];
  function pin(target, key, value) {
    saved.push([target, key, Object.getOwnPropertyDescriptor(target, key)]);
    Object.defineProperty(target, key, { configurable: true, get: function() { return value; } });
  }
  var o = screen.orientation;
  var values = {
    width: screen.width, height: screen.height,
    availWidth: screen.availWidth, availHeight: screen.availHeight
  };
  ['width', 'height', 'availWidth', 'availHeight'].forEach(function(k) {
    pin(Screen.prototype, k, values[k]);
  });
  if (o && window.ScreenOrientation) {
    pin(ScreenOrientation.prototype, 'type', o.type);
    pin(ScreenOrientation.prototype, 'angle', o.angle);
  }
  if ('orientation' in window) pin(window, 'orientation', window.orientation);
  window.__astryxScreenPin = saved;
})();
"""

// Undoes PIP_PIN_SCREEN_JS on PiP exit, then fires one resize so the page
// re-reads the real values (e.g. PiP expanded while the phone is landscape).
internal const val PIP_UNPIN_SCREEN_JS = """
(function() {
  var saved = window.__astryxScreenPin;
  if (!saved) return;
  window.__astryxScreenPin = null;
  for (var i = saved.length - 1; i >= 0; i--) {
    if (saved[i][2]) {
      Object.defineProperty(saved[i][0], saved[i][1], saved[i][2]);
    } else {
      delete saved[i][0][saved[i][1]];
    }
  }
  window.dispatchEvent(new Event('resize'));
})();
"""

// Entering PiP from HTML5 fullscreen with the phone in landscape: WebView
// itself ends fullscreen ~0.4s after PiP starts (stack seen on device:
// exitFullscreenModeForTab -> onHideCustomView; the display turns to
// portrait for the home screen, which Chromium treats as rotating out of a
// fullscreen video). Facebook's video viewer reacts to that fullscreenchange
// by re-rendering at the tiny PiP window size and discarding the <video>, so
// the PiP window went black, and on return its layout stayed at that width.
// Facebook only re-renders on a real fullscreen -> not-fullscreen
// transition, not on resize/orientationchange. So while in PiP, hold the
// fullscreenchange events back from the page (installed at PiP entry while
// fullscreen), and replay one after PiP once the window has settled at its
// final size (PIP_RELAYOUT_AFTER_FULLSCREEN_JS). Verified live: the video
// stayed and showed in PiP.
internal const val PIP_HOLD_FULLSCREENCHANGE_JS = """
(function() {
  if (window.__astryxFsHold) return;
  window.__astryxFsHold = function(e) { e.stopImmediatePropagation(); };
  window.addEventListener('fullscreenchange', window.__astryxFsHold, true);
  window.addEventListener('webkitfullscreenchange', window.__astryxFsHold, true);
})();
"""

// Removes PIP_HOLD_FULLSCREENCHANGE_JS's blocker. Used on its own on PiP exit
// when fullscreen survived PiP (nothing was held back), and by
// PIP_RELAYOUT_AFTER_FULLSCREEN_JS below.
internal const val PIP_RELEASE_FULLSCREENCHANGE_JS = """
(function() {
  if (!window.__astryxFsHold) return;
  window.removeEventListener('fullscreenchange', window.__astryxFsHold, true);
  window.removeEventListener('webkitfullscreenchange', window.__astryxFsHold, true);
  window.__astryxFsHold = null;
})();
"""

// Run on PiP exit when fullscreen ended during PiP: releases the blocker,
// then replays the fullscreenchange the page missed once resize events have
// stopped for a moment (the window can pass through portrait on its way back
// to landscape, and Facebook lays out only once, at whatever size it sees),
// with a cap in case no resize comes.
internal const val PIP_RELAYOUT_AFTER_FULLSCREEN_JS = PIP_RELEASE_FULLSCREENCHANGE_JS + """
(function() {
  var done = false, settle = null;
  function fire() {
    if (done) return;
    done = true;
    clearTimeout(settle);
    clearTimeout(cap);
    window.removeEventListener('resize', onResize, true);
    document.dispatchEvent(new Event('fullscreenchange'));
  }
  function onResize() {
    clearTimeout(settle);
    settle = setTimeout(fire, 800);
  }
  window.addEventListener('resize', onResize, true);
  settle = setTimeout(fire, 800);
  var cap = setTimeout(fire, 3000);
})();
"""

// Lock-screen audio handoff (see LockScreenAudioService). Installed on PiP
// entry when the setting is on. Observed on the device spike: at lock the
// page first gets visibilitychange -> hidden, Facebook's own handler pauses
// the video 25-60ms later, and ACTION_SCREEN_OFF reaches the app only ~0.5s
// after that, so the live `paused` is always true by then. This capture-phase
// listener snapshots the PiP target video (same selection as PIP_TOGGLE_JS)
// at the moment the page is hidden. On the way back (visible) it reports to
// PipBridge while a handoff is active, one of MainActivity's handback signals.
internal const val PIP_HANDOFF_ARM_JS = """
(function() {
  if (window.__astryxHandoffListener) return;
  var listener = function() {
    if (document.visibilityState !== 'hidden') {
      if (document.querySelector('[data-astryx-handoff-muted]')) {
        try { PipBridge.onPageVisible(); } catch (e) {}
      }
      return;
    }
    var best = document.querySelector('[data-astryx-pip-video]');
    if (!best) {
      best = window.__astryxLastActiveVideo;
      if (best && !document.documentElement.contains(best)) best = null;
    }
    if (!best) {
      var videos = document.querySelectorAll('video');
      var bestArea = 0;
      for (var i = 0; i < videos.length; i++) {
        var r = videos[i].getBoundingClientRect();
        var area = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0)) *
                   Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
        if (area > bestArea) { bestArea = area; best = videos[i]; }
      }
    }
    window.__astryxHandoffVideo = best || null;
    window.__astryxHandoffSnapshot = best
      ? { src: String(best.currentSrc || ''), time: best.currentTime, wasPlaying: !best.paused }
      : null;
  };
  window.__astryxHandoffListener = listener;
  document.addEventListener('visibilitychange', listener, true);
})();
"""

// Removes the listener and snapshot installed by PIP_HANDOFF_ARM_JS. Run on
// PiP exit and after every handback.
internal const val PIP_HANDOFF_DISARM_JS = """
(function() {
  if (window.__astryxHandoffListener) {
    document.removeEventListener('visibilitychange', window.__astryxHandoffListener, true);
  }
  window.__astryxHandoffListener = null;
  window.__astryxHandoffSnapshot = null;
  window.__astryxHandoffVideo = null;
})();
"""

// Runs at ACTION_SCREEN_OFF while in PiP. Returns the snapshot taken when the
// page was hidden ({ src, time, wasPlaying }), then mutes and pauses that
// video: Facebook restarts it by itself after unlock, so it must stay muted
// until handback or there's double audio. The prior muted state is kept in
// data-astryx-handoff-muted, which also marks the video for pipHandbackJs
// (the PiP marker itself is removed by PIP_RESTORE_MODE_JS on PiP exit).
internal const val PIP_HANDOFF_READ_JS = """
(function() {
  var snapshot = window.__astryxHandoffSnapshot;
  var v = window.__astryxHandoffVideo;
  if (!snapshot || !v || !document.documentElement.contains(v)) {
    return JSON.stringify({ src: '', time: 0, wasPlaying: false });
  }
  if (!v.hasAttribute('data-astryx-handoff-muted')) {
    v.setAttribute('data-astryx-handoff-muted', v.muted ? 'true' : 'false');
  }
  v.muted = true;
  v.pause();
  return JSON.stringify(snapshot);
})();
"""

/**
 * Hands playback back to the video PIP_HANDOFF_READ_JS muted: seeks to
 * [positionMs] (skipped when null, e.g. an aborted handoff), restores its
 * muted state, removes the marker, calls play() only if [play], then clears
 * the handoff listener and snapshot, re-installing them if [rearm].
 */
internal fun pipHandbackJs(positionMs: Long?, play: Boolean, rearm: Boolean): String = """
(function() {
  var v = document.querySelector('[data-astryx-handoff-muted]');
  if (!v) return 'none';
  ${if (positionMs != null) "v.currentTime = ${positionMs / 1000.0};" else ""}
  v.muted = v.getAttribute('data-astryx-handoff-muted') === 'true';
  v.removeAttribute('data-astryx-handoff-muted');
  ${if (play) "var p = v.play(); if (p && p.catch) p.catch(function() {});" else ""}
  return 'ok';
})();
""" + PIP_HANDOFF_DISARM_JS + (if (rearm) PIP_HANDOFF_ARM_JS else "")

@Composable
fun MaterialbookWebView(
    url: String,
    settingsVM: SettingsViewModel = viewModel(),
    pipToggleTrigger: Int = 0,
    pipEnteringTrigger: Int = 0,
    isInPipMode: Boolean = false,
    pipHandoffReadTrigger: Int = 0,
    pipHandback: PipHandback? = null,
    onPipHandoffRead: (String?) -> Unit = {},
    onPipPageVisible: () -> Unit = {},
    onVideoPlayingChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current

    val state = rememberSaveableWebViewState(url)
    val messengerPkg by settingsVM.messengerPackage.collectAsState()
    // Stable holder: navigator may keep the first interceptor, so read latest at call time.
    val currentMessengerPkg by rememberUpdatedState(messengerPkg)

    val isDesktop by settingsVM.desktopLayout.collectAsState()
    val isAutoDesktop = rememberAutoDesktop()
    val isEffectiveDesktop = effectiveDesktop(isDesktop, isAutoDesktop)

    val openExternalLink: (String) -> Unit = { externalUrl ->
        val opened = openExternalUrl(context, externalUrl)
        if (!opened) {
            Toast.makeText(
                context,
                resources.getString(R.string.open_external_failed_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Messages in desktop mode: Messages links open in MessagesLayer, a separate desktop
    // WebView over this one, so this page (and its scroll position) stays as it is. Not
    // used when this view is already the desktop site (Desktop layout, large screens),
    // which shows Messages by itself. Saved so the layer comes back after recreation.
    val messagesDesktopSetting by settingsVM.messagesDesktop.collectAsState()
    val currentMessagesDesktopSetting by rememberUpdatedState(messagesDesktopSetting)
    val currentIsEffectiveDesktop by rememberUpdatedState(isEffectiveDesktop)
    var messagesLayerUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val navigator = rememberWebViewNavigator(
        requestInterceptor = ExternalRequestInterceptor(
            tryOpenMessagesDesktop = { messagesUrl ->
                if (currentMessagesDesktopSetting && !currentIsEffectiveDesktop) {
                    messagesLayerUrl = messagesDesktopUrl(messagesUrl)
                    true
                } else {
                    false
                }
            },
            handleExternalUrl = openExternalLink,
            tryOpenMessenger = { messengerUrl ->
                val ok = openMessenger(context, messengerUrl, currentMessengerPkg)
                if (!ok) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.messenger_redirect_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ok
            },
        )
    )

    val initialUrl = rememberSaveable { mutableStateOf(url) }

    LaunchedEffect(navigator) {
        val bundle = state.viewState
        if (bundle == null) {
            navigator.loadUrl(url)
        }
    }

    // A new VIEW intent while the app is already open (MainActivity is singleTop).
    LaunchedEffect(url) {
        if (url != initialUrl.value) {
            initialUrl.value = url
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

    // Set by FullscreenController's host below while an HTML5 fullscreen
    // custom view is showing.
    var isFullscreen by remember { mutableStateOf(false) }

    // In HTML5 fullscreen the video renders in WebView's custom view, not the
    // page layout, so PiP just shows that view and focus mode (hide page DOM,
    // restyle the <video>) is skipped. Restore still always runs outside PiP:
    // it also unfreezes the active-video tracker that PIP_FREEZE_ACTIVE_VIDEO_JS
    // sets on every PiP attempt, fullscreen or not.
    LaunchedEffect(isInPipMode, state.loadingState) {
        if (state.loadingState is LoadingState.Finished) {
            if (!isInPipMode) {
                navigator.evaluateJavaScript(PIP_RESTORE_MODE_JS) {}
            } else if (isFullscreen) {
                Log.d("AstryxbookPiP", "PiP entered while fullscreen: skipping focus mode")
            } else {
                navigator.evaluateJavaScript(PIP_FOCUS_MODE_JS) {}
            }
        }
    }

    // Landscape fullscreen -> PiP: WebView ends fullscreen right after PiP
    // starts (see PIP_HOLD_FULLSCREENCHANGE_JS). Hold that change back from
    // the page while in PiP so Facebook keeps the video, apply focus mode once
    // fullscreen has ended (the skip above only covered PiP entry), and on
    // the way out of PiP replay the change at the final window size.
    var fullscreenEndedInPip by remember { mutableStateOf(false) }
    var holdingFullscreenChange by remember { mutableStateOf(false) }
    LaunchedEffect(isInPipMode) {
        if (isInPipMode && isFullscreen && state.loadingState is LoadingState.Finished) {
            holdingFullscreenChange = true
            navigator.evaluateJavaScript(PIP_HOLD_FULLSCREENCHANGE_JS) {}
        } else if (!isInPipMode && holdingFullscreenChange) {
            holdingFullscreenChange = false
            if (fullscreenEndedInPip) {
                fullscreenEndedInPip = false
                navigator.evaluateJavaScript(PIP_RELAYOUT_AFTER_FULLSCREEN_JS) {}
            } else {
                navigator.evaluateJavaScript(PIP_RELEASE_FULLSCREENCHANGE_JS) {}
            }
        }
    }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen && isInPipMode && state.loadingState is LoadingState.Finished) {
            Log.d("AstryxbookPiP", "fullscreen ended during PiP: applying focus mode")
            fullscreenEndedInPip = true
            navigator.evaluateJavaScript(PIP_FOCUS_MODE_JS) {}
        }
    }

    // Separate from focus mode on purpose (see PIP_PIN_SCREEN_JS): keeps
    // rotation during PiP from making Facebook drop the reel.
    LaunchedEffect(isInPipMode, state.loadingState) {
        if (state.loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript(
                if (isInPipMode) PIP_PIN_SCREEN_JS else PIP_UNPIN_SCREEN_JS
            ) {}
        }
    }

    // Native rendering workaround, not a page/JS issue: confirmed live via
    // DevTools that the video element is correctly selected, actively
    // playing, and has real decoded frame data (readyState 4 / HAVE_ENOUGH_
    // DATA) at the exact moment the PiP window shows solid black with audio
    // still playing. Correct data, wrong pixels - that's WebView's
    // hardware-accelerated video compositor layer (see setLayerType
    // LAYER_TYPE_HARDWARE below) holding a stale/blank buffer across the
    // window resize the system performs when PiP engages, a known class of
    // Android bug for hardware-accelerated video surfaces. Discarding and
    // rebuilding the layer forces a fresh composite instead of reusing
    // whatever stale buffer survived the resize. The short delay gives
    // Android's own PiP enter/exit transition animation (roughly 200-300ms)
    // a moment to finish before we act, since forcing this mid-transition is
    // unlikely to stick.
    LaunchedEffect(isInPipMode) {
        delay(150)
        val webView = state.nativeWebView
        webView.setLayerType(View.LAYER_TYPE_NONE, null)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.invalidate()
    }

    // Second, complementary attempt at the same black-pip bug (see
    // PIP_NUDGE_COMPOSITOR_JS's comment) - runs after the native invalidate
    // above (same 150ms settle delay, then immediately follows it) since we
    // don't have certainty about which layer is actually responsible; no real
    // cost to running both.
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            delay(150)
            navigator.evaluateJavaScript(PIP_NUDGE_COMPOSITOR_JS) {}
        }
    }

    // Lock-screen audio handoff/handback, driven from MainActivity (see
    // LockScreenAudioService). Confirmed on device that these effects still
    // run right after screen-off (the read answered within ~30ms).
    val pipLockscreenAudio by settingsVM.pipLockscreenAudio.collectAsState()
    // Only once the page has loaded, like the focus-mode effect above: the
    // navigator replays just its last event to a WebView that isn't attached
    // yet, so an unconditional evaluate at startup would replace the initial
    // loadUrl and the page would never load.
    LaunchedEffect(isInPipMode, pipLockscreenAudio, state.loadingState) {
        if (state.loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript(
                if (isInPipMode && pipLockscreenAudio) PIP_HANDOFF_ARM_JS else PIP_HANDOFF_DISARM_JS
            ) {}
        }
    }
    val currentOnPipHandoffRead by rememberUpdatedState(onPipHandoffRead)
    LaunchedEffect(pipHandoffReadTrigger) {
        if (pipHandoffReadTrigger > 0) {
            navigator.evaluateJavaScript(PIP_HANDOFF_READ_JS) { currentOnPipHandoffRead(it) }
        }
    }
    LaunchedEffect(pipHandback) {
        pipHandback?.let { request ->
            navigator.evaluateJavaScript(
                pipHandbackJs(request.positionMs, request.play, request.rearm)
            ) {
                Log.d("AstryxbookPiP", "handback JS: $request -> $it")
            }
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

    val braveBlockList by settingsVM.braveBlockList.collectAsState()
    LaunchedEffect(braveBlockList) {
        BraveBlockList.setEnabled(braveBlockList)
    }
    // Pull the latest Brave Block List; falls back to the bundled copy.
    LaunchedEffect(Unit) {
        BraveBlockList.refresh(resources)
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

    // HTML5 fullscreen video: the custom view goes into a black overlay on
    // the window's decor view, above the Compose content. Rotation doesn't
    // recreate the activity (configChanges), so the overlay just resizes.
    // On phones the app is otherwise held in portrait (see appOrientation);
    // fullscreen lifts that so the video can follow auto-rotate, and leaving
    // fullscreen puts it back, so Facebook lays its viewer out in portrait.
    val fullscreen = remember(activity) {
        var overlay: FrameLayout? = null
        FullscreenController(object : FullscreenController.Host {
            override fun attach(view: View) {
                val window = activity?.window ?: return
                val container = FrameLayout(activity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
                (window.decorView as FrameLayout).addView(
                    container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                )
                overlay = container
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                isFullscreen = true
                activity.requestedOrientation =
                    appOrientation(activity.resources.configuration.smallestScreenWidthDp, isFullscreen = true)
                Log.d("AstryxbookPiP", "HTML5 fullscreen: shown")
            }

            override fun detach(view: View) {
                overlay?.let { container ->
                    container.removeView(view)
                    (container.parent as? ViewGroup)?.removeView(container)
                }
                overlay = null
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                setWindow(settingsVM.immersiveMode.value)
                isFullscreen = false
                activity?.let {
                    it.requestedOrientation =
                        appOrientation(it.resources.configuration.smallestScreenWidthDp, isFullscreen = false)
                }
                Log.d("AstryxbookPiP", "HTML5 fullscreen: hidden")
            }
        })
    }

    // WebView/activity going away while fullscreen: detach and tell WebView.
    DisposableEffect(fullscreen) {
        onDispose { fullscreen.hide() }
    }

    // Declared after the page-level BackHandler above so it takes precedence.
    BackHandler(enabled = isFullscreen) {
        fullscreen.hide()
    }

    val userScripts by viewModel.scripts
    val loadingState = state.loadingState

    val pipEnabled by settingsVM.pipEnabled.collectAsState()

    // Loaded directly from the bundled resource, deliberately skipping
    // fetchScripts' network-fetch-with-GitHub-hotfix path that the rest of
    // userScripts go through (see MainViewModel.kt's loadScripts for why).
    // Confirmed via field reports: without this, the very first PiP attempt
    // right after a fresh install or app-cache clear - forcing every other
    // script back onto a cold network fetch - would not trigger PiP at all,
    // since PipBridge never got a chance to report isVideoPlaying in time.
    LaunchedEffect(loadingState, pipEnabled) {
        if (loadingState is LoadingState.Finished && pipEnabled) {
            val detectorScript = context.resources.openRawResource(R.raw.pip_video_detector)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(detectorScript) {}
        }
    }

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


    LaunchedEffect(isEffectiveDesktop) {
        val userAgent = if (isEffectiveDesktop) DESKTOP_USER_AGENT else ""
        // The WebView is created during layout, so on a cold start this can run first:
        // the library applies webSettings when it creates it, and a live WebView is
        // updated directly.
        state.webSettings.customUserAgentString = userAgent.ifEmpty { null }
        runCatching { state.nativeWebView }.getOrNull()?.settings?.userAgentString = userAgent
    }

    // The page underneath keeps running while the Messages layer is open: pause its videos
    // so a playing reel doesn't go on behind the chat.
    LaunchedEffect(messagesLayerUrl) {
        if (messagesLayerUrl != null && state.loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript(
                "document.querySelectorAll('video').forEach(function(v) { v.pause(); });"
            ) {}
        }
    }

    // needed to consume extra padding when keyboard is open
    val barsInsets = WindowInsets.systemBars.asPaddingValues()
    val imeHeight = rememberImeHeight()

    val primaryColor = colorScheme.primary.toArgb()
    val onPrimaryColor  = colorScheme.onPrimary.toArgb()

    val pageModifier = Modifier
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
        )

    WebView(
        modifier = pageModifier,
        state = state,
        navigator = navigator,
        platformWebViewParams = appWebViewParams(fullscreen),
        captureBackPresses = false,
        onCreated = { webView ->

            // Make Brave Block List active immediately, before the first request.
            BraveBlockList.loadInitial(webView.context.resources)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            // Privacy: third parties not on the block list can't set/read cross-site cookies.
            cookieManager.setAcceptThirdPartyCookies(webView, false)
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
                    PipBridge(onVideoPlayingChanged, onPipPageVisible),
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

    messagesLayerUrl?.let { layerUrl ->
        // A new Messages link while the layer is open (a notification, say) recreates it.
        key(layerUrl) {
            MessagesLayer(
                url = layerUrl,
                userScripts = userScripts,
                fullscreen = fullscreen,
                isFullscreen = isFullscreen,
                modifier = pageModifier,
                background = themeColor,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClose = { messagesLayerUrl = null },
                onOpenInMain = { pageUrl ->
                    messagesLayerUrl = null
                    navigator.loadUrl(pageUrl)
                },
                onExternalUrl = { externalUrl -> openExternalLink(fbRedirectSanitizer(externalUrl)) }
            )
        }
    }
}
