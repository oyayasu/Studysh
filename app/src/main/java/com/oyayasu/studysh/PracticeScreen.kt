package com.oyayasu.studysh

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject

private val Lavender=Color(0xFF9672C9)
private val Pale=Color(0xFFF1EAFB)
private val Dark=Color(0xFF23202A)
private val Soft=Color(0xFF797480)

private data class PracticeQuestion(val key:String,val term:String,val type:String,val instruction:String,
 val sentence:String,val prompt:String,val choices:List<String>,val answer:String,val explanation:String,val skill:String)

private fun JSONArray.asStrings():List<String> = (0 until length()).map{optString(it)}
private fun questionsFromLibrary(raw:String):List<PracticeQuestion> {
 val root=runCatching{JSONObject(raw)}.getOrNull()?:return emptyList()
 val entries=root.optJSONArray("entries")?:return emptyList()
 val questions=mutableListOf<PracticeQuestion>()
 for(i in 0 until entries.length()){
  val entry=entries.optJSONObject(i)?:continue
  val term=entry.optString("term")
  val arr=entry.optJSONArray("exercises")?:continue
  for(j in 0 until arr.length()){
   val ex=arr.optJSONObject(j)?:continue
   val type=ex.optString("type")
   if(type !in listOf("complete","build","choice"))continue
   val options=(if(type=="build")ex.optJSONArray("tiles") else ex.optJSONArray("options"))?.asStrings().orEmpty()
   val answer=ex.optString("answer")
   if(options.isEmpty() || answer.isBlank())continue
   if(type!="build" && answer !in options)continue
   questions.add(PracticeQuestion(
    key=term.lowercase()+":"+ex.optString("skillId").ifBlank{ex.optString("id","question-$j")},term=term,type=type,
    instruction=ex.optString("instruction",if(type=="build")"Build the sentence" else "Complete the sentence"),
    sentence=ex.optString("sentence"),prompt=ex.optString("ptPrompt"),
    choices=options,answer=answer,explanation=ex.optString("explanation"),skill=ex.optString("skillId")
   ))
  }
 }
 return questions
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StudyshPractice(library:String,readProgress:()->String,saveProgress:(String)->Unit) {
 val questions=remember(library){questionsFromLibrary(library)}
 val now=System.currentTimeMillis()
 var progressRaw by remember { mutableStateOf(readProgress()) }
 val progress=remember(progressRaw){runCatching{JSONObject(progressRaw)}.getOrElse{JSONObject()}}
 val due=questions.groupBy{it.key}.values.mapNotNull{group->
  val card=progress.optJSONObject(group.first().key)
  if(StudyFsrs.dueAt(card)>now)null
  else group[(card?.optInt("attempts",0)?:0)%group.size]
 }
 val practiced=questions.map{it.key}.distinct().count{progress.has(it)}
 var active by remember { mutableStateOf(false) }
 var cursor by remember { mutableIntStateOf(0) }
 var choice by remember { mutableStateOf("") }
 var tiles by remember { mutableStateOf(listOf<Int>()) }
 var checked by remember { mutableStateOf(false) }
 var reviewed by remember { mutableIntStateOf(0) }
 var correctCount by remember { mutableIntStateOf(0) }
 var suggestedRating by remember { mutableIntStateOf(3) }
 var chosenRating by remember { mutableIntStateOf(3) }
 var exerciseStartedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
 val session=remember(active,library){if(active)due else emptyList()}
 val current=session.getOrNull(cursor)
 fun resetAnswer(){choice="";tiles=emptyList();checked=false;exerciseStartedAt=System.currentTimeMillis()}
 fun record(correct:Boolean,q:PracticeQuestion,rating:Int){
  val previous=StudyFsrs.migrateLegacy(progress.optJSONObject(q.key))
  val next=StudyFsrs.review(previous,rating)
  progress.put(q.key,next)
  progressRaw=progress.toString()
  saveProgress(progressRaw)
  reviewed++
  if(correct)correctCount++
 }
 Column(Modifier.fillMaxSize().background(Color(0xFFFCFBFE))) {
  if(!active){
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
    item {
     Text("Practice",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Dark)
     Spacer(Modifier.height(5.dp))
     Text("Build confidence with the words you've studied.",color=Soft,fontSize=14.sp)
    }
    item {
     Surface(color=Pale,shape=RoundedCornerShape(26.dp)){
      Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Little by little.",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Dark)
        Text("Understand the pattern. Then use it naturally.",fontSize=14.sp,lineHeight=21.sp,color=Soft)
       }
       Box(Modifier.size(78.dp).background(Color(0xFFD5B7F6),RoundedCornerShape(46.dp)),contentAlignment=Alignment.Center){
        Icon(Icons.Outlined.AutoAwesome,null,tint=Color(0xFF6B439D),modifier=Modifier.size(34.dp))
       }
      }
     }
    }
    item {
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
      PracticeStat("${due.size}","Due now",Modifier.weight(1f))
      PracticeStat("${questions.map{it.key}.distinct().size}","Skills",Modifier.weight(1f))
      PracticeStat("$practiced","Practiced",Modifier.weight(1f))
     }
    }
    item {
     Button(onClick={cursor=0;reviewed=0;correctCount=0;resetAnswer();active=true},
      enabled=due.isNotEmpty(),modifier=Modifier.fillMaxWidth().height(54.dp),
      shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Lavender)){
      Text(if(due.isEmpty())"You're all caught up" else "Start practice",fontWeight=FontWeight.Bold)
     }
     Spacer(Modifier.height(8.dp))
     Text(if(questions.isEmpty())"Import a Studysh v3 lesson with exercises to start practicing."
      else if(due.isEmpty())"Your next reviews will appear here when they're due."
      else "FSRS-6 schedules reviews based on how well you remember.",fontSize=13.sp,color=Soft)
    }
    if(questions.isNotEmpty())item {
     Text("Your practice terms",fontWeight=FontWeight.Bold,fontSize=18.sp)
     Spacer(Modifier.height(9.dp))
     questions.map{it.term}.distinct().forEach{term->
      Text("•  $term",modifier=Modifier.padding(vertical=5.dp),color=Dark)
     }
    }
   }
  } else if(current==null){
   Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.Center){
    Icon(Icons.Outlined.CheckCircle,null,tint=Lavender,modifier=Modifier.size(58.dp))
    Text("Session complete",fontSize=26.sp,fontWeight=FontWeight.Bold)
    Text("$correctCount of $reviewed correct on the first check.",color=Soft)
    Button(onClick={active=false},colors=ButtonDefaults.buttonColors(containerColor=Lavender)){Text("Back to Practice")}
   }
  } else {
   val q=current
   val assembled=tiles.joinToString(" "){q.choices[it]}
   val submitted=if(q.type=="build")assembled else choice
   val correct=submitted.trim()==q.answer.trim()
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
     IconButton(onClick={active=false}){Icon(Icons.Outlined.Close,"Leave session")}
     LinearProgressIndicator(progress={ (cursor+1).toFloat()/session.size },modifier=Modifier.weight(1f).height(8.dp),color=Lavender,trackColor=Color(0xFFEAE6EF))
     Text("${cursor+1}/${session.size}",fontSize=12.sp,color=Soft)
    }
    Text(q.term.uppercase(),color=Lavender,fontSize=12.sp,fontWeight=FontWeight.Bold)
    Text(q.instruction,fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=31.sp,color=Dark)
    if(q.prompt.isNotBlank())Text(q.prompt,fontSize=16.sp,lineHeight=24.sp,color=Soft)
    if(q.sentence.isNotBlank())Surface(color=Pale,shape=RoundedCornerShape(20.dp)){
     Text(q.sentence,Modifier.fillMaxWidth().padding(18.dp),fontSize=19.sp,lineHeight=28.sp,color=Dark)
    }
    if(q.type=="build"){
     Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFE7E0EC))){
      Column(Modifier.fillMaxWidth().heightIn(min=90.dp).padding(12.dp)){
       if(tiles.isEmpty())Text("Tap the tiles to build your sentence.",color=Soft,fontSize=14.sp)
       else FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        tiles.forEach{index->PracticeTile(q.choices[index],true,!checked){tiles=tiles.filter{it!=index}}}
       }
      }
     }
     FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
      q.choices.forEachIndexed{index,tile->
       if(index !in tiles)PracticeTile(tile,false,!checked){tiles=tiles+index}
      }
     }
    } else {
     FlowRow(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
      q.choices.forEach{option->PracticeTile(option,choice==option,!checked){choice=option}}
     }
    }
    Spacer(Modifier.height(16.dp))
    if(checked){
     Surface(color=if(correct)Color(0xFFE7F5ED) else Color(0xFFFFF1E5),shape=RoundedCornerShape(18.dp)){
      Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
       Text(if(correct)"Correct!" else "Let's review",fontWeight=FontWeight.Bold,fontSize=19.sp)
       if(!correct)Text("Answer: ${q.answer}",fontWeight=FontWeight.SemiBold)
       if(q.explanation.isNotBlank())Text(q.explanation,fontSize=14.sp,lineHeight=22.sp)
      }
     }
     Text("How well did you remember?",fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=Dark)
     Text(if(correct)"Suggested: Good. Choose Hard if you struggled or Easy if it felt effortless." else "Incorrect answers are scheduled as Again.",fontSize=12.sp,color=Soft)
     Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
      listOf("Again","Hard","Good","Easy").forEachIndexed{index,label->
       val rating=index+1
       Surface(onClick={chosenRating=rating},enabled=correct || rating==1,modifier=Modifier.weight(1f),
        color=if(chosenRating==rating)Pale else if(!correct && rating!=1)Color(0xFFF3F2F4) else Color.White,
        shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(chosenRating==rating)Lavender else Color(0xFFE3DFE8))){
        Text(label,modifier=Modifier.padding(vertical=12.dp),fontSize=12.sp,fontWeight=if(chosenRating==rating)FontWeight.Bold else FontWeight.Normal,color=Dark,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
       }
      }
     }
     Button(onClick={record(correct,q,chosenRating);cursor++;resetAnswer()},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Lavender)){Text("Continue",fontWeight=FontWeight.Bold)}
    }else{
     Button(onClick={suggestedRating=if(!correct)1 else if(System.currentTimeMillis()-exerciseStartedAt>45_000L)2 else 3;chosenRating=suggestedRating;checked=true},enabled=if(q.type=="build")tiles.isNotEmpty() else choice.isNotBlank(),modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Lavender)){Text("Check",fontWeight=FontWeight.Bold)}
    }
   }
  }
 }
}

@Composable private fun PracticeStat(value:String,label:String,modifier:Modifier=Modifier){
 Surface(modifier=modifier,color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFEDE8F1))){
  Column(Modifier.padding(vertical=16.dp,horizontal=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
   Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Dark)
   Text(label,fontSize=11.sp,color=Soft)
  }
 }
}
@Composable private fun PracticeTile(text:String,selected:Boolean,enabled:Boolean,onClick:()->Unit){
 Surface(onClick=onClick,enabled=enabled,color=if(selected)Color(0xFFE4D6F5) else Color.White,
  shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,if(selected)Lavender else Color(0xFFE0DEE3))){
  Text(text,Modifier.padding(horizontal=15.dp,vertical=12.dp),fontSize=15.sp,color=Dark)
 }
}
