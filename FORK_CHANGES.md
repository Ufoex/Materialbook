# Astryxbook Fork Changes

Changes specific to this fork, kept separate from `CHANGE.md` (upstream's
own changelog) to avoid merge conflicts on a file eepiemi actively maintains
— same reasoning as `brand_strings.xml` being split out from `strings.xml`.

Organized by area rather than by release tag, since auto-versioning bumps a
patch version on every merged commit (`fix:`/`chore:`/etc.) — a per-tag log
would mostly be noise. See GitHub Releases for the actual per-version diffs.

## Rebrand

- Renamed to **Astryxbook**: `applicationId` (`com.astryx.book`), display
  name, and an original "A" monogram launcher icon in Facebook blue — not
  Facebook's own trademarked logo, to avoid impersonation/trademark issues.
- Restyled to match **Facebook's own original Android look**: Material You
  theming and AMOLED Black now ship **off by default** (previously on),
  using Facebook's native blue instead of wallpaper-derived colors. Both
  remain available as opt-in toggles.
- Fixed `-v31`/`-night-v31` resource variants that were pulling Android's
  dynamic `system_accent1_*` colors into both native chrome *and* the
  launcher icon itself, unconditionally — bypassing the above default
  entirely on API 31+ devices.
- Native typography (splash, settings sheet, dialogs) tightened to a
  denser, more Facebook-like scale.
- Removed the Buy Me a Coffee button/link and `FUNDING.yml` entry — not
  relevant to this fork.
- Local badge assets (`download.svg`, `open_issue.svg`) and the store
  listing icon/banner recolored to match; downloads badge swapped from an
  opaque third-party worker to a themed, GitHub-API-backed shields.io badge.

## Localization

- Added a complete Hebrew (`עברית`) locale, including Astryxbook branding.
- Ported the upstream Italian translation into a fork-safe locale with Astryxbook
  branding and current PiP ratio strings.
- Updated every existing translated locale with the new PiP aspect-ratio
  title and options.

## Android compatibility

- Raised the minimum supported Android version to 8.0 (API level 26).
- Removed the obsolete pre-API 26 launcher-icon fallback; supported installs
  now resolve the adaptive icon from the base `mipmap-anydpi` resources.

## Default behavior changes

- Phones stay in portrait while browsing; only a video in fullscreen can
  rotate (following the system auto-rotate setting), and leaving fullscreen
  returns to portrait. Facebook's mobile site doesn't handle a real
  landscape page: rotating made it skip the reel, and leaving fullscreen in
  landscape left an oversized still frame instead of the video (both also
  in Chrome). Tablets and other large screens (`smallestScreenWidthDp >=
  600`, the auto-desktop ones) still rotate freely.

- Auto desktop layout now triggers only on genuinely large screens
  (`smallestScreenWidthDp >= 600`), no longer on a phone that happens to be
  in landscape at app start. The automatic decision is computed at runtime
  and never persisted; only the user's own Desktop layout toggle is saved.
  Previously a phone launched once while sideways could get stuck in the
  desktop layout (desktop user agent, garbled Reels, broken PiP) until the
  user turned it off manually. A one-time migration unsticks such installs:
  a desktop setting that was written by the old auto logic is reset to off,
  while one the user chose themselves is kept.

## WebView

- HTML5 fullscreen video support: the WebView's chrome client now implements
  `onShowCustomView` / `onHideCustomView`, so Facebook's fullscreen button
  shows the video in a real fullscreen overlay (system bars hidden, screen
  kept on, follows the system auto-rotate setting) instead of only enlarging
  it inside the page. Back exits fullscreen; system bars then return to the
  Immersive mode setting. Entering PiP while fullscreen shows the fullscreen
  view directly and skips PiP focus mode.
  Verified on device: rotating to landscape keeps the same video playing,
  letterboxed; PiP from fullscreen shows the video (not black), the PiP
  Play/Pause action works, and expanding PiP returns to fullscreen. File
  upload (photo picker) still works through the extended chrome client.
  Facebook offers its fullscreen button on landscape videos; portrait ones
  open in its own Reels-style viewer instead.
