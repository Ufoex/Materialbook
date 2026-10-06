
// Desktop mode identifier
(() => {
    window.isDesktopMode = () => {
        return document.querySelector('html[id="facebook"]') !== null;
    }
})();

// Feed identifier
(() => {
    window.isFeed = () => {
        // ponytail: cheap string check first — the span scan below walks the
        // whole DOM, and every guarded observer calls isFeed per batch.
        const isHomeUrl = window.location.pathname === '/' &&
            (window.location.hostname === 'm.facebook.com' || window.location.hostname === 'www.facebook.com');
        if (!isHomeUrl) return false;

        if (window.isDesktopMode()) return true;

        const hasSpecialButton = Array.from(document.querySelectorAll('[role="button"] span'))
            .some(span => span.textContent === '󱥆');

        return hasSpecialButton;
    };
})();





(function() {
    if (!window.isDesktopMode()) return;

    document.documentElement.style.fontSize = '18px';


    // Do not stick the navbar by default
    (() => {
      const waitForBanner = () => new Promise(resolve => {
        const existing = document.querySelector('div[role="banner"]');
        if (existing) return resolve(existing);

        new MutationObserver((mutations, obs) => {
          for (const { addedNodes } of mutations) {
            for (const node of addedNodes) {
              if (node.nodeType === 1 && node.matches('div[role="banner"]')) {
                obs.disconnect();
                return resolve(node);
              }
            }
          }
        }).observe(document.body, { childList: true, subtree: true });
      });

      const forceAbsolute = el => {
        if (el?.classList.contains('xixxii4')) {
          el.style.setProperty('position', 'absolute', 'important');
        }
      };

      waitForBanner().then(banner => {
        const style = document.createElement('style');
        style.textContent = `
          div[role="banner"].xixxii4,
          div[role="banner"] .xixxii4 {
            position: absolute !important;
          }
        `;
        document.head.appendChild(style);

        forceAbsolute(banner);
        banner.querySelectorAll('.xixxii4').forEach(forceAbsolute);

        new MutationObserver(mutations => {
          for (const m of mutations) {
            if (m.type === 'childList') {
              m.addedNodes.forEach(n => {
                forceAbsolute(n);
                n.querySelectorAll?.('.xixxii4')?.forEach(forceAbsolute);
              });
            } else if (m.type === 'attributes' && m.attributeName === 'class') {
              forceAbsolute(m.target);
            }
          }
        }).observe(banner, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
      });
    })();


    // Remove "send" button to save space
    // Remove the third element in the interaction bar if nb is 4
    (function() {
      const parentSelector = '.xbmvrgn.x1diwwjn';
      const childSelector = '.x10b6aqq.x1yrsyyn.xs83m0k';

      function checkAndRemoveThird(parent) {
        const children = parent.querySelectorAll(childSelector);
        if (children.length === 4) children[2].remove();
      }

      document.querySelectorAll(parentSelector).forEach(checkAndRemoveThird);

      const observer = new MutationObserver(mutations => {
        for (const mutation of mutations) {
          mutation.addedNodes.forEach(node => {
            if (node.nodeType === 1) {
              if (node.matches(parentSelector)) {
                checkAndRemoveThird(node);
              }
              node.querySelectorAll(parentSelector).forEach(checkAndRemoveThird);
            }
          });
        }
      });

      observer.observe(document.body, { childList: true, subtree: true });
    })();
})();


// Scroll to top on back-press at feed
(() => {
    window.backHandlerNB = () => {

        const dialogs = document.querySelectorAll('div[role="dialog"]');
        const isMenu = document.querySelector('div[role="menu"]')

        function scrollToTop() {
            if (window.scrollY !== 0) {
              // to interrupt any current scroll event.
              document.body.style.overflow = 'hidden';
              setTimeout(() => {
                 document.body.style.overflow = '';
                 window.scrollTo({ top: 0, behavior: 'smooth' });
              }, 30);
              return "scrolling";
           } else return "exit";
        }

        if (window.isDesktopMode()) {
            if (window.isFeed() && !isMenu && dialogs.length === 1)
                return scrollToTop();
            else if (isMenu || dialogs.length > 1) {
                const escapeEvent = new KeyboardEvent('keydown', {
                    key: 'Escape',
                    code: 'Escape',
                    keyCode: 27,
                    which: 27,
                    bubbles: true,
                    cancelable: true
                });
                window.dispatchEvent(escapeEvent);
                return "true";
            } else return "false"
        } else if (window.isFeed() && !isMenu && !dialogs.length) {
            return scrollToTop();
        } else return "false";
    }
})();

// Enable press and hold caption selection and apply custom selection color
(() => {
  const makeSelectable = (el) => {
    if (el.closest('div[role="button"]')) return;
    el.style.userSelect = 'text';
    el.style.pointerEvents = 'auto';
  };

  const updateText = () => {
    // ponytail: caption-selectable only matters on feed; skip while viewing
    // a post so back-navigation batch doesn't pay this scan.
    if (!window.isFeed()) return;
    document.querySelectorAll('.native-text').forEach(makeSelectable);
  };

  const selectionStyle = document.createElement('style');
  selectionStyle.textContent = `
    .native-text::selection {
      background: #ccc;
      color: black;
    }
  `;
  document.head.appendChild(selectionStyle);

  updateText();

  new MutationObserver(updateText).observe(document.body, {
    childList: true,
    subtree: true
  });
})();

// Enhance Loading Overlay Script
(function() {
    function applyOverlayStyle() {
        const overlays = document.querySelectorAll('.loading-overlay');
        overlays.forEach(overlay => {
            overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.1)';
            // Never let a stuck Facebook loading overlay swallow taps:
            // it sometimes stays full-screen after a navigation is done
            // (e.g. after an account switch), blocking the whole UI.
            overlay.style.pointerEvents = 'none';
        });
    }
    applyOverlayStyle();

    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.addedNodes.length)
                applyOverlayStyle();
        });
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    // A full-screen .loading-overlay is Facebook's SPA/spinner layer. On this
    // WebView it sometimes never clears after a navigation or an account
    // switch, permanently covering and blocking the page (the switch actually
    // succeeds, but the UI appears to stay "loading"). Remove a full-screen
    // overlay that lingers after the document has finished loading.
    const coversViewport = (overlay) => {
        const rect = overlay.getBoundingClientRect();
        return (
            rect.width >= window.innerWidth - 2 &&
            rect.height >= window.innerHeight - 2
        );
    };

    const clearStale = () => {
        document.querySelectorAll('.loading-overlay').forEach((overlay) => {
            if (coversViewport(overlay)) overlay.remove();
        });
    };

    // Full document load: once finished, drop any leftover overlay quickly.
    if (document.readyState !== 'complete') {
        window.addEventListener('load', () => setTimeout(clearStale, 1500));
    }

    // SPA transitions (account switch, navigation inside m.facebook.com):
    // let a real transition breathe for a few seconds, then clear it.
    let stuckSince = 0;
    setInterval(() => {
        const overlay = document.querySelector('.loading-overlay');
        if (!overlay || document.readyState !== 'complete' || !coversViewport(overlay)) {
            stuckSince = 0;
            return;
        }
        stuckSince += 1000;
        if (stuckSince >= 3000) {
            overlay.remove();
            stuckSince = 0;
        }
    }, 1000);
})();

