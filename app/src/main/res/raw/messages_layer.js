// Messages layer (desktop site in its own WebView over the feed): tell the app when the
// page navigates in-page (pushState/replaceState/popstate) to somewhere outside Messages,
// so it can close the layer and open that page in the main, mobile view. WebView does not
// report those navigations as page loads.
(() => {
  if (window.__mbMessagesLayerHooked) return;
  window.__mbMessagesLayerHooked = true;

  const check = () => {
    try {
      if (/^\/(messages|messenger|login|checkpoint)/.test(location.pathname)) return;
      if (window.MessagesBridge) window.MessagesBridge.onLeftMessages(location.href);
    } catch (e) {}
  };
  ['pushState', 'replaceState'].forEach((k) => {
    const orig = history[k];
    history[k] = function () {
      const r = orig.apply(this, arguments);
      setTimeout(check, 0);
      return r;
    };
  });
  window.addEventListener('popstate', check);
})();
