// Collapsing toolbar: hide Facebook's top bars (logo bar + tab bar, pinned by
// sticky_navbar.js) when scrolling down and bring them back on any scroll up.
(function() {
    if (window.isDesktopMode && window.isDesktopMode()) return;
    if (window.__mbCollapsingToolbar) return;
    window.__mbCollapsingToolbar = true;

    const THRESHOLD = 6;   // px of movement before a direction change counts
    let hidden = false;

    const bars = () => [window._mbFeedNavbar, window._mbFeedTabbar].filter(b => b && b.isConnected);

    const setHidden = (hide) => {
        const els = bars();
        if (!els.length) return;
        // Only hide by their own height so nothing else (status bar area) shifts.
        const total = els.reduce((h, el) => h + el.offsetHeight, 0);
        els.forEach(el => {
            el.style.transition = 'transform 0.2s ease';
            el.style.transform = hide ? `translateY(-${total}px)` : '';
        });
        hidden = hide;
    };

    // The feed scrolls the document itself (the vscroller keeps scrollTop 0), so
    // follow the document scroll; scroll events don't bubble, hence window.
    let prev = 0;
    window.addEventListener('scroll', () => {
        if (window.location.pathname.includes('/photo.php')) return;
        const top = document.scrollingElement ? document.scrollingElement.scrollTop : window.scrollY;
        const dy = top - prev;
        if (top <= 0) { prev = top; if (hidden) setHidden(false); return; }
        if (Math.abs(dy) < THRESHOLD) return;
        prev = top;
        const h = bars().reduce((a, el) => a + el.offsetHeight, 0);
        if (dy > 0 && top > h && !hidden) setHidden(true);
        else if (dy < 0 && hidden) setHidden(false);
    }, { passive: true });
})();
