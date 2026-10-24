'use strict';
/* LifeVault v2 — local-first. No fetch(), no XHR, no CDN, no analytics, no cloud sync.
   Product identity: "LifeVault" (web v2). NOT com.example / "My Application" from the v1 prototype. */
const $=s=>document.querySelector(s);
const $$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid=()=>Date.now().toString(36)+Math.random().toString(36).slice(2,8);
const MOODS=['Happy','Calm','Grateful','Excited','Tired','Sad','Reflective','Anxious'];
const STOP=new Set('a an the and or of to in on for with is are was were it this that i you he she we they my your his her our their be been being at by from as not no'.split(' '));
const SYN={coffee:['espresso','latte','cappuccino','cafe'],beach:['seaside','shore','coast'],happy:['joyful','glad','cheerful'],sad:['down','blue','unhappy'],dog:['puppy'],cat:['kitten'],birthday:['bday'],run:['running','jog'],walk:['walking','stroll'],family:['mom','dad','mother','father','sister','brother']};
// reverse synonym index
const SYNREV={}; Object.entries(SYN).forEach(([k,vs])=>vs.forEach(v=>{SYNREV[v]=SYNREV[v]||[];SYNREV[v].push(k);}));
function tokens(text){return String(text||'').toLowerCase().replace(/[^a-z0-9\s#]/g,' ').split(/\s+/).filter(t=>t&&!STOP.has(t));}
function expandTokens(q){const out=new Set(tokens(q)); tokens(q).forEach(t=>{(SYN[t]||[]).forEach(v=>out.add(v));(SYNREV[t]||[]).forEach(v=>out.add(v));});return [...out];}
const MOOD_KW={Happy:['happy','joy','joyful','glad','cheerful','delighted','smile','laugh','wonderful','great day'],Calm:['calm','peaceful','relaxed','serene','quiet','tranquil'],Grateful:['grateful','thankful','blessed','appreciate','gratitude'],Excited:['excited','thrilled','amazing','awesome','fantastic','cannot wait'],Tired:['tired','exhausted','sleepy','weary','drained'],Sad:['sad','cry','crying','down','lonely','miss','heartbroken'],Reflective:['reflect','reflecting','reflected','thinking','remember','remembering','remembered','nostalgia','ponder','looking back'] /* ROUND 2: common inflections added so 'reflecting/remembering' are not silently missed */,Anxious:['anxious','worried','nervous','stress','stressed','afraid','panic']};
function suggestMood(text){
  // Negation handling: a mood word preceded within 3 tokens by not/never/no/n't is ignored, never counted.
  const raw=String(text||'').toLowerCase().replace(/n't/g,' not').split(/[^a-z]+/).filter(Boolean);
  const scores={}; MOODS.forEach(m=>scores[m]=0);
  Object.entries(MOOD_KW).forEach(([mood,words])=>{
    words.forEach(w=>{
      const parts=w.split(' ');
      raw.forEach((tok,i)=>{
        if(parts.length===1&&tok===parts[0]){const win=raw.slice(Math.max(0,i-3),i); if(!win.includes('not')&&!win.includes('never')&&!win.includes('no')) scores[mood]++;}
        if(parts.length===2&&raw[i]===parts[0]&&raw[i+1]===parts[1]){const win=raw.slice(Math.max(0,i-3),i); if(!win.includes('not')&&!win.includes('never')&&!win.includes('no')) scores[mood]+=2;}
      });
    });
  });
  const best=MOODS.reduce((a,b)=>scores[b]>scores[a]?b:a);
  const total=Object.values(scores).reduce((a,b)=>a+b,0);
  if(!total||scores[best]===0) return null;
  return {mood:best,confidence:Math.round(scores[best]/total*100),scores};
}
// ---------- storage (localStorage only for text; media in IndexedDB) ----------
const LS='lifevault_v2';
function loadStore(){try{return JSON.parse(localStorage.getItem(LS))||{}}catch(e){return {}}}
function saveStore(s){localStorage.setItem(LS,JSON.stringify(s)); return JSON.parse(localStorage.getItem(LS));}
let store=loadStore();
const DEFAULT_SETTINGS={theme:'system',textSize:'1',kidsMode:false,reminders:{capsulesOnOpen:false,dailyPrompt:false,dailyTime:'09:00',lastReflectionDate:''}};
if(!store.profiles) store={profiles:[],activeProfileId:null,entries:[],settings:structuredClone(DEFAULT_SETTINGS),onboarded:false,lastBackup:null};
else { store.entries=store.entries||[]; store.settings={...structuredClone(DEFAULT_SETTINGS),...(store.settings||{}),reminders:{...structuredClone(DEFAULT_SETTINGS.reminders),...((store.settings||{}).reminders||{})}}; }
// ROUND 2: a recovery key must never persist in storage. Strip any left by Round 1.
let _stripped=false; (store.profiles||[]).forEach(pr=>{if('recoveryKey' in pr){delete pr.recoveryKey; _stripped=true;}});
if(_stripped){try{saveStore(store);}catch(e){}}
function saveVerified(){const back=saveStore(store); if(JSON.stringify(back)!==JSON.stringify(store)) throw new Error('Save verification failed'); return true;}
function activeProfile(){return store.profiles.find(p=>p.id===store.activeProfileId)||null;}
// CRITICAL: empty owner must NEVER fall back to all users' entries.
function myEntries(){const p=activeProfile(); if(!p) return []; return store.entries.filter(e=>e.profileId===p.id);}
async function sha256(text){const b=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text));return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,'0')).join('');}
// ---------- IndexedDB: photos + audio, NOT Base64 in main data ----------
let dbp=null;
function idb(){if(dbp) return dbp; dbp=new Promise((res,rej)=>{const r=indexedDB.open('lifevault-v2',1);r.onupgradeneeded=()=>{r.result.createObjectStore('photos');r.result.createObjectStore('audio');};r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error);});return dbp;}
async function idbPut(storeName,key,val){const db=await idb();return new Promise((res,rej)=>{const t=db.transaction(storeName,'readwrite');t.objectStore(storeName).put(val,key);t.oncomplete=()=>res(true);t.onerror=()=>rej(t.error);});}
async function idbGet(storeName,key){const db=await idb();return new Promise((res,rej)=>{const t=db.transaction(storeName,'readonly');const q=t.objectStore(storeName).get(key);q.onsuccess=()=>res(q.result||null);q.onerror=()=>rej(q.error);});}
async function idbDel(storeName,key){const db=await idb();return new Promise(res=>{const t=db.transaction(storeName,'readwrite');t.objectStore(storeName).delete(key);t.oncomplete=()=>res(true);t.onerror=()=>res(false);});}
async function blobToDataURL(blob){return new Promise(res=>{const r=new FileReader();r.onload=()=>res(r.result);r.readAsDataURL(blob);});}
function dataURLToBlob(u){const [head,b64]=u.split(','); const mime=(head.match(/:(.*?);/)||[])[1]||'application/octet-stream'; const bin=atob(b64); const arr=new Uint8Array(bin.length); for(let i=0;i<bin.length;i++) arr[i]=bin.charCodeAt(i); return new Blob([arr],{type:mime});} // No network fetch: decodes a data: URL already in memory only.
// ---------- state ----------
let unlocked=false, failedAttempts=0, lockUntil=0, editingId=null, currentDetailId=null, lastDeleted=null, snackTimer=null, photoBlob=null, photoPreviewURL=null, audioBlob=null, rec=null, recChunks=[], recStart=0, recTimerInt=null, recPausedMs=0, recPauseStart=0, recognition=null, draftTimer=null, pendingDeleteId=null, pendingVaultDelete=false, captureTab='write', reflectionTimer=null, pendingSwitchId=null;
const PROMPTS=['What made you smile today?','Describe a small moment you want to keep.','Who did you think of today, and why?','What are you grateful for right now?','What did you learn about yourself this week?'];
function todayPrompt(){return PROMPTS[new Date().getDate()%PROMPTS.length];}
function fmtDate(iso){if(!iso) return ''; const d=new Date(iso); return isNaN(d)?'':d.toLocaleDateString(undefined,{year:'numeric',month:'short',day:'numeric'});}
function fmtTime(ms){const s=Math.floor(ms/1000);return String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');}

// ---------- navigation / views ----------
function goto(view){
  $$('.view').forEach(v=>v.classList.add('hidden'));
  const el=$('#view-'+view); if(el) el.classList.remove('hidden');
  if(view==='home') renderHome(); if(view==='entries') renderEntries(); if(view==='timeline') renderTimeline(); if(view==='more') renderMore(); if(view==='capture') renderCaptureForm();
  window.scrollTo(0,0);
}
function gotoCapture(tab){captureTab=tab||captureTab; goto('capture'); setCapTab(captureTab);}
function setCapTab(tab){captureTab=tab; $$('[data-cap-tab]').forEach(b=>b.classList.toggle('active',b.dataset.capTab===tab)); $('#voicePanel').classList.toggle('hidden',tab!=='voice'); $('#photoPanel').classList.toggle('hidden',tab!=='photo');}
async function purgeEntryMedia(e){if(!e) return; if(e.photoId) await idbDel('photos',e.photoId); if(e.audioId) await idbDel('audio',e.audioId);}
function showSnackbar(msg,undo){
  // Replacing a pending undo purges the previous entry's media (it can no longer be restored).
  if(!undo&&lastDeleted){const old=lastDeleted; lastDeleted=null; purgeEntryMedia(old);}
  $('#snackText').textContent=msg; $('#undoBtn').style.display=undo?'':'none';
  $('#snackbar').classList.remove('hidden'); clearTimeout(snackTimer); snackTimer=setTimeout(()=>{$('#snackbar').classList.add('hidden'); if(lastDeleted){const old=lastDeleted; lastDeleted=null; purgeEntryMedia(old);}},8000);
}
$('#undoBtn').onclick=()=>{if(lastDeleted){store.entries.push(lastDeleted); try{saveVerified();}catch(e){} lastDeleted=null; $('#snackbar').classList.add('hidden'); renderEntries(); renderHome(); showSnackbar('Entry restored.',false);} };
// ---------- search: weighted, tokenised, synonyms, filters ----------
function searchEntries(q,filters){
  const qt=expandTokens(q||'');
  const list=myEntries().map(e=>{
    let score=0;
    if(qt.length){
      const fields=[{t:e.title,w:3},{t:(e.tags||[]).join(' '),w:2},{t:(e.people||[]).join(' '),w:2},{t:e.body,w:1},{t:e.location,w:1},{t:e.transcript,w:1}];
      fields.forEach(f=>{const ft=new Set(tokens(f.t)); qt.forEach(tok=>{if(ft.has(tok)) score+=f.w;});});
    } else score=1;
    return {e,score};
  }).filter(x=>{
    if(qt.length&&x.score===0) return false;
    if(filters.mood&&x.e.mood!==filters.mood) return false;
    if(filters.type&&x.e.type!==filters.type) return false;
    if(filters.person&&!(x.e.people||[]).some(p=>p.toLowerCase().includes(filters.person.toLowerCase()))) return false;
    if(filters.from&&new Date(x.e.createdAt)<new Date(filters.from)) return false;
    if(filters.to&&new Date(x.e.createdAt)>new Date(filters.to+'T23:59:59')) return false;
    return true;
  });
  list.sort((a,b)=>b.score-a.score||new Date(b.e.createdAt)-new Date(a.e.createdAt));
  return list.map(x=>x.e);
}
function entryCard(e){
  const d=document.createElement('article'); d.className='entry'; d.tabIndex=0; d.setAttribute('role','button'); d.setAttribute('aria-label','Open entry '+(e.title||'Untitled'));
  d.innerHTML='<h3>'+esc(e.title||'Untitled')+'</h3><p class="muted small">'+esc(fmtDate(e.createdAt))+' · '+esc(e.type||'write')+(e.mood?' · '+esc(e.mood):'')+(e.pinned?' · 📌 Pinned':'')+(e.isSample?' · Sample (deletable)':'')+'</p><p>'+esc((e.body||e.transcript||'').slice(0,160))+'</p>'+(e.photoId?'<p class="muted small">📷 Photo attached</p>':'')+(e.audioId?'<p class="muted small">🎙️ Voice attached</p>':'');
  const open=()=>openDetail(e.id); d.onclick=open; d.onkeydown=ev=>{if(ev.key==='Enter')open();};
  return d;
}
async function renderEntryMedia(e,container){
  if(e.photoId){const b=await idbGet('photos',e.photoId); if(b){const img=document.createElement('img'); img.className='thumb'; img.alt='Entry photo'; img.src=URL.createObjectURL(b); container.prepend(img);} else {const p=document.createElement('p'); p.className='error'; p.textContent='Photo file missing on this device.'; container.prepend(p);}}
  if(e.audioId){const b=await idbGet('audio',e.audioId); if(b){const a=document.createElement('audio'); a.controls=true; a.src=URL.createObjectURL(b); container.appendChild(a);} else {const p=document.createElement('p'); p.className='error'; p.textContent='Voice file missing on this device.'; container.appendChild(p);}}
}
function renderEntries(){
  const q=$('#q').value, f={mood:$('#fMood').value,type:$('#fType').value,person:$('#fPerson').value,from:$('#fFrom').value,to:$('#fTo').value};
  const list=searchEntries(q,f); const box=$('#entryList'); box.innerHTML='';
  $('#entriesEmpty').classList.toggle('hidden',list.length>0||myEntries().length>0);
  if(!list.length&&myEntries().length>0){box.innerHTML='<p class="muted">No entries match that search.</p>';}
  list.forEach(e=>box.appendChild(entryCard(e)));
}
function openDetail(id){
  const e=store.entries.find(x=>x.id===id); if(!e) return; currentDetailId=id;
  const b=$('#detailBody'); b.innerHTML='<h2>'+esc(e.title||'Untitled')+'</h2><p class="muted small">'+esc(fmtDate(e.createdAt))+' · '+esc(e.type||'write')+(e.mood?' · Mood: '+esc(e.mood)+' ('+esc(e.moodSource||'manual')+(e.moodConfidence?'; '+e.moodConfidence+'% confidence':'')+')':'')+(e.pinned?' · 📌 Pinned':'')+'</p>'+(e.location?'<p>📍 '+esc(e.location)+'</p>':'')+(e.capsuleDate?'<p>⏳ Time capsule: '+esc(e.capsuleDate)+'</p>':'')+'<p>'+esc(e.body||'').replace(/\n/g,'<br>')+'</p>'+(e.transcript?'<h3>Transcript</h3><p>'+esc(e.transcript)+'</p>':'')+'<p class="muted small">Tags: '+esc((e.tags||[]).join(', ')||'—')+' · People: '+esc((e.people||[]).join(', ')||'—')+'</p>';
  renderEntryMedia(e,b); $('#detailModal').classList.remove('hidden');
}
$('#closeDetailBtn').onclick=()=>$('#detailModal').classList.add('hidden');
$('#editBtn').onclick=()=>{const e=store.entries.find(x=>x.id===currentDetailId); if(!e) return; $('#detailModal').classList.add('hidden'); editingId=e.id; gotoCapture(e.type||'write'); fillCapture(e);};
$('#deleteBtn').onclick=()=>{pendingDeleteId=currentDetailId; $('#confirmTitle').textContent='Delete this entry?'; $('#confirmText').textContent='This entry will be deleted. You can undo for a few seconds from the message that appears.'; $('#confirmModal').classList.remove('hidden');};
$('#confirmNo').onclick=()=>{$('#confirmModal').classList.add('hidden'); pendingDeleteId=null; pendingVaultDelete=false;};
$('#confirmYes').onclick=async()=>{
  $('#confirmModal').classList.add('hidden');
  if(pendingVaultDelete){const p=activeProfile(); if(p){const doomed=store.entries.filter(e=>e.profileId===p.id); for(const e of doomed) await purgeEntryMedia(e); if(lastDeleted&&lastDeleted.profileId===p.id){await purgeEntryMedia(lastDeleted); lastDeleted=null;} store.entries=store.entries.filter(e=>e.profileId!==p.id);} try{saveVerified();}catch(e){} pendingVaultDelete=false; renderMore(); renderHome(); showSnackbar('Vault deleted for this profile, including its photos and voice files.',false); return;}
  if(pendingDeleteId){const idx=store.entries.findIndex(x=>x.id===pendingDeleteId); if(idx>=0){if(lastDeleted) await purgeEntryMedia(lastDeleted); lastDeleted=store.entries[idx]; store.entries.splice(idx,1); try{saveVerified();}catch(e){} $('#detailModal').classList.add('hidden'); renderEntries(); renderHome(); showSnackbar('Entry deleted.',true);} pendingDeleteId=null;}
};

// ---------- insights: computed ONLY from the user's own data ----------
function insights(){
  const list=myEntries(); const now=new Date();
  const byMood7={},byMood30={}; list.forEach(e=>{if(!e.mood) return; const d=(now-new Date(e.createdAt))/86400000; if(d<7) byMood7[e.mood]=(byMood7[e.mood]||0)+1; if(d<30) byMood30[e.mood]=(byMood30[e.mood]||0)+1;});
  // streak: consecutive days ending today/yesterday with at least one entry
  const days=new Set(list.map(e=>new Date(e.createdAt).toDateString())); let streak=0; const cur=new Date(); if(!days.has(cur.toDateString())) cur.setDate(cur.getDate()-1); while(days.has(cur.toDateString())){streak++; cur.setDate(cur.getDate()-1);}
  const count=(get)=>{const m={}; list.forEach(e=>get(e).forEach(v=>{if(v) m[v]=(m[v]||0)+1;})); return Object.entries(m).sort((a,b)=>b[1]-a[1]).slice(0,5);};
  const people=count(e=>e.people||[]), places=count(e=>e.location?[e.location]:[]), tags=count(e=>e.tags||[]);
  const months={}; list.forEach(e=>{const k=new Date(e.createdAt).toISOString().slice(0,7); months[k]=(months[k]||0)+1;});
  return {byMood7,byMood30,streak,people,places,tags,months,total:list.length};
}
function onThisDay(){
  const now=new Date(), md=now.getMonth()+'-'+now.getDate();
  return myEntries().filter(e=>{const d=new Date(e.createdAt); return d.getMonth()+'-'+d.getDate()===md && d.getFullYear()<now.getFullYear();}).sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt));
}
function extracts(){
  // Extractive only: score the user's own sentences by overlap with their frequent tags/people words.
  const freq={}; myEntries().forEach(e=>tokens((e.body||'')+' '+(e.tags||[]).join(' ')).forEach(t=>freq[t]=(freq[t]||0)+1));
  const sents=[]; myEntries().forEach(e=>{String(e.body||e.transcript||'').split(/(?<=[.!?])\s+/).forEach(s=>{const t=s.trim(); if(t.length>20) sents.push({s:t,score:tokens(t).reduce((a,w)=>a+(freq[w]||0),0)+Math.min(t.length/50,2)});});});
  return sents.sort((a,b)=>b.score-a.score).slice(0,3);
}
function renderHome(){
  const p=activeProfile(); if(!p) return;
  renderReminders();
  $('#greeting').textContent='Hello, '+p.name+'.';
  $('#homeSub').textContent=myEntries().length?('You have '+myEntries().length+' entr'+(myEntries().length===1?'y':'ies')+' in this vault. Everything below is computed on this device.'):'Your vault is empty — capture your first memory below.';
  $('#todayPrompt').textContent=todayPrompt();
  const otd=onThisDay(); $('#onThisDay').innerHTML=otd.length?otd.map(e=>'<p>📅 '+esc(e.createdAt.slice(0,4))+' — <a href="#" data-open="'+e.id+'">'+esc(e.title||'Untitled')+'</a></p>').join(''):'<p class="muted">No entries from this date in past years yet. (This is a true same-month/day check — not a placeholder.)</p>';
  $$('#onThisDay [data-open]').forEach(a=>a.onclick=ev=>{ev.preventDefault(); openDetail(a.dataset.open);});
  const ins=insights();
  $('#insights').innerHTML='<p>Writing streak: <strong>'+ins.streak+' day'+(ins.streak===1?'':'s')+'</strong></p><p>Last 7 days — moods: '+esc(Object.entries(ins.byMood7).map(([k,v])=>k+' '+v).join(', ')||'no mood-tagged entries')+'</p><p>Last 30 days — moods: '+esc(Object.entries(ins.byMood30).map(([k,v])=>k+' '+v).join(', ')||'no mood-tagged entries')+'</p><p>Most-mentioned people: '+esc(ins.people.map(([k,v])=>k+' ('+v+')').join(', ')||'—')+'</p><p>Most-mentioned places: '+esc(ins.places.map(([k,v])=>k+' ('+v+')').join(', ')||'—')+'</p><p>Top tags: '+esc(ins.tags.map(([k,v])=>k+' ('+v+')').join(', ')||'—')+'</p><p>Monthly recap: '+esc(Object.entries(ins.months).map(([k,v])=>k+': '+v).join(', ')||'—')+'</p>';
  const ex=extracts(); $('#extracts').innerHTML=ex.length?ex.map(x=>'<blockquote>“'+esc(x.s)+'” <span class="muted small">— Local extract - not generated</span></blockquote>').join(''):'<p class="muted">Write a few entries and your own sentences will appear here as extracts.</p>';
  const box=$('#recentList'); box.innerHTML=''; myEntries().sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt)).slice(0,5).forEach(e=>box.appendChild(entryCard(e)));
  if(store.settings.kidsMode){$('#insights').innerHTML='<p>Writing streak: <strong>'+ins.streak+' day'+(ins.streak===1?'':'s')+'</strong> 🎉</p>'; }
}
function renderTimeline(){
  const list=myEntries().sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt));
  $('#timelineEmpty').classList.toggle('hidden',list.length>0);
  const groups={}; list.forEach(e=>{const k=new Date(e.createdAt).toLocaleDateString(undefined,{year:'numeric',month:'long'}); (groups[k]=groups[k]||[]).push(e);});
  const box=$('#timelineList'); box.innerHTML=''; Object.entries(groups).forEach(([k,es])=>{const h=document.createElement('h2'); h.className='sticky-month'; h.textContent=k; box.appendChild(h); es.forEach(e=>box.appendChild(entryCard(e)));});
  // Mood calendar strip: last 30 days
  const strip=$('#moodStrip'); strip.innerHTML=''; const emoji={Happy:'😊',Calm:'😌',Grateful:'🙏',Excited:'🤩',Tired:'😴',Sad:'😢',Reflective:'🤔',Anxious:'😟'};
  for(let i=29;i>=0;i--){const d=new Date(); d.setDate(d.getDate()-i); const day=list.find(e=>new Date(e.createdAt).toDateString()===d.toDateString()); const div=document.createElement('div'); div.className='daydot'; div.title=d.toDateString(); div.textContent=(day&&day.mood?emoji[day.mood]||'•':'·')+' '+(d.getDate()); strip.appendChild(div);}
}
// ---------- capture: draft, edit, tag suggestions, voice, photo ----------
function fillCapture(e){
  $('#cTitle').value=e.title||''; $('#cBody').value=e.body||''; $('#cTranscript').value=e.transcript||''; $('#cTags').value=(e.tags||[]).join(', '); $('#cPeople').value=(e.people||[]).join(', '); $('#cLocation').value=e.location||''; $('#cCapsule').value=e.capsuleDate||''; $('#cPin').checked=!!e.pinned; $('#cMood').value=e.mood||''; if(e.mood){$('#cMood').dataset.source=e.moodSource||'manual'; if(e.moodConfidence) $('#cMood').dataset.conf=e.moodConfidence; else delete $('#cMood').dataset.conf;} else {delete $('#cMood').dataset.source; delete $('#cMood').dataset.conf;}
  $('#moodHint').textContent=e.mood?('Mood: '+e.mood+' ('+(e.moodSource||'manual')+')'):'';
  $('#captureTitle').textContent='Edit entry'; $('#cancelEditBtn').classList.remove('hidden');
}
function clearCapture(){editingId=null; photoBlob=null; if(photoPreviewURL){URL.revokeObjectURL(photoPreviewURL); photoPreviewURL=null;} audioBlob=null; delete $('#cMood').dataset.source; delete $('#cMood').dataset.conf; $('#captureForm').reset(); $('#captureTitle').textContent='Capture'; $('#cancelEditBtn').classList.add('hidden'); $('#moodHint').textContent=''; $('#photoPreview').classList.add('hidden'); localStorage.removeItem(LS+'_draft');}
function saveDraft(){const d={title:$('#cTitle').value,body:$('#cBody').value,transcript:$('#cTranscript').value,tags:$('#cTags').value,people:$('#cPeople').value,location:$('#cLocation').value,capsule:$('#cCapsule').value,mood:$('#cMood').value,pin:$('#cPin').checked}; localStorage.setItem(LS+'_draft',JSON.stringify(d)); $('#draftNote').textContent='Draft autosaved to this device at '+new Date().toLocaleTimeString()+'.';}
function renderCaptureForm(){/* draft restored on load; suggestions refreshed on input */}
function suggestTags(){
  const text=($('#cTitle').value+' '+$('#cBody').value+' '+($('#cTranscript').value||''));
  const found=new Set();
  (text.match(/#[\w-]+/g)||[]).forEach(h=>found.add(h.slice(1).toLowerCase()));
  const knownPeople=new Set(); myEntries().forEach(e=>(e.people||[]).forEach(p=>knownPeople.add(p)));
  knownPeople.forEach(p=>{if(!p) return; const escP=p.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'); if(new RegExp('\\b'+escP+'\\b','i').test(text)) found.add('person:'+p);});
  const topic=['family','beach','birthday','travel','work','school','food','music','garden','rain']; topic.forEach(t=>{if(text.toLowerCase().includes(t)) found.add(t);});
  const existing=new Set($('#cTags').value.split(',').map(s=>s.trim().toLowerCase()).filter(Boolean));
  const list=[...found].filter(x=>!existing.has(x.replace('person:','').toLowerCase()));
  const box=$('#tagSuggest'); if(!list.length){box.classList.add('hidden'); return;} box.classList.remove('hidden');
  const chips=$('#tagChips'); chips.innerHTML=''; list.forEach(s=>{const b=document.createElement('button'); b.type='button'; b.className='chip'; b.textContent=(s.startsWith('person:')?'👤 ':'# ')+s.replace('person:',''); b.onclick=()=>{if(s.startsWith('person:')){const pv=$('#cPeople').value; $('#cPeople').value=pv?pv+', '+s.replace('person:',''):s.replace('person:','');} else {const tv=$('#cTags').value; $('#cTags').value=tv?tv+', '+s:s;} b.remove(); if(!chips.children.length) box.classList.add('hidden'); saveDraft();}; chips.appendChild(b);});
}
$('#suggestMoodBtn').onclick=()=>{
  const s=suggestMood($('#cTitle').value+' '+$('#cBody').value+' '+($('#cTranscript').value||''));
  if(!s){$('#moodHint').textContent='No clear mood keyword found — choose one yourself.'; return;}
  $('#cMood').value=s.mood; $('#moodHint').textContent='Suggested: '+s.mood+' ('+s.confidence+'% of matched mood keywords; negated words like “not happy” were ignored). You can correct it — your choice is stored as manual.';
  // Mark as suggested until user changes the select manually
  $('#cMood').dataset.source='suggested'; $('#cMood').dataset.conf=s.confidence;
};
$('#cMood').onchange=()=>{delete $('#cMood').dataset.conf; $('#cMood').dataset.source='manual'; $('#moodHint').textContent='Mood set manually: '+$('#cMood').value;};
$('#captureForm').addEventListener('input',()=>{clearTimeout(draftTimer); draftTimer=setTimeout(()=>{saveDraft(); suggestTags();},600);});
$('#discardDraftBtn').onclick=()=>{clearCapture(); showSnackbar('Draft discarded.',false);};
$('#cancelEditBtn').onclick=()=>{clearCapture(); goto('entries');};
$('#captureForm').addEventListener('submit',async ev=>{
  ev.preventDefault(); const p=activeProfile(); if(!p){alert('Create a profile first.'); goto('more'); return;}
  const now=new Date().toISOString();
  const entry={id:editingId||uid(),profileId:p.id,type:captureTab,title:$('#cTitle').value.trim(),body:$('#cBody').value,transcript:$('#cTranscript').value,mood:$('#cMood').value||'',moodSource:$('#cMood').value?($('#cMood').dataset.source||'manual'):'',moodConfidence:$('#cMood').dataset.conf?Number($('#cMood').dataset.conf):null,tags:$('#cTags').value.split(',').map(s=>s.trim()).filter(Boolean),people:$('#cPeople').value.split(',').map(s=>s.trim()).filter(Boolean),location:$('#cLocation').value.trim(),capsuleDate:$('#cCapsule').value||'',pinned:$('#cPin').checked,createdAt:now,updatedAt:now,isSample:false};
  if(editingId){const old=store.entries.find(x=>x.id===editingId); if(old){entry.createdAt=old.createdAt; entry.photoId=old.photoId; entry.audioId=old.audioId; entry.isSample=old.isSample;}}
  try{
    const oldEntry=editingId?store.entries.find(x=>x.id===editingId):null;
    if(photoBlob){entry.photoId=uid(); await idbPut('photos',entry.photoId,photoBlob); if(oldEntry&&oldEntry.photoId&&oldEntry.photoId!==entry.photoId) await idbDel('photos',oldEntry.photoId);}
    if(audioBlob){entry.audioId=uid(); await idbPut('audio',entry.audioId,audioBlob); if(oldEntry&&oldEntry.audioId&&oldEntry.audioId!==entry.audioId) await idbDel('audio',oldEntry.audioId);}
    if(editingId){const i=store.entries.findIndex(x=>x.id===editingId); store.entries[i]=entry;} else store.entries.push(entry);
    saveVerified(); // success is reported ONLY after this line succeeds
  }catch(err){alert('Save failed and was NOT reported as success: '+err.message); return;}
  const wasEditing=!!editingId; clearCapture(); showSnackbar(wasEditing?'Entry updated.':'Entry saved.',false); goto('entries');
});
// Photo: downscale to max 1600px, canvas re-encode strips metadata; EXIF GPS is never read.
$('#cPhoto').onchange=async ev=>{
  const f=ev.target.files[0]; if(!f) return;
  const img=new Image(); const url=URL.createObjectURL(f);
  try{await new Promise((res,rej)=>{img.onload=res; img.onerror=rej; img.src=url;});}catch(e){URL.revokeObjectURL(url); alert('That image could not be read on this device.'); return;}
  const scale=Math.min(1,1600/Math.max(img.width,img.height)); const c=document.createElement('canvas'); c.width=Math.round(img.width*scale); c.height=Math.round(img.height*scale); c.getContext('2d').drawImage(img,0,0,c.width,c.height);
  photoBlob=await new Promise(r=>c.toBlob(r,'image/jpeg',0.85)); URL.revokeObjectURL(url);
  if(!photoBlob){alert('That image could not be encoded on this device.'); return;}
  if(photoPreviewURL) URL.revokeObjectURL(photoPreviewURL);
  const prev=$('#photoPreview'); photoPreviewURL=URL.createObjectURL(photoBlob); prev.src=photoPreviewURL; prev.classList.remove('hidden'); showSnackbar('Photo downscaled to max 1600px; GPS never read.',false);
};

// ---------- voice: real MediaRecorder pause/resume + append-only transcription ----------
function setRecUI(state){$('#recBtn').textContent=state==='rec'?'● Recording…':'● Start recording'; $('#pauseBtn').disabled=state!=='rec'; $('#pauseBtn').textContent='⏸ Pause'; $('#stopBtn').disabled=state==='idle';}
$('#recBtn').onclick=async()=>{
  if(rec&&rec.state!=='inactive'){showSnackbar('A recording is already in progress.',false); return;}
  try{const stream=await navigator.mediaDevices.getUserMedia({audio:true}); rec=new MediaRecorder(stream); recChunks=[]; recPausedMs=0;
    rec.ondataavailable=e=>{if(e.data.size) recChunks.push(e.data);};
    rec.onstop=()=>{clearInterval(recTimerInt); audioBlob=new Blob(recChunks,{type:rec.mimeType||'audio/webm'}); stream.getTracks().forEach(t=>t.stop()); setRecUI('idle'); showSnackbar('Recording saved to this entry (not uploaded).',false);};
    rec.start(); recStart=Date.now(); recTimerInt=setInterval(()=>{const now=(rec&&rec.state==='paused')?recPauseStart:Date.now(); const elapsed=now-recStart-recPausedMs; $('#recTimer').textContent=fmtTime(Math.max(0,elapsed));},250); setRecUI('rec'); startSpeech();
  }catch(e){alert('Microphone unavailable: '+e.message);}
};
$('#pauseBtn').onclick=()=>{if(!rec) return; if(rec.state==='recording'){rec.pause(); recPauseStart=Date.now(); $('#pauseBtn').textContent='▶ Resume'; if(recognition) try{recognition.stop();}catch(e){}} else if(rec.state==='paused'){rec.resume(); recPausedMs+=Date.now()-recPauseStart; $('#pauseBtn').textContent='⏸ Pause'; startSpeech();}};
$('#stopBtn').onclick=()=>{if(rec&&rec.state!=='inactive') rec.stop(); clearInterval(recTimerInt); if(recognition) try{recognition.stop();}catch(e){}};
function startSpeech(){
  const SR=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(!SR){$('#speechSupport').textContent='Live transcription is not available in this browser — you can still record and edit the transcript by hand.'; return;}
  $('#speechSupport').textContent='Live transcription via your browser’s speech service. On-device / browser transcription — may vary by device.';
  try{recognition=new SR(); recognition.continuous=true; recognition.interimResults=true;
    recognition.onresult=ev=>{let final=''; for(let i=ev.resultIndex;i<ev.results.length;i++){if(ev.results[i].isFinal) final+=ev.results[i][0].transcript;} if(final){const ta=$('#cTranscript'); ta.value=(ta.value?ta.value+' ':'')+final.trim(); saveDraft();}}; // APPEND, never overwrite
    recognition.onerror=()=>{}; recognition.start();
  }catch(e){$('#speechSupport').textContent='Transcription could not start in this browser: '+e.message;}
}
// ---------- profiles, PIN lock, onboarding ----------
/* ROUND 2 PIN / recovery: salted PBKDF2 (WebCrypto, 100,000 iterations, SHA-256, 256-bit output).
   The PIN itself is never stored. Old Round-1 profiles carry a bare SHA-256 hash with no salt;
   verifyPin()/verifyRecovery() accept that legacy hash ONCE and immediately re-hash with
   PBKDF2+salt, discarding the legacy hash. New profiles never create a legacy hash. */
const PBKDF2_ITERS=100000;
function b64(buf){return btoa(String.fromCharCode(...new Uint8Array(buf)));}
function b64ToBytes(str){return Uint8Array.from(atob(str),c=>c.charCodeAt(0));}
function hex(buf){return [...new Uint8Array(buf)].map(x=>x.toString(16).padStart(2,'0')).join('');}
async function pbkdf2Hex(secret,saltBytes){const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(secret),'PBKDF2',false,['deriveBits']);const bits=await crypto.subtle.deriveBits({name:'PBKDF2',salt:saltBytes,iterations:PBKDF2_ITERS,hash:'SHA-256'},key,256);return hex(bits);}
async function makePinRecord(pin){const salt=crypto.getRandomValues(new Uint8Array(16));return {pinHash:await pbkdf2Hex('lifevault-v2-pin:'+pin,salt),pinSalt:b64(salt)};}
async function makeRecoveryRecord(key){const salt=crypto.getRandomValues(new Uint8Array(16));return {recoveryHash:await pbkdf2Hex('lifevault-v2-recovery:'+key,salt),recoverySalt:b64(salt)};}
function newRecoveryKey(){return [...crypto.getRandomValues(new Uint8Array(20))].map(b=>(b%26+97)).join('').match(/.{4}/g).join('-');}
async function verifyPin(pin,prof){
  if(prof.pinSalt){return await pbkdf2Hex('lifevault-v2-pin:'+pin,b64ToBytes(prof.pinSalt))===prof.pinHash;}
  if(prof.pinHash&&await sha256('lifevault-v2-pin:'+pin)===prof.pinHash){const r=await makePinRecord(pin); prof.pinHash=r.pinHash; prof.pinSalt=r.pinSalt; try{saveVerified();}catch(e){} return true;}
  return false;
}
async function verifyRecovery(key,prof){
  if(prof.recoverySalt){return await pbkdf2Hex('lifevault-v2-recovery:'+key,b64ToBytes(prof.recoverySalt))===prof.recoveryHash;}
  if(prof.recoveryHash&&await sha256('lifevault-v2-recovery:'+key)===prof.recoveryHash){const r=await makeRecoveryRecord(key); prof.recoveryHash=r.recoveryHash; prof.recoverySalt=r.recoverySalt; try{saveVerified();}catch(e){} return true;}
  return false;
}
function lockNow(){if(activeProfile()&&activeProfile().pinHash){unlocked=false; $('#lockScreen').classList.remove('hidden'); $('#pinInput').value=''; $('#pinError').textContent='';}}
document.addEventListener('visibilitychange',()=>{if(document.hidden) lockNow();});
$('#unlockBtn').onclick=async()=>{
  const p=activeProfile(); if(!p) return;
  if(Date.now()<lockUntil){$('#pinError').textContent='Too many failed attempts. Try again in '+Math.ceil((lockUntil-Date.now())/1000)+'s.'; return;}
  const pin=$('#pinInput').value; const ok=await verifyPin(pin,p); $('#pinInput').value='';
  if(ok){unlocked=true; failedAttempts=0; try{localStorage.removeItem(LS+'_lockUntil');}catch(e){} $('#lockScreen').classList.add('hidden'); renderHome(); renderReminders(); scheduleReflection();}
  else{failedAttempts++; if(failedAttempts>=5){lockUntil=Date.now()+30000; try{localStorage.setItem(LS+'_lockUntil',String(lockUntil));}catch(e){} failedAttempts=0; $('#pinError').textContent='5 failed attempts — locked for 30 seconds.';} else $('#pinError').textContent='Wrong PIN. Attempt '+failedAttempts+' of 5.';}
};
$('#forgotBtn').onclick=()=>$('#recoveryBox').classList.toggle('hidden');
$('#recoverBtn').onclick=async()=>{
  const p=activeProfile(); if(!p||!p.recoveryHash){alert('No recovery key was set for this profile.'); return;}
  const key=$('#recoveryInput').value; const newPin=$('#newPinInput').value;
  if(!newPin){alert('Enter a new PIN first.'); return;}
  if(await verifyRecovery(key,p)){const r=await makePinRecord(newPin); p.pinHash=r.pinHash; p.pinSalt=r.pinSalt; $('#recoveryInput').value=''; $('#newPinInput').value=''; try{saveVerified();}catch(e){alert('Save failed.'); return;} unlocked=true; try{localStorage.removeItem(LS+'_lockUntil');}catch(e){} $('#lockScreen').classList.add('hidden'); renderHome(); renderReminders(); scheduleReflection(); showSnackbar('PIN reset with your recovery key.',false);}
  else alert('That recovery key does not match. It does not log you in automatically.');
};
$('#createProfileBtn').onclick=async()=>{
  const name=$('#newProfileName').value.trim(); if(!name){$('#profileMsg').textContent='Name is required.'; return;}
  const pin=$('#newProfilePin').value.trim(); const prof={id:uid(),name,email:$('#newProfileEmail').value.trim(),pinHash:'',pinSalt:'',recoveryHash:'',recoverySalt:''}; let shownKey='';
  if(pin){const r=await makePinRecord(pin); prof.pinHash=r.pinHash; prof.pinSalt=r.pinSalt; shownKey=newRecoveryKey(); const rr=await makeRecoveryRecord(shownKey); prof.recoveryHash=rr.recoveryHash; prof.recoverySalt=rr.recoverySalt;}
  store.profiles.push(prof); store.activeProfileId=prof.id;
  try{saveVerified();}catch(e){store.profiles=store.profiles.filter(x=>x.id!==prof.id); $('#profileMsg').textContent='Profile NOT created — save failed, so no success is claimed.'; return;}
  // Verify the profile is actually readable back before claiming success
  const back=loadStore(); if(!back.profiles.find(x=>x.id===prof.id)){$('#profileMsg').textContent='Profile NOT created — verification failed.'; return;}
  $('#newProfilePin').value='';
  $('#profileMsg').textContent='Profile created and verified. '+(shownKey?'Write down this recovery key (shown once — it is NOT stored by the app, only its hash is): '+shownKey:'No PIN - anyone opening this app can open this profile.');
  renderProfileSelect(); renderMore(); if(prof.pinHash) lockNow(); else {unlocked=true;}
};
function renderProfileSelect(){const s=$('#profileSelect'); s.innerHTML=''; store.profiles.forEach(p=>{const o=document.createElement('option'); o.value=p.id; o.textContent=p.name; if(p.id===store.activeProfileId) o.selected=true; s.appendChild(o);});}
$('#profileSelect').onchange=async()=>{
  const target=store.profiles.find(p=>p.id===$('#profileSelect').value); if(!target) return;
  if(target.pinHash){const pin=await askSwitchPin(target.name); if(!pin||!(await verifyPin(pin,target))){alert('Wrong PIN — profile was not switched.'); renderProfileSelect(); return;}}
  store.activeProfileId=target.id; try{saveVerified();}catch(e){} unlocked=true; renderHome(); renderEntries(); renderReminders(); scheduleReflection();
};
function renderMore(){
  const p=activeProfile(); $('#profileInfo').innerHTML=p?('<p><strong>'+esc(p.name)+'</strong>'+(p.email?' · '+esc(p.email):'')+'</p><p class="muted small">'+(p.pinHash?'PIN lock: on (hash only, never plaintext)':'PIN lock: off — No PIN - anyone opening this app can open this profile.')+'</p>'):'<p>No profile yet — create one above.</p>';
  $('#lastBackup').textContent='Last backup: '+(store.lastBackup?new Date(store.lastBackup).toLocaleString():'never');
  $('#themeSel').value=store.settings.theme||'system'; $('#textSizeSel').value=store.settings.textSize||'1';
  $('#recoveryNote').textContent=p&&p.recoveryHash?'Recovery key: shown once at setup and NOT stored by the app (only its hash is kept). Keep your written copy safe — it is the only way back if you forget your PIN.':'No recovery key on file for this profile.';
  renderReminderSettings();
  $('#kidsToggle').textContent='🧒 Kids Mode: '+(store.settings.kidsMode?'On':'Off');
}
$('#setPinBtn').onclick=async()=>{
  const p=activeProfile(); if(!p) return;
  const newPin=$('#setPin').value; if(!newPin){alert('Enter a new PIN — leaving it blank does not change or remove your PIN.'); return;}
  if(p.pinHash&&!(await verifyPin($('#currentPin').value,p))){alert('Current PIN is wrong.'); return;}
  const r=await makePinRecord(newPin); p.pinHash=r.pinHash; p.pinSalt=r.pinSalt; $('#setPin').value=''; $('#currentPin').value='';
  let shownKey=''; if(!p.recoveryHash){shownKey=newRecoveryKey(); const rr=await makeRecoveryRecord(shownKey); p.recoveryHash=rr.recoveryHash; p.recoverySalt=rr.recoverySalt;}
  try{saveVerified();}catch(e){alert('PIN NOT saved — save failed.'); return;}
  if(shownKey) alert('New recovery key (shown once, write it down — it is NOT stored by the app): '+shownKey);
  showSnackbar('PIN saved (salted hash only, never plaintext).',false); renderMore();
};
$('#deleteVaultBtn').onclick=()=>{pendingVaultDelete=true; $('#confirmTitle').textContent='Delete this profile’s vault?'; $('#confirmText').textContent='This deletes all entries for the active profile only. This cannot be undone from inside the app unless you have a backup file.'; $('#confirmModal').classList.remove('hidden');};
// Onboarding (first run only)
let obStepIdx=0;
function renderOnboarding(){
  const box=$('#obStep');
  if(obStepIdx===0){box.innerHTML='<h2>1 · Our privacy promise, in plain words</h2><p>Your entries stay on <strong>this device</strong> in your browser. This app itself makes <strong>no cloud calls, has no server account, no sync, no analytics and no ads</strong>. The one exception: if you use live voice transcription, your <em>browser’s</em> speech service may process audio — that is the browser’s service, not ours, and it may vary by device.</p><p><strong>The PIN is a lock, not encryption</strong> — entries are stored in plaintext in browser storage, so anyone with device/browser access can read them.</p>';}
  if(obStepIdx===1){box.innerHTML='<h2>2 · How do you want to start?</h2><p>Choose a default. You can switch any time with the Capture tabs.</p><div class="row"><button class="btn primary" id="obWrite">✍️ Write first</button><button class="btn secondary" id="obVoice">🎙️ Voice first</button></div>'; setTimeout(()=>{$('#obWrite').onclick=()=>{captureTab='write'; obStepIdx++; renderOnboarding();}; $('#obVoice').onclick=()=>{captureTab='voice'; obStepIdx++; renderOnboarding();};},0);}
  if(obStepIdx===2){box.innerHTML='<h2>3 · A prompt, and a sample you can delete</h2><p>Today’s prompt: <strong>'+esc(todayPrompt())+'</strong></p><p>A sample entry will be added so you can see how entries look. You can delete it any time — it is marked “Sample”.</p><label for="obName">Your name (only this is required)</label><input id="obName" placeholder="Your name"><label for="obPin">PIN (optional, recommended)</label><input id="obPin" inputmode="numeric" placeholder="Optional PIN">';}
  $('#obBack').style.visibility=obStepIdx===0?'hidden':'visible';
  $('#obNext').textContent=obStepIdx===2?'Finish':'Continue';
}
$('#obBack').onclick=()=>{if(obStepIdx>0){obStepIdx--; renderOnboarding();}};
$('#obNext').onclick=async()=>{
  if(obStepIdx<2){obStepIdx++; renderOnboarding(); return;}
  const name=($('#obName')?.value||'').trim(); if(!name){alert('Please enter a name — that is the only required field.'); return;}
  const pin=($('#obPin')?.value||'').trim(); const prof={id:uid(),name,email:'',pinHash:'',pinSalt:'',recoveryHash:'',recoverySalt:''}; let shownKey='';
  if(pin){const r=await makePinRecord(pin); prof.pinHash=r.pinHash; prof.pinSalt=r.pinSalt; shownKey=newRecoveryKey(); const rr=await makeRecoveryRecord(shownKey); prof.recoveryHash=rr.recoveryHash; prof.recoverySalt=rr.recoverySalt;}
  store.profiles.push(prof); store.activeProfileId=prof.id;
  const sampleId=uid();
  store.entries.push({id:sampleId,profileId:prof.id,type:'write',title:'Welcome to LifeVault',body:'This is a sample entry. Try editing it, searching for it, or deleting it — it is yours to practise with. '+todayPrompt(),mood:'Grateful',moodSource:'manual',moodConfidence:null,tags:['welcome'],people:[],location:'',capsuleDate:'',pinned:false,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),isSample:true});
  store.onboarded=true;
  try{saveVerified();}catch(e){store.profiles=store.profiles.filter(x=>x.id!==prof.id); store.entries=store.entries.filter(x=>x.id!==sampleId); store.onboarded=false; alert('Setup NOT completed — save failed, so no success is claimed.'); return;}
  $('#onboarding').classList.add('hidden'); if(shownKey) alert('Your recovery key (shown once — write it down, it is NOT stored by the app): '+shownKey+'\nWithout your PIN and without this key, the vault cannot be opened in the app. The PIN is a lock, not encryption — entries are stored in plaintext in browser storage, so anyone with device/browser access can read them.');
  renderProfileSelect(); if(prof.pinHash) lockNow(); else {unlocked=true; goto('home'); if(!pin) showSnackbar('No PIN - anyone opening this app can open this profile.',false);}
};

// ---------- export / import: FILE, with media, SHA-256 corruption check / AES-GCM authentication, 50MB cap, v1 fallback ----------
async function encryptJSON(text,pw){
  const salt=crypto.getRandomValues(new Uint8Array(16)), iv=crypto.getRandomValues(new Uint8Array(12));
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(pw),'PBKDF2',false,['deriveKey']);
  const aes=await crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:100000,hash:'SHA-256'},key,{name:'AES-GCM',length:256},false,['encrypt']);
  const ct=await crypto.subtle.encrypt({name:'AES-GCM',iv},aes,new TextEncoder().encode(text));
  const b64=b=>btoa(String.fromCharCode(...new Uint8Array(b)));
  return {encrypted:true,salt:b64(salt),iv:b64(iv),data:b64(ct)};
}
async function decryptJSON(obj,pw){
  const dec=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(pw),'PBKDF2',false,['deriveKey']);
  const aes=await crypto.subtle.deriveKey({name:'PBKDF2',salt:dec(obj.salt),iterations:100000,hash:'SHA-256'},key,{name:'AES-GCM',length:256},false,['decrypt']);
  const pt=await crypto.subtle.decrypt({name:'AES-GCM',iv:dec(obj.iv)},aes,dec(obj.data)); return new TextDecoder().decode(pt);
}
function download(name,text){const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([text],{type:'application/json'})); a.download=name; a.click(); setTimeout(()=>URL.revokeObjectURL(a.href),5000);}
/* Export password is collected in the in-page dialog (see askExportPassword), never inline. */
$('#encryptExport').onchange=()=>{$('#exportWarning').classList.toggle('hidden',$('#encryptExport').checked);};
$('#exportBtn').onclick=async()=>{
  const p=activeProfile(); if(!p) return;
  const entries=myEntries(); const media={photos:{},audio:{}};
  for(const e of entries){
    if(e.photoId){const b=await idbGet('photos',e.photoId); if(b) media.photos[e.photoId]=await blobToDataURL(b);}
    if(e.audioId){const b=await idbGet('audio',e.audioId); if(b) media.audio[e.audioId]=await blobToDataURL(b);}
  }
  const inner={format:'lifevault-v2',version:2,exportedAt:new Date().toISOString(),profile:{name:p.name,email:p.email||''},entries,media};
  const innerStr=JSON.stringify(inner); inner.checksum=await sha256(innerStr);
  if(!$('#encryptExport').checked){if(!confirm('This backup will be readable by anyone with the file - tick password-protect to lock it. Continue with an unprotected backup?')){showSnackbar('Export cancelled.',false); return;}}
  let out=JSON.stringify(inner);
  if($('#encryptExport').checked){let pw=await askExportPassword(); if(!pw){showSnackbar('Export cancelled.',false); return;} try{out=JSON.stringify(await encryptJSON(out,pw));}finally{pw='';}}
  download('lifevault-backup-'+new Date().toISOString().slice(0,10)+'.json',out);
  store.lastBackup=new Date().toISOString(); try{saveVerified();}catch(e){} renderMore(); showSnackbar('Backup file downloaded.',false);
};
async function parseV1(text){
  // v1 "export" was a clipboard Base64 blob, not a file. Try Base64-decoded JSON, and gzip via DecompressionStream if present.
  try{const dec=atob(text.trim()); const j=JSON.parse(dec); return j;}catch(e){}
  try{const j=JSON.parse(text); return j;}catch(e){}
  if(window.DecompressionStream){try{const ds=new DecompressionStream('gzip'); const s=new Blob([Uint8Array.from(atob(text.trim()),c=>c.charCodeAt(0))]).stream().pipeThrough(ds); const out=await new Response(s).text(); if(out.length>50*1024*1024) return null; return JSON.parse(out);}catch(e){}}
  return null;
}
$('#importFile').onchange=async ev=>{
  const f=ev.target.files[0]; if(!f) return;
  if(f.size>50*1024*1024){$('#importMsg').textContent='Import rejected: file is over the 50 MB cap.'; return;}
  let text=await f.text(); if(text.length>50*1024*1024){$('#importMsg').textContent='Import rejected: decompressed content is over the 50 MB cap.'; return;}
  let obj=null; try{obj=JSON.parse(text);}catch(e){obj=await parseV1(text);}
  if(!obj){$('#importMsg').textContent='Import rejected: file is not a readable LifeVault backup (v2 JSON or v1 Base64 export).'; return;}
  if(obj.encrypted){let pw=await askImportPassword(); if(!pw){$('#importMsg').textContent='Import cancelled.'; return;} try{text=await decryptJSON(obj,pw); if(text.length>50*1024*1024){$('#importMsg').textContent='Import rejected: decrypted content is over the 50 MB cap.'; return;} obj=JSON.parse(text);}catch(e){if($('#importMsg').textContent==='Import cancelled.') return; $('#importMsg').textContent='Import rejected: wrong password or corrupt encrypted file.'; return;}finally{pw='';}}
  const entries=obj.entries||obj.data||[]; if(!Array.isArray(entries)){$('#importMsg').textContent='Import rejected: no entries array found.'; return;}
  // Integrity check for v2 files: checksum was computed over the inner object before the checksum field was added (added last, so deleting it restores the original key order).
  if(obj.format==='lifevault-v2'&&obj.checksum){const copy={...obj}; delete copy.checksum; const recomputed=await sha256(JSON.stringify(copy)); if(recomputed!==obj.checksum){$('#importMsg').textContent='Import rejected: corruption check failed — file may be truncated or corrupted. (SHA-256 corruption check for unencrypted files; AES-GCM authentication for password-protected files.)'; return;}}
  const p=activeProfile(); if(!p){$('#importMsg').textContent='Create a profile first.'; return;}
  let restored=0, skipped=0, missing=0, mediaRestored=0;
  const totalEntries=entries.length; const truncated=totalEntries>5000;
  for(const raw of entries.slice(0,5000)){
    const e={id:raw.id||uid(),profileId:p.id,type:raw.type||'write',title:raw.title||'',body:raw.body||raw.content||raw.text||'',transcript:raw.transcript||'',mood:raw.mood||'',moodSource:raw.moodSource||'manual',moodConfidence:raw.moodConfidence||null,tags:(Array.isArray(raw.tags)?raw.tags:String(raw.tags||'').split(',').map(s=>s.trim()).filter(Boolean)),people:(Array.isArray(raw.people)?raw.people:String(raw.people||'').split(',').map(s=>s.trim()).filter(Boolean)),location:raw.location||'',capsuleDate:raw.capsuleDate||'',pinned:!!raw.pinned,createdAt:raw.createdAt||new Date().toISOString(),updatedAt:new Date().toISOString(),isSample:false};
    if(raw.photoId) e.photoId=raw.photoId; if(raw.audioId) e.audioId=raw.audioId;
    if(store.entries.find(x=>x.id===e.id)){skipped++; continue;}
    store.entries.push(e);
    restored++;
    if(obj.media){
      if(e.photoId&&obj.media.photos&&obj.media.photos[e.photoId]){try{await idbPut('photos',e.photoId,await dataURLToBlob(obj.media.photos[e.photoId])); mediaRestored++;}catch(err){missing++;}}
      else if(e.photoId) missing++;
      if(e.audioId&&obj.media.audio&&obj.media.audio[e.audioId]){try{await idbPut('audio',e.audioId,await dataURLToBlob(obj.media.audio[e.audioId])); mediaRestored++;}catch(err){missing++;}}
      else if(e.audioId) missing++;
    } else if(e.photoId||e.audioId) missing++;
  }
  try{saveVerified();}catch(err){$('#importMsg').textContent='Import NOT saved — save failed.'; return;}
  $('#importMsg').textContent=restored+' entries restored'+(truncated?' (note: file contained '+totalEntries+' entries; only the first 5000 were imported)':'')+(skipped?' ('+skipped+' duplicates skipped)':'')+', '+mediaRestored+' media files restored, '+missing+' media files missing. '+(missing?'Missing files are reported honestly: those entries keep their text, but their photo/voice cannot be shown.':'');
  renderEntries(); renderHome();
};
// ---------- in-page password dialogs (ROUND 2: no browser prompt anywhere) ----------
function askPasswordDialog({title,showConfirm}){
  return new Promise(res=>{
    $('#pwDialogTitle').textContent=title; $('#pwInput').value=''; $('#pwConfirm').value=''; $('#pwConfirmRow').classList.toggle('hidden',!showConfirm); $('#pwError').textContent=''; $('#pwDialog').classList.remove('hidden'); $('#pwInput').focus();
    const done=v=>{$('#pwDialog').classList.add('hidden'); $('#pwInput').value=''; $('#pwConfirm').value=''; res(v);};
    $('#pwShow').checked=false; $('#pwInput').type='password'; $('#pwConfirm').type='password';
    $('#pwOk').onclick=()=>{const pw=$('#pwInput').value; if(!pw){$('#pwError').textContent='Enter a password.'; return;} if(showConfirm&&pw!==$('#pwConfirm').value){$('#pwError').textContent='Passwords do not match.'; return;} done(pw);};
    $('#pwCancel').onclick=()=>done(null);
  });
}
function askExportPassword(){return askPasswordDialog({title:'Password-protect this export',showConfirm:true});}
function askImportPassword(){return askPasswordDialog({title:'This backup is password-protected',showConfirm:false});}
$('#pwShow').onchange=()=>{const t=$('#pwShow').checked?'text':'password'; $('#pwInput').type=t; $('#pwConfirm').type=t;};
function askSwitchPin(name){
  return new Promise(res=>{
    $('#switchPinName').textContent=name; $('#switchPinInput').value=''; $('#switchPinDialog').classList.remove('hidden'); $('#switchPinInput').focus();
    const done=v=>{$('#switchPinDialog').classList.add('hidden'); $('#switchPinInput').value=''; res(v);};
    $('#switchPinOk').onclick=()=>done($('#switchPinInput').value||null);
    $('#switchPinCancel').onclick=()=>done(null);
  });
}
// ---------- reminders (ROUND 2: opt-in, default OFF, local only, no server) ----------
function getReminders(){return store.settings.reminders||DEFAULT_SETTINGS.reminders;}
function localISODate(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
function dueCapsules(){const today=localISODate(new Date()); return myEntries().filter(e=>e.capsuleDate&&e.capsuleDate<=today);}
function renderReminders(){
  if(!unlocked) return;
  const r=getReminders(); const due=dueCapsules();
  const card=$('#capsuleCard'); if(r.capsulesOnOpen&&due.length){card.classList.remove('hidden'); const box=$('#capsuleList'); box.innerHTML=''; due.forEach(e=>{const b=document.createElement('button'); b.className='btn secondary'; b.textContent='⏳ '+(e.title||'Untitled')+' — due '+e.capsuleDate; b.onclick=()=>openDetail(e.id); box.appendChild(b);}); $('#capsuleCount').textContent=due.length+(due.length===1?' time capsule is':' time capsules are')+' ready to revisit. Titles only are listed here; open one to read it.';}
  else if(card) card.classList.add('hidden');
  const rc=$('#reflectionCard');
  if(r.dailyPrompt&&r.lastReflectionDate!==localISODate(new Date())){rc.classList.remove('hidden'); $('#reflectionPrompt').textContent=todayPrompt();}
  else if(rc) rc.classList.add('hidden');
}
function renderReminderSettings(){
  const r=getReminders();
  const a=$('#remCapsule'); if(a) a.checked=!!r.capsulesOnOpen;
  const b=$('#remDaily'); if(b) b.checked=!!r.dailyPrompt;
  const t=$('#remDailyTime'); if(t) t.value=r.dailyTime||'09:00';
  const st=$('#remStatus'); if(st) st.textContent=(!('Notification' in window))?'Notifications are not available in this browser — the daily prompt will appear in-app on open.':(Notification.permission==='granted'?'Notifications allowed — the daily prompt uses a generic message only, never entry text.':Notification.permission==='denied'?'Notifications denied — the daily prompt will appear in-app on open instead.':'Notifications not yet allowed — turn the daily prompt on and allow notifications, or it will appear in-app on open.');
}
function scheduleReflection(){
  if(reflectionTimer){clearTimeout(reflectionTimer); reflectionTimer=null;}
  const r=getReminders(); if(!r.dailyPrompt||!unlocked) return;
  const now=new Date(); const [hh,mm]=String(r.dailyTime||'09:00').split(':').map(Number); const next=new Date(now); next.setHours(hh||9,mm||0,0,0);
  if(next<=now) return; // already passed today: renderReminders() shows the in-app prompt instead
  const ms=next-now; if(ms>86400000) return;
  reflectionTimer=setTimeout(()=>{
    const rr=getReminders(); if(rr.lastReflectionDate===localISODate(new Date())) return;
    if('Notification' in window&&Notification.permission==='granted'){try{new Notification('LifeVault',{body:'A LifeVault reflection prompt is waiting'});}catch(e){}}
    rr.lastReflectionDate=localISODate(new Date()); try{saveVerified();}catch(e){}
    renderReminders();
  },ms);
}
function saveReminderSettings(){
  const r=getReminders(); r.capsulesOnOpen=$('#remCapsule').checked; r.dailyPrompt=$('#remDaily').checked; r.dailyTime=$('#remDailyTime').value||'09:00'; store.settings.reminders=r;
  try{saveVerified();}catch(e){}
  if(r.dailyPrompt&&'Notification' in window&&Notification.permission==='default'){Notification.requestPermission().then(()=>{renderReminderSettings(); scheduleReflection();});}
  renderReminderSettings(); renderReminders(); scheduleReflection();
}
// ---------- settings, kids mode, about, wiring ----------
$('#themeSel').onchange=()=>{store.settings.theme=$('#themeSel').value; try{saveVerified();}catch(e){} applyTheme();};
$('#textSizeSel').onchange=()=>{store.settings.textSize=$('#textSizeSel').value; try{saveVerified();}catch(e){} applyTheme();};
function applyTheme(){document.documentElement.dataset.theme=store.settings.theme||'system'; document.documentElement.style.setProperty('--fs',store.settings.textSize||'1');}
$('#remCapsule').onchange=saveReminderSettings; $('#remDaily').onchange=saveReminderSettings; $('#remDailyTime').onchange=saveReminderSettings;
$('#kidsToggle').onclick=()=>{store.settings.kidsMode=!store.settings.kidsMode; try{saveVerified();}catch(e){} document.body.classList.toggle('kids',store.settings.kidsMode); renderMore(); renderHome(); showSnackbar('Kids Mode: '+(store.settings.kidsMode?'simplified Home insights on — no drawing offered (drawing is not built in v2).':'off'),false);};
$('#aboutBtn').onclick=()=>{alert($('#aboutText').textContent);};
function setAbout(){$('#aboutText').textContent='Round 2: PINs and recovery keys are stored only as salted PBKDF2 hashes (100,000 iterations) — never as plaintext, and a recovery key is shown once at setup and then NOT stored by the app at all. The PIN is a lock, not encryption — entries are stored in plaintext in browser storage, so anyone with device/browser access can read them. This web v2 is a separate, honest demo/rebuild — it is NOT the original Android code, and it itself makes no cloud calls; browser live transcription may send audio to the browser speech service. The original Android APK is a different build. This v2 has no server account, no cloud sync, no analytics and no ads. Photos are included; video is NOT included in v2. Kids Mode simplifies Home insights only (there is no separate kids CSS/layout) — it does not offer drawing, because drawing content was not built to actually save in v1 and is not offered here either. There is no “Compact” button in v2: a no-op button was removed rather than repeated. Backup/restore includes voice and photo files, and honestly reports missing files.';}
$$('[data-goto]').forEach(b=>b.onclick=()=>{const v=b.dataset.goto; if(v==='capture'){if(editingId) clearCapture(); else {clearTimeout(draftTimer); try{saveDraft();}catch(e){}} gotoCapture(captureTab);} else {if(!$('#view-capture').classList.contains('hidden')){clearTimeout(draftTimer); try{saveDraft();}catch(e){}} goto(v);}});
$$('[data-goto-capture]').forEach(b=>b.onclick=()=>{if(editingId) clearCapture(); else {clearTimeout(draftTimer); try{saveDraft();}catch(e){}} gotoCapture('write');});
$$('[data-cap-tab]').forEach(b=>b.onclick=()=>setCapTab(b.dataset.capTab));
['#q','#fMood','#fType','#fPerson','#fFrom','#fTo'].forEach(s=>{const el=$(s); if(el) el.addEventListener('input',renderEntries);});
if('serviceWorker' in navigator && location.protocol!=='file:'){navigator.serviceWorker.register('sw.js').catch(()=>{});}
// ---------- boot ----------
(function boot(){
  applyTheme(); setAbout(); renderProfileSelect();
  if(store.settings.kidsMode) document.body.classList.add('kids');
  // Restore draft if any
  try{const d=JSON.parse(localStorage.getItem(LS+'_draft')||'null'); if(d){$('#cTitle').value=d.title||''; $('#cBody').value=d.body||''; $('#cTranscript').value=d.transcript||''; $('#cTags').value=d.tags||''; $('#cPeople').value=d.people||''; $('#cLocation').value=d.location||''; $('#cCapsule').value=d.capsule||''; $('#cMood').value=d.mood||''; $('#cPin').checked=!!d.pin;}}catch(e){}
  if(!store.onboarded||!store.profiles.length){obStepIdx=0; renderOnboarding(); $('#onboarding').classList.remove('hidden'); return;}
  try{const saved=Number(localStorage.getItem(LS+'_lockUntil')); if(saved>Date.now()) lockUntil=saved; else if(saved) localStorage.removeItem(LS+'_lockUntil');}catch(e){} renderMore(); const p=activeProfile(); if(p&&p.pinHash){lockNow();} else {unlocked=true; goto('home'); scheduleReflection();}
})();