- PiP from fullscreen with the phone in landscape: WebView itself ends
  fullscreen right after PiP starts (the display turns to portrait for the
  home screen, which Chromium treats as rotating out of a fullscreen video).
  Facebook's viewer reacted to that by re-rendering at the PiP window size
  and discarding the video, so the PiP window went black and the app came
  back with the page squeezed to that size. While in PiP the page now
  doesn't see that `fullscreenchange`; focus mode is applied once
  fullscreen has ended, and on return the event is replayed once the window
  has settled, so Facebook lays out at the full size.

## CI/CD

- `./gradlew test`, `:app:lintDebug`, and `connectedAndroidTest` now gate
  every build — previously nothing ran tests or static Android checks before
  signing/releasing.
- Android lint runs as its own CI job and uploads HTML, XML, and text reports
  as a workflow artifact, including on failure. Dependency-update notices are
  advisory because the current pinned libraries target `compileSdk 36`; the
  latest available versions require the newer compile SDK.
- Split into `ci.yml` (PR validation) and `create-release.yml` (tag/dispatch
  only, calls `ci.yml` as a reusable workflow) — signing secrets no longer
  touch PR runs, and PR checks show correctly instead of "Create Release".
- AVD image + boot snapshot cached — first run populates it, later runs
  skip re-downloading the emulator/system-image and the cold boot.
- Releases now auto-version from the merge commit's conventional-commit
  prefix (`feat:` → minor, `fix:`/`chore:`/etc → patch, `feat!:`/
  `BREAKING CHANGE` → major) instead of manual tagging.
- A push to `main` that only changes docs (Markdown, `LICENSE`, the
  `fastlane/` store listing, README images in `assets/`) no longer publishes
  a release; its commits roll into the next code release's version bump.
- APK's actual `versionName`/`versionCode` now baked in from the resolved
  release tag at build time — previously hardcoded to a permanent `1.0.0`
  regardless of the real release version.
- Release APK filename cleaned up to `Astryxbook-<version>.apk`.
- Combined test coverage report (JaCoCo, unit + instrumented merged) added
  as a downloadable CI artifact.

## Testing

Added test coverage for the rebrand and default-behavior changes: settings
defaults, theme colors, app identity/strings, launcher icon, applicationId,
and the pinned external script source — none of which existed upstream.

Also covers the PiP focus-mode/toggle/freeze JS (`PipFocusModeJsTest`, 24
tests driven against a real `WebView` with synthetic DOM fixtures rather
than live Facebook):

- Non-kept siblings hidden at every ancestor level, not just `body`'s direct
  children; the video's own ancestor chain staying untouched.
- The download button (`download_content.js`) hidden via inline override
  in PiP and restored on exit, including the case where its own
  higher-specificity `#id.visible` stylesheet rule would otherwise win.
- The clipboard-copy button (`copy_to_clipboard.js`) hidden/restored the
  same way — same root cause, same fix, found by reading its source rather
  than live DevTools.
- Toggle targeting the locked-in video instead of re-deriving "the active
  video" by area, with both a marked-video case and a fallback case.
- The active-video freeze installed at PiP-entry: blocks writes while
  frozen, preserves whatever was already tracked at install time, resumes
  on unfreeze.
- Restore cleanup — all PiP-mode DOM markers actually removed on exit.
- The video sized to the visual viewport (wide ancestors left untouched),
  re-fitted on viewport resize, and the resize listener removing itself
  after restore.
- The screen pin: values held from pin time, idempotent, native getters
  restored on unpin with a single `resize` fired.
- Holding `fullscreenchange` back during PiP and replaying exactly one
  after PiP, only once resize events have settled.
- The anomaly scan (below): a clean-page case reporting nothing, and a case
  where a deliberately-unhideable element (inline `!important`, the same
  trick that caused the two button leaks) is correctly caught and named.

Lock-screen audio, fullscreen and auto-desktop have their own suites:

- `PipHandoffJsTest` (12, real `WebView`): the snapshot taken at page hide
  even when the video is paused right after, mute/pause and restore of the
  prior mute state, the visible-again signal only while a handoff is active,
  handback position/play behaviour, and listener cleanup/re-arming.
