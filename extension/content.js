// TruTranslate content script: sayfa metnini DOM'u bozmadan yerinde çevirir
(() => {
  if (window.__trutranslateLoaded) return;
  window.__trutranslateLoaded = true;

  const SKIP_TAGS = new Set(['SCRIPT','STYLE','NOSCRIPT','TEXTAREA','INPUT','CODE','PRE','KBD','SAMP','VAR','SVG','CANVAS','VIDEO','AUDIO','SELECT','OPTION','IFRAME','OBJECT','HEAD']);
  let originals = null; // Map<Text, string>
  let busy = false;

  function collectNodes() {
    const nodes = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
        const t = node.nodeValue.trim();
        if (t.length < 2) return NodeFilter.FILTER_REJECT;
        let el = node.parentElement;
        if (!el) return NodeFilter.FILTER_REJECT;
        while (el) {
          if (SKIP_TAGS.has(el.tagName)) return NodeFilter.FILTER_REJECT;
          if (el.isContentEditable) return NodeFilter.FILTER_REJECT;
          el = el.parentElement;
        }
        const r = node.parentElement.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    let n;
    while ((n = walker.nextNode())) nodes.push(n);
    return nodes;
  }

  function badge(text) {
    let b = document.getElementById('__trutranslate_badge');
    if (!b) {
      b = document.createElement('div');
      b.id = '__trutranslate_badge';
      b.style.cssText = 'position:fixed;bottom:12px;right:12px;z-index:2147483647;background:#111;color:#fff;padding:8px 12px;border-radius:8px;font:13px/1.4 sans-serif;box-shadow:0 2px 10px rgba(0,0,0,.4);max-width:320px';
      document.documentElement.appendChild(b);
    }
    b.textContent = text;
    b.style.display = text ? 'block' : 'none';
  }

  // Aynı blok içindeki kısa metinleri \n ile birleştirip tek seferde çevir (hız için)
  function groupTexts(nodes, maxLen = 700) {
    const groups = [];
    let cur = [], curLen = 0;
    for (const n of nodes) {
      const t = n.nodeValue.trim();
      if (curLen + t.length > maxLen && cur.length) { groups.push(cur); cur = []; curLen = 0; }
      cur.push(n); curLen += t.length + 1;
    }
    if (cur.length) groups.push(cur);
    return groups;
  }

  async function translatePage(lang) {
    if (busy) return { ok: false, error: 'Zaten çeviri sürüyor' };
    busy = true;
    try {
      const nodes = collectNodes();
      if (!nodes.length) { badge(''); return { ok: true, count: 0 }; }
      originals = new Map(nodes.map(n => [n, n.nodeValue]));
      const groups = groupTexts(nodes);
      let done = 0;
      for (const g of groups) {
        const joined = g.map(n => n.nodeValue.trim()).join('\n');
        const resp = await chrome.runtime.sendMessage({ target: 'offscreen', type: 'TRANSLATE_BATCH', texts: [joined], lang, job: 'page' });
        if (resp && resp.ok && resp.results && resp.results[0]) {
          const parts = resp.results[0].split('\n');
          g.forEach((n, i) => {
            if (parts[i] !== undefined && document.contains(n)) {
              n.nodeValue = n.nodeValue.replace(n.nodeValue.trim(), parts[i].trim());
            }
          });
        }
        done++;
        badge('TruTranslate: çevriliyor ' + done + '/' + groups.length + ' (cihazda, sunucusuz)');
      }
      badge('TruTranslate: çeviri tamam ✓');
      setTimeout(() => badge(''), 4000);
      return { ok: true, count: nodes.length };
    } catch (e) {
      badge('TruTranslate hata: ' + (e && e.message || e));
      return { ok: false, error: String(e && e.message || e) };
    } finally { busy = false; }
  }

  function revertPage() {
    if (originals) {
      for (const [n, t] of originals) { if (document.contains(n)) n.nodeValue = t; }
      originals = null;
    }
    badge('');
    return { ok: true };
  }

  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    (async () => {
      if (msg.type === 'TRANSLATE_PAGE') sendResponse(await translatePage(msg.lang || 'tr'));
      else if (msg.type === 'REVERT_PAGE') sendResponse(revertPage());
      else sendResponse({ ok: false });
    })();
    return true;
  });
})();
