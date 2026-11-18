import test from "node:test";
import assert from "node:assert/strict";
import {canonical,lessonKey,parsePayload,mergeEntries,makeBackup,extractQuestions,assemble,matches,rubyHtml,markdown,migrateProgress} from "../web/core.js";
import {review,dueAt,migrateLegacy} from "../web/fsrs.js";
import {buildPrompt} from "../web/prompt.js";

const example={term:"学校",language:"Japanese",furigana:[{text:"学校",reading:"がっこう"}],
  blocks:[{title:"Example",style:"purple",text:"A **core idea**",examples:[{en:"学校で勉強します。",pt:"Estudo na escola."}]}],
  exercises:[{id:"q1",skillId:"meaning",type:"choice",options:["学校","机"],answer:"学校"},
    {id:"q2",skillId:"structure",type:"build",tiles:["勉強","します。"],answer:"勉強します。"}]};
test("canonical keys match Android language-specific library keys",()=>{
 assert.equal(canonical("ja-JP"),"Japanese");
 assert.equal(lessonKey(example),"ja::学校");
 assert.equal(lessonKey({term:"book",language:"English"}),"en::book");
});
test("JSON import and Android backup preserve lessons, notes and review history",()=>{
 const old={...example,personalNotes:[{title:"My note",text:"Remember this"}]};
 const merged=mergeEntries([old],[{...example,blocks:[]}]);
 assert.equal(merged.length,1);
 assert.equal(merged[0].personalNotes[0].text,"Remember this");
 const history={"ja::学校:meaning":{attempts:3,dueAt:1700000000000}};
 const backup=makeBackup(merged,history);
 const imported=parsePayload(JSON.stringify(backup));
 assert.equal(imported.backup,true);
 assert.deepEqual(imported.progress,history);
 assert.equal(imported.entries[0].language,"Japanese");
});
test("practice supports Japanese build, exact matching, and scoped progress",()=>{
 const bank=extractQuestions([example],"Japanese");
 assert.equal(bank.length,2);
 assert.equal(bank[0].key,"ja::学校:meaning");
 assert.equal(bank[1].key,"ja::学校:structure");
 assert.equal(assemble(["勉強","します。"],"Japanese"),"勉強します。");
 assert.equal(matches("勉強 します。","勉強します。","Japanese"),true);
 assert.equal(matches("book","book ","English"),true);
 assert.equal(extractQuestions([example],"English").length,0);
 const legacy=migrateProgress({"学校:meaning":{attempts:1}},[example],bank);
 assert.equal(legacy["ja::学校:meaning"].attempts,1);
});
test("FSRS-6 new/learning/relearning schedules are Android-compatible",()=>{
 const now=1750000000000;
 const first=review(null,3,now);
 assert.equal(first.state,"learning");
 assert.equal(first.step,1);
 assert.equal(dueAt(first),now+600000);
 const next=review(first,3,now+600000);
 assert.equal(next.state,"review");
 assert.equal(next.attempts,2);
 const lapse=review(next,1,now+86400000);
 assert.equal(lapse.state,"relearning");
 assert.equal(dueAt(lapse),now+86400000+600000);
 const migrated=migrateLegacy({attempts:2,streak:2,dueAt:now});
 assert.equal(migrated.algorithm,"fsrs-6");
});
test("furigana keeps exact surface forms and Markdown escapes untrusted HTML",()=>{
 assert.match(rubyHtml("学校で勉強します。",example.furigana,true),/<ruby>学校<rt>がっこう<\/rt><\/ruby>/);
 assert.equal(rubyHtml("学校",example.furigana,false),"学校");
 assert.doesNotMatch(markdown("<script>alert(1)</script> **safe**"),/<script>/);
 assert.match(markdown("**bold**"),/<strong>bold<\/strong>/);
});
test("prompt retains Android JSON 3.0 and target-specific instructions",()=>{
 const prompt=buildPrompt([{term:"寝よっか",context:"home",originalSentence:"",specificQuestion:""}],"Japanese");
 assert.match(prompt,/schemaVersion "3\.0"/);
 assert.match(prompt,/STRICT, COMPLETE JSON ONLY/);
 assert.match(prompt,/Do NOT append hiragana or katakana readings/);
 assert.match(prompt,/"term": "寝よっか"/);
});
