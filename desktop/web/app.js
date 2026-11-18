import {LANGUAGES,canonical,languageId,lessonKey,validEntries,parsePayload,mergeEntries,makeBackup,
 extractQuestions,migrateProgress,assemble,matches,esc,markdown,rubyHtml} from "./core.js";
import {dueAt,review,migrateLegacy} from "./fsrs.js";
import {buildPrompt} from "./prompt.js";
import {mascot} from "./mascot.js";
import {icon,mountStaticIcons} from "./icons.js";

const STORE="studysh.desktop.v1.";
const $=(id)=>document.getElementById(id);
function readJson(key,fallback){try{return JSON.parse(localStorage.getItem(STORE+key))??fallback;}catch{return fallback;}}
function readSetting(key,fallback){return localStorage.getItem(STORE+key)??fallback;}
const blank=()=>({term:"",context:"",originalSentence:"",specificQuestion:""});
const state={
  view:"home",entries:validEntries({entries:readJson("entries",[])}),
  progress:readJson("progress",{}),language:canonical(readSetting("language","English")),
  practiceLanguage:canonical(readSetting("practiceLanguage","English")),
  furigana:readSetting("furigana","false")==="true",
  voice:readSetting("voice",""),voiceEngine:readSetting("voiceEngine","windows"),qwenSpeaker:readSetting("qwenSpeaker","auto"),muted:readSetting("muted","false")==="true",
  selected:null,filter:"All",search:"",terms:[blank()],session:null
};
const renderRoot=$("content"),modal=$("modal"),backdrop=$("modal-backdrop");
mountStaticIcons();
let toastTimer;
function save(){localStorage.setItem(STORE+"entries",JSON.stringify(state.entries));localStorage.setItem(STORE+"progress",JSON.stringify(state.progress));}
function persistSetting(name,value){localStorage.setItem(STORE+name,String(value));}
function showToast(message){const node=$("toast");node.textContent=message;node.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>node.classList.remove("show"),3800);}
const tabButton=(language,active,action)=>'<button data-action="'+action+'" data-language="'+esc(language)+'" class="'+(active?"active":"")+'">'+esc(language)+'</button>';
const heading=(k,title,subtitle)=>'<div class="page-head"><div><div class="eyebrow">'+esc(k)+'</div><h1 class="page-title">'+esc(title)+'</h1><p class="page-subtitle">'+esc(subtitle)+'</p></div></div>';
function stat(value,label){return '<div class="card stat"><b>'+esc(value)+'</b><span>'+esc(label)+'</span></div>';}
function currentLessons(){return state.entries.filter(e=>canonical(e.language)===state.language);}
function dueQuestions(language){
 const all=extractQuestions(state.entries,language);
 state.progress=migrateProgress(state.progress,state.entries,all);
 const groups=new Map();
 for(const q of all){if(!groups.has(q.key))groups.set(q.key,[]);groups.get(q.key).push(q);}
 const now=Date.now(),out=[];
 for(const [key,group] of groups){
  if(dueAt(state.progress[key])>now)continue;
  const attempts=state.progress[key]?.attempts||0;
  out.push(group[attempts%group.length]);
 }
 return out.sort((a,b)=>{
  const aDue=dueAt(state.progress[a.key]),bDue=dueAt(state.progress[b.key]);
  return (aDue===0?0:1)-(bDue===0?0:1)||aDue-bDue;
 });
}
function selectedEntry(){return state.entries.find(e=>lessonKey(e)===state.selected);}
function activePracticeJapanese(){return state.view==="practice"&&state.practiceLanguage==="Japanese";}
function shouldShowFurigana(){return state.language==="Japanese"||activePracticeJapanese()||
 (state.view==="lessons"&&selectedEntry()&&canonical(selectedEntry().language)==="Japanese");}