- `LockScreenHandoffTest` (8): handoff eligibility (`https://` only, not
  `blob:`/empty, not already paused) and parsing of the page's answer.
- `FullscreenControllerTest` (6): the show/hide contract (second show
  dismissed, hide without show is a no-op, WebView told exactly once).
- `AutoDesktopTest` (11): the effective-desktop rule, the one-time
  migration of the old persisted auto decision, and the orientation rule
  (phones portrait except fullscreen video, large screens unlocked).
- `SettingsDefaultsTest` and `PipManifestTest` extended: lock-screen audio
  off by default; the service declared with `foregroundServiceType`
  mediaPlayback and exported.

Facebook's own player behavior (the re-pause limitation below) is
deliberately out of scope — external, unfixable from here.

## Messages in desktop mode

Facebook's mobile site (`m.facebook.com`) no longer renders Messages (it shows a
"Download Messenger" page). When the setting is on, the Messages section is shown inside
the app using the desktop site, only for that section.

- Every Messages/Messenger entry point (the Messages tab, `facebook.com/messages`,
  `m.me`, `messenger.com`, `fb-messenger://`, `intent://`) opens the desktop Messages
  page. Links that point at a conversation keep it (`/messages/t/<id>`, `m.me/<name>`,
  `messenger.com/t/<id>`, `fb-messenger://user/<id>`); anything else opens the inbox.
- The desktop user agent applies only while Messages is open. Leaving it, either by a
  real page load or by an in-page navigation of the desktop single-page app
  (reported by `messages_tab.js` through `MessagesBridge`, since WebView does not report
  `pushState`/`popstate` as page loads), switches back to the normal user agent and
  reloads the page (no duplicate history entry).
- `messages_tab.js` hooks the Messages tab so the "Download Messenger" page never
  flashes; it recognises the tab by its English label or its icon glyph. There is no
  position check on purpose: if Facebook changed both, matching by position could hijack
  another tab, and a miss only leaves the "Download Messenger" page as before.
- Setting **Messages in desktop mode** (off by default, opt-in). Off keeps the previous
  behavior, including `fb-messenger://` and `intent://` links reaching the Messenger app.
  The flag is saved across Activity recreation.
- `MessagesDesktopTest` covers the URL helpers.

## Picture-in-Picture

New feature, opt-in (off by default): shrinks into a floating window when
leaving the app while a Facebook video or Reel is playing.

- Detects playback via a lightweight JS bridge (ignores muted feed autoplay).
- Native resume control (Play/Pause) on the PiP overlay, since
  Android/Chromium auto-pauses WebView video on PiP entry and there's no
  public API to override that.
- Page chrome hidden while in PiP so only the video shows, filling the
  window — not just a shrunk copy of the whole page.
- Portrait-video window ratio is selectable from Settings: 4:7 (the
  recommended default), 2:3, 3:4, or 9:16. Changes are applied immediately.
- Landscape videos auto-match their detected source ratio (including 7:4, 4:3,
  and 16:9), clamped to Android's supported PiP range, with 16:9 fallback
  when dimensions are unavailable.
- The 4:7 default trades a small top/bottom crop for reliable sizing on
  devices that render a true 9:16 window oversized or clipped off-screen.
- On Android 12 and newer, the WebView bounds are supplied as the PiP source
  rectangle to preserve smooth system transitions.
- Two other injected UI elements (the download button, the clipboard-copy
  button — see Testing above) are force-hidden while in PiP and restored on
  exit, since they're wanted controls in the normal view but obstructive in
  a small floating window.
- A permanent, silent-unless-triggered sanity check runs after PiP's page
  chrome is hidden: if anything is still unexpectedly visible, it's reported
  by tag/id/class through the existing `AstryxbookPiP` logcat channel. This
  class of bug (an element leaking into the PiP window past known hiding
  rules) has come up three times — the page toolbar, the download button,
  the clipboard-copy button — each only found by live DevTools or reading
  the offending script's source. This lets the *next* one be diagnosed from
  an ordinary field `adb logcat` capture instead of needing a reproducible
  live session.
