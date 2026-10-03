## Materialbook Fork - v1.2.7

<ins>**Changelog:**</ins>

* Fix: Links opened outside the app again (host match instead of a slash-dependent regex; Facebook root no longer bounced to the browser).
* Fix: External links kept their `#fragment` and stopped being double-encoded into a broken URL.
* Fix: `intent://` links and app deep links now resolve instead of throwing; single failure toast if nothing can open them.

## Materialbook Fork - v1.2.6

<ins>**Changelog:**</ins>

* Fix: Screen rotation no longer force-refreshes the app (upstream #10, `configChanges` on MainActivity).

## Materialbook Fork - v1.2.5

<ins>**Changelog:**</ins>

* Fix: Photo viewer first-open black/slipped screen fixed for good (viewer center loop no longer observer-dependent, early pre-scroll, late pass for slow decode).

## Materialbook Fork - v1.2.4

<ins>**Changelog:**</ins>

* Fix: Back-navigation stays light on weak devices (feed observers sleep off-feed, scans deferred off back-paint via rAF).

## Materialbook Fork - v1.2.3

<ins>**Changelog:**</ins>

* Fix: Photo viewer first-open black screen fixed properly (negative-bottom stretch reverted, photo scroll-centered, no DOM mutation persists).
* Fix: Remote scripts now fetch from the fork repo (was still pointing at upstream).

## Materialbook Fork - v1.2.2

<ins>**Changelog:**</ins>

* Fix: Feed no longer slows down the more posts you open (bundle guard + observer debounce + regex precompile).
* Fix: Hide Facebook "Mở ứng dụng / Open app" bottom banner.

## Materialbook Fork - v1.2.1

<ins>**Changelog:**</ins>

* Feature: Lock orientation toggle in Materialbook Settings.
* Fix: Photo viewer no longer black on first open with Sticky Navbar on (viewer holder margin reverted, photo re-centered).
* Fix: Banner observer redeclaration no longer aborts injected scripts on SPA navigation.

## Materialbook Fork - v1.2.0

<ins>**Changelog:**</ins>

* Feature: Open Messenger via launcher entry — no download interstitial flash, back returns to the right tab.
* Feature: Toast "Please open Messenger in Materialbook Settings." when Messenger can't open.
* Tweak: Rebranded application ID (`vip.dh6k.materialbook_fork`), installs alongside the original.
* Tweak: "Support my work" button is now "Donate to original author" (donations go to eepiemi).
* Docs: README rewritten — fork differences, honest AI disclosure, correct fork links.

## Materialbook - v1.1.0

<ins>**Changelog:**</ins>

* Feature: Open Messenger links directly in the Messenger app, with configurable package name in settings.
* Feature: New "Open Messenger" item at the top of Materialbook Settings.
* Fix: Catch obfuscated Sponsored + paid-partnership labels, incl. aria-label on desktop.
* Fix: Tolerate new FB mobile ad label without PUA marker.
* Fix: Reels hiding for alternative Unicode code point icon.
* Tweak: Harden adblock with structural signals from uBO-style filters.
* Tweak: Update app icon, Arabic translations, README.

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