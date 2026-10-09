## Faceboost (Ufoex fork, formerly Materialbook) - v1.1.0

<ins>**Changelog:**</ins>

* Feature: Merged the improvements of the AstryxBook, dh6k, BraveBook and LiteBook forks (see FORK_CHANGES.md).
* Feature: Auto-scroll reels (setting): goes to the next reel when the current one ends; a button in the reel viewer pauses it.
* Feature: Reel audio with the screen off (setting, off by default): adds an audio button to the reel viewer that keeps the reel playing when the screen turns off.
* Feature: Check for updates on start (setting, on by default) and a Check now button in Settings; the dialog downloads the new APK and opens the system installer (Android asks once to allow installing from Faceboost).
* Fix: Reels no longer jump to another reel when Facebook rebuilds the list; the thumbnail fades out only when a video starts, not when it resumes.
* Fix: The text typed in the post box (groups and others) is visible in Facebook's dark mode instead of black on black.
* Feature: The app opens more Facebook links: web/mobile/touch/mbasic.facebook.com, fb.com, fb.me and the Facebook app's fb:// and facebook:// links (profile, page, group, event, reel, marketplace...).
* Feature: App renamed to Faceboost with a new icon (the applicationId stays `com.ufoex.materialbook`, so updates keep replacing the installed app).
* Feature: Long-press the app icon for shortcuts: Messages, Search, Marketplace, Reels and Notifications. A link opened from outside (a shortcut) now closes the Messages layer and loads again even if it is the same link twice.
* Feature: Notifications permission is asked the first time the app opens (Android 13+), with a Notifications row in Settings that shows the state and opens the system notification settings.
* Feature: Hide navigation bar (setting): hides the on-screen buttons or the gesture line, keeping the status bar; swipe from the bottom edge to show it.
* Feature: Settings translated to every language of the app (Arabic, Bengali, German, Spanish, French, Italian, Hebrew, Polish, Portuguese, Traditional Chinese).
* Fix: Fullscreen video rotates by the sensor even when the system auto-rotate is off, instead of going back to portrait.
* Tweak: New applicationId `com.ufoex.materialbook`; runtime scripts are fetched from this repo.

---

## Materialbook - v1.0.0

<ins>**Changelog:**</ins>

* Feature: Add 'Material You' setting that themes Facebook's blues using your MY colors.
* Feature: Hide more login screen distractions.
* Tweak: Settings are now a full page, with visual sections of settings. (upstream)
* Tweak: Settings use Material You fallback colors on Android 10 or below.
* Tweak: Change the button at the bottom of settings to a 'Support my work! ☕' button.
* Tweak: Change settings gear's icon and color.
* Tweak: Change default settings.
* Tweak: Make loading bar's background transparent.
* Tweak: Increase settings header vertical padding
* Tweak: Make settings header use default background color
* Fix: Navigation bar's color follows the settings' background color when inside settings.
* Fix: Improve AMOLED Black.
* Non-app related: Improved the README.

> [!NOTE]
> I'm sorry this update has taken multiple months.
>
> The Material You script was not good enough to publish by my standards
> (even when it did get the job done), so as I got better I did a full rewrite
> and made it 4x shorter and increased the coverage.

> [!TIP]
> Did you find a place that isn't AMOLED Black or Material You? Comment
> in [this discussion](https://github.com/eepiemi/Materialbook/discussions/1).
>
> Or maybe you're dissatisfied with the lack of translations for some elements? Help
> me get everything translated by commenting in
> [this discussion](https://github.com/eepiemi/Materialbook/discussions/2).
> Any help with the large amount of gaps that are present will be HIGHLY APPRECIATED!!
>
> Or you just have an issue? File an issue [here](https://github.com/eepiemi/Materialbook/issues/new/choose).
> I can't guarantee I'll be able to fix it with my current Kotlin + Jetpack Compose skills though 😅

I REALLY hope you enjoy what has become the biggest project of my life! 🥹