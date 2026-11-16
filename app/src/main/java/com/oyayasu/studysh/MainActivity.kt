package com.oyayasu.studysh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject

private val Ink=Color(0xFF17191C)
private val Muted=Color(0xFF74777D)
private val Canvas=Color(0xFFFCFBFE)
private val Pastels=listOf(Color(0xFFF9F0E4),Color(0xFFEAF1FB),Color(0xFFE7F5EE),Color(0xFFF5ECF5),Color(0xFFFFF3DF),Color(0xFFEBF2F3))
data class StudyQuery(val term:String="",val context:String="",val sentence:String="",val doubt:String="")
data class Note(val title:String,val type:String,val content:String,val examples:List<Pair<String,String>> = emptyList())
data class Entry(val term:String,val pronunciation:String,val status:String,val notes:List<Note>,val original:String,val partOfSpeech:String="",val language:String="English")
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { StudyshApp(
   read={getPreferences(MODE_PRIVATE).getString("library","{\"schemaVersion\":\"1.0\",\"entries\":[]}")?:""},
   save={getPreferences(MODE_PRIVATE).edit().putString("library",it).apply()},
   readProgress={getPreferences(MODE_PRIVATE).getString("practice_progress","{}")?:"{}"},
   saveProgress={getPreferences(MODE_PRIVATE).edit().putString("practice_progress",it).apply()}
  ) }
 }
}
private fun normalizeStudyJson(input:String):String {
 var source=input.trim().removePrefix("\uFEFF").trim()
 if(source.startsWith("```")){
  source=source.substringAfter('\n',source).substringBeforeLast("```",source).trim()
 }
 val result=StringBuilder(source.length)
 var quoted=false
 var i=0
 while(i<source.length){
  val c=source[i]
  if(c=='\\' && i+1<source.length){
   val next=source[i+1]
   if((!quoted && next in "[]{}") || (quoted && next in "*_[]")){
    result.append(next);i+=2;continue
   }
   if(quoted && next=='\\' && i+2<source.length && source[i+2] in "*_"){
    result.append(source[i+2]);i+=3;continue
   }
   result.append(c).append(next);i+=2;continue
  }
  if(c=='"')quoted=!quoted
  result.append(c);i++
 }
 return result.toString()
}
private fun recoverCompleteEntries(raw:String):List<JSONObject> {
 val key=raw.indexOf("\"entries\""); if(key<0)return emptyList()
 val bracket=raw.indexOf('[',key); if(bracket<0)return emptyList()
 val start=bracket+1
 val found=mutableListOf<JSONObject>()
 var depth=0; var begin=-1; var quoted=false; var escaped=false
 for(i in start until raw.length){
  val c=raw[i]
  if(quoted){
   if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false
   continue
  }
  if(c=='"'){quoted=true;continue}
  if(c=='{' ){if(depth==0)begin=i;depth++}
  else if(c=='}' && depth>0){depth--;if(depth==0 && begin>=0){
   runCatching{JSONObject(raw.substring(begin,i+1))}.getOrNull()?.let{found.add(it)}
   begin=-1
  }}
  else if(c==']' && depth==0)break
 }
 return found
}
private fun parseEntries(raw:String):List<Entry>{
 val root=JSONObject(raw);val arr=root.optJSONArray("entries")?:JSONArray()
 return (0 until arr.length()).mapNotNull { i ->
  val obj=arr.optJSONObject(i)?:return@mapNotNull null
  val blocks=obj.optJSONArray("blocks")?:obj.optJSONArray("notes")?:JSONArray()
  val notes=(0 until blocks.length()).mapNotNull { j ->
   blocks.optJSONObject(j)?.let { block ->
    val examples=block.optJSONArray("examples")?:JSONArray()
    Note(block.optString("title","Explanation"),block.optString("style",block.optString("type","purple")),
     block.optString("text",block.optString("content","")),
     (0 until examples.length()).mapNotNull { k -> examples.optJSONObject(k)?.let{ex -> Pair(ex.optString("en"),ex.optString("pt"))} })
   }
  }
  Entry(obj.optString("term"),obj.optString("pronunciation"),obj.optString("status",""),notes,obj.toString(),obj.optString("partOfSpeech"),obj.optString("language","English"))
 }.filter { it.term.isNotBlank() }
}
private fun prompt(queries:List<StudyQuery>):String {
 val requested=JSONArray()
 queries.filter{it.term.isNotBlank()}.forEach { q ->
  requested.put(JSONObject().put("term",q.term.trim()).put("context",q.context.trim())
   .put("originalSentence",q.sentence.trim()).put("specificQuestion",q.doubt.trim()))
 }
 return """
ROLE
You are a private teacher of natural American English for a Brazilian Portuguese learner. Your goal is independent understanding, recognition, and production of words, expressions and constructions. Teach what is needed for real mastery, not for length. Never behave like an encyclopedia, dictionary, or rigid template.

REQUESTS — analyze each independently; context and source sentence belong ONLY to their own term:
${requested.toString(2)}

PEDAGOGY — follow all principles, but select only relevant material per term:
1. Start with a concise CENTRAL MENTAL MODEL in Brazilian Portuguese: what the term communicates, implies, emphasizes, and how its meanings relate. Do not reduce it to a memorized translation. Separate truly unrelated senses rather than forcing a false common definition.
2. If a context is provided, explain that sense FIRST and explain its role in the original sentence if provided. Context is a freely written subject or situation, not a media category, and is a STARTING POINT, never a restriction. Expand to other useful senses and ordinary contexts. If a specificQuestion is provided, answer it directly while still teaching the necessary foundations. If parentheses mark part of an expression, prioritize that part within the whole construction. If the source is incomplete, do not invent intent; acknowledge genuine ambiguity only where needed.
3. Compare near alternatives ONLY when it changes interpretation or word choice. Explain differences in connotation, intensity, register, frequency, naturalness, syntax, and Portuguese interference. Do not invent hard distinctions where words overlap.
4. Teach productive constructions explicitly: TERM + preposition/complement/clause, what can fill each slot, important restrictions, related forms, derived constructions, and typical collocations. Distinguish productive grammar from fixed phrases and typical word partnerships. Cover the important patterns without endless interchangeable examples.
5. Give genuinely natural American English sentences paired with idiomatic Brazilian Portuguese. Each example must add something: a basic use, variation, nuance, another context, or restriction. Explain significant peculiarities immediately. Avoid contrived sentences.
6. Address realistic mistakes: unnatural literal translation, incorrect prepositions, misleading near synonyms, inappropriate formality, and constructions that are possible but unusual. Never label uncommon language as categorically wrong.
7. Adapt depth to complexity; anticipate useful doubts without repetition, trivia, etymology, generic introductions, inspirational language, conclusions, or compulsory sections. Do NOT put quizzes inside the lesson: exercises are a separate dataset.
8. Write primarily in clear Brazilian Portuguese, preserving English terms, patterns, and example sentences. Use compact, elegant Markdown: **bold** for central patterns, *italics* when helpful, short paragraphs and lists only when useful. Order ideas so one leads logically to the next. Keep the reading experience like a well-formatted ChatGPT teaching response.
9. Before finalizing, verify that the learner can understand the concept, recognize it across contexts, distinguish important alternatives, identify its natural constructions and collocations, and build original sentences.

EXERCISE DESIGN
Generate a compact but meaningful practice bank grounded in the ACTUAL taught meanings, patterns and nuances. Prefer these modes:
- "complete": choose the missing word/preposition or phrase in a natural English sentence;
- "build": assemble a natural sentence from shuffled word/phrase tiles;
- "choice": choose the most natural expression in a given context.
Use "recognition", "construction", "nuance", or "production" as the skill, selecting what the question genuinely tests.
For each exercise, give a stable ID unique within its entry, a skillId matching one of the knowledge skills, clear English instructions, an optional ptPrompt for meaning/context, answer and short Brazilian Portuguese explanation of WHY. For multiple-choice: exactly one unambiguously best answer, plausible distractors, and avoid accidental alternative correct readings. For build: tiles must contain exactly the fragments needed, with an unambiguous order and a canonical answer; do not use punctuation as separate tiles. No misleading artificial sentences. Include enough variety to test recognition and pattern use, without fixed quotas or duplicating the same fact.
Knowledge skill IDs must be short stable semantic identifiers (e.g. "meaning-transfer", "pattern-convey-to"), not positional numbers. Patterns and exercises reference these IDs. Never claim that multiple-choice success alone proves productive mastery.
Do not generate arbitrary spaced-repetition intervals, scores, timestamps, mastery or review dates. Those belong to the app's LOCAL progress store and future FSRS scheduler, not AI content.
The AI cannot reliably evaluate unrestricted original sentences using exact-string comparison. Do not create open-ended exercises requiring automatic semantic grading.

OUTPUT — STRICT, COMPLETE JSON ONLY, no fences or commentary.
Use schemaVersion "3.0", with this example as a STRUCTURAL MODEL, not a mandatory set of sections or sample content:
{
 "schemaVersion":"3.0",
 "entries":[{
  "term":"convey","language":"English","pronunciation":"/kənˈveɪ/","partOfSpeech":"verb",
  "context":"Art — composition","originalSentence":"",
  "blocks":[
   {"id":"concept","style":"purple","title":"Ideia central","text":"Texto didático com **destaques**."},
   {"id":"patterns","style":"blue","title":"Construções","text":"**convey something to someone** — ..."},
   {"id":"examples","style":"green","title":"Exemplos naturais","examples":[{"en":"The painting conveys a sense of depth.","pt":"A pintura transmite uma sensação de profundidade."}]}
  ],
  "knowledge":{
   "skills":[{"id":"meaning-transfer","label":"Meaning and recognition","type":"recognition"},
             {"id":"pattern-convey-to","label":"Convey something to someone","type":"construction"}],
   "patterns":[{"id":"convey-to","skillId":"pattern-convey-to","structure":"convey + something + to + someone","explanation":"Transmitir algo a alguém.","examples":["convey a message to the audience"]}],
   "collocations":[{"expression":"convey emotion","meaning":"transmitir emoção","skillId":"meaning-transfer"}],
   "nuances":[{"expression":"convey vs show","explanation":"Diferença relevante, sem absolutismos.","skillId":"meaning-transfer"}]
  },
  "exercises":[
   {"id":"convey-to-01","type":"complete","skillId":"pattern-convey-to","instruction":"Complete the sentence.","sentence":"She conveyed the idea ___ her team.","options":["to","for","at","with"],"answer":"to","explanation":"Usamos **convey something to someone**."},
   {"id":"convey-build-01","type":"build","skillId":"pattern-convey-to","instruction":"Build the sentence.","ptPrompt":"O artista transmitiu uma mensagem ao público.","tiles":["to the audience.","The artist","conveyed","a message"],"answer":"The artist conveyed a message to the audience.","explanation":"A ordem é sujeito + convey + conteúdo + to + destinatário."},
   {"id":"convey-choice-01","type":"choice","skillId":"meaning-transfer","instruction":"Choose the most natural sentence.","ptPrompt":"Escolha a frase adequada ao contexto.","options":["The drawing conveys a sense of movement.","The drawing conveys a sense of movement to."],"answer":"The drawing conveys a sense of movement.","explanation":"Não se usa **to** sem complemento."}
  ]
 }]
}
For blocks, titles are free and sections are optional. Style colors: purple=concept, blue=patterns, green=examples, yellow=nuance, rose=caveat, plain=neutral. Do not repeat the structured knowledge data verbatim in every block. Knowledge is machine-readable metadata; blocks are the readable lesson. Each exercise must refer to a defined skillId. Every answer must be exactly reconstructible from options or tiles. Keep JSON compact enough to finish ALL entries; prefer fewer strong exercises and precise explanations over excessive volume. Use standard JSON escaping only. No trailing commas, no Markdown fences, no incomplete entries.
""".trimIndent()
}
@Composable private fun StudyshApp(read:()->String,save:(String)->Unit,readProgress:()->String,saveProgress:(String)->Unit){
 var library by remember { mutableStateOf(read()) }
 var entries by remember { mutableStateOf(runCatching { parseEntries(library) }.getOrDefault(emptyList())) }
 var page by remember { mutableStateOf(1) }
 var filter by remember { mutableStateOf("All") }
 var compact by remember { mutableStateOf(true) }
 var selected by remember { mutableStateOf<Entry?>(null) }
 var search by remember { mutableStateOf("") }
 var fields by remember { mutableStateOf(listOf(StudyQuery())) }
 var error by remember { mutableStateOf("") }
 var jsonText by remember { mutableStateOf("") }
 var showPaste by remember { mutableStateOf(false) }
 var importSource by remember { mutableStateOf("paste") }
 var editNote by remember { mutableStateOf(false) }
 var customNote by remember { mutableStateOf("") }
 var editEntry by remember { mutableStateOf(false) }
 var deleteEntry by remember { mutableStateOf(false) }
 var entryDraft by remember { mutableStateOf("") }
 var editorError by remember { mutableStateOf("") }
 var advancedEdit by remember { mutableStateOf(false) }
 val clipboard=LocalClipboardManager.current
 val context=androidx.compose.ui.platform.LocalContext.current
 val backupExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
  uri?.let{
   runCatching{
    val backup=JSONObject().put("format","studysh-backup-1").put("library",JSONObject(library))
     .put("practiceProgress",JSONObject(readProgress()))
    context.contentResolver.openOutputStream(it)?.bufferedWriter(Charsets.UTF_8)?.use{writer->writer.write(backup.toString(2))}
     ?:throw IllegalStateException("Unable to write backup")
   }.onSuccess{error="Backup exported successfully."}
    .onFailure{error="Backup failed: "+(it.message?:"Unable to write file")}
  }
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){ uri ->
  uri?.let { runCatching { context.contentResolver.openInputStream(it)!!.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() } }.onSuccess { jsonText=it;importSource="file";showPaste=true;error="" }.onFailure { error=it.message?:"Unable to read file" } }
 }
 fun merge(raw:String){
  try {
   val normalized=normalizeStudyJson(raw)
   val root=runCatching { JSONObject(normalized) }.getOrElse { parseError ->
    val recovered=recoverCompleteEntries(normalized)
    throw IllegalArgumentException("JSON is incomplete or malformed. No terms were imported. Please import a complete .json file or copy the entire response.")
   }
   val partial=false
   if(root.optString("format")=="studysh-backup-1"){
    val restored=root.optJSONObject("library")?:throw IllegalArgumentException("Backup is missing the library.")
    val restoredProgress=root.optJSONObject("practiceProgress")?:JSONObject()
    require(restored.optJSONArray("entries")!=null){"Backup is missing entries."}
    val parsed=parseEntries(restored.toString())
    library=restored.toString();save(library);entries=parsed
    saveProgress(restoredProgress.toString());showPaste=false;page=1;error="Backup restored: ${parsed.size} terms and review history.";return
   }
   if(root.optString("schemaVersion")!="1.0" && root.optInt("version")!=2 && root.optString("schemaVersion")!="3.0") throw IllegalArgumentException("Unsupported JSON format. Expected Studysh v3, v2 or legacy v1.")
   if(root.optJSONArray("entries")==null) throw IllegalArgumentException("Missing entries array.")
   val incoming=parseEntries(root.toString())
   if(incoming.isEmpty()) throw IllegalArgumentException("No terms found in JSON.")
   val map=linkedMapOf<String,JSONObject>()
   entries.forEach { map[it.term.lowercase()]=JSONObject(it.original) }
   incoming.forEach { item ->
    val key=item.term.lowercase()
    val old=map[key]
    val replacement=JSONObject(item.original)
    if(old!=null){
     val personal=old.optJSONArray("personalNotes")
     if(personal!=null)replacement.put("personalNotes",personal)
     val oldNotes=old.optJSONArray("notes")
     if(oldNotes!=null){
      val saved=JSONArray()
      for(j in 0 until oldNotes.length()){
       val n=oldNotes.optJSONObject(j)?:continue
       if(n.optString("type")=="custom")saved.put(n)
      }
      if(saved.length()>0){
       val existing=replacement.optJSONArray("personalNotes")?:JSONArray()
       for(j in 0 until saved.length())existing.put(saved.getJSONObject(j))
       replacement.put("personalNotes",existing)
      }
     }
    }
    map[key]=replacement
   }
   val arr=JSONArray();map.values.forEach { arr.put(it) }
   library=JSONObject().put("schemaVersion","3.0").put("entries",arr).toString()
   save(library);entries=parseEntries(library);error=if(partial) "Recovered ${incoming.size} complete term(s). The response was cut off; remaining terms were not imported." else "Imported ${incoming.size} term(s).";showPaste=false;page=1
  }catch(e:Exception){error="Import failed: "+(e.message?:"Invalid JSON").take(180)}
 }
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,background=Canvas,surface=Color.White,onSurface=Ink)){
  Column(Modifier.fillMaxSize().background(Canvas)){
   Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Outlined.Layers,null,tint=Color(0xFF8C60CB),modifier=Modifier.size(28.dp))
    Spacer(Modifier.width(10.dp))
    Text("Studysh",fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    IconButton(onClick={backupExporter.launch("studysh-backup.json")}){Icon(Icons.Outlined.SaveAlt,"Export backup")}
    IconButton(onClick={picker.launch("*/*")}){Icon(Icons.Outlined.FileUpload,"Import JSON or backup")}
   }
   if(selected!=null){
    val item=selected!!
    Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick={selected=null}){Icon(Icons.Outlined.ArrowBack,"Back")}
     Text("Library",color=Muted)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item {
      Row(verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f)){Text(item.term,fontSize=30.sp,fontWeight=FontWeight.Bold);if(item.pronunciation.isNotBlank() || item.partOfSpeech.isNotBlank())Text(listOf(item.pronunciation,item.partOfSpeech).filter{it.isNotBlank()}.joinToString(" · "),color=Muted,fontSize=15.sp)}
       IconButton(onClick={entryDraft=JSONObject(item.original).toString(2);editorError="";advancedEdit=false;editEntry=true}){Icon(Icons.Outlined.Edit,"Edit term")}
       IconButton(onClick={deleteEntry=true}){Icon(Icons.Outlined.DeleteOutline,"Delete term",tint=Color(0xFFAE5A68))}
      }
     }
     val studyContext=runCatching{JSONObject(item.original).optString("context")}.getOrDefault("")
     if(studyContext.isNotBlank())item{
      Surface(color=Color(0xFFF1ECF8),shape=RoundedCornerShape(14.dp)){
       Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        Icon(Icons.Outlined.TravelExplore,null,modifier=Modifier.size(18.dp),tint=Color(0xFF785C9E))
        Column{Text("Original context",fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=Color(0xFF785C9E));Text(studyContext,fontSize=14.sp,lineHeight=20.sp)}
       }
      }
     }
     items(item.notes.size){index->StudyNoteCard(item.notes[index],index)}
     val savedPersonal=runCatching{JSONObject(item.original).optJSONArray("personalNotes")}.getOrNull()
     if(savedPersonal!=null)items(savedPersonal.length()){index->
      val note=savedPersonal.optJSONObject(index)?.optString("text").orEmpty()
      if(note.isNotBlank())StudyNoteCard(Note("My notes","plain",note),index)
     }
     item {
      Column(Modifier.fillMaxWidth().border(1.dp,Color(0xFFE5E1E9),RoundedCornerShape(20.dp)).background(Color.White,RoundedCornerShape(20.dp)).padding(16.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(8.dp));Text("My notes",fontSize=19.sp,fontWeight=FontWeight.Bold)}
       Spacer(Modifier.height(8.dp))
       OutlinedTextField(value=customNote,onValueChange={customNote=it},placeholder={Text("Add your own observation...")},modifier=Modifier.fillMaxWidth(),minLines=3)
       Spacer(Modifier.height(8.dp))
       Button(onClick={
        if(customNote.isNotBlank()){
         val obj=JSONObject(item.original)
         val arr=obj.optJSONArray("personalNotes")?:JSONArray()
         arr.put(JSONObject().put("text",customNote))
         obj.put("personalNotes",arr)
         val all=JSONObject(library);val data=all.getJSONArray("entries")
         for(i in 0 until data.length()){if(data.getJSONObject(i).optString("term")==item.term){data.put(i,obj);break}}
         library=all.toString();save(library);entries=parseEntries(library);selected=entries.find{it.term==item.term};customNote=""
        }
       }){Text("Save note")}
      }
     }
    }
   }else{
    when(page){
     0->LazyColumn(modifier=Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      item{Text("New search",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Begin with your context, then explore natural usage elsewhere.",color=Muted,fontSize=14.sp,lineHeight=21.sp)}
      items(fields.size){i->
       val query=fields[i]
       Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(22.dp)).border(1.dp,Color(0xFFE9E4ED),RoundedCornerShape(22.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
         Text("Term ${i+1}",fontWeight=FontWeight.SemiBold,fontSize=16.sp,modifier=Modifier.weight(1f))
         if(fields.size>1)IconButton(onClick={fields=fields.filterIndexed{j,_->i!=j}}){Icon(Icons.Outlined.Close,"Remove term")}
        }
        OutlinedTextField(value=query.term,onValueChange={v->fields=fields.mapIndexed{j,q->if(i==j)q.copy(term=v) else q}},placeholder={Text("Word or expression")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=query.context,onValueChange={v->fields=fields.mapIndexed{j,q->if(i==j)q.copy(context=v) else q}},label={Text("Context (optional)")},placeholder={Text("e.g. Art — anatomy, perspective, composition")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=query.sentence,onValueChange={v->fields=fields.mapIndexed{j,q->if(i==j)q.copy(sentence=v) else q}},label={Text("Original sentence (optional)")},placeholder={Text("Paste the sentence where you found it")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=query.doubt,onValueChange={v->fields=fields.mapIndexed{j,q->if(i==j)q.copy(doubt=v) else q}},label={Text("Specific question (optional)")},placeholder={Text("What exactly would you like to understand?")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,shape=RoundedCornerShape(14.dp))
       }
      }
      item{
       OutlinedButton(onClick={fields=fields+StudyQuery()},enabled=fields.size<5,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(" Add term")}
       Spacer(Modifier.height(16.dp))
       Button(onClick={clipboard.setText(AnnotatedString(prompt(fields.filter{it.term.isNotBlank()})));error="Prompt copied to clipboard."},enabled=fields.any{it.term.isNotBlank()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ContentCopy,null);Text(" Copy study prompt")}
       Spacer(Modifier.height(8.dp))
       OutlinedButton(onClick={importSource="paste";jsonText="";showPaste=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DataObject,null);Text(" Import AI response")}
      }
     }
     2->Box(Modifier.weight(1f)){StudyshPractice(library,readProgress,saveProgress)}
     else->LazyColumn(modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
      item{
       Row(verticalAlignment=Alignment.CenterVertically){Text("Library",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick={compact=!compact}){Icon(Icons.Outlined.Tune,"Layout")}}
       Spacer(Modifier.height(8.dp))
       OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("Search your terms")},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(22.dp))
       Spacer(Modifier.height(10.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("All","English","Japanese","Other").forEach{f->
        val active=filter==f
        Surface(onClick={filter=f},color=if(active)Color(0xFF956DCE) else Color.White,shape=RoundedCornerShape(50),border=if(active)null else androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFE6E0EA))){
         Text(f,Modifier.padding(horizontal=12.dp,vertical=9.dp),fontSize=12.sp,color=if(active)Color.White else Ink)
        }
       }}
      }
      val visible=entries.filter{it.term.contains(search,true)}.filter{entry->
       val language=entry.language
       filter=="All" || (filter=="Other" && language !in listOf("English","Japanese")) || language.equals(filter,true)
      }
      items(visible.size){i->
       val entry=visible[i]
       Row(Modifier.fillMaxWidth().background(Pastels[i%Pastels.size],RoundedCornerShape(22.dp)).clickable{selected=entry}.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Outlined.BookmarkBorder,null,modifier=Modifier.size(25.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)){Text(entry.term,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(entry.pronunciation.ifBlank{"${entry.notes.size} explanation sections"},fontSize=13.sp,color=Muted,maxLines=1)}
        Surface(color=Color.White.copy(alpha=.65f),shape=RoundedCornerShape(50)){Text(if(entry.language.equals("Japanese",true))"JA" else "EN",Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=11.sp,color=Muted)}
       }
      }
      if(visible.isEmpty())item{Text(if(entries.isEmpty())"Your library is empty. Add your first study term." else "No matching terms.",color=Muted,modifier=Modifier.padding(top=16.dp))}
     }
    }
    NavigationBar(containerColor=Color(0xFFFAF7FD),tonalElevation=0.dp){
     NavigationBarItem(selected=page==1,onClick={page=1},icon={Icon(Icons.Outlined.Home,null)},label={Text("Home")})
     NavigationBarItem(selected=page==0,onClick={page=0},icon={Icon(Icons.Outlined.AddCircleOutline,null)},label={Text("Create")})
     NavigationBarItem(selected=page==2,onClick={page=2},icon={Icon(Icons.Outlined.Extension,null)},label={Text("Practice")})
    }
   }
   if(error.isNotBlank())Text(error,modifier=Modifier.padding(horizontal=20.dp, vertical=5.dp),color=Muted,fontSize=12.sp,maxLines=4,overflow=TextOverflow.Ellipsis)
  }
  if(deleteEntry && selected!=null)AlertDialog(
   onDismissRequest={deleteEntry=false},
   title={Text("Delete this term?")},
   text={Text("Remove \"${selected?.term}\" and its lesson from the library? This cannot be undone. Its practice history will also be removed. Export a backup first if you want to keep it.")},
   confirmButton={TextButton(onClick={
    val term=selected!!.term
    val arr=JSONArray()
    entries.filterNot{it.term==term}.forEach{arr.put(JSONObject(it.original))}
    library=JSONObject().put("schemaVersion","3.0").put("entries",arr).toString()
    save(library);entries=parseEntries(library)
    val history=runCatching{JSONObject(readProgress())}.getOrElse{JSONObject()}
    val keys=history.keys().asSequence().filter{it.startsWith(term.lowercase()+":")}.toList()
    keys.forEach{history.remove(it)};saveProgress(history.toString())
    selected=null;deleteEntry=false
   }){Text("Delete",color=Color(0xFFAE5A68))}},
   dismissButton={TextButton(onClick={deleteEntry=false}){Text("Cancel")}}
  )
  if(editEntry && selected!=null)AlertDialog(
   onDismissRequest={editEntry=false},
   title={Text("Edit term")},
   text={
    Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
     Text("Edit basic details below, or open Advanced to edit explanations and exercises. Keep skill IDs unchanged to preserve review progress.",fontSize=13.sp,color=Muted)
     val obj=runCatching{JSONObject(entryDraft)}.getOrNull()
     if(!advancedEdit && obj!=null){
      listOf("term" to "Term","pronunciation" to "Pronunciation","partOfSpeech" to "Part of speech",
       "context" to "Context","originalSentence" to "Original sentence","specificQuestion" to "Specific question").forEach{(key,label)->
       OutlinedTextField(value=obj.optString(key),onValueChange={value->
        entryDraft=JSONObject(entryDraft).put(key,value).toString(2)
       },label={Text(label)},modifier=Modifier.fillMaxWidth(),maxLines=3)
      }
     }else{
      OutlinedTextField(value=entryDraft,onValueChange={entryDraft=it;editorError=""},label={Text("Entry JSON")},modifier=Modifier.fillMaxWidth(),minLines=10,maxLines=22,textStyle=androidx.compose.ui.text.TextStyle(fontSize=12.sp,lineHeight=17.sp))
     }
     TextButton(onClick={advancedEdit=!advancedEdit}){Text(if(advancedEdit)"Basic fields" else "Advanced · edit full lesson JSON")}
     if(editorError.isNotBlank())Text(editorError,color=Color(0xFFAE5A68),fontSize=12.sp)
    }
   },
   confirmButton={TextButton(onClick={
    try{
     val edited=JSONObject(normalizeStudyJson(entryDraft))
     val old=selected!!
     val term=edited.optString("term").trim()
     require(term.isNotBlank()){"Term cannot be empty."}
     require(entries.none{it.term.equals(term,true) && !it.term.equals(old.term,true)}){"Another term already has this name."}
     require(edited.optJSONArray("blocks")!=null || edited.optJSONArray("notes")!=null){"The lesson needs blocks or notes."}
     val updated=JSONArray()
     entries.forEach{entry->updated.put(if(entry.term==old.term)edited else JSONObject(entry.original))}
     library=JSONObject().put("schemaVersion","3.0").put("entries",updated).toString()
     save(library);entries=parseEntries(library)
     if(!term.equals(old.term,true)){
      val history=runCatching{JSONObject(readProgress())}.getOrElse{JSONObject()}
      val keys=history.keys().asSequence().filter{it.startsWith(old.term.lowercase()+":")}.toList()
      keys.forEach{key->val value=history.optJSONObject(key);if(value!=null){history.put(term.lowercase()+key.substring(old.term.length),value);history.remove(key)}}
      saveProgress(history.toString())
     }
     selected=entries.find{it.term==term};editEntry=false
    }catch(e:Exception){editorError=e.message?:"Invalid JSON"}
   }){Text("Save")}},
   dismissButton={TextButton(onClick={editEntry=false}){Text("Cancel")}}
  )
  if(showPaste) AlertDialog(onDismissRequest={showPaste=false},title={Text("Import study response")},text={
   Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Large lessons are supported. Import a JSON file or paste the full response from your clipboard.",color=Muted,fontSize=13.sp)
    OutlinedButton(onClick={picker.launch("*/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FolderOpen,null);Text(" Choose JSON / TXT file")}
    OutlinedButton(onClick={
     val clip=clipboard.getText()?.text.orEmpty()
     if(clip.isNotBlank()){jsonText=clip;importSource="clipboard";error=""}else error="Clipboard is empty."
    },modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ContentPaste,null);Text(" Paste from clipboard")}
    if(jsonText.isNotBlank()){
     Text("Ready: ${jsonText.length} characters (${importSource}).",fontWeight=FontWeight.SemiBold,fontSize=13.sp)
     Text(jsonText.take(180).replace("\\n"," ") + if(jsonText.length>180)"…" else "",fontSize=11.sp,color=Muted,maxLines=4)
    }else{
     OutlinedTextField(value=jsonText,onValueChange={jsonText=it},modifier=Modifier.fillMaxWidth().heightIn(min=100.dp,max=180.dp),placeholder={Text("Or paste a short JSON response here")},maxLines=5)
    }
   }
  },confirmButton={TextButton(onClick={merge(jsonText)},enabled=jsonText.isNotBlank()){Text("Import")}},dismissButton={TextButton(onClick={showPaste=false}){Text("Cancel")}})

 }
}

