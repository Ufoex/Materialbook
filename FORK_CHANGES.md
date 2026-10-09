# Fork changes (Ufoex/Materialbook)

Fork of [eepiemi/Materialbook](https://github.com/eepiemi/Materialbook) that combines the useful work of other forks.
All credit to eepiemi and to the fork authors below. Package: `com.ufoex.materialbook` (installs next to the original).

| Source | What was taken |
|---|---|
| [ofirc73/AstryxBook](https://github.com/ofirc73/AstryxBook) | Picture-in-Picture for reels with selectable ratios, lock-screen audio, HTML5 fullscreen video, portrait lock on phones (except fullscreen video), auto-desktop fixes, concurrent script fetch with timeout, tests |
| [dh6k/Materialbook_fork](https://github.com/dh6k/Materialbook_fork) | Open Messenger links in the Messenger app (configurable package), external links in the default browser, hardened sponsored-post detection, photo viewer fixes, feed slowdown fix (bundle inject-once guard), lighter back-navigation, hide "Open app" banner |
| [farhun1/BraveBook](https://github.com/farhun1/BraveBook) | Network-level ad/tracker blocking with the Brave block list (toggle in settings), third-party cookies off, thread-safe toasts, hide group posts from the feed |
| [Kdomy/LiteBook](https://github.com/Kdomy/LiteBook) | Copy post text on long press, reliable photo/video/reel downloads (video+audio mux), legible AMOLED login screens, `lm.facebook.com` redirects, VIEW intents while the app is open |

Own additions in this repo: a collapsing toolbar (top bars hide when scrolling down and come back when scrolling up; needs Sticky Navbar), reel controls (time, seek bar, fullscreen and volume on paused reels, a button that hides all the reel UI except the video and its progress line, and a button that switches between filling the screen and showing the whole video), auto-scroll for reels (setting; goes to the next reel when the current one ends, with a button in the reel viewer to pause it for the session), reel audio with the screen off (setting; adds a button in the reel viewer, off by default, that keeps the reel's audio playing when the screen turns off), a lighter reel ad scan that also covers every video viewer, a reel viewer that stays on the reel you are watching when Facebook rebuilds the list (and fades the thumbnail into the video when it starts), an update check on start with a Check now button in Settings (looks at this repo's latest GitHub release and offers the APK), launcher shortcuts, a notifications row and first-launch prompt, a Hide navigation bar setting, sensor rotation for fullscreen video, the Faceboost name and icon, translations of the settings, and the Messages in desktop mode setting.

Messages in desktop mode was first written here and then merged into AstryxBook (PR #6), whose maintainer reworked it: Messages opens in its own desktop WebView layer over the feed, so the feed keeps its scroll position. This fork uses that version, and keeps the setting on by default.

Not taken: the other forks' rebrands, Astryx's/dh6k's overlapping orientation and auto-desktop logic, BraveBook's own fullscreen manager (Astryx's is used),
LiteBook's `MIXED_CONTENT_ALWAYS_ALLOW` (insecure).

Runtime scripts are fetched from this repo's `main` (`SCRIPT_SRC` in `fetchScripts.kt`) with the bundled copy as fallback.
