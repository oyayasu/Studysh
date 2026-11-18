import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {icon,iconNames} from "../web/icons.js";

test("all desktop icons use scalable inline SVG instead of font-dependent glyphs",()=>{
 assert.ok(iconNames.includes("house"));
 assert.ok(iconNames.includes("volume-2"));
 for(const name of iconNames){
  const svg=icon(name);
  assert.match(svg,/^<svg /);
  assert.match(svg,/viewBox="0 0 24 24"/);
  assert.match(svg,/<\/svg>$/);
 }
 assert.throws(()=>icon("invalid-icon"),/Unknown icon/);
});

test("Windows navigation and toolbar mount the shared vector icon set",()=>{
 const html=readFileSync(new URL("../web/index.html",import.meta.url),"utf8");
 for(const name of ["house","pencil","book-open","brain","file-down","file-up","settings"]){
  assert.ok(html.includes('data-icon="'+name+'"'),"Missing "+name);
 }
 assert.ok(html.includes('id="furigana-btn"'));
});

test("Qwen3 uses the native Tauri worker and never replaces Windows speech",()=>{
 const rust=readFileSync(new URL("../src-tauri/src/lib.rs",import.meta.url),"utf8");
 const bridge=readFileSync(new URL("../src-tauri/python/qwen_tts_bridge.py",import.meta.url),"utf8");
 const web=readFileSync(new URL("../web/app.js",import.meta.url),"utf8");
 for(const command of ["qwen_status","qwen_install","qwen_prepare","qwen_synthesize"]){
  assert.ok(rust.includes(command),command);
 }
 assert.match(rust,/QwenRuntime\(Mutex<Option<QwenProcess>>\)/);
 assert.match(bridge,/HF_HUB_OFFLINE/);
 assert.match(bridge,/Qwen3-TTS-12Hz-0\.6B-CustomVoice/);
 assert.match(web,/case "test-selected-voice"/);
 assert.match(web,/value="windows"/);
 assert.match(web,/value="qwen"/);
});