function navigate(view){
 state.view=view;state.selected=null;state.session=null;render();renderRoot.scrollTop=0;
}
function render(){
 document.querySelectorAll(".nav-btn").forEach(b=>{b.classList.toggle("active",b.dataset.view===state.view);});
 $("lesson-count").textContent=state.entries.length;
 $("breadcrumb").textContent=state.selected?("Lessons / "+(selectedEntry()?.term||"")):state.view[0].toUpperCase()+state.view.slice(1);
 $("furigana-btn").hidden=!shouldShowFurigana();
 $("furigana-btn").setAttribute("aria-pressed",String(state.furigana));
 renderRoot.innerHTML=state.view==="create"?viewCreate():state.view==="lessons"?viewLessons():
   state.view==="practice"?viewPractice():viewHome();
}
function viewHome(){
 const questions=extractQuestions(state.entries,state.language);
 const due=dueQuestions(state.language);
 const practiced=new Set(questions.filter(q=>state.progress[q.key]?.attempts>0).map(q=>q.key)).size;
 return '<div class="content-width">'+
 '<div class="hero"><div><div class="eyebrow">YOUR LEARNING SPACE</div><h1>Make language<br>feel natural.</h1>'+
 '<p>Learn words and constructions in depth, keep your lessons organized, and practice what matters.</p>'+
 '<div class="hero-actions"><button class="primary" data-action="goto-create">Create a lesson</button>'+
 '<button class="quiet" data-action="goto-practice">Start practice</button></div></div>'+mascot("idle")+'</div>'+
 '<h2 class="section-title">Your progress · '+esc(state.language)+'</h2>'+
 '<div class="grid3">'+stat(currentLessons().length,"Lessons")+stat(practiced,"Skills practiced")+stat(due.length,"Due for review")+'</div>'+
 '<h2 class="section-title">Continue learning</h2><div class="grid2">'+
 '<div class="card"><div class="pill">LESSONS</div><h2 style="margin-top:14px">Your library</h2>'+
 '<p class="muted">Study the notes, examples and patterns you saved.</p><button class="secondary" data-action="goto-lessons">Open lessons</button></div>'+
 '<div class="card"><div class="pill blue">PRACTICE</div><h2 style="margin-top:14px">Strengthen recall</h2>'+
 '<p class="muted">Review exercises with FSRS-6 scheduling.</p><button class="secondary" data-action="goto-practice">Review now</button></div></div></div>';
}
function queryCard(query,i){
 return '<section class="card term-form"><header><span>Term '+(i+1)+'</span>'+
 (state.terms.length>1?'<button class="quiet small" data-action="remove-term" data-index="'+i+'">Remove</button>':'')+'</header>'+
 '<div class="form-grid">'+
 field("Term or construction",query.term,"term",i,"word, expression, phrase...")+
 field("Context (optional)",query.context,"context",i,"Art book, casual conversation...")+
 field("Original sentence (optional)",query.originalSentence,"originalSentence",i,"Sentence you found")+
 '<div style="grid-column:1/-1">'+field("Specific question (optional)",query.specificQuestion,"specificQuestion",i,"What is unclear about the term?")+'</div>'+
 '</div></section>';
}
function field(label,value,name,index,placeholder){
 return '<div><label class="field" for="query-'+index+'-'+name+'">'+esc(label)+'</label>'+
 '<input class="field-input" id="query-'+index+'-'+name+'" data-query="'+index+'" data-field="'+name+
 '" value="'+esc(value)+'" placeholder="'+esc(placeholder)+'"></div>';
}
function viewCreate(){
 return '<div class="content-width">'+heading("NEW LESSON","Create","Prepare up to five requests and copy the teaching prompt into your AI.")+
 '<div class="card" style="margin-bottom:16px"><div class="row spread"><div><label class="field">Study language</label><p class="muted small" style="margin:0">The prompt and practice content follow this language.</p></div>'+
 '<select class="field-select" id="study-language" style="max-width:230px">'+LANGUAGES.map(l=>'<option '+(l===state.language?"selected":"")+'>'+l+'</option>').join("")+'</select></div></div>'+
 state.terms.map(queryCard).join("")+
 '<div class="sticky-actions"><button class="quiet" data-action="add-term" '+(state.terms.length>=5?"disabled":"")+'>' + icon("plus",16) + ' Add term</button>'+
 '<button class="secondary" data-action="open-import">Import AI response</button>'+
 '<button class="primary" data-action="generate-prompt" '+(!state.terms.some(t=>t.term.trim())?"disabled":"")+'>Generate prompt</button></div></div>';
}
function viewLessons(){
 if(state.selected && selectedEntry())return viewLesson(selectedEntry());
 const list=state.entries.filter(e=>(state.filter==="All"||canonical(e.language)===state.filter)
   &&(e.term.toLocaleLowerCase().includes(state.search.toLocaleLowerCase())||
    (e.context||"").toLocaleLowerCase().includes(state.search.toLocaleLowerCase())));
 return '<div class="content-width">'+heading("YOUR LIBRARY","Lessons","Your imported lessons, separated by language.")+
 '<div class="row spread" style="margin-bottom:18px;flex-wrap:wrap"><div class="lang-tabs">'+["All",...LANGUAGES]
 .map(l=>tabButton(l,l===state.filter,"lesson-filter")).join("")+'</div>'+
 '<div class="searchbar">' + icon("search",18) + '<input aria-label="Search lessons" id="lesson-search" placeholder="Search lessons..." value="'+esc(state.search)+'"></div></div>'+
 (list.length?'<div class="lesson-grid">'+list.map(entry=>'<button class="lesson-card" data-action="open-lesson" data-key="'+esc(lessonKey(entry))+'">'+
 '<div class="lesson-top"><span class="pill">'+esc(canonical(entry.language))+'</span><span class="muted">' + icon("chevron-right",18) + '</span></div>'+
 '<strong>'+esc(entry.term)+'</strong><p>'+esc(entry.pronunciation||entry.partOfSpeech||"Lesson")+'</p>'+
 '<div class="small muted">'+(Array.isArray(entry.blocks)?entry.blocks.length:(entry.notes?.length||0))+' notes</div></button>').join("")+'</div>':
 '<div class="empty"><div style="font-size:36px">' + icon("book-open",36) + '</div><h2>No lessons found</h2>'+
 '<p>Import a Studysh JSON lesson to build your library.</p><button class="secondary" data-action="open-import">Import JSON</button></div>')+'</div>';
}
function viewLesson(entry){
 const blocks=entry.blocks||entry.notes||[];
 const html=blocks.map(b=>{
  const color=["purple","green","blue","yellow","rose","plain"].includes(b.style||b.type)?b.style||b.type:"plain";
  const examples=(Array.isArray(b.examples)?b.examples:[]).map(ex=>
    '<div class="example"><p>'+rubyHtml(String(ex.en||""),entry.furigana,state.furigana && canonical(entry.language)==="Japanese")+
    '</p><p>'+esc(ex.pt||"")+'</p><button class="quiet small" data-action="speak" data-speech="'+esc(ex.en||"")+
    '" data-language="'+esc(entry.language)+'">' + icon("volume-2",16) + ' Listen</button></div>').join("");
  return '<section class="block '+color+'"><h3>'+esc(b.title||"Explanation")+'</h3>'+markdown(b.text||b.content||"")+examples+'</section>';
 }).join("");
 const notes=Array.isArray(entry.personalNotes)?entry.personalNotes:[];
 return '<div class="content-width" style="max-width:910px"><button class="back-link" data-action="back-lessons">' + icon("arrow-left",16) + ' All lessons</button>'+
 '<div class="card lesson-header"><div><span class="pill">'+esc(canonical(entry.language))+'</span>'+
 '<h1 class="lesson-term">'+rubyHtml(entry.term,entry.furigana,state.furigana && canonical(entry.language)==="Japanese")+'</h1>'+
 '<div class="lesson-pronunciation">'+esc(entry.pronunciation||"")+' '+esc(entry.partOfSpeech||"")+'</div></div>'+
 '<div class="row"><button class="quiet" data-action="speak" data-speech="'+esc(entry.term)+'" data-language="'+esc(entry.language)+'">' + icon("volume-2",16) + ' Listen</button>'+
 '<button class="quiet" data-action="edit-lesson">Edit JSON</button><button class="danger" data-action="delete-lesson">Delete</button></div></div>'+
 (html||'<div class="empty"><p>No lesson blocks in this entry.</p></div>')+
 '<section class="card" style="margin-top:20px"><h2>Personal notes</h2><p class="muted small">Saved locally and preserved when reimporting this term.</p>'+
 notes.map((n,i)=>'<div class="note-box"><div class="row spread"><strong>'+esc(n.title||"Note")+'</strong>'+
 '<button class="quiet small" data-action="remove-note" data-index="'+i+'">Delete</button></div>'+
 '<p style="white-space:pre-wrap">'+esc(n.text||n.content||"")+'</p></div>').join("")+
 '<textarea class="field-area" id="new-note" placeholder="Add your own observation..."></textarea>'+
 '<div style="margin-top:12px"><button class="secondary" data-action="add-note">Save note</button></div></section></div>';
}
function viewPractice(){
 if(state.session)return viewSession();
 const questions=extractQuestions(state.entries,state.practiceLanguage);
 const due=dueQuestions(state.practiceLanguage);
 const practiced=new Set(questions.filter(q=>state.progress[q.key]?.attempts>0).map(q=>q.key)).size;
 const future=questions.map(q=>dueAt(state.progress[q.key])).filter(v=>v>Date.now()).sort((a,b)=>a-b)[0];
 const next=future?new Date(future).toLocaleString():"";
 return '<div class="content-width">'+heading("RECALL & RETENTION","Practice","A separate exercise bank and review history for each language.")+
 '<div class="lang-tabs" style="margin-bottom:20px">'+LANGUAGES.map(l=>tabButton(l,l===state.practiceLanguage,"practice-language")).join("")+'</div>'+
 '<div class="grid3">'+stat(due.length,"Due now")+stat(new Set(questions.map(q=>q.key)).size,"Skills")+stat(practiced,"Practiced")+'</div>'+
 '<div class="card" style="margin-top:20px;display:grid;grid-template-columns:1fr 190px;align-items:center;gap:15px">'+
 '<div><h2>Ready for review?</h2><p class="muted">Each answer updates your FSRS-6 schedule without needing internet.</p>'+
 '<button class="primary" data-action="start-session" '+(!due.length?"disabled":"")+'>Start practice</button>'+
 '<p class="small muted">'+(!questions.length?"Import lessons with exercises to get started.":due.length?"Up to 20 due skills per session.":next?"Next scheduled review: "+esc(next):"Everything is reviewed for now.")+'</p></div>'+
 mascot("idle")+'</div></div>';
}
function viewSession(){
 const se=state.session,q=se.questions[se.index];
 if(!q)return '<div class="content-width" style="max-width:630px;padding-top:60px;text-align:center">'+
 mascot("happy")+'<h1 class="page-title">Session complete</h1><p class="muted">'+se.correct+' of '+se.reviewed+' correct on the first check.</p>'+
 '<button class="primary" data-action="finish-session">Back to Practice</button></div>';
 const answer=q.type==="build"?assemble(se.tiles.map(i=>q.choices[i]),q.language):se.choice;
 const correct=matches(answer,q.answer,q.language);
 const usable=q.type==="build"?se.tiles.length>0:!!se.choice;
 const tileHTML=q.type==="build"?
 '<div class="tiles">'+(se.tiles.length?se.tiles.map(i=>'<button class="tile selected" data-action="remove-tile" data-index="'+i+'" '+(se.checked?"disabled":"")+'>'+esc(q.choices[i])+'</button>').join(""):'<span class="muted small">Tap the tiles to build your sentence.</span>')+'</div>'+
 '<div class="tiles">'+q.choices.map((v,i)=>se.tiles.includes(i)?'':'<button class="tile" data-action="add-tile" data-index="'+i+'" '+(se.checked?"disabled":"")+'>'+esc(v)+'</button>').join("")+'</div>':
 '<div class="choice-list">'+q.choices.map((v,i)=>'<button class="choice '+(se.choice===v?"selected":"")+
 '" data-action="choose" data-index="'+i+'" '+(se.checked?"disabled":"")+'>'+esc(v)+'</button>').join("")+'</div>';
 const feedback=se.checked?'<div class="feedback '+(correct?"":"wrong")+'"><h3>'+(correct?"Correct!":"Let’s review")+'</h3>'+
 (correct?"":'<p>Answer: <strong>'+esc(q.answer)+'</strong></p>')+
 (q.explanation?'<p>'+markdown(q.explanation)+'</p>':'')+'</div>'+
 '<p class="muted small">How well did you remember? '+(correct?"Suggested: Good.":"Incorrect answers are scheduled as Again.")+'</p>'+
 '<div class="rating-group">'+["Again","Hard","Good","Easy"].map((v,i)=>'<button data-action="choose-rating" data-rating="'+(i+1)+
 '" class="'+(se.rating===i+1?"active":"")+'" '+(!correct&&i>0?"disabled":"")+'>'+v+'</button>').join("")+'</div>'+
 '<button class="primary wide" data-action="next-question">Continue ' + icon("arrow-right",17) + '</button>':
 '<button class="primary wide" data-action="check-answer" '+(usable?"":"disabled")+'>Check answer</button>';
 return '<div class="content-width"><div class="row" style="margin-bottom:18px"><button class="quiet" data-action="finish-session">' + icon("x",20) + '</button>'+
 '<div class="progress-track"><div class="progress-fill" style="width:'+((se.index+1)/se.questions.length*100)+'%"></div></div>'+
 '<span class="muted small">'+(se.index+1)+'/'+se.questions.length+'</span></div>'+
 '<div class="practice-layout"><div class="card mascot-card">'+mascot(se.checked?(correct?"happy":"sad"):"idle")+'</div>'+
 '<div class="practice-panel"><div class="card"><div class="eyebrow">'+esc(q.term.toUpperCase())+'</div>'+
 '<h2 class="question">'+esc(q.instruction)+'</h2>'+(q.prompt?'<p class="muted">'+esc(q.prompt)+'</p>':'')+
 (q.sentence?'<div class="question-sentence">'+rubyHtml(q.sentence,q.furigana,state.furigana&&q.language==="Japanese")+'</div>':'')+
 '<div style="margin-top:20px">'+tileHTML+'</div></div>'+feedback+'</div></div></div>';
}
function setLanguage(value){state.language=canonical(value);persistSetting("language",state.language);render();}
function modalShow(title,content,actions=""){
 modal.innerHTML='<div class="modal-top"><h2 id="modal-title">'+esc(title)+'</h2><button class="icon-btn" data-action="close-modal" aria-label="Close">' + icon("x",20) + '</button></div>'+
 content+'<div class="modal-actions">'+actions+'</div>';
 backdrop.hidden=false;
}
function modalClose(){backdrop.hidden=true;modal.innerHTML="";}
function importModal(){
 modalShow("Import Studysh JSON",'<p class="muted">Paste an AI response in schema 3.0, or load an Android / Windows backup. Existing lessons are merged; personal notes are preserved.</p>'+
 '<textarea class="field-area" id="import-text" spellcheck="false" placeholder="Paste JSON here..."></textarea>',
 '<button class="quiet" data-action="import-file">Choose JSON file</button><button class="primary" data-action="import-text">Import pasted JSON</button>');
}
function importData(text){
 const data=parsePayload(text);
 state.entries=mergeEntries(state.entries,data.entries);
 if(data.progress)state.progress={...state.progress,...data.progress};
 save();modalClose();render();
 showToast(data.entries.length+" lesson(s) imported"+(data.backup?" with review history":"")+".");
}
async function openFile(){
 try {
  const invoke=window.__TAURI__?.core?.invoke;
  if(invoke){const data=await invoke("open_json_file");if(data!==null)importData(data);return;}
  const picker=document.createElement("input");picker.type="file";picker.accept=".json,application/json";
  picker.onchange=async()=>{if(picker.files?.[0])try{importData(await picker.files[0].text());}catch(e){showToast("Import failed: "+e.message);}};
  picker.click();
 }catch(e){showToast("Could not open file: "+e.message);}
}
async function exportData(){
 const data=JSON.stringify(makeBackup(state.entries,state.progress),null,2);
 try{
  const invoke=window.__TAURI__?.core?.invoke;
  if(invoke){const path=await invoke("save_json_file",{content:data});if(path)showToast("Backup saved.");return;}
  const blob=new Blob([data],{type:"application/json"}),url=URL.createObjectURL(blob);
  const a=document.createElement("a");a.href=url;a.download="studysh-backup.json";a.click();
  setTimeout(()=>URL.revokeObjectURL(url),2000);
  showToast("Backup exported.");
 }catch(e){showToast("Export failed: "+e.message);}
}
function voiceList(){return "speechSynthesis" in window?speechSynthesis.getVoices():[];}
const QWEN_SPEAKERS=[
 ["auto","Automatic (native accent)"],["Aiden","Aiden · American English"],["Ryan","Ryan · English"],
 ["Ono_Anna","Ono Anna · Japanese"],["Vivian","Vivian · Chinese"],["Serena","Serena · Chinese"],
 ["Uncle_Fu","Uncle Fu · Chinese"],["Dylan","Dylan · Chinese"],["Eric","Eric · Chinese"],["Sohee","Sohee · Korean"]
];
const speakerIds=new Set(QWEN_SPEAKERS.map(v=>v[0]));
let voiceDraft=null,qwenInfo=null,qwenError="",qwenBusy=false,qwenStage="",activeAudio=null,speechRequest=0;
const speechCache=new Map();
const previewSpeech={English:"Hello! Let's practice English together.",Japanese:"こんにちは。今日は日本語を練習しましょう。",
 Spanish:"Hola. Vamos a practicar español.",Portuguese:"Olá! Vamos praticar português."};
