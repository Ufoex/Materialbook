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

- Auto desktop layout now triggers only on genuinely large screens
  (`smallestScreenWidthDp >= 600`), no longer on a phone that happens to be
  in landscape at app start. The automatic decision is computed at runtime
  and never persisted; only the user's own Desktop layout toggle is saved.
  Previously a phone launched once while sideways could get stuck in the
  desktop layout (desktop user agent, garbled Reels, broken PiP) until the
  user turned it off manually. A one-time migration unsticks such installs:
  a desktop setting that was written by the old auto logic is reset to off,
  while one the user chose themselves is kept.

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

Also covers the PiP focus-mode/toggle/freeze JS (`PipFocusModeJsTest`, 15
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
- The anomaly scan (below): a clean-page case reporting nothing, and a case
  where a deliberately-unhideable element (inline `!important`, the same
  trick that caused the two button leaks) is correctly caught and named.

Facebook's own player behavior (the re-pause limitation below) is
deliberately out of scope — external, unfixable from here.

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
  wall, are unaffected.
- Store listing screenshots (`fastlane/metadata/.../phoneScreenshots/`)
  removed as stale; not replaced yet (need real device captures).