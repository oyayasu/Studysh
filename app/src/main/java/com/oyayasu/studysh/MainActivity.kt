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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  setContent { StudyshApp(
   read={getPreferences(MODE_PRIVATE).getString("library","{\"schemaVersion\":\"1.0\",\"entries\":[]}")?:""},
   save={getPreferences(MODE_PRIVATE).edit().putString("library",it).apply()},
   readProgress={getPreferences(MODE_PRIVATE).getString("practice_progress","{}")?:"{}"},
   saveProgress={getPreferences(MODE_PRIVATE).edit().putString("practice_progress",it).apply()},
   readLanguage={getPreferences(MODE_PRIVATE).getString("study_language","English")?:"English"},
   saveLanguage={getPreferences(MODE_PRIVATE).edit().putString("study_language",it).apply()},
   readAppLanguage={getPreferences(MODE_PRIVATE).getString("app_language","en")?:"en"},
   saveAppLanguage={getPreferences(MODE_PRIVATE).edit().putString("app_language",it).apply()}
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
@Composable internal fun StudyshApp(read:()->String,save:(String)->Unit,readProgress:()->String,saveProgress:(String)->Unit,readLanguage:()->String,saveLanguage:(String)->Unit,readAppLanguage:()->String,saveAppLanguage:(String)->Unit){
 var library by remember { mutableStateOf(read()) }
 var entries by remember { mutableStateOf(runCatching { parseEntries(library) }.getOrDefault(emptyList())) }
 var page by remember { mutableStateOf(1) }
 var settingsWindow by remember { mutableStateOf<SettingsWindow?>(null) }
 var filter by remember { mutableStateOf("All") }
 var studyLanguage by remember { mutableStateOf(readLanguage().takeIf{it in listOf("English","Japanese","Spanish","Portuguese")} ?: "English") }
 var practiceLanguage by remember { mutableStateOf(studyLanguage) }
 var appLanguage by remember { mutableStateOf(readAppLanguage().takeIf{it in listOf("en","pt","es","ja")} ?: "en") }
 StudyshI18n.language=appLanguage
 var compact by remember { mutableStateOf(true) }
 var selected by remember { mutableStateOf<Entry?>(null) }
 var search by remember { mutableStateOf("") }
 var fields by remember { mutableStateOf(listOf(StudyQuery())) }
 var pendingExample by remember { mutableStateOf<Pair<String,String>?>(null) }
 var error by remember { mutableStateOf("") }
 var jsonText by remember { mutableStateOf("") }
 var showPaste by remember { mutableStateOf(false) }
 var importSource by remember { mutableStateOf("paste") }
 var editNote by remember { mutableStateOf(false) }
 var customNote by remember { mutableStateOf("") }
 var editingPersonalNote by remember { mutableStateOf<Int?>(null) }
 var personalNoteDraft by remember { mutableStateOf("") }
 var deletePersonalNote by remember { mutableStateOf<Int?>(null) }
 var editEntry by remember { mutableStateOf(false) }
 var deleteEntry by remember { mutableStateOf(false) }
 var entryDraft by remember { mutableStateOf("") }
 var editorError by remember { mutableStateOf("") }
 var advancedEdit by remember { mutableStateOf(false) }
 val clipboard=LocalClipboardManager.current
 val context=androidx.compose.ui.platform.LocalContext.current
 val uiPrefs=remember(context){context.getSharedPreferences("studysh_ui",android.content.Context.MODE_PRIVATE)}
 var furiganaEnabled by rememberSaveable { mutableStateOf(uiPrefs.getBoolean("furigana",false)) }
 val sounds=remember(context){StudyshSounds(context.applicationContext)}
 val piper=remember(context){StudyshPiper(context)}
 DisposableEffect(piper,sounds){onDispose{piper.close();sounds.close()}}
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
    saveProgress(restoredProgress.toString());showPaste=false;page=3;error="Backup restored: ${parsed.size} terms and review history.";return
   }
   if(root.optString("schemaVersion")!="1.0" && root.optInt("version")!=2 && root.optString("schemaVersion")!="3.0") throw IllegalArgumentException("Unsupported JSON format. Expected Studysh v3, v2 or legacy v1.")
   if(root.optJSONArray("entries")==null) throw IllegalArgumentException("Missing entries array.")
   val incoming=parseEntries(root.toString())
   if(incoming.isEmpty()) throw IllegalArgumentException("No terms found in JSON.")
   val arr=JSONArray()
   StudyLibrary.merge(entries,incoming).forEach{arr.put(it)}
   library=JSONObject().put("schemaVersion","3.0").put("entries",arr).toString()
   save(library);entries=parseEntries(library);error=if(partial) "Recovered ${incoming.size} complete term(s). The response was cut off; remaining terms were not imported." else "Imported ${incoming.size} term(s).";showPaste=false;page=3
  }catch(e:Exception){error="Import failed: "+(e.message?:"Invalid JSON").take(180)}
 }
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,background=Canvas,surface=Color.White,onSurface=Ink)){
  val readingSettings=FuriganaSettings(furiganaEnabled,
   StudyLanguage.canonical(selected?.language ?: if(page==2)practiceLanguage else studyLanguage)=="Japanese",
   selected?.let{runCatching{JapaneseReadings.overridesFrom(JSONObject(it.original))}.getOrDefault(emptyMap())}.orEmpty())
  CompositionLocalProvider(LocalFurigana provides readingSettings){
  Column(Modifier.fillMaxSize().background(Canvas)){
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Outlined.Layers,null,tint=Color(0xFF8C60CB),modifier=Modifier.size(24.dp))
    Spacer(Modifier.width(8.dp))
    Text("Studysh",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1)
    IconButton(onClick={picker.launch("*/*")},modifier=Modifier.testTag("import-json")){Icon(Icons.Outlined.SaveAlt,"Import JSON or backup")}
    IconButton(onClick={backupExporter.launch("studysh-backup.json")},modifier=Modifier.testTag("export-backup")){Icon(Icons.Outlined.FileUpload,"Export backup")}
    if(StudyLanguage.showFurigana(studyLanguage,selected?.language,if(page==2 && selected==null)practiceLanguage else null)){
     IconToggleButton(checked=furiganaEnabled,onCheckedChange={value->furiganaEnabled=value;uiPrefs.edit().putBoolean("furigana",value).apply();sounds.play(SoundCue.Tap)},
      modifier=Modifier.testTag("furigana-toggle").semantics{contentDescription="Furigana";stateDescription=if(furiganaEnabled)"On" else "Off"}){
      Column(horizontalAlignment=Alignment.CenterHorizontally){Text("ふ",fontSize=9.sp,lineHeight=9.sp);Text("漢",fontSize=19.sp,lineHeight=21.sp,color=if(furiganaEnabled)Color(0xFF956DCE) else Muted)}
     }
    }
    IconButton(onClick={settingsWindow=SettingsWindow.Settings},modifier=Modifier.testTag("global-settings")){Icon(Icons.Outlined.Tune,"Settings")}
   }
   Box(Modifier.weight(1f)){
   if(page==4){
    BookCapture(maxStudies=(5-fields.count{it.term.isNotBlank()}).coerceAtLeast(0),onClose={page=0},onAdd={captured->
     fields=fields.filter{it.term.isNotBlank()}+captured
     page=0
     error="Added ${captured.size} book study request(s). Review and copy the prompt."
    })
   }else if(selected!=null){
    val item=selected!!
    Column(Modifier.fillMaxSize()){
    Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
     IconButton(onClick={selected=null}){Icon(Icons.Outlined.ArrowBack,"Back")}
     Text(ui("Lessons"),color=Muted)
    }
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item {
      Row(verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f)){
        StudyText(item.term,fontSize=30.sp,fontWeight=FontWeight.Bold)
        val pronunciation=if(SpeechRouting.isJapanese(item.language) && furiganaEnabled) "" else item.pronunciation
        if(pronunciation.isNotBlank() || item.partOfSpeech.isNotBlank())Text(listOf(pronunciation,item.partOfSpeech).filter{it.isNotBlank()}.joinToString(" · "),color=Muted,fontSize=15.sp)
       }
       IconButton(onClick={piper.speak(item.term,item.language)}){Icon(Icons.Outlined.VolumeUp,"Speak term")}
       IconButton(onClick={entryDraft=JSONObject(item.original).toString(2);editorError="";advancedEdit=false;editEntry=true}){Icon(Icons.Outlined.Edit,"Edit term")}
       IconButton(onClick={deleteEntry=true}){Icon(Icons.Outlined.DeleteOutline,"Delete term",tint=Color(0xFFAE5A68))}
      }
      if(SpeechRouting.isJapanese(item.language)){
       if(piper.japanese.busy){
        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        TextButton(onClick={piper.japanese.stop()}){Text("Stop audio")}
       }
       if(piper.japanese.message.isNotBlank())Text(piper.japanese.message,fontSize=13.sp,color=Muted)
       piper.japanese.error?.let{Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
      }else if(piper.message.isNotBlank())Text(piper.message,fontSize=13.sp,color=MaterialTheme.colorScheme.error)
     }
     val studyContext=runCatching{JSONObject(item.original).optString("context")}.getOrDefault("")
     if(studyContext.isNotBlank())item{
      Surface(color=Color(0xFFF1ECF8),shape=RoundedCornerShape(14.dp)){
       Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        Icon(Icons.Outlined.TravelExplore,null,modifier=Modifier.size(18.dp),tint=Color(0xFF785C9E))
        Column{Text("Original context",fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=Color(0xFF785C9E));StudyText(studyContext,fontSize=14.sp,lineHeight=20.sp)}
       }
      }
     }
     items(item.notes.size){index->StudyNoteCard(item.notes[index],index,onSpeak={piper.speak(it,item.language)},onStudy={term,sentence->pendingExample=term to sentence})}
     val savedPersonal=runCatching{JSONObject(item.original).optJSONArray("personalNotes")}.getOrNull()
     if(savedPersonal!=null)items(savedPersonal.length()){index->
      val note=savedPersonal.optJSONObject(index)?.optString("text").orEmpty()
      if(note.isNotBlank()){
       Column(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
         Text(ui("My notes"),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
         IconButton(onClick={editingPersonalNote=index;personalNoteDraft=note}){Icon(Icons.Outlined.Edit,"Edit note")}
         IconButton(onClick={deletePersonalNote=index}){Icon(Icons.Outlined.DeleteOutline,"Delete note")}
        }
        StudyNoteCard(Note("My notes","plain",note),index,onSpeak={piper.speak(it,item.language)},onStudy={term,sentence->pendingExample=term to sentence})
       }
      }
     }
     item {
      Column(Modifier.fillMaxWidth().border(1.dp,Color(0xFFE5E1E9),RoundedCornerShape(20.dp)).background(Color.White,RoundedCornerShape(20.dp)).padding(16.dp)){
       Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(8.dp));Text(ui("My notes"),fontSize=19.sp,fontWeight=FontWeight.Bold)}
       Spacer(Modifier.height(8.dp))
       OutlinedTextField(value=customNote,onValueChange={customNote=it},placeholder={Text(ui("Add your own observation..."))},modifier=Modifier.fillMaxWidth(),minLines=3)
       Spacer(Modifier.height(8.dp))
       Button(onClick={
        if(customNote.isNotBlank()){
         val obj=JSONObject(item.original)
         val arr=obj.optJSONArray("personalNotes")?:JSONArray()
         arr.put(JSONObject().put("text",customNote))
         obj.put("personalNotes",arr)
         val all=JSONObject(library);val data=all.getJSONArray("entries")
         for(i in 0 until data.length()){if(StudyLanguage.lessonKey(data.getJSONObject(i))==item.id){data.put(i,obj);break}}
         library=all.toString();save(library);entries=parseEntries(library);selected=entries.find{it.id==item.id};customNote=""
        }
       }){Text(ui("Save note"))}
      }
     }
    }
    }
   }else{
    when(page){
     0->LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      item{Text(ui("New search"),fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Begin with your context, then explore natural usage elsewhere.",color=Muted,fontSize=14.sp,lineHeight=21.sp)}
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
       OutlinedButton(onClick={fields=fields+StudyQuery()},enabled=fields.size<5,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(" "+ui("Add term"))}
       Spacer(Modifier.height(8.dp))
       OutlinedButton(onClick={page=4},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.PhotoCamera,null);Text(" "+ui("Capture from book"))}
       Spacer(Modifier.height(8.dp))
       Spacer(Modifier.height(16.dp))
       Button(onClick={clipboard.setText(AnnotatedString(studyPrompt(fields.filter{it.term.isNotBlank()},studyLanguage)));error="Prompt copied to clipboard."},enabled=fields.any{it.term.isNotBlank()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.ContentCopy,null);Text(" "+ui("Copy study prompt"))}
       Spacer(Modifier.height(8.dp))
       OutlinedButton(onClick={importSource="paste";jsonText="";showPaste=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DataObject,null);Text(" "+ui("Import AI response"))}
      }
     }
     2->key(practiceLanguage){StudyshPractice(library,readProgress,saveProgress,practiceLanguage,{practiceLanguage=it},sounds)}
     1->LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      item{
       Row(verticalAlignment=Alignment.CenterVertically){
        Text(ui("Welcome to Studysh"),fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
       }
       Text("Understand words deeply. Remember them naturally.",fontSize=14.sp,color=Muted)
      }
      item{
       Surface(color=Color(0xFFF1EAFB),shape=RoundedCornerShape(24.dp)){
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
         Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
          Text(ui("Your learning space"),fontSize=21.sp,fontWeight=FontWeight.Bold)
          Text("Read your lessons, then strengthen your memory with practice.",fontSize=14.sp,color=Muted,lineHeight=21.sp)
         }
         StudyshMascot(Modifier.size(100.dp))
        }
       }
      }
      item{
       Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(onClick={page=3},modifier=Modifier.weight(1f),color=Color(0xFFEAF1FB),shape=RoundedCornerShape(20.dp)){
         Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
          Icon(Icons.Outlined.MenuBook,null,tint=Color(0xFF617AA1))
          Text("${entries.size}",fontSize=27.sp,fontWeight=FontWeight.Bold)
          Text(ui("Lessons"),fontSize=13.sp,color=Muted)
         }
        }
        Surface(onClick={page=2},modifier=Modifier.weight(1f),color=Color(0xFFE7F5EE),shape=RoundedCornerShape(20.dp)){
         Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
          Icon(Icons.Outlined.Extension,null,tint=Color(0xFF648C75))
          Text(ui("Practice"),fontSize=19.sp,fontWeight=FontWeight.Bold)
          Text("Review your skills",fontSize=13.sp,color=Muted)
         }
        }
       }
      }
      item{
       Text("Recent lessons",fontSize=19.sp,fontWeight=FontWeight.Bold)
       if(entries.isEmpty())Text("Your lessons will appear here after importing a study response.",fontSize=14.sp,color=Muted)
       else entries.takeLast(4).reversed().forEach{entry->
        Surface(onClick={selected=entry},color=Color.White,shape=RoundedCornerShape(16.dp),
         border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFEAE5EE))){
         Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
          Column(Modifier.weight(1f)){
           StudyText(entry.term,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
           Text(entry.pronunciation,fontSize=12.sp,color=Muted)
          }
          Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
         }
        }
        Spacer(Modifier.height(8.dp))
       }
      }
     }
     else->LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
      item{
       Row(verticalAlignment=Alignment.CenterVertically){Text(ui("Lessons"),fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))}
       Spacer(Modifier.height(10.dp))
       Surface(color=Color(0xFFF1EAFB),shape=RoundedCornerShape(22.dp)){
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
         Column(Modifier.weight(1f)){
          Text(ui("Your vocabulary library"),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
          Text("${entries.size} saved lessons",fontSize=12.sp,color=Muted)
         }
         StudyshMascot(Modifier.size(78.dp))
        }
       }
       Spacer(Modifier.height(8.dp))
       OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text(ui("Search your terms"))},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(22.dp))
       Spacer(Modifier.height(10.dp))
       Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("All","English","Japanese","Spanish","Portuguese","Other").forEach{f->
        val active=filter==f
        Surface(onClick={filter=f},color=if(active)Color(0xFF956DCE) else Color.White,shape=RoundedCornerShape(50),border=if(active)null else androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFE6E0EA))){
         Text(ui(f),Modifier.padding(horizontal=12.dp,vertical=9.dp),fontSize=12.sp,color=if(active)Color.White else Ink)
        }
       }}
      }
      val visible=entries.filter{it.term.contains(search,true)}.filter{entry->
       val language=entry.language
       filter=="All" || (filter=="Other" && language !in listOf("English","Japanese","Spanish","Portuguese")) || language.equals(filter,true)
      }
      items(visible.size){i->
       val entry=visible[i]
       Row(Modifier.fillMaxWidth().background(Pastels[i%Pastels.size],RoundedCornerShape(22.dp)).clickable{selected=entry}.padding(if(compact)14.dp else 23.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Outlined.BookmarkBorder,null,modifier=Modifier.size(25.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)){StudyText(entry.term,fontSize=if(compact)19.sp else 23.sp,fontWeight=FontWeight.Bold);Text(entry.pronunciation.ifBlank{"${entry.notes.size} explanation sections"},fontSize=13.sp,color=Muted,maxLines=1)}
        Surface(color=Color.White.copy(alpha=.65f),shape=RoundedCornerShape(50)){Text(when(entry.language.lowercase()){"japanese"->"JA";"spanish"->"ES";"portuguese"->"PT";else->"EN"},Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=11.sp,color=Muted)}
       }
      }
      if(visible.isEmpty())item{Text(if(entries.isEmpty())"Your library is empty. Add your first study term." else "No matching terms.",color=Muted,modifier=Modifier.padding(top=16.dp))}
     }
    }
   }
   }
   NavigationBar(containerColor=Color(0xFFFAF7FD),tonalElevation=0.dp){
    NavigationBarItem(selected=page==1 && selected==null,onClick={selected=null;page=1;piper.stop()},modifier=Modifier.testTag("nav-home"),icon={Icon(Icons.Outlined.Home,null)},label={Text(ui("Home"))})
    NavigationBarItem(selected=page==0 && selected==null,onClick={selected=null;page=0;piper.stop()},modifier=Modifier.testTag("nav-create"),icon={Icon(Icons.Outlined.AddCircleOutline,null)},label={Text(ui("Create"))})
    NavigationBarItem(selected=page==3 || selected!=null,onClick={selected=null;page=3;piper.stop()},modifier=Modifier.testTag("nav-lessons"),icon={Icon(Icons.Outlined.MenuBook,null)},label={Text(ui("Lessons"))})
    NavigationBarItem(selected=page==2 && selected==null,onClick={selected=null;page=2;piper.stop()},modifier=Modifier.testTag("nav-practice"),icon={Icon(Icons.Outlined.Extension,null)},label={Text(ui("Practice"))})
   }
   if(error.isNotBlank())Text(error,modifier=Modifier.padding(horizontal=20.dp, vertical=5.dp),color=Muted,fontSize=12.sp,maxLines=4,overflow=TextOverflow.Ellipsis)
  }
  when(settingsWindow){
   SettingsWindow.Settings->StudyshSettings(appLanguage,studyLanguage,compact,sounds,
    onAppLanguage={appLanguage=it;StudyshI18n.language=it;saveAppLanguage(it)},
    onStudyLanguage={studyLanguage=it;practiceLanguage=it;saveLanguage(it)},onCompact={compact=it},
    onVoices={settingsWindow=SettingsWindow.Voices},onClose={settingsWindow=null})
   SettingsWindow.Voices->StudyshWindow(onDismiss={settingsWindow=SettingsWindow.Settings}){
    PiperVoiceSettings(piper,onBack={settingsWindow=SettingsWindow.Settings})
   }
   null->Unit
  }
  pendingExample?.let{(term,sentence)->
   AlertDialog(
    onDismissRequest={pendingExample=null},
    title={Text("Create study prompt")},
    text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
     Text("Term: $term",fontWeight=FontWeight.SemiBold)
     Text("Original sentence: $sentence",color=Muted)
     if(fields.count{it.term.isNotBlank()}>=5)Text("Your prompt already has five terms. You can replace an empty slot or copy the current prompt first.",color=Color(0xFF9C5C39))
    }},
    confirmButton={TextButton(onClick={
     val study=StudyQuery(term=term,sentence=sentence)
     val nonempty=fields.filter{it.term.isNotBlank()}
     if(nonempty.size<5){
      fields=nonempty+study
      pendingExample=null;selected=null;page=0
      error="Added \"$term\" with its original sentence. Review your prompt."
     }else error="The prompt supports up to five terms. Remove one before adding another."
    },enabled=fields.count{it.term.isNotBlank()}<5){Text("Add to prompt")}},
    dismissButton={TextButton(onClick={pendingExample=null}){Text(ui("Cancel"))}}
   )
  }
  if(deleteEntry && selected!=null)AlertDialog(
   onDismissRequest={deleteEntry=false},
   title={Text("Delete this term?")},
   text={Text("Remove \"${selected?.term}\" and its lesson from the library? This cannot be undone. Its practice history will also be removed. Export a backup first if you want to keep it.")},
   confirmButton={TextButton(onClick={
    val removed=selected!!
    val term=removed.term
    val uniqueLegacy=entries.none{it.id!=removed.id && it.term.equals(term,true)}
    val arr=JSONArray()
    entries.filterNot{it.id==removed.id}.forEach{arr.put(JSONObject(it.original))}
    library=JSONObject().put("schemaVersion","3.0").put("entries",arr).toString()
    save(library);entries=parseEntries(library)
    val history=runCatching{JSONObject(readProgress())}.getOrElse{JSONObject()}
    val keys=history.keys().asSequence().filter{it.startsWith(StudyLanguage.progressPrefix(removed.language,term)) || (uniqueLegacy && it.startsWith(term.lowercase()+":"))}.toList()
    keys.forEach{history.remove(it)};saveProgress(history.toString())
    selected=null;deleteEntry=false
   }){Text(ui("Delete"),color=Color(0xFFAE5A68))}},
   dismissButton={TextButton(onClick={deleteEntry=false}){Text(ui("Cancel"))}}
  )
  if(editEntry && selected!=null)AlertDialog(
   onDismissRequest={editEntry=false},
   title={Text(ui("Edit term"))},
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
     val editedLanguage=StudyLanguage.canonical(edited.optString("language",old.language))
     edited.put("language",editedLanguage)
     val editedId=StudyLanguage.lessonKey(editedLanguage,term)
     val uniqueLegacy=entries.none{it.id!=old.id && it.term.equals(old.term,true)}
     require(entries.none{it.id==editedId && it.id!=old.id}){"Another lesson in this language already has this name."}
     require(edited.optJSONArray("blocks")!=null || edited.optJSONArray("notes")!=null){"The lesson needs blocks or notes."}
     val updated=JSONArray()
     entries.forEach{entry->updated.put(if(entry.id==old.id)edited else JSONObject(entry.original))}
     library=JSONObject().put("schemaVersion","3.0").put("entries",updated).toString()
     save(library);entries=parseEntries(library)
     if(editedId!=old.id){
      val history=runCatching{JSONObject(readProgress())}.getOrElse{JSONObject()}
      val oldPrefix=StudyLanguage.progressPrefix(old.language,old.term)
      val legacyPrefix=old.term.lowercase()+":"
      val newPrefix=StudyLanguage.progressPrefix(editedLanguage,term)
      val keys=history.keys().asSequence().filter{it.startsWith(oldPrefix) || (uniqueLegacy && it.startsWith(legacyPrefix))}.toList()
      keys.forEach{key->val value=history.optJSONObject(key);if(value!=null){val suffix=key.removePrefix(if(key.startsWith(oldPrefix))oldPrefix else legacyPrefix);history.put(newPrefix+suffix,value);history.remove(key)}}
      saveProgress(history.toString())
     }
     selected=entries.find{it.id==editedId};editEntry=false
    }catch(e:Exception){editorError=e.message?:"Invalid JSON"}
   }){Text("Save")}},
   dismissButton={TextButton(onClick={editEntry=false}){Text(ui("Cancel"))}}
  )
  if(editingPersonalNote!=null && selected!=null) AlertDialog(
   onDismissRequest={editingPersonalNote=null},
   title={Text(ui("Edit note"))},
   text={OutlinedTextField(value=personalNoteDraft,onValueChange={personalNoteDraft=it},modifier=Modifier.fillMaxWidth(),minLines=4)},
   confirmButton={TextButton(onClick={
    val item=selected!!
    val index=editingPersonalNote!!
    val all=JSONObject(library)
    val data=all.getJSONArray("entries")
    for(i in 0 until data.length()){
     val obj=data.getJSONObject(i)
     if(StudyLanguage.lessonKey(obj)==item.id){
      val notes=obj.optJSONArray("personalNotes")?:JSONArray()
      if(index in 0 until notes.length()){
       val old=notes.optJSONObject(index)?:JSONObject()
       old.put("text",personalNoteDraft.trim())
       notes.put(index,old)
       obj.put("personalNotes",notes)
       data.put(i,obj)
      }
      break
     }
    }
    library=all.toString();save(library);entries=parseEntries(library)
    selected=entries.find{it.id==item.id};editingPersonalNote=null
   },enabled=personalNoteDraft.isNotBlank()){Text(ui("Save changes"))}},
   dismissButton={TextButton(onClick={editingPersonalNote=null}){Text(ui("Cancel"))}}
  )
  if(deletePersonalNote!=null && selected!=null) AlertDialog(
   onDismissRequest={deletePersonalNote=null},
   title={Text("Delete note?")},
   text={Text("This personal note will be permanently deleted.")},
   confirmButton={TextButton(onClick={
    val item=selected!!
    val index=deletePersonalNote!!
    val all=JSONObject(library)
    val data=all.getJSONArray("entries")
    for(i in 0 until data.length()){
     val obj=data.getJSONObject(i)
     if(StudyLanguage.lessonKey(obj)==item.id){
      val notes=obj.optJSONArray("personalNotes")?:JSONArray()
      val remaining=JSONArray()
      for(j in 0 until notes.length())if(j!=index)remaining.put(notes.get(j))
      obj.put("personalNotes",remaining)
      data.put(i,obj)
      break
     }
    }
    library=all.toString();save(library);entries=parseEntries(library)
    selected=entries.find{it.id==item.id};deletePersonalNote=null
   }){Text(ui("Delete"))}},
   dismissButton={TextButton(onClick={deletePersonalNote=null}){Text(ui("Cancel"))}}
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
  },confirmButton={TextButton(onClick={merge(jsonText)},enabled=jsonText.isNotBlank()){Text("Import")}},dismissButton={TextButton(onClick={showPaste=false}){Text(ui("Cancel"))}})

 }
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
@Composable private fun StudyNoteCard(note:Note,index:Int,onSpeak:(String)->Unit={},onStudy:(String,String)->Unit={_,_->}){
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
   StudyText(note.title,fontSize=17.sp,fontWeight=FontWeight.Bold,lineHeight=23.sp)
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
         StudyText(markdown(line.trimStart().drop(2)),fontSize=15.sp,lineHeight=23.sp,color=Ink,modifier=Modifier.weight(1f))
        }
       }
      }
     }else StudyText(markdown(paragraph),fontSize=15.sp,lineHeight=24.sp,color=Ink)
    }
   }
  }
  note.examples.forEachIndexed { i,example ->
   if(i>0)HorizontalDivider(color=Color(0x1C5D6064),thickness=1.dp)
   Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     SelectableExampleSentence(readableText(example.first),Modifier.weight(1f),onStudy)
     IconButton(onClick={onSpeak(readableText(example.first))}){Icon(Icons.Outlined.VolumeUp,"Speak example")}
    }
    if(example.second.isNotBlank())StudyText(readableText(example.second),fontSize=14.sp,lineHeight=21.sp,color=Muted)
   }
  }
 }
}