private fun readableText(raw:String):String {
 return raw.replace("\\r\\n","\n").replace("\\n","\n").replace("\\t","  ")
  .replace("\\*","*").replace("\\_","_")
}
private fun markdown(raw:String):AnnotatedString = buildAnnotatedString {
 val text=readableText(raw)
 val regex=Regex("""(\*\*[^*]+\*\*|\*[^*\n]+\*)""")
 var cursor=0
 regex.findAll(text).forEach { match ->
  append(text.substring(cursor,match.range.first))
  val bold=match.value.startsWith("**")
  val stripped=if(bold)match.value.drop(2).dropLast(2) else match.value.drop(1).dropLast(1)
  withStyle(SpanStyle(
   fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,
   fontStyle=if(bold)FontStyle.Normal else FontStyle.Italic
  )){append(stripped)}
  cursor=match.range.last+1
 }
 append(text.substring(cursor))
}
@Composable private fun StudyNoteCard(note:Note,index:Int){
 val tone=when(note.type.lowercase()){
  "purple","concept","definition"->Color(0xFFF0EBF8)
  "blue","grammar","pattern","patterns"->Color(0xFFEAF2F9)
  "green","examples","usage","comparison"->Color(0xFFE9F5F0)
  "yellow","nuance","observation"->Color(0xFFFAF0E4)
  "rose","warning","caveat","ambiguity"->Color(0xFFF9EAF0)
  "plain"->Color(0xFFF8F8F8)
  else->Pastels[index%Pastels.size]
 }
 val icon=when(note.type.lowercase()){
  "purple","concept","definition"->Icons.Outlined.Book
  "blue","grammar","pattern","patterns"->Icons.Outlined.Extension
  "green","examples","usage","comparison"->Icons.Outlined.ChatBubbleOutline
  "yellow","nuance","observation"->Icons.Outlined.Lightbulb
  "rose","warning","caveat","ambiguity"->Icons.Outlined.Info
  else->Icons.Outlined.Notes
 }
 Column(Modifier.fillMaxWidth().background(tone,RoundedCornerShape(20.dp)).padding(17.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Icon(icon,null,tint=Color(0xFF62646A),modifier=Modifier.size(19.dp))
   Text(note.title,fontSize=17.sp,fontWeight=FontWeight.Bold,lineHeight=23.sp)
  }
  if(note.content.isNotBlank()){
   val paragraphs=readableText(note.content).trim().split(Regex("""\n\s*\n"""))
   Column(verticalArrangement=Arrangement.spacedBy(11.dp)){
    paragraphs.filter{it.isNotBlank()}.forEach { paragraph ->
     val lines=paragraph.lines()
     if(lines.all{it.trimStart().startsWith("- ") || it.trimStart().startsWith("• ")}){
      Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
       lines.forEach{line->
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top){
         Text("•",fontSize=16.sp,color=Color(0xFF7D6E90))
         Text(markdown(line.trimStart().drop(2)),fontSize=15.sp,lineHeight=23.sp,color=Ink,modifier=Modifier.weight(1f))
        }
       }
      }
     }else Text(markdown(paragraph),fontSize=15.sp,lineHeight=24.sp,color=Ink)
    }
   }
  }
  note.examples.forEachIndexed { i,example ->
   if(i>0)HorizontalDivider(color=Color(0x1C5D6064),thickness=1.dp)
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(readableText(example.first),fontSize=16.sp,fontStyle=FontStyle.Italic,lineHeight=24.sp,color=Ink)
    if(example.second.isNotBlank())Text(readableText(example.second),fontSize=14.sp,lineHeight=21.sp,color=Muted)
   }
  }
 }
}
