// TruTranslate background service worker: popup <-> content <-> offscreen köprüsü
const OFFSCREEN_URL = 'offscreen.html';
let creating = null;

async function ensureOffscreen() {
  const has = await chrome.offscreen.hasDocument().catch(() => false);
  if (has) return;
  if (!creating) {
    creating = chrome.offscreen.createDocument({
      url: OFFSCREEN_URL,
      reasons: ['WORKERS', 'BLOBS', 'DOM_SCRAPING'],
      justification: 'Cihaz üzerinde çeviri modeli (WASM) çalıştırma ve model dosyasını yerel depolamaya indirme'
    }).finally(() => { creating = null; });
  }
  await creating;
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    if (msg.target === 'offscreen') {
      await ensureOffscreen();
      const resp = await chrome.runtime.sendMessage({ ...msg, target: undefined, toOffscreen: true });
      sendResponse(resp);
    } else if (msg.type === 'TRANSLATE_PAGE' || msg.type === 'REVERT_PAGE') {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab) { sendResponse({ ok: false, error: 'Sekme bulunamadı' }); return; }
      if (msg.type === 'TRANSLATE_PAGE') await ensureOffscreen();
      const resp = await chrome.tabs.sendMessage(tab.id, msg);
      sendResponse(resp);
    } else if (msg.type === 'MODEL_STATUS') {
      await ensureOffscreen();
      const resp = await chrome.runtime.sendMessage({ toOffscreen: true, type: 'MODEL_STATUS' });
      sendResponse(resp);
    } else if (msg.type === 'OFFSCREEN_EVENT') {
      // offscreen -> popup/content yayını (ilerleme vb.)
      chrome.runtime.sendMessage(msg).catch(() => {});
      if (msg.tabId) chrome.tabs.sendMessage(msg.tabId, msg).catch(() => {});
      sendResponse({ ok: true });
    } else {
      sendResponse({ ok: false, error: 'bilinmeyen mesaj' });
    }
  })().catch(e => sendResponse({ ok: false, error: String(e && e.message || e) }));
  return true;
});