@Composable private fun SelectableExampleSentence(sentence:String,modifier:Modifier=Modifier,onStudy:(String,String)->Unit){
 if(LocalFurigana.current.enabled && LocalFurigana.current.japanese && JapaneseReadings.hasKanji(sentence)){
  StudyText(sentence,modifier,fontSize=16.sp,fontStyle=FontStyle.Italic,lineHeight=24.sp,selectable=true,onStudy=onStudy)
  return
 }
 var value by remember(sentence){mutableStateOf(TextFieldValue(sentence))}
 val selection=value.selection
 val selectedTerm=if(!selection.collapsed && selection.start>=0 && selection.end<=sentence.length)
  sentence.substring(selection.min,selection.max).trim() else ""
 Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)){
  BasicTextField(
   value=value,
   onValueChange={updated->value=updated.copy(annotatedString=AnnotatedString(sentence))},
   readOnly=true,
   textStyle=androidx.compose.ui.text.TextStyle(color=Ink,fontSize=16.sp,fontStyle=FontStyle.Italic,lineHeight=24.sp),
   modifier=Modifier.fillMaxWidth()
  )
  if(selectedTerm.isNotBlank()){
   Surface(onClick={onStudy(selectedTerm,sentence);value=TextFieldValue(sentence)},shape=RoundedCornerShape(12.dp),
    color=Color(0xFFDFD2F2)){
    Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
     Icon(Icons.Outlined.AddCircleOutline,null,modifier=Modifier.size(17.dp))
     Spacer(Modifier.width(7.dp))
     Text("Create prompt: $selectedTerm",fontSize=13.sp,maxLines=2)
    }
   }
  }
 }
}