function qwenSpeakerFor(language,preferred){
 if(preferred!=="auto"&&speakerIds.has(preferred))return preferred;
 return canonical(language)==="Japanese"?"Ono_Anna":"Aiden";
}
function showSettings(){
 modalShow("Settings",'<div class="voice-panel"><div><label class="field" for="settings-language">Study language</label>'+
  '<select id="settings-language" class="field-select">'+LANGUAGES.map(l=>'<option '+(state.language===l?"selected":"")+'>'+l+'</option>').join("")+'</select></div>'+
  '<div class="voice-summary"><div class="row spread"><div><strong>'+icon("headphones",18)+' Voice</strong>'+
  '<p class="muted small">Configure Windows or Qwen3-TTS voices.</p></div>'+
  '<button class="secondary" data-action="open-voice-settings">Voice options</button></div></div>'+
  '<div class="row spread"><div><strong>Furigana</strong><p class="muted small">Show imported reading annotations over Japanese kanji.</p></div>'+
  '<input type="checkbox" id="settings-furigana" '+(state.furigana?"checked":"")+'></div>'+
  '<div class="row spread"><div><strong>Mute speech</strong><p class="muted small">Disable pronunciation playback.</p></div>'+
  '<input type="checkbox" id="settings-muted" '+(state.muted?"checked":"")+'></div></div>',
  '<button class="primary" data-action="save-settings">Save settings</button>');
}
async function refreshQwen(){
 const invoke=window.__TAURI__?.core?.invoke;
 if(!invoke){qwenInfo=null;return;}
 try{qwenInfo=await invoke("qwen_status");}catch(e){qwenInfo=null;qwenError=String(e);}
}
function renderVoiceSettings(){
 if(!voiceDraft)return;
 const engine=voiceDraft.engine,ready=!!qwenInfo?.model_downloaded;
 const voiceOptions='<option value="">Automatic system voice</option>'+voiceList().map(v=>
  '<option value="'+esc(v.voiceURI)+'" '+(v.voiceURI===voiceDraft.voice?"selected":"")+'>'+esc(v.name+" · "+v.lang)+'</option>').join("");
 const speakers=QWEN_SPEAKERS.map(([id,label])=>'<option value="'+esc(id)+'" '+(voiceDraft.speaker===id?"selected":"")+'>'+esc(label)+'</option>').join("");
 const status=qwenBusy?qwenStage:ready?"Installed — offline speech on CPU.":
  qwenInfo?.dependencies_installed?"Dependencies installed; download the voice model.":
  window.__TAURI__?.core?.invoke?"Not installed. A one-time download is required.":"Available in the installed Windows app.";
 const qwenHtml='<div class="voice-panel"><div><label class="field" for="voice-speaker">Qwen3 speaker</label>'+
  '<select id="voice-speaker" class="field-select" '+(qwenBusy?"disabled":"")+'>'+speakers+'</select></div>'+
  '<p class="voice-hint">Qwen3-TTS 0.6B CustomVoice · nine voices · English, Japanese, Spanish and Portuguese. Native-accent voices are recommended for language practice.</p>'+
  '<div class="voice-status '+(qwenError?"error":ready?"ready":"")+'" role="status">'+esc(qwenError||status)+'</div>'+
  (!ready?'<button class="secondary" data-action="install-qwen" '+(qwenBusy||!window.__TAURI__?.core?.invoke?"disabled":"")+'>'+
    icon("cloud-download",17)+' '+(qwenBusy?"Working…":qwenInfo?.dependencies_installed?"Download model":"Install & download")+'</button>':
    '<div class="pill green">'+icon("check",15)+' Ready offline</div>')+
  '<p class="voice-hint">Setup needs internet, Python 3.11/3.12 and several GB of disk space. Generation then works offline. CPU synthesis may be slow.</p></div>';
 modalShow("Voice options",'<div class="voice-panel"><div><label class="field" for="voice-engine">Speech engine</label>'+
  '<select id="voice-engine" class="field-select" '+(qwenBusy?"disabled":"")+'>'+
  '<option value="windows" '+(engine==="windows"?"selected":"")+'>Windows voices</option>'+
  '<option value="qwen" '+(engine==="qwen"?"selected":"")+'>Qwen3-TTS · Neural offline</option></select></div>'+
  (engine==="qwen"?qwenHtml:'<div><label class="field" for="voice-system">Windows voice</label>'+
  '<select id="voice-system" class="field-select">'+voiceOptions+'</select></div>')+'</div>',
  '<button class="quiet" data-action="test-selected-voice" '+(qwenBusy||engine==="qwen"&&!ready?"disabled":"")+'>'+
  icon("volume-2",16)+' Test voice</button>'+
  '<button class="primary" data-action="save-voice-settings" '+(qwenBusy?"disabled":"")+'>Save voice</button>');
}
async function showVoiceSettings(){
 voiceDraft={engine:state.voiceEngine,voice:state.voice,speaker:state.qwenSpeaker};
 qwenError="";await refreshQwen();renderVoiceSettings();
}
async function installQwen(){
 const invoke=window.__TAURI__?.core?.invoke;
 if(qwenBusy||!invoke)return;
 qwenBusy=true;qwenError="";
 try{
  if(!qwenInfo?.dependencies_installed){
   qwenStage="Installing the local engine (large download)…";renderVoiceSettings();
   await invoke("qwen_install");
  }
  qwenStage="Downloading Qwen3-TTS 0.6B model and tokenizer…";renderVoiceSettings();
  await invoke("qwen_prepare");await refreshQwen();
  showToast("Qwen3-TTS is ready for offline speech.");
 }catch(e){qwenError="Setup failed: "+String(e);await refreshQwen();}
 finally{qwenBusy=false;qwenStage="";renderVoiceSettings();}
}
async function speak(text,language,selection=null){
 if(state.muted)return;
 const request=++speechRequest;
 const current=selection||{engine:state.voiceEngine,voice:state.voice,speaker:state.qwenSpeaker};
 if(activeAudio){activeAudio.pause();activeAudio=null;}
 if("speechSynthesis" in window)speechSynthesis.cancel();
 if(current.engine==="qwen"){
  const invoke=window.__TAURI__?.core?.invoke;
  if(!invoke){showToast("Qwen3 needs the Windows desktop application.");return;}
  const lang=canonical(language),speaker=qwenSpeakerFor(lang,current.speaker);
  const cacheKey=JSON.stringify([text,lang,speaker]);
  try{
   let wav=speechCache.get(cacheKey);
   if(!wav){
    showToast("Generating speech locally…");
    wav=await invoke("qwen_synthesize",{text:String(text),language:lang,speaker});
    speechCache.set(cacheKey,wav);
    if(speechCache.size>12)speechCache.delete(speechCache.keys().next().value);
   }
   if(request!==speechRequest)return;
   activeAudio=new Audio("data:audio/wav;base64,"+wav);
   await activeAudio.play();
  }catch(e){if(request===speechRequest)showToast("Qwen3 voice error: "+String(e));}
  return;
 }
 if(!("speechSynthesis" in window)){showToast("Speech synthesis is not available.");return;}
 const u=new SpeechSynthesisUtterance(String(text));
 const code={English:"en-US",Japanese:"ja-JP",Spanish:"es-ES",Portuguese:"pt-BR"}[canonical(language)]||"en-US";
 const v=voiceList().find(x=>x.voiceURI===current.voice)||
  voiceList().find(x=>x.lang.toLowerCase().startsWith(code.slice(0,2)));
 if(v)u.voice=v;
 u.lang=code;u.rate=.9;speechSynthesis.speak(u);
}
async function copyText(text){
 try{await navigator.clipboard.writeText(text);showToast("Copied to clipboard.");}
 catch{
  const node=$("prompt-text");if(node){node.select();document.execCommand("copy");showToast("Select and copy the prompt.");}
 }
}
function startSession(){
 const due=dueQuestions(state.practiceLanguage);
 if(!due.length){showToast("Nothing due for this language.");return;}
 state.session={questions:due.slice(0,20),index:0,choice:"",tiles:[],checked:false,rating:3,correct:0,reviewed:0};
 render();
}
function advanceSession(){
 const se=state.session,q=se?.questions[se.index];
 if(!se||!q||!se.checked)return;
 const answer=q.type==="build"?assemble(se.tiles.map(i=>q.choices[i]),q.language):se.choice;
 const correct=matches(answer,q.answer,q.language);
 const rating=correct?se.rating:1;
 state.progress[q.key]=review(migrateLegacy(state.progress[q.key]),rating);
 se.reviewed++;if(correct)se.correct++;
 save();se.index++;se.choice="";se.tiles=[];se.checked=false;se.rating=3;render();
}
document.querySelectorAll("[data-view]").forEach(b=>b.addEventListener("click",()=>navigate(b.dataset.view)));
$("import-btn").addEventListener("click",importModal);
$("export-btn").addEventListener("click",exportData);
$("furigana-btn").addEventListener("click",()=>{state.furigana=!state.furigana;persistSetting("furigana",state.furigana);render();});
$("settings-btn").addEventListener("click",showSettings);
backdrop.addEventListener("click",e=>{if(e.target===backdrop)modalClose();});
document.addEventListener("input",e=>{
 const t=e.target;
 if(t.matches("[data-query]"))state.terms[Number(t.dataset.query)][t.dataset.field]=t.value;
 if(t.id==="lesson-search"){
   const caret=t.selectionStart;state.search=t.value;render();const field=$("lesson-search");
   if(field){field.focus();field.setSelectionRange(caret,caret);}
 }
});
document.addEventListener("change",e=>{
 if(e.target.id==="study-language")setLanguage(e.target.value);
});
document.addEventListener("click",async e=>{
 const btn=e.target.closest("[data-action]");if(!btn)return;
 const action=btn.dataset.action,index=Number(btn.dataset.index);
 try{
 switch(action){
  case "goto-create":navigate("create");break;
  case "goto-lessons":navigate("lessons");break;
  case "goto-practice":navigate("practice");break;
  case "open-import":importModal();break;
  case "close-modal":modalClose();break;
  case "import-file":await openFile();break;
  case "import-text":importData($("import-text").value);break;
  case "remove-term":state.terms.splice(index,1);render();break;
  case "add-term":if(state.terms.length<5){state.terms.push(blank());render();}break;
  case "generate-prompt":{
   const prompt=buildPrompt(state.terms,state.language);
   modalShow("AI lesson prompt",'<p class="muted small">Send this prompt to your preferred AI. Import its JSON response into Studysh.</p>'+
    '<textarea id="prompt-text" class="field-area" rows="16" readonly>'+esc(prompt)+'</textarea>',
    '<button class="primary" data-action="copy-prompt">Copy prompt</button>');
   break;
  }
  case "copy-prompt":await copyText($("prompt-text").value);break;
  case "lesson-filter":state.filter=btn.dataset.language;render();break;
  case "practice-language":state.practiceLanguage=canonical(btn.dataset.language);persistSetting("practiceLanguage",state.practiceLanguage);render();break;
  case "open-lesson":state.selected=btn.dataset.key;render();renderRoot.scrollTop=0;break;
  case "back-lessons":state.selected=null;render();break;
  case "speak":speak(btn.dataset.speech,btn.dataset.language);break;
  case "add-note":{
    const entry=selectedEntry(),text=$("new-note")?.value.trim();
    if(!entry||!text){showToast("Write a note first.");break;}
    entry.personalNotes=[...(entry.personalNotes||[]),{title:"Personal note",text}];save();render();break;
  }
  case "remove-note":{
    const entry=selectedEntry();if(entry){entry.personalNotes.splice(index,1);save();render();}break;
  }
  case "edit-lesson":{
    const entry=selectedEntry();if(!entry)break;
    modalShow("Edit lesson JSON",'<p class="muted small">Edit this entry as a valid JSON object. The lesson identity must include a term.</p>'+
      '<textarea class="field-area" id="edit-json">'+esc(JSON.stringify(entry,null,2))+'</textarea>',
      '<button class="primary" data-action="save-edit">Save changes</button>');break;
  }
  case "save-edit":{
    const entry=selectedEntry(),updated=JSON.parse($("edit-json").value);
    if(!updated||typeof updated.term!=="string"||!updated.term.trim())throw Error("A lesson needs a term.");
    updated.language=canonical(updated.language);
    state.entries=state.entries.filter(x=>lessonKey(x)!==lessonKey(entry)&&lessonKey(x)!==lessonKey(updated));
    state.entries.push(updated);state.selected=lessonKey(updated);save();modalClose();render();showToast("Lesson saved.");break;
  }
  case "delete-lesson":{
    const entry=selectedEntry();
    if(entry&&confirm("Delete "+entry.term+"? This does not delete your exported backups.")){
      state.entries=state.entries.filter(x=>lessonKey(x)!==lessonKey(entry));
      state.selected=null;save();render();
    }break;
  }
  case "start-session":startSession();break;
  case "finish-session":state.session=null;render();break;
  case "choose":state.session.choice=state.session.questions[state.session.index].choices[index];render();break;
  case "add-tile":state.session.tiles.push(index);render();break;
  case "remove-tile":state.session.tiles=state.session.tiles.filter(i=>i!==index);render();break;
  case "check-answer":{
    const se=state.session,q=se.questions[se.index];
    const actual=q.type==="build"?assemble(se.tiles.map(i=>q.choices[i]),q.language):se.choice;
    if(!actual)break;
    se.checked=true;se.rating=matches(actual,q.answer,q.language)?3:1;render();break;
  }
  case "choose-rating":state.session.rating=Number(btn.dataset.rating);render();break;
  case "next-question":advanceSession();break;
  case "save-settings":{
    state.language=canonical($("settings-language").value);state.voice=$("settings-voice").value;
    state.furigana=$("settings-furigana").checked;state.muted=$("settings-muted").checked;
    for(const key of ["language","voice","furigana","muted"])persistSetting(key,state[key]);
    modalClose();render();showToast("Settings saved.");break;
  }
  case "test-voice":speak("Hello. This is Studysh.",state.language);break;
 }
 }catch(err){showToast((action.includes("import")?"Import failed: ":"Error: ")+(err?.message||String(err)));}
});
document.addEventListener("keydown",e=>{
 if(e.key==="Escape"&&!backdrop.hidden){modalClose();return;}
 if(e.ctrlKey&&e.key.toLowerCase()==="s"){e.preventDefault();exportData();return;}
 if(!backdrop.hidden||state.view!=="practice"||!state.session||
   ["INPUT","TEXTAREA","SELECT"].includes(document.activeElement?.tagName))return;
 const se=state.session,q=se.questions[se.index];if(!q)return;
 if(/^[1-4]$/.test(e.key)){
  const index=Number(e.key)-1;
  if(se.checked){if(index===0||matches(q.type==="build"?assemble(se.tiles.map(i=>q.choices[i]),q.language):se.choice,q.answer,q.language))se.rating=index+1;}
  else if(q.type!=="build"&&index<q.choices.length)se.choice=q.choices[index];
  render();e.preventDefault();
 }else if(e.key==="Enter"){
  e.preventDefault();if(se.checked)advanceSession();
  else if(se.choice||se.tiles.length){se.checked=true;se.rating=matches(q.type==="build"?assemble(se.tiles.map(i=>q.choices[i]),q.language):se.choice,q.answer,q.language)?3:1;render();}
 }
});
if("speechSynthesis" in window)speechSynthesis.addEventListener?.("voiceschanged",()=>{});
render();
