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
data class Note(val title:String,val type:String,val content:String,val examples:List<Pair<String,String>> = emptyList())
data class Entry(val term:String,val pronunciation:String,val status:String,val notes:List<Note>,val original:String,val partOfSpeech:String="",val language:String="English")
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { StudyshApp(
   read={getPreferences(MODE_PRIVATE).getString("library","{\"schemaVersion\":\"1.0\",\"entries\":[]}")?:""},
   save={getPreferences(MODE_PRIVATE).edit().putString("library",it).apply()}
  ) }
 }
}
private fun normalizeStudyJson(input:String):String {
 var text=input.trim().removePrefix("\uFEFF").trim()
 if(text.startsWith("```")){
  text=text.substringAfter('\n',text).substringBeforeLast("```",text).trim()
 }
 // Some chat/export surfaces escape Markdown and JSON punctuation in the outer text.
 // Only remove backslashes before punctuation that is illegal as a JSON escape.
 text=text.replace(Regex("""(?<!\\)\\([\[\]\*_])"""),"$1")
 return text
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
private fun prompt(terms:List<String>):String = """
You are an expert teacher of natural American English for a Brazilian Portuguese speaker.
Analyze these terms independently: ${terms.joinToString("; ")}.
Your job is to give a personalized, natural, bilingual explanation that helps the student understand and USE each term, not an encyclopedia or a rigid dictionary template.
Write explanations primarily in Brazilian Portuguese. Keep English phrases, constructions, and natural example sentences in English, followed by their Brazilian Portuguese interpretation.
For each term, choose ONLY the content that term genuinely needs: central meaning, grammatical patterns, natural examples, comparisons, register, unusual nuances, caveats, incomplete expressions, or context-dependent interpretations, as appropriate. Do not force categories, counts, or repeated sections. Favor clear concise paragraphs and precise insights over volume.
If a query contains parentheses, prioritize the expression inside them. If a phrase is incomplete or ambiguous, explain what can be inferred and what cannot; never invent the author's intent or a missing context. Request the original sentence when needed, inside a natural explanatory block. Do not add uncertainty warnings without a real reason.
Give examples only when they clarify usage. Explain unnatural or humorous usage when instructive. No quizzes, exercises, progress labels, filler, or motivational text.
Return ONLY a compact, valid JSON object, without Markdown fences, with this structure:
{"version":2,"entries":[{"term":"...","language":"English","pronunciation":"/IPA/ (only if confident)","partOfSpeech":"verb (only if relevant)","blocks":[{"style":"purple","title":"Free, natural title in Portuguese","text":"Markdown with **bold** and *italic*"},{"style":"green","title":"Exemplos naturais","examples":[{"en":"English example.","pt":"Interpretação natural em português."}]}]}]}
Each entry may have ANY number of blocks, any titles, and any combination of text and examples. Use style values: purple (concept/explanation), blue (grammar/constructions), green (examples/comparisons), yellow (nuances/observations), rose (caveats/ambiguity), or plain (long-form text). Colors are visual hints, NOT required categories. Omit fields that do not apply. Use simple Markdown **bold** and *italic* within text; never put Markdown syntax in titles. Use explicit examples arrays for paired translations. Make the JSON as short as reasonably possible while preserving meaningful distinctions. Never output partial JSON; if the response would be too long, output fewer COMPLETE entries.
""".trimIndent()
@Composable private fun StudyshApp(read:()->String,save:(String)->Unit){
 var library by remember { mutableStateOf(read()) }
 var entries by remember { mutableStateOf(runCatching { parseEntries(library) }.getOrDefault(emptyList())) }
 var page by remember { mutableStateOf(1) }
 var filter by remember { mutableStateOf("All") }
 var compact by remember { mutableStateOf(true) }
 var selected by remember { mutableStateOf<Entry?>(null) }
 var search by remember { mutableStateOf("") }
 var fields by remember { mutableStateOf(listOf("")) }
 var error by remember { mutableStateOf("") }
 var jsonText by remember { mutableStateOf("") }
 var showPaste by remember { mutableStateOf(false) }
 var importSource by remember { mutableStateOf("paste") }
 var editNote by remember { mutableStateOf(false) }
 var customNote by remember { mutableStateOf("") }
 val clipboard=LocalClipboardManager.current
 val context=androidx.compose.ui.platform.LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){ uri ->
  uri?.let { runCatching { context.contentResolver.openInputStream(it)!!.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() } }.onSuccess { jsonText=it;importSource="file";showPaste=true;error="" }.onFailure { error=it.message?:"Unable to read file" } }
 }
 fun merge(raw:String){
  try {
   val normalized=normalizeStudyJson(raw)
   val root=runCatching { JSONObject(normalized) }.getOrElse { parseError ->
    val recovered=recoverCompleteEntries(normalized)
    if(recovered.isEmpty()) throw IllegalArgumentException("The response is incomplete or invalid. Copy the complete JSON or import a .json file.")
    val arr=JSONArray();recovered.forEach{arr.put(it)}
    JSONObject().put("version",2).put("entries",arr)
   }
   val partial=runCatching{JSONObject(normalized)}.isFailure
   if(root.optString("schemaVersion")!="1.0" && root.optInt("version")!=2) throw IllegalArgumentException("Unsupported JSON format. Expected Studysh v2 or legacy v1.")
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
   library=JSONObject().put("version",2).put("entries",arr).toString()
   save(library);entries=parseEntries(library);error=if(partial) "Recovered ${incoming.size} complete term(s). The response was cut off; remaining terms were not imported." else "Imported ${incoming.size} term(s).";showPaste=false;page=1
  }catch(e:Exception){error="Import failed: "+(e.message?:"Invalid JSON").take(180)}
 }
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,background=Canvas,surface=Color.White,onSurface=Ink)){
  Column(Modifier.fillMaxSize().background(Canvas)){
   Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Outlined.Layers,null,tint=Color(0xFF8C60CB),modifier=Modifier.size(28.dp))
    Spacer(Modifier.width(10.dp))
    Text("Studysh",fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    IconButton(onClick={picker.launch("application/json")}){Icon(Icons.Outlined.FileUpload,"Import JSON")}
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
       Icon(Icons.Outlined.BookmarkBorder,"Bookmark",tint=Muted)
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
      item{Text("New search",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Explore up to five expressions.",color=Muted)}
      items(fields.size){i->
       Row(verticalAlignment=Alignment.CenterVertically){
        OutlinedTextField(value=fields[i],onValueChange={v->fields=fields.mapIndexed{j,s->if(i==j)v else s}},label={Text("Term ${i+1}")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp))
        if(fields.size>1)IconButton(onClick={fields=fields.filterIndexed{j,_->i!=j}}){Icon(Icons.Outlined.Close,"Remove")}
       }
      }
      item{
       OutlinedButton(onClick={fields=fields+""},enabled=fields.size<5,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(" Add term")}
       Spacer(Modifier.height(16.dp))
       Button(onClick={clipboard.setText(AnnotatedString(prompt(fields.map{it.trim()}.filter{it.isNotEmpty()})));error="Prompt copied to clipboard."},enabled=fields.any{it.isNotBlank()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ContentCopy,null);Text(" Copy study prompt")}
       Spacer(Modifier.height(8.dp))
       OutlinedButton(onClick={importSource="paste";jsonText="";showPaste=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DataObject,null);Text(" Import AI response")}
      }
     }
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
    }
   }
   if(error.isNotBlank())Text(error,modifier=Modifier.padding(horizontal=20.dp, vertical=5.dp),color=Muted,fontSize=12.sp,maxLines=4,overflow=TextOverflow.Ellipsis)
  }
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

