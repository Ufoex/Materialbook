// Messages in desktop mode, mobile page side: hook the Messages tab so it goes straight to
// a messages URL, which the app opens in the Messages layer. Without the hook the tab still
// gets there through its own fb-messenger://threads link, which is intercepted too; the
// hook just skips Facebook's detour through the Messenger app link.
(() => {
  if (window.__mbMessagesTabHooked) return;
  window.__mbMessagesTabHooked = true;

  // Locale independent: the English label, or the tab's icon glyph. No position check:
  // if Facebook changed both, matching by position could hijack another tab, and a miss
  // only falls back to the intercepted fb-messenger:// link.
  const MESSAGES_GLYPH = '\u{F0388}';
  const isMessagesTab = (t) => {
    const tab = t && t.closest && t.closest('[role="tab"]');
    if (!tab) return null;
    if (/^messages\b/i.test(tab.getAttribute('aria-label') || '')) return tab;
    if ((tab.textContent || '').indexOf(MESSAGES_GLYPH) !== -1) return tab;
    return null;
  };

  let last = 0;
  const go = (e) => {
    if (!isMessagesTab(e.target)) return;
    e.stopImmediatePropagation();
    // First of pointerup/click wins; touchend/mouseup are only swallowed.
    if ((e.type === 'pointerup' || e.type === 'click') && Date.now() - last > 1000) {
      last = Date.now();
      window.location.href = 'https://m.facebook.com/messages/';
    }
  };
  ['click', 'touchend', 'pointerup', 'mouseup'].forEach(
    (type) => document.addEventListener(type, go, true)
  );
})();
