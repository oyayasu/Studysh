// Runs against the NSIS-installed app in a fresh GitHub-hosted Windows user profile.
import assert from "node:assert/strict";
import {mkdir,writeFile} from "node:fs/promises";
import {setTimeout as sleep} from "node:timers/promises";

async function until(fn, timeout, label){
 const start=Date.now();
 while(Date.now()-start<timeout){
  const result=await fn();
  if(result)return result;
  await sleep(400);
 }
 throw Error("Timed out waiting for "+label);
}
const target=await until(async()=>{
 try{
  const pages=await (await fetch("http://127.0.0.1:9222/json/list")).json();
  return pages.find(p=>p.type==="page"&&p.url.includes("tauri.localhost"));
 }catch{return false;}
},90_000,"Studysh WebView2");
const ws=new WebSocket(target.webSocketDebuggerUrl);
await new Promise((ok,fail)=>{ws.onopen=ok;ws.onerror=fail;});
let id=0;
const calls=new Map();
ws.onmessage=({data})=>{
 const reply=JSON.parse(data);
 if(calls.has(reply.id)){calls.get(reply.id)(reply);calls.delete(reply.id);}
};
async function js(expression){
 const seq=++id;
 const promise=new Promise(ok=>calls.set(seq,ok));
 ws.send(JSON.stringify({id:seq,method:"Runtime.evaluate",params:{expression,returnByValue:true,awaitPromise:true}}));
 const reply=await promise;
 if(reply.error||reply.result.exceptionDetails)throw Error(JSON.stringify(reply.error||reply.result.exceptionDetails));
 return reply.result.result.value;
}
const click=sel=>js('document.querySelector('+JSON.stringify(sel)+').click()');
const action=name=>click('[data-action="'+name+'"]');
const select=(sel,value)=>js('(()=>{const el=document.querySelector('+JSON.stringify(sel)+');el.value='+JSON.stringify(value)+';el.dispatchEvent(new Event("change",{bubbles:true}));})()');
async function openVoice(){
 await click("#settings-btn");await action("open-voice-settings");
 await until(()=>js('!!document.querySelector("#voice-engine")'),10_000,"voice settings");
 await select("#voice-engine","qwen");
}
const setupStatus=()=>js('document.querySelector("#qwen-stage")?.textContent');
try{
 await until(()=>js('!!document.querySelector("#settings-btn")&&!!window.__TAURI__?.core'),20_000,"desktop IPC");
 await openVoice();
 await action("install-qwen");
 let last="";
 await until(async()=>{
  const status=await setupStatus();
  if(status!==last){console.log(status);last=status;}
  if(await js('!!document.querySelector("#qwen-stage.error")'))throw Error(await js('document.querySelector("#qwen-log").textContent'));
  return await js('document.querySelector("#qwen-progress")?.value>0');
 },25*60_000,"measured model download");
 await action("cancel-qwen");
 await until(()=>js('!!document.querySelector(\'[data-action="install-qwen"]\')'),30_000,"safe cancellation");
 assert.match(await setupStatus(),/cancelled/i);
 await action("install-qwen");
 await until(async()=>{
  const status=await setupStatus();
  if(status!==last){console.log(status);last=status;}
  if(await js('!!document.querySelector("#qwen-stage.error")'))throw Error(await js('document.querySelector("#qwen-log").textContent'));
  return status.includes("Installed");
 },30*60_000,"verified offline installation");
 // Observe the actual HTMLAudioElement created by the app's Test voice button.
 await js(`window.samples=[];const original=HTMLMediaElement.prototype.play;
 HTMLMediaElement.prototype.play=function(){
  const el=this;
  el.addEventListener("ended",()=>window.samples.push({wav:el.src.split(",")[1],duration:el.duration,time:el.currentTime}),{once:true});
  return original.call(el);
 };`);
 await mkdir("speech-results",{recursive:true});
 for(const [index,language] of ["English","Japanese"].entries()){
  if(index){
   await action("close-modal");await click("#settings-btn");await select("#settings-language",language);
   await action("save-settings");await openVoice();
  }
  await action("test-selected-voice");
  await until(async()=>{
   const toast=await js('document.querySelector("#toast").textContent');
   if(/voice error/i.test(toast))throw Error(toast);
   return await js("window.samples.length")===(index+1);
  },12*60_000,language+" playback ended");
  const sample=await js("window.samples["+index+"]");
  const wav=Buffer.from(sample.wav,"base64");
  assert.equal(wav.toString("ascii",0,4),"RIFF");
  assert.equal(wav.toString("ascii",8,12),"WAVE");
  assert.ok(sample.duration>0.25 && sample.time>=sample.duration-0.05);
  const data=wav.indexOf("data",12)+8;assert.ok(data>=20);
  let peak=0;
  for(let i=data;i+1<wav.length;i+=2)peak=Math.max(peak,Math.abs(wav.readInt16LE(i)));
  assert.ok(peak>100,"Expected non-silent generated samples");
  await writeFile("speech-results/"+language+".wav",wav);
  console.log(language+" played to end in Studysh: "+sample.duration.toFixed(2)+" s, PCM peak "+peak);
 }
}finally{ws.close();}
