# Fork changes (Ufoex/Materialbook)

Fork of [eepiemi/Materialbook](https://github.com/eepiemi/Materialbook) that combines the useful work of other forks.
All credit to eepiemi and to the fork authors below. Package: `com.ufoex.materialbook` (installs next to the original).

| Source | What was taken |
|---|---|
| [ofirc73/AstryxBook](https://github.com/ofirc73/AstryxBook) | Picture-in-Picture for reels with selectable ratios, lock-screen audio, HTML5 fullscreen video, portrait lock on phones (except fullscreen video), auto-desktop fixes, concurrent script fetch with timeout, tests |
| [dh6k/Materialbook_fork](https://github.com/dh6k/Materialbook_fork) | Open Messenger links in the Messenger app (configurable package), external links in the default browser, hardened sponsored-post detection, photo viewer fixes, feed slowdown fix (bundle inject-once guard), lighter back-navigation, hide "Open app" banner |
| [farhun1/BraveBook](https://github.com/farhun1/BraveBook) | Network-level ad/tracker blocking with the Brave block list (toggle in settings), third-party cookies off, thread-safe toasts, hide group posts from the feed |
| [Kdomy/LiteBook](https://github.com/Kdomy/LiteBook) | Copy post text on long press, reliable photo/video/reel downloads (video+audio mux), legible AMOLED login screens, `lm.facebook.com` redirects, VIEW intents while the app is open |

Not taken: the other forks' rebrands, Astryx's/dh6k's overlapping orientation and auto-desktop logic, BraveBook's own fullscreen manager (Astryx's is used),
LiteBook's `MIXED_CONTENT_ALWAYS_ALLOW` (insecure).

Runtime scripts are fetched from this repo's `main` (`SCRIPT_SRC` in `fetchScripts.kt`) with the bundled copy as fallback.
