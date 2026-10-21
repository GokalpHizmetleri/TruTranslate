const $ = (id) => document.getElementById(id);
function setStatus(t){ $('status').textContent = t; }
function showBar(p){ $('bar').style.display='block'; $('fill').style.width=Math.round(p*100)+'%'; if(p>=1) setTimeout(()=>$('bar').style.display='none',1500); }

async function refreshStatus(){
  const r = await chrome.runtime.sendMessage({ type:'MODEL_STATUS' }).catch(()=>null);
  if (!r || !r.ok) { setStatus('Motor hazırlanıyor...'); return; }
  if (r.status==='ready') setStatus('Model hazır ✓ (çeviri tamamen bu cihazda)');
  else if (r.status==='downloading') { setStatus('Model indiriliyor (bir kere): '+Math.round((r.progress||0)*100)+'%'); showBar(r.progress||0); }
  else if (r.status==='error') setStatus('Hata: '+r.error);
  else setStatus('Model henüz inmedi - ilk çeviride bir kere iner (~1.1 GB)');
}

$('go').onclick = async () => {
  $('go').disabled = true;
  setStatus('Çeviri başladı, model gerekiyorsa önce iner...');
  const lang = $('lang').value;
  const r = await chrome.runtime.sendMessage({ type:'TRANSLATE_PAGE', lang }).catch(e=>({ok:false,error:String(e)}));
  $('go').disabled = false;
  if (r && r.ok) setStatus('Bitti ✓ ' + (r.count||0) + ' metin çevrildi');
  else setStatus('Hata: ' + (r && r.error || 'bilinmiyor'));
};
$('revert').onclick = async () => {
  await chrome.runtime.sendMessage({ type:'REVERT_PAGE' }).catch(()=>null);
  setStatus('Orijinal sayfa geri yüklendi');
};
chrome.runtime.onMessage.addListener((msg)=>{
  if (msg.type==='OFFSCREEN_EVENT' && msg.kind==='model'){
    if (msg.status==='downloading'){ setStatus('Model indiriliyor (bir kere): '+Math.round((msg.progress||0)*100)+'%'); showBar(msg.progress||0); }
    else if (msg.status==='ready'){ setStatus('Model hazır ✓'); showBar(1); }
    else if (msg.status==='error'){ setStatus('Hata: '+msg.error); }
  }
});
refreshStatus();
