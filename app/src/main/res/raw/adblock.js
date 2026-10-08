(function() {

    if (isDesktopMode()) {
        (function() {
          const selector = 'div.sponsored_ad, article[data-ft*="sponsored_ad"]';

          // Structural ad markers (uBO fb.txt / personal-ad-filter idea):
          // an ad unit carries profile_name + story_message + cta-* rendering roles.
          // Language-independent, survives label-text changes like issue #29.
          const removeRoleAds = (scope) => {
            const roots = [];
            if (scope instanceof HTMLElement && scope.matches('[data-ad-rendering-role="profile_name"]')) roots.push(scope);
            scope.querySelectorAll('[data-ad-rendering-role="profile_name"]').forEach(el => roots.push(el));
            roots.forEach(el => {
              const post = el.closest('div[aria-posinset], article, div[data-tracking-duration-id]');
              if (post && post.querySelector('[data-ad-rendering-role="story_message"]') &&
                  post.querySelector('[data-ad-rendering-role^="cta-"]')) {
                post.remove();
              }
            });
          };

          const removeSponsored = (root = document) => {
            root.querySelectorAll(selector).forEach(el => el.remove());
            removeRoleAds(root);
            // uBO fb.txt: explicit Sponsored link survives obfuscation/layout renames
            const links = [];
            if (root instanceof HTMLElement && root.matches('a[aria-label="Sponsored"]')) links.push(root);
            root.querySelectorAll('a[aria-label="Sponsored"]').forEach(el => links.push(el));
            links.forEach(el => el.closest('div[aria-posinset], article, div[data-tracking-duration-id]')?.remove());
          };

          removeSponsored();

          const observer = new MutationObserver(mutations => {
            for (const mutation of mutations) {
              for (const node of mutation.addedNodes) {
                if (!(node instanceof HTMLElement)) continue;
                if (node.matches(selector)) {
                  node.remove();
                } else {
                  removeSponsored(node);
                }
              }
            }
          });
          observer.observe(document.body, {
            childList: true,
            subtree: true
          });
        })();

        return;
    }

    //  The Ad tag and separator + special icon elements have a unique color of #8a8d91 in both theme modes (we'll see if they fix this with time)

    const processedAds = new WeakSet();

    function removeFeedAds() {
        const spans = document.querySelectorAll('span.f5[style*="color:#8a8d91"]:not([data-nosnippet])');

        spans?.forEach(span => {
            if (processedAds.has(span)) return;
            processedAds.add(span);
        })

        for (const span of spans) {
            const parent = span.parentElement;

            if (!parent?.matches('div.native-text.rslh')) {
                continue;
            }

            const container = parent.closest('div[data-dcm-id="1"][data-mcomponent="MContainer"]');
            container.style.display = 'none'

            const postSeparator = container.previousElementSibling;

            if (postSeparator && postSeparator.offsetHeight === 1 && postSeparator.querySelector('[data-fd-action]') ) {
              postSeparator.style.display = 'none';
            }
        }
    }

    // Feed ads only: skip the reel viewer (it mutates constantly and has no feed ads) and
    // run at most once per frame instead of on every single mutation.
    let feedAdsScheduled = false;
    const adsObserver = new MutationObserver(() => {
        if (window.location.pathname.indexOf('/reel') === 0 || feedAdsScheduled) return;
        feedAdsScheduled = true;
        requestAnimationFrame(() => { feedAdsScheduled = false; removeFeedAds(); });
    }).observe(document.body, {
        childList: true,
        subtree: true
    });

    removeFeedAds();

    const sponsoredTexts = [
        "Sponsored", "Ad", "Gesponsert", "Sponsorlu", "Sponsorowane",
        "Ispoonsara godhameera", "Geborg", "Bersponsor", "Ditaja",
        "Disponsori", "Giisponsoran", "Sponzorováno", "Sponsoreret",
        "Publicidad", "May Sponsor", "Sponsorisée", "Sponsorisé", "Oipytyvôva",
        "Ɗaukar Nayin", "Sponzorirano", "Uterwa inkunga", "Sponsorizzato",
        "Imedhaminiwa", "Hirdetés", "Misy Mpiantoka", "Gesponsord",
        "Sponset", "Patrocinado", "Sponsorizat", "Sponzorované",
        "Sponsoroitu", "Sponsrat", "Được tài trợ", "Χορηγούμενη",
        "Спонсорирано", "Спонзорирано", "Ивээн тэтгэсэн", "Реклама",
        "Спонзорисано", "במימון", "سپانسرڈ", "دارای پشتیبانی مالی",
        "ስፖንሰር የተደረገ", "प्रायोजित", "ተደረገ", "प", "স্পনসর্ড",
        "ਪ੍ਰਯੋਜਿਤ", "પ્રાયોજિત", "ପ୍ରାୟୋଜିତ", "செய்யப்பட்ட செய்யப்பட்ட",
        "చేయబడినది చేయబడినది", "ಪ್ರಾಯೋಜಿಸಲಾಗಿದೆ", "ചെയ്‌തത് ചെയ്‌തത്",
        "ලද ලද ලද", "สนับสนุน สนับสนุน รับ สนับสนุน สนับสนุน",
        "ကြော်ငြာ ကြော်ငြာ", "ឧបត្ថម្ភ ឧបត្ថម្ភ ឧបត្ថម្ភ", "광고",
        "贊助", "赞助内容", "広告", "സ്‌പോൺസർ ചെയ്‌തത്",
        "Anzeige","Peye","Oglas"
    ];

    const specialChar = '󰞋';

    const sponsoredRegex = new RegExp(`(${sponsoredTexts.join('|')})\\s*${specialChar}`, 'i');

    // Issue #29: new FB mobile UI shows bare "Ad ·" without the trailing PUA marker,
    // so legacy sponsoredRegex alone misses it. Accept bare labels too (exact match
    // after stripping trailing delimiters), then climb to the post container.
    const sponsoredSet = new Set(sponsoredTexts.map(s => s.toLowerCase()));
    const sponsoredWordRegexes = sponsoredTexts.map(
        w => new RegExp(`\\b${w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i')
    );

    function isSponsoredLabel(text) {
        if (!text) return false;
        const t = text.trim();
        if (!t || t.length > 64) return false;
        if (sponsoredRegex.test(t)) return true;
        const cleaned = t.replace(/[\s·•・.\uF000-\uF8FF\u{F0000}-\u{10FFFF}]+$/u, '').trim().toLowerCase();
        if (cleaned.length < 2) return false; // bare single chars (e.g. "प") FP too easily
        if (sponsoredSet.has(cleaned)) return true;
        // uBO fb.txt: "S-*-p-*-o..." separator obfuscation + paid-attribution variants
        if (cleaned.replace(/[^a-z]/g, '') === 'sponsored') return true;
        return cleaned === 'paid partnership' || cleaned.includes('paid for by');
    }

    function isSplitSponsored(el) {
        // uBO fb.txt idea: FB splits "Sponsored" into per-letter spans (S/p/o/n/...) to dodge
        // text filters. Reassemble sibling single-char spans sharing the same order-style parent.
        const parent = el.parentElement;
        if (!parent || parent.children.length < 6) return false;
        const letters = Array.from(parent.children)
            .filter(c => (c.textContent || '').trim().length === 1)
            .map(c => (c.textContent || '').trim().toLowerCase())
            .join('');
        return letters.includes('sponsored');
    }

    function hidePost(el) {
        const post = el.closest ? el.closest('div[data-tracking-duration-id], div[aria-posinset]') : null;
        if (post && post.style.display !== 'none') post.style.display = 'none';
    }

    function hideLabelSpan(span) {
        if (isSponsoredLabel(span.textContent) || isSplitSponsored(span)) {
            hidePost(span);
            return;
        }
        // Personal-ad-filter idea: structural triple profile_name + story_message + cta-*
        // marks an ad unit regardless of label language.
        if (span.matches && span.matches('[data-ad-rendering-role="profile_name"]')) {
            const post = span.closest('div[data-tracking-duration-id], div[aria-posinset]');
            if (post && post.querySelector('[data-ad-rendering-role="story_message"]') &&
                post.querySelector('[data-ad-rendering-role^="cta-"]')) {
                post.style.display = 'none';
            }
        }
    }

    function hideSponsoredLink(root) {
        // uBO fb.txt idea: explicit "Sponsored" aria-label link present in old+new markup.
        const links = [];
        if (root instanceof HTMLElement && root.matches('a[aria-label="Sponsored"]')) links.push(root);
        (root.querySelectorAll ? root.querySelectorAll('a[aria-label="Sponsored"]') : []).forEach(el => links.push(el));
        links.forEach(hidePost);
    }

    function hideAllAds(root = document) {
        hideSponsoredLink(root);
        if (root instanceof HTMLElement) {
            if (root.matches('span')) hideLabelSpan(root);
            root.querySelectorAll('span').forEach(hideLabelSpan);
            return;
        }
        document.querySelectorAll('div[data-tracking-duration-id] span, div[aria-posinset] span').forEach(hideLabelSpan);
    }

    hideAllAds();

    let adScheduled = false;
    const observer = new MutationObserver(mutations => {
        // ponytail: back-navigation swaps the whole tree in one batch —
        // sleep while viewing a post so 20 observers don't all rescan.
        if (window.location.pathname !== '/') return;
        // ponytail: defer off the back-paint — hide after first frame, not during.
        if (adScheduled) return;
        adScheduled = true;
        const batch = mutations;
        requestAnimationFrame(() => {
            adScheduled = false;
            for (const mutation of batch) {
                for (const node of mutation.addedNodes) {
                    if (!(node instanceof HTMLElement)) continue;
                    hideAllAds(node);
                }
            }
        });
    });
    observer.observe(document.body, { childList: true, subtree: true });

    // One combined regex instead of ~70 separate tests per text.
    const sponsoredCombined = new RegExp(sponsoredWordRegexes.map(re => `(?:${re.source})`).join('|'), 'i');
    function containsSponsoredText(text) {
        return sponsoredCombined.test(text.toLowerCase());
    }


    // Reel ads. The scan is deliberately cheap and deferred: it used to run 70 regexes on
    // every span of every reel inside each DOM mutation, which could block the page long
    // enough for the reel viewer to stop loading more reels.
    //  - runs at most every 300 ms, never inside the mutation callback itself
    //  - only reels on or next to the screen (the label can appear late, when the reel
    //    loads, so a reel is re-checked on every scan while it is near)
    //  - only short texts (the sponsored label is short; captions are skipped)
    // Keep the reel on screen where it is. Facebook rebuilds the reel list (a like, more reels
    // loading), which drops our display:none: the ads come back, the list gets taller above the
    // reel being watched, and the viewer shows another reel (and restarts its video) until the
    // scan hides the ads again. Hiding an ad has the same effect. Ads have no stable id to
    // hide them again in time, so instead the scroll is corrected whenever the list changes.
    let anchorReel = null, lastScroll = 0;
    const reelList = () => document.querySelectorAll('div.vertically-snappable');
    const reelVideoId = (c) => c.querySelector('[data-video-id]')?.dataset.videoId || '';
    function pickAnchor() {
        let best = null, area = 0;
        reelList().forEach(c => {
            const r = c.getBoundingClientRect();
            const h = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));
            if (h > area) { area = h; best = c; }
        });
        return best && { el: best, id: reelVideoId(best), top: best.getBoundingClientRect().top };
    }
    function restoreAnchor() {
        const scroller = document.querySelector('div.vscroller-snap');
        // While the user is scrolling the position is moving on purpose: leave it alone.
        if (!scroller || !anchorReel || performance.now() - lastScroll < 150) return;
        let el = anchorReel.el;
        if (!el.isConnected || el.style.display === 'none') {
            el = anchorReel.id && [...reelList()].find(c => c.style.display !== 'none' && reelVideoId(c) === anchorReel.id);
        }
        if (!el) { anchorReel = null; return; }
        const delta = el.getBoundingClientRect().top - anchorReel.top;
        if (Math.abs(delta) > 2) scroller.scrollTop += delta;
        anchorReel.el = el;
    }
    let anchorFrame = 0;
    document.addEventListener('scroll', () => {
        lastScroll = performance.now();
        if (anchorFrame) return;
        anchorFrame = requestAnimationFrame(() => { anchorFrame = 0; anchorReel = pickAnchor(); });
    }, { capture: true, passive: true });

    function hideReelAd(container) {
        container.dataset.adHidden = 'true';
        // Stop the ad's video and keep it silent even if Facebook tries to autoplay it,
        // then take the reel out of the layout: no placeholder, the next reel takes its place.
        container.querySelectorAll('video').forEach(v => {
            v.muted = true;
            try { v.pause(); } catch (e) {}
            v.addEventListener('play', () => { v.muted = true; v.pause(); });
        });
        container.style.setProperty('display', 'none', 'important');
        restoreAnchor();
    }

    function scanReelAds() {
        document.querySelectorAll('div.vertically-snappable').forEach(container => {
            if (container.dataset.adHidden === 'true') return;
            const r = container.getBoundingClientRect();
            if (r.height === 0 || r.bottom < -window.innerHeight || r.top > 2 * window.innerHeight) return;
            for (const span of container.querySelectorAll('span')) {
                const text = span.textContent;
                if (text && text.length <= 48 && containsSponsoredText(text)) {
                    hideReelAd(container);
                    break;
                }
            }
        });
    }

    let reelScanTimer = null;
    function removeReelAds() {
        if (reelScanTimer) return;
        reelScanTimer = setTimeout(() => { reelScanTimer = null; scanReelAds(); }, 300);
    }

    // Initial cleanup
    // Reels are watched on the feed ("/") and in the reel viewer ("/reel/<id>"); the
    // viewer used to be skipped by the off-feed guard, so its ads were never removed.
    // Other video viewers (/<user>/videos/<id>, /watch...) use the same reel list, so any
    // page that has one is covered: the scan itself is a no-op when there is no reel list.
    const onReelSurface = () => true;
    removeReelAds();
    // Any DOM change on a reel surface just schedules a (cheap, deferred) scan: labels
    // can render after the reel container itself.
    const reelObserver = new MutationObserver((mutations) => {
        // Reels added or removed by Facebook (runs before the next paint).
        if (anchorReel && mutations.some(m => [...m.addedNodes, ...m.removedNodes].some(n =>
            n instanceof HTMLElement && (n.matches('div.vertically-snappable') || n.querySelector('div.vertically-snappable'))))) {
            restoreAnchor();
        }
        if (onReelSurface()) removeReelAds();
    });

    reelObserver.observe(document.body, { childList: true, subtree: true });
})();
