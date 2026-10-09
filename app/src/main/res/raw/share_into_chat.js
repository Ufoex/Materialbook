// Messages layer: (1) type what is being shared into the chat that is open, (2) report the
// first chats of the inbox so the app can offer them in Android's share sheet.
(() => {
    if (window.__mbShare) return;
    window.__mbShare = true;
    const inThread = () => /\/messages\/(e2ee\/)?t\/\d+/.test(location.pathname);
    let busy = false;
    setInterval(async () => {
        if (busy || !window.ShareBridge || !ShareBridge.active() || !inThread()) return;
        const box = document.querySelector('[role="main"] [role="textbox"][contenteditable="true"]');
        if (!box) return;
        busy = true;
        try {
            box.focus();
            const text = ShareBridge.text();
            if (text) document.execCommand('insertText', false, text);
            const files = JSON.parse(ShareBridge.files());
            const input = files.length && document.querySelector('input[type="file"]');
            if (input) {
                const dt = new DataTransfer();
                for (let i = 0; i < files.length; i++) {
                    const b64 = ShareBridge.fileData(i);
                    if (!b64) continue;
                    const bin = atob(b64);
                    const bytes = new Uint8Array(bin.length);
                    for (let j = 0; j < bin.length; j++) bytes[j] = bin.charCodeAt(j);
                    dt.items.add(new File([bytes], files[i].name, { type: files[i].mime }));
                }
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'files').set.call(input, dt.files);
                input.dispatchEvent(new Event('change', { bubbles: true }));
            }
            ShareBridge.done();
        } catch (e) {}
        busy = false;
    }, 700);

    let last = '';
    setInterval(() => {
        if (!window.ShareBridge || !/^\/messages/.test(location.pathname)) return;
        const seen = new Set();
        const chats = [];
        document.querySelectorAll('a[href*="/messages/"]').forEach((a) => {
            const m = (a.getAttribute('href') || '').match(/\/t\/(\d+)/);
            if (!m || seen.has(m[1]) || chats.length >= 6) return;
            seen.add(m[1]);
            const name = ((a.querySelector('span[dir="auto"]') || a).innerText || '').split('\n')[0].trim();
            const img = a.querySelector('img');
            if (name) chats.push({ id: m[1], name: name, img: img ? img.src : '' });
        });
        const json = JSON.stringify(chats);
        if (chats.length && json !== last) {
            last = json;
            try { ShareBridge.chats(json); } catch (e) {}
        }
    }, 4000);
})();
