// TruTranslate offscreen motor: wllama (WASM llama.cpp) ile tamamen cihazda çeviri
import { Wllama } from './engine/wllama.js';

const MODEL_URL = 'https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_K_M.gguf';
const CONFIG_PATHS = {
  'single-thread/wllama.wasm': './engine/wllama.wasm',
  'single-thread/wllama.js': './engine/llama-worker.js',
};
const LANGS = { tr:'Turkish', en:'English', de:'German', fr:'French', es:'Spanish', pt:'Portuguese', it:'Italian', nl:'Dutch', pl:'Polish', cs:'Czech', ru:'Russian', uk:'Ukrainian', ar:'Arabic', he:'Hebrew', fa:'Persian', ur:'Urdu', hi:'Hindi', bn:'Bengali', ta:'Tamil', te:'Telugu', mr:'Marathi', gu:'Gujarati', zh:'Chinese', 'zh-Hant':'Traditional Chinese', ja:'Japanese', ko:'Korean', th:'Thai', vi:'Vietnamese', ms:'Malay', id:'Indonesian', tl:'Filipino', km:'Khmer', my:'Burmese', bo:'Tibetan', kk:'Kazakh', mn:'Mongolian', ug:'Uyghur', yue:'Cantonese' };

let wllama = null;
let modelState = { status: 'idle', progress: 0 };
let loadPromise = null;

function emit(ev) {
  chrome.runtime.sendMessage({ type: 'OFFSCREEN_EVENT', ...ev }).catch(() => {});
}

async function ensureModel() {
  if (wllama) return;
  if (loadPromise) return loadPromise;
  loadPromise = (async () => {
    modelState = { status: 'downloading', progress: 0 };
    emit({ kind: 'model', status: 'downloading', progress: 0 });
    const w = new Wllama(CONFIG_PATHS, { parallelDownloads: 1 });
    w.setCompat(null);
    await w.loadModelFromUrl(MODEL_URL, {
      n_ctx: 2048, n_threads: 1, n_batch: 512,
      progressCallback: ({ loaded, total }) => {
        modelState = { status: 'downloading', progress: total ? loaded / total : 0, loaded, total };
        emit({ kind: 'model', status: 'downloading', progress: modelState.progress, loaded, total });
      },
    });
    wllama = w;
    modelState = { status: 'ready', progress: 1 };
    emit({ kind: 'model', status: 'ready', progress: 1 });
  })().catch(e => {
    modelState = { status: 'error', error: String(e && e.message || e) };
    emit({ kind: 'model', status: 'error', error: modelState.error });
    loadPromise = null;
    throw e;
  });
  return loadPromise;
}

async function translateOne(text, targetLang) {
  const langName = LANGS[targetLang] || 'Turkish';
  const prompt = 'Translate the following segment into ' + langName + ', without additional explanation：' + text;
  const out = await wllama.createCompletion(prompt, {
    nPredict: Math.min(1024, Math.max(64, text.length * 2)),
    sampling: { temp: 0.7, top_p: 0.6, top_k: 20, penalty_repeat: 1.05 },
  });
  return out.trim();
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (!msg.toOffscreen) return false;
  (async () => {
    if (msg.type === 'MODEL_STATUS') {
      sendResponse({ ok: true, ...modelState });
      return;
    }
    if (msg.type === 'PRELOAD_MODEL') {
      await ensureModel();
      sendResponse({ ok: true, ...modelState });
      return;
    }
    if (msg.type === 'TRANSLATE_BATCH') {
      await ensureModel();
      const results = [];
      for (let i = 0; i < msg.texts.length; i++) {
        try { results.push(await translateOne(msg.texts[i], msg.lang)); }
        catch (e) { results.push(null); }
        emit({ kind: 'batch-progress', done: i + 1, total: msg.texts.length, job: msg.job });
      }
      sendResponse({ ok: true, results, job: msg.job });
      return;
    }
    sendResponse({ ok: false, error: 'bilinmeyen offscreen mesajı' });
  })().catch(e => sendResponse({ ok: false, error: String(e && e.message || e) }));
  return true;
});
