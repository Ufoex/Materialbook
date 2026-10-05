<p align="middle">
    <img src='./fastlane/metadata/android/en-US/images/featureGraphic.png' alt="Materialbook banner" width="100%">
</p>

<h1 align="middle">
    📱 Download
</h1>

<p align="middle">
    <a href='https://github.com/dh6k/Materialbook/releases/latest'><img alt='Download' height='40' src='./assets/download.svg'/></a>
</p>

<h2 align="middle">
    🙋 Have issues? Enjoy the app? 🎁
</h2>

<p align="middle">
    <a href='https://github.com/dh6k/Materialbook/issues/new/choose'><img alt='Open issue' height='40' src='./assets/open_issue.svg'/></a>
    <a href='https://buymeacoffee.com/eepiemi'><img alt='Donate to the original author!' height='40' src='./assets/buy_me_a_coffee.svg'/></a>
</p>

> **Donations go to [eepiemi](https://buymeacoffee.com/eepiemi), the original author of Materialbook — not to this fork's maintainer.**

<h2 align="middle">
    🍴 About this fork
</h2>

This is a fork of [eepiemi/Materialbook](https://github.com/eepiemi/Materialbook) maintained by [dh6k](https://github.com/dh6k).
All credit for the original app — idea, design, and the bulk of the code — belongs to eepiemi.

Fork-only changes (on top of upstream v1.0.0):
* Open Messenger links directly in the Messenger app, with a configurable package name in settings
* New "Open Messenger" item at the top of Materialbook Settings
* Harden adblock with structural signals, catch obfuscated Sponsored + paid-partnership labels
* Rebranded application ID (`com.eepiemi.materialbook`) so it installs alongside the original

<h2 align="middle">
    🤖 AI disclosure
</h2>

To be fully transparent: **the fork-only changes above were written with heavy AI assistance.**
An AI coding assistant (Muse Spark, via an agentic harness) implemented the Messenger integration,
the adblock hardening, the rebrand, and this README — iteratively, with the maintainer testing
each change on a real device and directing every decision. The upstream codebase it builds on is
100% human-made by eepiemi and contributors. AI-written code was reviewed by building, installing,
and exercising it on-device before each commit — but expect AI-shaped rough edges, and please
[file an issue](https://github.com/dh6k/Materialbook/issues/new/choose) if you find any.

<h2 align="middle">
    ⚙️ Features
</h2>

If enabled, the app:
* Uses Material You colors instead of Facebook's blues
* Makes Facebook AMOLED Black
* Blocks sponsored ads
* Hides distractions like:
    * Suggested posts
    * Reels
    * Stories
    * Groups
    * People you may know
* Keeps the navigation bar at the top
* Downloads media or copies it to the clipboard
* And more!

<h2 align="middle">
    🛠️ Setup
</h2>

1.  **Clone the repository**
    * In Android Studio:
      * File > New > Project from Version Control
      * Paste `https://github.com/dh6k/Materialbook.git` and clone.
    * Or via terminal:
    ```
    git clone https://github.com/dh6k/Materialbook.git
    cd Materialbook
    ```
2.  **Open in Android Studio.** (only if cloned via terminal)
    * Select Open an Existing Project and choose the cloned folder.
3.  **Sync the project** to download dependencies.
4.  **Run the app** in a device or emulator.

<h2 align="middle">
    💗 Acknowledgement:
</h2>

* [eepiemi/Materialbook](https://github.com/eepiemi/Materialbook) — the original app this fork builds on
* [@KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)
