(function() {
  var POST_SELECTORS = [
    'div[role="article"]',
    'div[data-tracking-duration-id]'
  ];

  var TEXT_SELECTORS = [
    '[data-ad-preview="message"]',
    '.userContent',
    '.story_body_container',
    '.native-text'
  ];

  var IGNORE_SELECTORS = [
    'a',
    '[role="button"]',
    '[role="menu"]',
    '[role="dialog"]',
    '[role="textbox"]',
    '[role="link"]',
    'input',
    'textarea',
    '[contenteditable="true"]',
    '[aria-label*="menu"]'
  ];

  // Comments already expose their own long-press copy (and reactions): the
  // whole-post copy must never hijack a long-press on a comment.
  var COMMENT_SELECTORS = [
    '[role="comment"]',
    '[aria-label*="comment"]',
    '[aria-label*="Comment"]',
    '[data-pagelet*="Comment"]',
    '[data-comment-preview]',
    '[data-sigil*="comment"]'
  ];

  var HOLD_MS = 1000;
  var MOVE_TOLERANCE = 14;

  var pressTimer = null;
  var pressStartX = 0;
  var pressStartY = 0;
  var pressTarget = null;

  // Preserve the post's formatting: line breaks (including blank lines),
  // indentation and spacing are kept as-is. Only invisible characters and
  // platform-specific line endings are normalized.
  var cleanText = function(raw) {
    if (!raw) return "";
    return raw
      .replace(/\u00a0/g, " ")
      .replace(/[\u200b\u200c\u200d]/g, "")
      .replace(/\r\n/g, "\n")
      .replace(/\r/g, "\n")
      .trim();
  };

  var extractPostText = function(container) {
    for (var i = 0; i < TEXT_SELECTORS.length; i++) {
      var nodes = container.querySelectorAll(TEXT_SELECTORS[i]);
      var best = "";
      for (var j = 0; j < nodes.length; j++) {
        var t = cleanText(nodes[j].innerText || nodes[j].textContent);
        if (t.length > best.length) best = t;
      }
      if (best) return best;
    }

    var candidates = container.querySelectorAll("p, div, span");
    var best = "";
    for (var k = 0; k < candidates.length; k++) {
      var el = candidates[k];
      if (el.children.length > 0) continue;
      var text = cleanText(el.innerText || el.textContent);
      if (text.length < 30) continue;
      if (text.length > best.length) best = text;
    }
    if (best) return best;

    return cleanText(container.innerText || container.textContent);
  };

  var isComment = function(el) {
    if (!el || el.nodeType !== 1) return false;
    return !!el.closest(COMMENT_SELECTORS.join(","));
  };

  var findPostContainer = function(target) {
    if (!target || target.nodeType !== 1) return null;
    var el = target;
    while (el && el !== document.body) {
      // Any comment in the ancestry chain (even one that does not itself
      // match a post selector) blocks the whole-post copy.
      if (isComment(el)) return null;
      for (var i = 0; i < POST_SELECTORS.length; i++) {
        if (el.matches(POST_SELECTORS[i])) {
          return el;
        }
      }
      el = el.parentElement;
    }
    return null;
  };

  var isInteractive = function(target) {
    if (!target || target.nodeType !== 1) return false;
    for (var i = 0; i < IGNORE_SELECTORS.length; i++) {
      if (target.closest(IGNORE_SELECTORS[i])) return true;
    }
    return false;
  };

  var copyPostText = function(container) {
    var text = extractPostText(container);
    if (!text) return;
    if (window.ClipboardBridge && window.ClipboardBridge.copyText) {
      window.ClipboardBridge.copyText(text);
    } else {
      try {
        navigator.clipboard.writeText(text);
      } catch (e) {}
    }
  };

  var clearPress = function() {
    if (pressTimer) {
      clearTimeout(pressTimer);
      pressTimer = null;
    }
    pressTarget = null;
  };

  var onTouchStart = function(event) {
    if (event.touches.length !== 1) {
      clearPress();
      return;
    }
    if (isInteractive(event.target)) {
      clearPress();
      return;
    }
    var container = findPostContainer(event.target);
    if (!container) {
      clearPress();
      return;
    }
    var touch = event.touches[0];
    pressStartX = touch.clientX;
    pressStartY = touch.clientY;
    pressTarget = event.target;
    pressTimer = setTimeout(function() {
      pressTimer = null;
      if (pressTarget) {
        // If the browser started a native text selection (long-press + drag),
        // defer to it instead of copying the whole post.
        var sel = "";
        try {
          sel = (window.getSelection() && window.getSelection().toString()) || "";
        } catch (e) {}
        if (sel.length > 0) {
          pressTarget = null;
          return;
        }
        var c = findPostContainer(pressTarget);
        if (c) copyPostText(c);
      }
      pressTarget = null;
    }, HOLD_MS);
  };

  var onTouchMove = function(event) {
    if (!pressTimer) return;
    var touch = event.touches[0];
    if (
      Math.abs(touch.clientX - pressStartX) > MOVE_TOLERANCE ||
      Math.abs(touch.clientY - pressStartY) > MOVE_TOLERANCE
    ) {
      clearPress();
    }
  };

  var onTouchEnd = function() {
    clearPress();
  };

  var onContextMenu = function(event) {
    // Only suppress the long-press menu when our copy is actually pending: a
    // steady hold with no finger movement. If the user moved the finger (to
    // start a native text selection), the pending copy was cancelled and the
    // native selection UI must be allowed to appear. Interactive elements
    // (Like button, links, menus) always keep contextmenu so Facebook's
    // reaction picker still opens.
    if (
      pressTimer &&
      event.target &&
      !isInteractive(event.target) &&
      findPostContainer(event.target)
    ) {
      event.preventDefault();
    }
  };

  document.addEventListener("touchstart", onTouchStart, { passive: true });
  document.addEventListener("touchmove", onTouchMove, { passive: true });
  document.addEventListener("touchend", onTouchEnd, { passive: true });
  document.addEventListener("touchcancel", onTouchEnd, { passive: true });
  document.addEventListener("contextmenu", onContextMenu, true);

  // Diagnostic: intercept any navigation that looks like a reaction action.
  // If the reaction picker tap causes "page not available", the console will
  // show the URL that was followed.
  document.addEventListener("click", function(e) {
    var a = e.target && e.target.closest ? e.target.closest("a[href]") : null;
    if (!a) return;
    var href = a.getAttribute("href") || "";
    if (/react|like|emoji|reel|feedback/i.test(href)) {
      console.warn("[Materialbook] Reaction-related navigation:", href, a);
    }
  }, true);
})();