private fun markdown(text:String):AnnotatedString = buildAnnotatedString {
 val regex=Regex("""(\\*\\*[^*]+\\*\\*|\\*[^*\\n]+\\*)""")
 var cursor=0
 regex.findAll(text).forEach { match ->
  append(text.substring(cursor,match.range.first))
  val bold=match.value.startsWith("**")
  val stripped=if(bold)match.value.drop(2).dropLast(2) else match.value.drop(1).dropLast(1)
  withStyle(SpanStyle(fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,fontStyle=if(bold)FontStyle.Normal else FontStyle.Italic)){append(stripped)}
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
  "purple","concept","definition"->Icons.Outlined.MenuBook
  "blue","grammar","pattern","patterns"->Icons.Outlined.Widgets
  "green","examples","usage","comparison"->Icons.Outlined.SpeakerNotes
  "yellow","nuance","observation"->Icons.Outlined.LightbulbOutline
  "rose","warning","caveat","ambiguity"->Icons.Outlined.Info
  else->Icons.Outlined.Notes
 }
 Column(Modifier.fillMaxWidth().background(tone,RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Icon(icon,null,tint=Color(0xFF62646A),modifier=Modifier.size(21.dp))
   Text(note.title,fontSize=19.sp,fontWeight=FontWeight.Bold,lineHeight=24.sp)
  }
  if(note.content.isNotBlank())Text(markdown(note.content),fontSize=16.sp,lineHeight=25.sp,color=Ink)
  note.examples.forEachIndexed { i,example ->
   if(i>0)HorizontalDivider(color=Color(0x1C5D6064),thickness=1.dp)
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(example.first,fontSize=16.sp,fontStyle=FontStyle.Italic,lineHeight=24.sp,color=Ink)
    if(example.second.isNotBlank())Text(example.second,fontSize=14.sp,lineHeight=21.sp,color=Muted)
   }
  }
 }
}
