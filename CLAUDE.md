# Astryxbook

Android wrapper around m.facebook.com (Kotlin, Jetpack Compose,
compose-webview-multiplatform, Media3). Package `com.eepiemi.materialbook`,
applicationId `com.astryx.book`. Fork-specific changes are documented in
`FORK_CHANGES.md` (upstream's own log is `CHANGE.md`); update it with every
user-visible change.

## Build, install, test (Windows / PowerShell)

- Release build for a device: `.\gradlew.bat assembleRelease "-PversionNameOverride=99.99.N"`.
  The quotes are required in PowerShell. A plain local build is `versionCode`
  13, lower than the installed app, so `adb install -r` needs the override
  (bump N for each install).
- Debug builds install as a separate app (`com.astryx.book.test`). Use it
  for device checks when the phone has an official (release-key) build:
  a local release APK won't install over it. `connectedDebugAndroidTest`
  uninstalls that app when it finishes (losing its Facebook login), so run
  instrumented tests before or after a device session, not in the middle.
- `.\gradlew.bat testDebugUnitTest` (use the variant task; `--tests` filtering
  doesn't work on plain `test`), `.\gradlew.bat connectedDebugAndroidTest`
  (needs a device), `.\gradlew.bat lintDebug`. CI (`ci.yml`) gates on
  `test`, `:app:lintDebug` and `connectedAndroidTest`.
- Pushing to `main` runs `create-release.yml` (which calls `ci.yml`); it
  auto-versions from conventional commits (`feat:` = minor bump) and
  publishes a release. A docs-only push (`*.md`, `LICENSE`, `fastlane/`,
  `assets/`) skips it via `paths-ignore`.

## WebView and PiP code (`ui/screens/MaterialbookWV.kt`)

- compose-webview's navigator replays only its *last* event to a WebView that
  isn't attached yet. Never call `navigator.evaluateJavaScript` unconditionally
  at composition: guard on `state.loadingState is LoadingState.Finished` or a
  trigger counter `> 0`, or it replaces the initial `loadUrl` and the page
  never loads.
- Native -> JS uses Compose trigger counters/state passed from `MainActivity`
  into `MaterialbookWebView`; JS -> native uses `PipBridge`.
- PiP focus mode (`PIP_FOCUS_MODE_JS` / `PIP_RESTORE_MODE_JS`) was tuned
  through live DevTools sessions and is covered by `PipFocusModeJsTest`. Never
  restyle or resize the video's ancestor elements: Facebook's reels controller
  watches them and pauses the reel. Add new PiP behaviour as separate scripts
  and effects instead of editing focus mode.
- The page scripts in `res/raw` (except the PiP ones loaded directly in
  `MaterialbookWV.kt`) are fetched at runtime from `main` on GitHub
  (`SCRIPT_SRC` in `fetchScripts.kt`), falling back to the bundled copy. A
  push to `main` changes them in every installed app on its next launch, and
  a local build still runs `main`'s version of a script that exists there.
- All PiP, lock-screen audio and fullscreen logs use the tag `AstryxbookPiP`:
  `adb logcat AstryxbookPiP:D *:S`.
- `WebView.setWebContentsDebuggingEnabled(true)` is unconditional on purpose.
  Live debugging on the device: forward `localabstract:webview_devtools_remote_<pid>`
  and use the DevTools protocol (`/json`, `Runtime.evaluate`).

- Facebook's video viewer re-lays out only on a real fullscreen transition
  (`fullscreenchange`), not on `resize`/`orientationchange`, and writes its
  widths into inline styles. Chromium ends HTML5 fullscreen by itself when
  PiP starts from landscape (see `PIP_HOLD_FULLSCREENCHANGE_JS`).

## Facebook behaviour, not ours

Verify against Chrome/Opera on the phone before "fixing" these: Reels can
re-pause a resumed video; finished videos auto-advance (and leave
fullscreen). A real landscape page is broken on Facebook's side (reel skip
on rotation, oversized still frame after leaving fullscreen), which is why
phones are held in portrait except during fullscreen video
(`appOrientation`). See Known
limitations in `FORK_CHANGES.md`.

## Repo housekeeping

- Plan files (`*_PLAN.md`) and `spike.log` are working notes; don't commit them.
- The `stats` branch is data-only (weekly release download counts written by
  `download-stats.yml`); never merge it into `main`.
- On Windows, `git worktree remove` can fail with "Filename too long"; delete
  the folder with PowerShell, then `git worktree prune`.
