(function() {

    if (window.isDesktopMode()) {
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

          const forceFixed = el => {
            if (el?.classList.contains('xixxii4')) {
              el.style.setProperty('position', 'fixed', 'important');
            }
          };

          waitForBanner().then(banner => {
            const style = document.createElement('style');
            style.textContent = `
              div[role="banner"].xixxii4,
              div[role="banner"] .xixxii4 {
                position: fixed !important;
              }
            `;
            document.head.appendChild(style);

            forceFixed(banner);
            banner.querySelectorAll('.xixxii4').forEach(forceFixed);

            new MutationObserver(mutations => {
              for (const m of mutations) {
                if (m.type === 'childList') {
                  m.addedNodes.forEach(n => {
                    forceFixed(n);
                    n.querySelectorAll?.('.xixxii4')?.forEach(forceFixed);
                  });
                } else if (m.type === 'attributes' && m.attributeName === 'class') {
                  forceFixed(m.target);
                }
              }
            }).observe(banner, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
          });
        })();
        return;
    }

    const isPhotoViewer = () => window.location.pathname.includes('/photo.php');

    // Option B: only revert nodes WE touched on feed (body + feed scroller
    // content + fixed navbars). Never touch the viewer's .hscroller holder:
    // FB owns that layout, and mutating it persists across SPA reuses
    // (photo stuck to top on later opens).
    const revertOurFeedForcing = () => {
        const s = document.body.style;
        if (s.paddingTop === '0px') s.paddingTop = '';
        if (s.marginTop === '0px') s.marginTop = '';
        if (s.overflow === 'visible') s.overflow = '';
        if (s.height === '100%') s.height = '';
        const off = window._mbFeedOffset;
        if (window._mbScrollContent?.isConnected) {
            if (!off || window._mbScrollContent.style.marginTop === off)
                window._mbScrollContent.style.removeProperty('margin-top');
        } else window._mbScrollContent = null;
        if (window._mbFeedScroller?.isConnected) {
            if (!off || window._mbFeedScroller.style.paddingTop === off)
                window._mbFeedScroller.style.removeProperty('padding-top');
        } else window._mbFeedScroller = null;
        for (const k of ['_mbFeedNavbar', '_mbFeedTabbar']) {
            const el = window[k];
            if (el?.isConnected) {
                el.style.removeProperty('position');
                el.style.removeProperty('top');
                el.style.removeProperty('left');
                el.style.removeProperty('width');
                el.style.removeProperty('z-index');
            } else window[k] = null;
        }
    };

    // Viewer photo can land below the visible area on first open: FB pins the
    // image with top + negative bottom measured while our feed forcing was
    // still on, stretching it past its natural ratio. Drop the bad bottom so
    // the photo keeps its ratio (holder math is FB's own and stays), then
    // scroll-center. No margin mutation, so nothing sticks on later opens.
    const centerViewerPhoto = () => {
        const img = document.querySelector('.hscroller img');
        const sc = document.querySelector('.hscroller');
        if (!img || !sc) return;
        // Early: holder exists but image not decoded yet. Pre-scroll by holder
        // geometry so the photo is near viewport when it paints, then refine.
        if (!img.complete || !img.naturalWidth || !img.clientWidth) {
            if (!img._mbCenterHook) {
                img._mbCenterHook = true;
                img.addEventListener('load', () => {
                    img._mbCenterN = 0;
                    centerViewerPhoto();
                }, { once: true });
            }
            const hr = img.parentElement?.getBoundingClientRect();
            if (hr && hr.height > 0) {
                const outTop = hr.top < 0, outBottom = hr.bottom > window.innerHeight;
                if (outTop || outBottom)
                    sc.scrollTop = Math.max(0, Math.round(hr.top + sc.scrollTop - (window.innerHeight - hr.height) / 2 + 10));
            }
            return;
        }
        const cs = getComputedStyle(img);
        const natH = img.clientWidth * img.naturalHeight / img.naturalWidth;
        if (parseFloat(cs.bottom) < 0 && img.clientHeight > natH * 1.05) {
            img.style.setProperty('height', 'auto', 'important');
            img.style.setProperty('bottom', 'auto', 'important');
        }
        const r = img.getBoundingClientRect();
        if (r.height <= 0) return;
        const outTop = r.top < 0, outBottom = r.bottom > window.innerHeight;
        if (!outTop && !outBottom) return;
        // +10px overshoot so the last pixel row truly fits (buttons show only
        // when rect.bottom <= innerHeight; FB can snap 1-2px back after scroll).
        sc.scrollTop = Math.max(0, Math.round(r.top + sc.scrollTop - (window.innerHeight - r.height) / 2 + 10));
        // FB can snap scroll back; re-check next frames (5 passes for weak devices).
        img._mbCenterN = (img._mbCenterN || 0) + 1;
        if (img._mbCenterN < 5) requestAnimationFrame(() => centerViewerPhoto());
    };
    // Independent loop: observer may fire before the viewer DOM exists (miss
    // entirely). On viewer entry, drive center for ~0.5s regardless, plus one
    // late pass for late-decoding images. Scroll-only, resets per entry.
    const kickViewerLoop = () => {
        if (window._mbLoopId === location.href) return;
        window._mbLoopId = location.href;
        let f = 0;
        const step = () => {
            try { centerViewerPhoto(); } catch (e) {}
            if (!window.location.pathname.includes('/photo.php')) return;
            if (++f < 30) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
        setTimeout(() => {
            try {
                const img = document.querySelector('.hscroller img');
                if (img) img._mbCenterN = 0;
                centerViewerPhoto();
            } catch (e) {}
        }, 600);
    };
    const installViewerNavHook = () => {
        if (window._mbNavHook) return;
        window._mbNavHook = true;
        const check = () => {
            if (window.location.pathname.includes('/photo.php')) {
                revertOurFeedForcing();
                kickViewerLoop();
            }
        };
        const wrap = (fn) => function(...a) {
            const r = fn.apply(this, a);
            try { check(); } catch (e) {}
            return r;
        };
        history.pushState = wrap(history.pushState);
        history.replaceState = wrap(history.replaceState);
        window.addEventListener('popstate', check);
    };
    const applyStyles = () => {
        if (isPhotoViewer()) {
            revertOurFeedForcing();
            centerViewerPhoto();
            kickViewerLoop();
            return;
        }
        const navbar = document.querySelector('div[data-tti-phase="-1"][data-mcomponent="MContainer"][data-type="container"][data-focusable="true"].m');
        const tabbar = document.querySelector('div[role="tablist"][data-tti-phase="-1"][data-type="container"][data-mcomponent="MContainer"].m');
        const scroller = document.querySelector('div[data-type="vscroller"][data-is-pull-to-refresh-allowed="true"]');

        const hasLogo = navbar?.querySelector('div[aria-label*="Facebook"]');
        const hasFeed = tabbar?.querySelector('div[aria-label*="feed"]');
        const navbarHeight = navbar ? parseFloat(getComputedStyle(navbar).height) || parseFloat(navbar.style.height) || 0 : 0;
        const tabbarHeight = tabbar ? parseFloat(getComputedStyle(tabbar).height) || parseFloat(tabbar.style.height) || 0 : 0;

        if (hasLogo) Object.assign(navbar.style, {
            position: 'fixed',
            top: '0',
            left: '0',
            width: '100%',
            zIndex: '1000',
            pointerEvents: 'auto'
        });

        if (hasFeed) Object.assign(tabbar.style, {
            position: 'fixed',
            top: hasLogo ? navbarHeight + 'px' : '',
            left: '0',
            width: '100%',
            zIndex: '999',
            pointerEvents: 'auto'
        });

        if (scroller) {
            const offset = (hasLogo ? navbarHeight : 0) + (hasFeed ? tabbarHeight : 0);
            const scrollContent = scroller.querySelector(':scope > div:not(.pull-to-refresh-spinner-container)');
            scrollContent ? scrollContent.style.marginTop = offset + 'px' : scroller.style.paddingTop = offset + 'px';
            window._mbScrollContent = scrollContent || null;
            window._mbFeedScroller = scroller;
            window._mbFeedOffset = offset + 'px';
            if (window.isFeed()) scroller.style.paddingBottom = '0';

            const spinnerContainer = scroller.querySelector('.pull-to-refresh-spinner-container');
            if (spinnerContainer) Object.assign(spinnerContainer.style, {
                zIndex: '1001',
            });

            const spinner = scroller.querySelector('.pull-to-refresh-spinner');
            if (spinner) spinner.style.margin = '0 auto';
        }
        if (hasLogo) window._mbFeedNavbar = navbar; else window._mbFeedNavbar = null;
        if (hasFeed) window._mbFeedTabbar = tabbar; else window._mbFeedTabbar = null;

        Object.assign(document.body.style, {
            paddingTop: '0',
            marginTop: '0',
            overflow: 'visible',
            height: '100%'
        });
    };
    installViewerNavHook();
    applyStyles();
    // ponytail: feed scan is expensive — run it on feed only. The viewer
    // still needs applyStyles (its revert+center branch returns early).
    new MutationObserver(() => { if (isPhotoViewer() || window.location.pathname === '/') applyStyles(); }).observe(document.body, { childList: true, subtree: true });
})();