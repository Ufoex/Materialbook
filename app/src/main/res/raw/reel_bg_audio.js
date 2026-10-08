// "Reel audio with screen off": reel_controls.js draws the viewer's audio button when this is
// injected (it starts off at every launch). While the button is on and a reel is on screen the
// app keeps the WebView's window "visible", so Chromium does not pause the reel at screen-off.
(function() {
    if (window.__mbBgAudio) return;
    const state = window.__mbBgAudio = { enabled: false, sent: false };
    setInterval(() => {
        const p = location.pathname;
        const onReel = p.indexOf('/reel/') === 0 ||
            (p.indexOf('/videos/') !== -1 && !!document.querySelector('.vertically-snappable video'));
        const want = state.enabled && onReel;
        if (want === state.sent) return;
        state.sent = want;
        try { BgAudioBridge.set(want); } catch (e) {}
    }, 250);
})();