// Hide facebook download button and other distractions at login page
(function() {
  function removeDistr() {
    document.querySelector('div[data-bloks-name="bk.components.Flexbox"][style*="background: rgb(255, 255, 255)"].wbloks_1')?.parentElement?.remove();

    document.querySelectorAll(
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding-top: 20px; padding-bottom: 20px"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding-left: 4px; padding-right: 4px; padding-bottom: 4px"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding: 10px 12px; background: rgb(255, 255, 255)"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding: 20px"]'
    )?.forEach(distr => distr.remove());
  }

  removeDistr();

  new MutationObserver(mutations => {
    for (const m of mutations) {
      if (m.type === 'childList' && m.addedNodes.length) {
        removeDistr();
        break;
      }
    }
  }).observe(document.body, {
    childList: true,
    subtree: true
  });
})();

// Make the loading bar's background transparent
(() => {
    const style = document.createElement('style');
    style.textContent = `
    .revamped-progress-bar-color .loading-bar-background { background: transparent; }
    .loading-bar-background { background-color: transparent; }
    `;
    document.head.appendChild(style);
})();

// Guarded: the bundle is re-evaluated on every SPA navigation in the same
// context, so a top-level const would throw "already declared" and abort
// everything below it (settings button, theme notify, download hook).
if (!window._mbBannerObserver) {
    window._mbBannerObserver = new MutationObserver(() => {

      // ponytail: FB's "Mở ứng dụng / Open app" smart banner lives in
      // .bottom.fixed-container — same bucket as the height<80 banner above.
      // Match the button text (not container text) so typing the phrase in
      // the comment composer can't nuke it. Runs before the feed-ready
      // early-return so the banner dies even while the feed is loading.
      const banner = document.querySelector('.bottom.fixed-container');
      const bannerBtn = banner?.querySelector('div[role="button"]');
      if (bannerBtn && /Mở ứng dụng|Open (in )?app/i.test(bannerBtn.textContent || '')) {
        banner.style.display = 'none';
        return;
      }

      if (location.pathname === '/'
      && document.querySelector('div[role="button"][aria-label*="Facebook"]') === null) return;

      const element = banner;
      if (
        element &&
        !element.hasAttribute('data-shift-on-keyboard-shown')
      ) {
        const heightAttr = element.getAttribute('data-actual-height');
        if (heightAttr && parseInt(heightAttr, 10) < 80) {
          element.style.display = 'none';
        }
      }
    });

    window._mbBannerObserver.observe(document.body, { childList: true, subtree: true });
}