- The video fills the PiP window exactly: it's sized to the visual viewport
  (the visible area) in px and re-fitted whenever that resizes. Neither CSS
  unit worked everywhere: on the Reels tab Facebook's reel wrappers (the
  video's own ancestors, deliberately left untouched) keep their full-screen
  width, Chromium zooms the page out and a `100vw` video covered only the
  window's top-left corner; for a video playing inline in the feed the
  fixed-position containing block keeps its full-screen size, so `100%` made
  the PiP window a zoomed-in crop. Resizing the wrappers also fixed the zoom
  but made Facebook pause the reel.
- Rotating the phone during PiP keeps the reel: the PiP window doesn't
  change size, but Facebook read the rotated screen on `resize` and dropped
  the reel for `/watch/`, laid out at PiP size. While in PiP the page now
  reads the screen values from PiP entry (`screen` size, orientation,
  `window.orientation`); the real ones are restored, with one `resize`, on
  exit.
- "Keep audio when screen locks" (opt-in, off by default, shown under the
  PiP settings): locking the screen while a video is in PiP hands its audio
  off from the WebView to a native Media3 player in a `mediaPlayback`
  foreground service, with lock-screen media controls. Unlocking (or
  returning to the app) hands playback back to the WebView video at the same
  position, resuming only if it was still playing. The page video stays
  muted in between, since Facebook restarts it on its own after unlock. The
  service only accepts this app's own controller (trusted system controllers
  get transport controls only). It is bound idle at PiP entry and only goes
  to the foreground at screen-off, so no notification shows during ordinary
  PiP use.

## Known limitations

- On Reels specifically, Facebook's own web player can re-pause a video
  shortly after it's resumed from the PiP overlay's Play button. Facebook's
  player enforces which single video is allowed to play at a time on its own
  side, independent of anything this app does, so this isn't something we
  can override from here.
- Opening a video from the feed wall via Facebook's own tap-to-fullscreen
  viewer (as opposed to a Reel, or a video playing inline in the feed) can
  show a black PiP window: Facebook's own screen-navigation system hides an
  ancestor of the video (`display:none`) when the app backgrounds, treating
  it the same as the user leaving that screen. Forcing that ancestor back to
  a renderable display works only briefly - the video shows correctly for a
  moment, then goes black again, most likely because Facebook replaces the
  element with a fresh DOM node during its own teardown rather than mutating
  the one we're holding onto, which a style/class-attribute-watching
  `MutationObserver` can't catch. Chasing DOM node replacement itself would
  mean broad `childList`/subtree observation and re-running detection on
  every hit, with no guarantee of actually winning against Facebook's own
  process - not attempted, for the same reason as the Reels re-pause above.
  Reels playing from the Reels tab, and videos playing inline in the feed
  wall, are unaffected. Real fullscreen (Facebook's fullscreen button on a
  landscape video, see WebView above) is unaffected too: its PiP shows the
  video.
- On large screens (tablets, which still rotate freely, see Default
  behavior changes) Facebook's mobile site handles a real landscape page
  badly: rotating while watching Reels makes it skip to another video and
  switch to `/watch/`, and leaving fullscreen in landscape leaves its viewer
  showing an oversized still frame instead of the video (both also happen
  in Chrome on m.facebook.com). Phones avoid both by staying portrait.
- When a reel or fullscreen video ends, Facebook moves on by itself: in PiP
  the window can go empty (the finished video collapsed, or the page went
  back to the feed), and a fullscreen video auto-advances and leaves
  fullscreen.
- After lock-screen audio hands back on unlock, the page video is told to
  resume, but on Reels Facebook can pause it again right away (the same
  re-pause behavior as the first item).
- Lock-screen audio plays Facebook's signed video URL directly, and those
  URLs expire. If one expires during a long lock, native playback stops
  (no refresh); on unlock the WebView video is restored at the last known
  position. It also only covers videos with a plain `https://` source, not
  `blob:` (MSE) streams.
- Store listing screenshots (`fastlane/metadata/.../phoneScreenshots/`)
  removed as stale; not replaced yet (need real device captures).