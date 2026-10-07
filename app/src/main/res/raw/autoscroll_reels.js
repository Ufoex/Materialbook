// Auto-scroll reels: when the reel on screen reaches its end, go to the next one.
// Reels loop by default, so the end is detected from the playback position wrapping
// around (and from the "ended" event for players that do not loop).
// window.__mbAutoScroll.enabled lets the reel viewer button pause it for the session.
(function() {
    if (window.__mbAutoScroll) return;
    const state = window.__mbAutoScroll = { enabled: true };

    const onReelSurface = () => {
        const p = location.pathname;
        return p.indexOf('/reel/') === 0 ||
            (p.indexOf('/videos/') !== -1 && !!document.querySelector('.vertically-snappable video'));
    };

    // The video that is mostly on screen (reels are one per screen).
    const activeVideo = () => {
        let best = null, area = 0;
        document.querySelectorAll('video').forEach(v => {
            const r = v.getBoundingClientRect();
            const w = Math.max(0, Math.min(r.right, innerWidth) - Math.max(r.left, 0));
            const h = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
            if (w * h > area) { area = w * h; best = v; }
        });
        return best;
    };

    let lastAdvance = 0;
    const advance = (v) => {
        if (Date.now() - lastAdvance < 1500) return;
        const cont = v.closest('.vertically-snappable');
        if (!cont) return;
        // skip reels that were removed from the list (ads) or are not laid out
        let next = cont.nextElementSibling;
        while (next && (next.dataset.adHidden === 'true' || getComputedStyle(next).display === 'none')) {
            next = next.nextElementSibling;
        }
        if (!next) return; // the next reels have not loaded yet: the next loop will try again
        lastAdvance = Date.now();
        next.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    const lastTime = new WeakMap();
    setInterval(() => {
        if (!state.enabled || !onReelSurface()) return;
        const v = activeVideo();
        if (!v || !isFinite(v.duration) || v.duration < 2) return;
        const prev = lastTime.get(v);
        lastTime.set(v, v.currentTime);
        if (prev === undefined || v.paused) return;
        // Wrapped from the last moments back to the beginning: a loop restart, not a seek
        // (a seek back from the end of the bar is only possible while paused).
        if (prev > v.duration - 0.9 && v.currentTime < prev - v.duration * 0.5) advance(v);
    }, 200);

    document.addEventListener('ended', (e) => {
        const v = e.target;
        if (state.enabled && v instanceof HTMLVideoElement && onReelSurface() && v === activeVideo()) advance(v);
    }, true);
})();