// Hold Effect Script
(function() {
  const style = document.createElement('style');
  style.innerHTML = '* { -webkit-tap-highlight-color: rgba(180, 180, 180, 0.35); }';
  document.head.appendChild(style);
})();


/* The below scripts are specific to the com.eepiemi.materialbook application. */

(() => {
  const onReady = (fn) => {
    if (document.readyState === 'loading')
      document.addEventListener('DOMContentLoaded', fn);
     else fn();
  };

  onReady(() => {
    const BUTTON_ID = 'custom-settings-btn';
    const ICON_SVG = `
        <svg width="28" height="28" viewBox="0 -960 960 960"  fill="%FILL%">
            <path d="m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm112-260q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Z"/>
        </svg>`;

    const getFillColor = () => {
      const color = document.querySelector('meta[name="theme-color"]')?.content?.toLowerCase();
      return color === '#ffffff' ? '#080809' : '#e4e6eb';
    };

    const updateButtonColor = () => {
      const svg = document.querySelector(`#${BUTTON_ID} svg`);
      if (svg) svg.setAttribute('fill', getFillColor());
    };

    const findInsertionPoint = () => {
      const iconSpan = Array.from(document.querySelectorAll('span'))
        .find(span => span.textContent === '󱥊');
      const container = iconSpan?.closest('div[role="button"]')?.parentNode;

      const desktopTarget = document.querySelector(
        '.x6s0dn4.x78zum5.x1s65kcs.x1n2onr6.x1ja2u2z'
      );

      return { container, desktopTarget };
    };

    const createButton = () => {
      const btn = document.createElement('button');
      btn.id = BUTTON_ID;
      btn.setAttribute('style', `
        position: ${findInsertionPoint().desktopTarget === null ? 'fixed' : 'block'};
        top: 8px;
        right: 100px;
        background: transparent;
        border: none;
        border-radius: 50%;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        pointer-events: auto;
      `);
      btn.innerHTML = ICON_SVG.replace('%FILL%', getFillColor());
      btn.onclick = () => SettingsBridge?.onSettingsToggle?.();
      return btn;
    };

    const insertButton = () => {
      if (document.getElementById(BUTTON_ID)) return;

      const { container, desktopTarget } = findInsertionPoint();
      const button = createButton();

      if (desktopTarget) desktopTarget.insertBefore(button, desktopTarget.firstChild);
      else if (container) container.insertBefore(button, container.firstChild);
    };

    insertButton();

    const observer = new MutationObserver(() => {
      if (!document.getElementById(BUTTON_ID) && isFeed()) {
        insertButton();
      }
    });

    observer.observe(document.body, { childList: true, subtree: true });

    // observer for theme-color changes
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) {
      new MutationObserver(updateButtonColor).observe(themeMeta, {
        attributes: true,
        attributeFilter: ['content'],
      });
    }
  });
})();


// Color Extraction Script
(function() {
    const meta = document.querySelector('meta[name="theme-color"]');
    const notify = () => window.ThemeBridge?.onThemeColorChanged?.(meta?.content ?? "null");
    if (meta) {
        notify();
        new MutationObserver(() => notify())
            .observe(meta, { attributes: true, attributeFilter: ['content'] });
    }
})();

