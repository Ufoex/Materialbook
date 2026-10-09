<p align="middle">
    <img src='./fastlane/metadata/android/en-US/images/featureGraphic.png' alt="Faceboost banner" width="100%">
</p>

<h1 align="middle">
    📱 Download
</h1>

<p align="middle">
    <a href='https://github.com/Ufoex/Materialbook/releases/latest'><img alt='Download' height='40' src='./assets/download.svg'/></a>
</p>

<p align="middle">
    <a href='https://grev.shehryar.ae/?owner=Ufoex&repo=Materialbook'><img alt='Downloads count' height='40' src='https://img.shields.io/github/downloads/Ufoex/Materialbook/total?style=for-the-badge&logo=github&label=Downloads&color=1877F2&labelColor=24292F'/></a>
</p>

<h2 align="middle">
    🙋 Have issues?
</h2>

<p align="middle">
    <a href='https://github.com/Ufoex/Materialbook/issues/new/choose'><img alt='Open issue' height='40' src='./assets/open_issue.svg'/></a>
</p>

<h2 align="middle">
    ✏️ About this repo
</h2>

The app is called **Faceboost** (formerly Materialbook; the package name and the repo keep the old name so updates keep working).

This is **not an original project**. It is a collection repo where the changes from other
[Materialbook](https://github.com/eepiemi/Materialbook) forks are brought together in one app.
Almost all the code was written by the people below; this repo only merges it and builds the APK
(a new release is published on every push to `main`).

| Original author | Project | What it contributed |
|---|---|---|
| [eepiemi](https://github.com/eepiemi) | [Materialbook](https://github.com/eepiemi/Materialbook) | The base app |
| [ofirc73](https://github.com/ofirc73) | [AstryxBook](https://github.com/ofirc73/AstryxBook) | PiP for reels, lock-screen audio, fullscreen video, portrait on phones, the Messages layer, safer file downloads |
| [dh6k](https://github.com/dh6k) | [Materialbook_fork](https://github.com/dh6k/Materialbook_fork) | Messenger redirect, external links, adblock hardening, photo viewer and feed performance fixes |
| [farhun1](https://github.com/farhun1) | [BraveBook](https://github.com/farhun1/BraveBook) | Brave block list ad/tracker blocking, hide group posts |
| [Kdomy](https://github.com/Kdomy) | [LiteBook](https://github.com/Kdomy/LiteBook) | Copy post text, reliable video/reel downloads, AMOLED login fix |
| [Ufoex](https://github.com/Ufoex) | This fork | Reel controls, auto-scroll and screen-off audio for reels, a steadier reel viewer, collapsing toolbar, update check |

See [FORK_CHANGES.md](./FORK_CHANGES.md) for the details. Material You theming and AMOLED Black ship off by default (from AstryxBook); both are toggles in Settings.

<h2 align="middle">
    ⚙️ Features
</h2>

Most features are toggles in the app's Settings.

On by default:
*  Blocks sponsored ads
*  Keeps the navigation bar at the top
*  Downloads media or copies it to the clipboard
*  Long-press the app icon for shortcuts to Messages, Search, Marketplace, Reels and Notifications
*  Opens Facebook links from other apps, including `fb.me`, `fb.com` and the Facebook app's `fb://` links
*  Checks GitHub for a new version when the app opens (Settings → *Check for updates on start*); *Check for updates now* does it on demand, and the dialog downloads the new APK
*  Reel controls: time, seek bar, fullscreen and volume on paused reels, a button to hide the reel icons and one to switch between filling the screen and showing the whole video

Off by default (opt-in):
*  Uses Material You colors instead of Facebook's blues (Facebook's own blue is the default theme)
*  Makes Facebook AMOLED Black
*  Hides distractions like:
    *  Suggested posts
    *  Reels
    *  Stories
    *  Groups
    *  People you may know
*  Hides the system bars (immersive mode), or only the navigation bar / gesture line (Hide navigation bar)
*  Auto-scrolls reels: goes to the next reel when the current one ends, with a button in the reel viewer to pause it
*  Keeps a reel's audio playing with the screen off, with a button in the reel viewer
*  Collapsing toolbar: the top bars hide when scrolling down and return when scrolling up (needs Sticky Navbar)
*  Allows pinch-to-zoom anywhere
*  Lets you access Messenger (Desktop layout only)
*  Opens Messages inside the app on the desktop site, in its own layer over the feed, so Back returns you to where you were (Settings → *Messages in desktop mode*); links to a conversation keep it
*  Supports Picture-in-Picture playback with selectable portrait-window ratios
*  Keeps a Picture-in-Picture video's audio playing when the screen locks

Always on:
*  Keeps the reel you are watching in place when Facebook rebuilds the reel list or an ad is hidden, and fades the thumbnail into the video when it starts
*  Supports real fullscreen video, including landscape, from Facebook's fullscreen button
   (on phones the app otherwise stays in portrait, since Facebook's mobile site breaks in landscape)
*  Fullscreen video rotates by the sensor, even with the system auto-rotate off
*  Supports English, Arabic, Bengali, German, Hebrew (`עברית`), Italian, Spanish, French, Portuguese, and Traditional Chinese
*  And more!

<h2 align="middle">
    🖼️ Picture-in-Picture
</h2>

To use Picture-in-Picture:

1. Enable **Picture-in-Picture** in the app's Settings.
2. Tap **PiP aspect ratio** (shown when PiP is enabled) and choose the window ratio for portrait videos:
   * **4:7** (recommended; default)
   * **2:3**
   * **3:4**
   * **9:16** (may overflow on some devices)
3. If needed, also allow PiP in Android under **Settings > Apps > Materialbook > Picture-in-picture**.
4. Open a Facebook video or Reel and start playback.
5. Keep the video visible and audible, then use the Home gesture or Home button.

On Android 12 and newer, PiP is configured for a smooth automatic transition as you leave
the app. On older supported Android versions, PiP is entered when Android notifies the app
that it is being left.

The app ignores muted feed autoplay and selects the largest visible audible video when
multiple videos are present. Landscape videos use their detected source ratio when available,
including ratios such as 7:4, 4:3, and 16:9, with 16:9 fallback when dimensions are unavailable.
Portrait videos use the ratio selected in Settings, and a changed selection is applied immediately
to the active video without restarting the app.

The default 4:7 ratio is a reliable compromise: it stays close to the video's true 9:16
shape while avoiding the oversized or off-screen window seen on some devices. The wider
4:7, 2:3, and 3:4 windows crop the top and bottom as needed to fill the PiP window without
black bars. Selecting 9:16 preserves the exact video shape, but the window may overflow on
some devices. Extreme detected landscape ratios are clamped to Android's supported PiP range.

The page itself is hidden while in PiP so only the video shows, filling the window.
Rotating the phone while a video is in PiP keeps the same video playing.

To keep listening with the screen locked, also turn on **Keep audio when screen locks**
(shown under the PiP settings). Locking the screen while a video is in PiP then hands its
audio to a native player, with media controls on the lock screen. Unlocking hands it back to
the video at the same position. Facebook's video links expire after a while, so audio during
a very long lock can stop; the video is still restored where it left off.

Android/Chromium auto-pauses the video the moment PiP starts (a platform limitation, not
something an app can override) — tap the Play button on the PiP overlay to resume it.

If PiP does not appear, confirm that the video is actively playing with volume above zero
and that both the app and Android system PiP permissions are enabled.

<h2 align="middle">
    🛠️ Setup
</h2>

Materialbook requires Android 8.0 (API level 26) or newer.

1.  **Clone the repository**
    * In Android Studio:
      * File > New > Project from Version Control
      * Paste `https://github.com/Ufoex/Materialbook.git` and clone.
    * Or via terminal:
    ```
    git clone https://github.com/Ufoex/Materialbook.git
    cd Materialbook
    ```
2.  **Open in Android Studio.** (only if cloned via terminal)
    * Select Open an Existing Project and choose the cloned folder.
3.  **Sync the project** to download dependencies.
4.  **Run the app** in a device or emulator.

<h2 align="middle">
    ✅ Testing
</h2>

Covers the rebrand and default-behavior changes in this fork — defaults (Material You/AMOLED off), theme colors, app identity/strings, launcher icon, applicationId, and the pinned scripts source.

*  **Unit tests** (no device/emulator needed): `./gradlew test`
*  **Android lint** (no device/emulator needed): `./gradlew :app:lintDebug`
*  **Instrumented tests** (needs a connected device or running emulator): `./gradlew connectedAndroidTest`

GitHub Actions runs all three checks. Android lint reports are uploaded as a workflow artifact for every run. The lint gate treats dependency-update notices as advisory while still failing on actionable Android findings.

<h2 align="middle">
    💗 Acknowledgement:
</h2>

*  This is a fork of [Materialbook](https://github.com/eepiemi/Materialbook) by eepiemi, itself a fork of [Nobook](https://github.com/ycngmn/Nobook) by ycngmn
*  [@KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)
