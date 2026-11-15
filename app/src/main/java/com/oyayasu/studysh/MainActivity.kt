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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject

private val Ink=Color(0xFF17191C)
private val Muted=Color(0xFF74777D)
private val Canvas=Color(0xFFF9FAFB)
private val Pastels=listOf(Color(0xFFF9F0E4),Color(0xFFEAF1FB),Color(0xFFE7F5EE),Color(0xFFF5ECF5),Color(0xFFFFF3DF),Color(0xFFEBF2F3))
data class Note(val title:String,val type:String,val content:String)
data class Entry(val term:String,val pronunciation:String,val status:String,val notes:List<Note>,val original:String)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { StudyshApp(
   read={getPreferences(MODE_PRIVATE).getString("library","{\"schemaVersion\":\"1.0\",\"entries\":[]}")?:""},
   save={getPreferences(MODE_PRIVATE).edit().putString("library",it).apply()}
  ) }
 }
}
private fun parseEntries(raw:String):List<Entry>{
 val root=JSONObject(raw); val arr=root.optJSONArray("entries")?:JSONArray()
 return (0 until arr.length()).mapNotNull { i ->
  val obj=arr.optJSONObject(i)?:return@mapNotNull null
  val notes=obj.optJSONArray("notes")?:JSONArray()
  Entry(obj.optString("term"),obj.optString("pronunciation"),obj.optString("status","Learning"),
   (0 until notes.length()).mapNotNull { j -> notes.optJSONObject(j)?.let { Note(it.optString("title","Note"),it.optString("type","custom"),it.optString("content")) } },obj.toString())
 }.filter { it.term.isNotBlank() }
}
private fun prompt(terms:List<String>):String = """
You are an expert teacher of natural American English for a Brazilian Portuguese learner.
Teach the following lexical units deeply: ${terms.joinToString("; ")}.
For EACH unit: identify exact meaning, explain its central concept in Brazilian Portuguese, give an English definition, grammar and reusable patterns, nuanced contrasts with near synonyms, register, collocations, multiple real-life contexts, progressive natural American English examples with Portuguese interpretations, common errors, recognition exercises, recall exercises and independent production exercises. If parentheses appear in a query, prioritize the enclosed subexpression. Be accurate, avoid fabricated evidence, and include unusual insights as additional notes rather than forcing fixed categories.
OUTPUT ONLY valid JSON (no Markdown fences or commentary) with schemaVersion "1.0" and entries. Each entry requires term, pronunciation (IPA if confident), status "Learning", notes array and exercises array. Each note has id, type, title and content (Markdown text); add optional examples [{en,pt}], tags and extra custom notes freely. Each exercise has question, answer, explanation. Preserve the FULL lesson in the notes, not a summary. If output is too long, return fewer complete entries and ask for a second batch separately. Never output partial JSON.
""".trimIndent()
@Composable private fun StudyshApp(read:()->String,save:(String)->Unit){
 var library by remember { mutableStateOf(read()) }
 var entries by remember { mutableStateOf(runCatching { parseEntries(library) }.getOrDefault(emptyList())) }
 var page by remember { mutableStateOf(0) }
 var selected by remember { mutableStateOf<Entry?>(null) }
 var search by remember { mutableStateOf("") }
 var fields by remember { mutableStateOf(listOf("")) }
 var error by remember { mutableStateOf("") }
 var jsonText by remember { mutableStateOf("") }
 var showPaste by remember { mutableStateOf(false) }
 var editNote by remember { mutableStateOf(false) }
 var customNote by remember { mutableStateOf("") }
 val clipboard=LocalClipboardManager.current
 val importFile=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){ uri ->
  uri?.let { runCatching { val text=androidx.compose.ui.platform.LocalContext.current /* placeholder */ } }
 }
 // File picker is implemented via Activity's ContentResolver in the import callback below.
 val context=androidx.compose.ui.platform.LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){ uri ->
  uri?.let { runCatching { context.contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> reader.readText() } }.onSuccess { jsonText=it;showPaste=true }.onFailure { error=it.message?:"Unable to read file" } }
 }
 fun merge(raw:String){
  try {
   val incoming=parseEntries(raw)
   if(incoming.isEmpty()) throw IllegalArgumentException("No terms found in JSON.")
   val map=linkedMapOf<String,JSONObject>()
   entries.forEach { map[it.term.lowercase()]=JSONObject(it.original) }
   incoming.forEach { item ->
    val key=item.term.lowercase()
    val previous=map[key]
    if(previous==null) map[key]=JSONObject(item.original)
    else {
     val oldNotes=previous.optJSONArray("notes")?:JSONArray()
     val newNotes=JSONObject(item.original).optJSONArray("notes")?:JSONArray()
     val seen=(0 until oldNotes.length()).map { oldNotes.optJSONObject(it)?.optString("id") }.toMutableSet()
     for(j in 0 until newNotes.length()){val n=newNotes.optJSONObject(j)?:continue; if(n.optString("id") !in seen){oldNotes.put(n);seen.add(n.optString("id"))}}
     previous.put("notes",oldNotes)
    }
   }
   val arr=JSONArray();map.values.forEach { arr.put(it) }
   library=JSONObject().put("schemaVersion","1.0").put("entries",arr).toString()
   save(library);entries=parseEntries(library);error="Imported ${incoming.size} term(s).";showPaste=false;page=1
  }catch(e:Exception){error="Import failed: ${e.message}"}
 }
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,background=Canvas,surface=Color.White,onSurface=Ink)){
  Column(Modifier.fillMaxSize().background(Canvas)){
   Row(Modifier.fillMaxWidth().padding(horizontal=22.dp, vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
    Text("Studysh",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
    IconButton(onClick={picker.launch("application/json")}){Icon(Icons.Outlined.FileUpload,"Import JSON")}
   }
   if(selected!=null){
    val item=selected!!
    Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick={selected=null}){Icon(Icons.Outlined.ArrowBack,"Back")}
     Text("Library",color=Muted)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item {
      Column(Modifier.fillMaxWidth().border(1.dp,Color(0xFFE4E5E7),RoundedCornerShape(22.dp)).background(Color.White,RoundedCornerShape(22.dp)).padding(20.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){
        Text(item.term,fontSize=32.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        Text(item.status,color=Muted,fontSize=12.sp)
       }
       if(item.pronunciation.isNotBlank())Text(item.pronunciation,color=Muted,fontSize=16.sp)
      }
     }
     items(item.notes){note->
      val idx=item.notes.indexOf(note)
      Column(Modifier.fillMaxWidth().background(Pastels[idx%Pastels.size],RoundedCornerShape(20.dp)).padding(18.dp)){
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        Icon(when(note.type.lowercase()){"grammar","pattern"->Icons.Outlined.Extension;"comparison","contrast"->Icons.Outlined.CompareArrows;"concept"->Icons.Outlined.Lightbulb;"examples","usage"->Icons.Outlined.ChatBubbleOutline;else->Icons.Outlined.StickyNote2},null,modifier=Modifier.size(20.dp))
        Text(note.title,fontSize=19.sp,fontWeight=FontWeight.Bold)
       }
       Spacer(Modifier.height(12.dp))
       Text(note.content,fontSize=16.sp,lineHeight=25.sp)
      }
     }
     item {
      Column(Modifier.fillMaxWidth().border(1.dp,Color.LightGray,RoundedCornerShape(20.dp)).padding(16.dp)){
       Text("My notes",fontSize=20.sp,fontWeight=FontWeight.Bold)
       Spacer(Modifier.height(8.dp))
       OutlinedTextField(value=customNote,onValueChange={customNote=it},placeholder={Text("Add your own observation...")},modifier=Modifier.fillMaxWidth(),minLines=3)
       Spacer(Modifier.height(8.dp))
       Button(onClick={
        if(customNote.isNotBlank()){
         val obj=JSONObject(item.original)
         val arr=obj.optJSONArray("notes")?:JSONArray()
         arr.put(JSONObject().put("id","personal-${System.currentTimeMillis()}").put("type","custom").put("title","My notes").put("content",customNote))
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
     0->LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
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
       OutlinedButton(onClick={showPaste=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DataObject,null);Text(" Import AI response")}
      }
     }
     else->LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      item{
       Text("Library",fontSize=27.sp,fontWeight=FontWeight.Bold)
       Spacer(Modifier.height(12.dp))
       OutlinedTextField(value=search,onValueChange={search=it},label={Text("Search your terms")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
      }
      items(entries.filter{it.term.contains(search,true)}){entry->
       Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(18.dp)).border(1.dp,Color(0xFFE9EAED),RoundedCornerShape(18.dp)).clickable{selected=entry}.padding(18.dp)){
        Text(entry.term,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text("${entry.notes.size} notes  ·  ${entry.status}",color=Muted,fontSize=13.sp)
       }
      }
      if(entries.isEmpty())item{Text("No terms yet. Import an AI response to begin.",color=Muted)}
     }
    }
    Spacer(Modifier.weight(1f))
    NavigationBar(containerColor=Color.White){
     NavigationBarItem(selected=page==0,onClick={page=0},icon={Icon(Icons.Outlined.Search,null)},label={Text("Search")})
     NavigationBarItem(selected=page==1,onClick={page=1},icon={Icon(Icons.Outlined.AutoStories,null)},label={Text("Library")})
    }
   }
   if(error.isNotBlank())Text(error,modifier=Modifier.padding(horizontal=20.dp, vertical=5.dp),color=Muted,fontSize=12.sp)
  }
  if(showPaste) AlertDialog(onDismissRequest={showPaste=false},title={Text("Import JSON")},text={
   OutlinedTextField(value=jsonText,onValueChange={jsonText=it},modifier=Modifier.fillMaxWidth().heightIn(min=170.dp),placeholder={Text("Paste the complete AI JSON response")})
  },confirmButton={TextButton(onClick={merge(jsonText)}){Text("Import")}},dismissButton={TextButton(onClick={showPaste=false}){Text("Cancel")}})
 }
}