// Image load fix for media viewer
(function() {
  // When an image is promoted to its own composited layer (will-change,
  // transitions), Android WebView can paint only the top part of a tall
  // photo and never finishes the rest. Keep viewer images out of their own
  // layer; transforms (pinch zoom) still work.
  const style = document.createElement("style");
  style.textContent = `
    div[role="dialog"] img {
      will-change: auto !important;
    }
  `;
  document.head.appendChild(style);

  const isViewerImage = (img) => {
    if (location.pathname.indexOf("/photo") === 0) return true;
    const dialog = img.closest && img.closest('div[role="dialog"]');
    if (!dialog) return false;
    // In the video viewer the visible media is the <video>, not the poster
    // image. Leave video viewers alone so the video element and the download
    // capture logic are never disturbed.
    if (dialog.querySelector && dialog.querySelector("video")) return false;
    return true;
  };

  // Force a fresh GPU texture upload: a sub-pixel scale toggle re-rasterizes
  // the element in place, which fixes the "bottom of a tall photo stays
  // black" compositing glitch without touching the image source.
  const nudgeRepaint = (el) => {
    try {
      el.style.webkitTransform = "scale(0.9999)";
      void el.offsetWidth;
      el.style.webkitTransform = "";
    } catch (e) {}
  };

  // Clear and restore src in the same task: the empty state is never painted
  // and the second decode is served from the image cache, producing a
  // complete texture.
  const reDecode = (img) => {
    if (img._fbRedecoding) return;
    const src = img.currentSrc || img.src;
    if (!src || src.indexOf("data:") === 0) return;
    if (!img.complete || img.naturalWidth <= 0) return;
    img._fbRedecoding = true;
    setTimeout(() => {
      img._fbRedecoding = false;
    }, 600);
    img.src = "";
    void img.offsetWidth;
    img.src = src;
  };

  // Retry a few times: the first attempt can run while the browser is still
  // decoding, so we nudge on every pass and re-decode once the image is
  // definitely complete.
  const heal = (img, attempt) => {
    if (!img.isConnected) return;
    if (attempt > 4) return;
    setTimeout(() => {
      if (!img.isConnected) return;
      if (!(img.complete && img.naturalWidth > 0)) {
        heal(img, attempt + 1);
        return;
      }
      nudgeRepaint(img);
      if (attempt === 1) reDecode(img);
      heal(img, attempt + 1);
    }, attempt === 0 ? 150 : 700);
  };

  const processImage = (img) => {
    if (!(img instanceof HTMLImageElement)) return;
    if (!isViewerImage(img)) return;
    img.loading = "eager";
    const start = () => {
      if (!img.isConnected) return;
      if (!(img.complete && img.naturalWidth > 0)) {
        setTimeout(start, 300);
        return;
      }
      heal(img, 0);
    };
    if (typeof img.decode === "function") {
      img.decode().then(start).catch(start);
    }
    img.addEventListener("load", start, { once: true });
    img.addEventListener("error", start, { once: true });
    setTimeout(start, 600);
  };

  // Facebook sometimes renders the viewer photo as a div with
  // background-image. Nudge those too.
  const processBackgrounds = (dialog) => {
    const candidates = dialog.querySelectorAll('div[style*="background-image"]');
    for (const el of candidates) {
      if (el._fbNudged) continue;
      const rect = el.getBoundingClientRect();
      if (rect.width < 200 || rect.height < 200) continue;
      el._fbNudged = true;
      setTimeout(() => nudgeRepaint(el), 300);
    }
  };

  const scanViewer = () => {
    const dialog = document.querySelector('div[role="dialog"]');
    if (dialog) {
      const images = dialog.querySelectorAll('img[src]');
      for (const img of images) processImage(img);
      processBackgrounds(dialog);
    }
    // Full photo pages (photo.php) render the photo as a normal page element.
    // Heal the largest visible photo so it only re-decodes that one image.
    if (location.pathname.indexOf("/photo") === 0) {
      const images = Array.from(document.querySelectorAll('img[src*="fbcdn"]'))
        .filter((img) => {
          const rect = img.getBoundingClientRect();
          return rect.width > 300 && rect.height > 300;
        })
        .sort((a, b) => {
          const areaA = a.getBoundingClientRect().width * a.getBoundingClientRect().height;
          const areaB = b.getBoundingClientRect().width * b.getBoundingClientRect().height;
          return areaB - areaA;
        });
      if (images[0]) processImage(images[0]);
    }
  };

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node.nodeType !== 1) continue;
        if (node.matches && node.matches('div[role="dialog"]')) {
          scanViewer();
          return;
        }
        if (node.querySelector) {
          const dialog = node.matches && node.matches('div[role="dialog"]')
            ? node
            : node.querySelector('div[role="dialog"]');
          if (dialog) {
            scanViewer();
            return;
          }
        }
      }
      if (mutation.type === "attributes" && mutation.target.tagName === "IMG") {
        processImage(mutation.target);
      }
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["src", "srcset"]
  });

  let rescanTimer = null;
  const scheduleRescan = () => {
    if (rescanTimer) return;
    rescanTimer = setTimeout(() => {
      rescanTimer = null;
      if (document.querySelector('div[role="dialog"]') || location.pathname.indexOf("/photo") === 0) {
        scanViewer();
        scheduleRescan();
      }
    }, 1500);
  };

  scanViewer();
  scheduleRescan();
})();
// Messages tab: skip Facebook's "Download Messenger" interstitial (it renders for a
// moment before the native side redirects). Navigate straight to the messages URL,
// which MaterialbookWV turns into the desktop-mode Messages page.
(() => {
  if (window.__mbMessagesTabHooked) return;
  window.__mbMessagesTabHooked = true;
  const isMessagesTab = (t) => {
    const tab = t && t.closest && t.closest('[role="tab"]');
    return tab && /^messages\b/i.test(tab.getAttribute('aria-label') || '') ? tab : null;
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
