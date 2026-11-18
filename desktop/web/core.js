export const LANGUAGES = ["English", "Japanese", "Spanish", "Portuguese"];
const LANG_IDS = { English:"en", Japanese:"ja", Spanish:"es", Portuguese:"pt" };
export function canonical(value="English") {
  const v = String(value || "English").trim().toLowerCase().split(/[-_]/)[0];
  return ({en:"English",english:"English",ja:"Japanese",japanese:"Japanese","日本語":"Japanese",
    es:"Spanish",spanish:"Spanish","español":"Spanish",pt:"Portuguese",
    portuguese:"Portuguese","português":"Portuguese"})[v] || (String(value).trim() || "English");
}
export function languageId(language){ return LANG_IDS[canonical(language)] || canonical(language).toLowerCase(); }
export function lessonKey(entry){ return languageId(entry.language)+ "::" + String(entry.term || "").trim().toLowerCase(); }
export function validEntries(data) {
  return Array.isArray(data?.entries) ? data.entries.filter(e=>e && typeof e==="object" && typeof e.term==="string" && e.term.trim()) : [];
}
export function parsePayload(text) {
  let value = typeof text==="string" ? JSON.parse(text.replace(/^\uFEFF/,"").trim()) : text;
  if (!value || typeof value!=="object" || Array.isArray(value)) throw Error("Expected a JSON object.");
  if (value.format==="studysh-backup-1") {
    if (!value.library || !Array.isArray(value.library.entries)) throw Error("Invalid Studysh backup.");
    return {entries:validEntries(value.library),progress:isRecord(value.practiceProgress)?value.practiceProgress:null,backup:true};
  }
  if (!Array.isArray(value.entries)) throw Error("Expected a JSON object with an entries array.");
  return {entries:validEntries(value),progress:null,backup:false};
}
function isRecord(x){return x && typeof x==="object" && !Array.isArray(x);}
export function mergeEntries(existing,incoming) {
  const merged=new Map();
  for(const e of validEntries({entries:existing})) merged.set(lessonKey(e),structuredClone(e));
  for(const e of validEntries({entries:incoming})) {
    const id=lessonKey(e),prior=merged.get(id);
    const next=structuredClone(e);
    if(prior) {
      if(Array.isArray(prior.personalNotes)) next.personalNotes=structuredClone(prior.personalNotes);
      const custom=(Array.isArray(prior.notes)?prior.notes:[]).filter(n=>n?.type==="custom");
      if(custom.length) next.personalNotes=[...(next.personalNotes||[]),...structuredClone(custom)];
    }
    next.language=canonical(next.language);
    merged.set(id,next);
  }
  return [...merged.values()];
}
export function makeBackup(entries,progress){
  return {format:"studysh-backup-1",library:{schemaVersion:"3.0",entries},practiceProgress:progress};
}
export function progressKey(q){return lessonKey(q)+":"+q.skill;}
export function extractQuestions(entries,language){
 const result=[];
 for(const entry of validEntries({entries})){
  if(canonical(entry.language)!==canonical(language)) continue;
  (Array.isArray(entry.exercises)?entry.exercises:[]).forEach((ex,index)=>{
    if(!["choice","complete","build"].includes(ex?.type))return;
    const options=(ex.type==="build"?ex.tiles:ex.options);
    if(!Array.isArray(options)||!options.length||!options.every(v=>typeof v==="string")||typeof ex.answer!=="string"||!ex.answer.trim())return;
    if(ex.type!=="build" && !options.includes(ex.answer))return;
    const skill=ex.skillId||ex.id||("question-"+index);
    result.push({term:entry.term,language:canonical(entry.language),skill,
      type:ex.type,instruction:ex.instruction || (ex.type==="build"?"Build the sentence":"Complete the sentence"),
      sentence:ex.sentence||"",prompt:ex.ptPrompt||"",choices:options,answer:ex.answer,
      explanation:ex.explanation||"",furigana:entry.furigana||[],key:languageId(entry.language)+"::"+entry.term.trim().toLowerCase()+":"+skill,
      legacyKey:entry.term.trim().toLowerCase()+":"+skill});
  });
 }
 return result;
}
export function migrateProgress(progress,entries,questions){
 const out={...progress};
 const langsByTerm=new Map();
 for(const e of validEntries({entries})){
  const term=e.term.trim().toLowerCase();
  if(!langsByTerm.has(term))langsByTerm.set(term,new Set());
  langsByTerm.get(term).add(canonical(e.language));
 }
 for(const q of questions){
  if(!out[q.key] && langsByTerm.get(q.term.trim().toLowerCase())?.size===1 && out[q.legacyKey])
   out[q.key]=structuredClone(out[q.legacyKey]);
 }
 return out;
}
export function assemble(tiles,language){return tiles.join(canonical(language)==="Japanese"?"":" ");}
export function matches(actual,answer,language){
 return canonical(language)==="Japanese" ? actual.replace(/\s+/g,"")===answer.replace(/\s+/g,"") : actual.trim()===answer.trim();
}
export function esc(text){
 return String(text??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
}
export function markdown(text){
 let html=esc(text).replace(/^### (.+)$/gm,"<h4>$1</h4>").replace(/^## (.+)$/gm,"<h3>$1</h3>")
  .replace(/^# (.+)$/gm,"<h2>$1</h2>").replace(/\*\*(.+?)\*\*/g,"<strong>$1</strong>")
  .replace(/(^|[\s(])\*([^*\n]+)\*/g,"$1<em>$2</em>").replace(/\x60([^\x60]+)\x60/g,"<code>$1</code>");
 return html.split(/\n\s*\n/g).map(part=>part.startsWith("<h")?part:"<p>"+part.replace(/\n/g,"<br>")+"</p>").join("");
}
export function rubyHtml(text,overrides=[],enabled=false){
 if(!enabled||!Array.isArray(overrides)||!overrides.length)return esc(text);
 const dictionary=overrides.filter(v=>typeof v?.text==="string"&&v.text&&typeof v?.reading==="string"&&v.reading)
   .sort((a,b)=>b.text.length-a.text.length);
 if(!dictionary.length)return esc(text);
 let result="",pos=0;
 while(pos<text.length){
  const chosen=dictionary.find(v=>text.startsWith(v.text,pos));
  if(chosen){result+="<ruby>"+esc(chosen.text)+"<rt>"+esc(chosen.reading)+"</rt></ruby>";pos+=chosen.text.length;}
  else {result+=esc(text[pos++]);}
 }
 return result;
}
