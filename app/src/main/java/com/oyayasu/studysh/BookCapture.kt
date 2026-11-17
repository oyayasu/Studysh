package com.oyayasu.studysh

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.min

private data class BookWord(val value:String,val box:android.graphics.Rect)
private fun photoUri(context:android.content.Context):Uri {
 val folder=File(context.cacheDir,"book_photos").apply{mkdirs()}
 val file=File.createTempFile("studysh_",".jpg",folder)
 return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
}
private fun loadBookBitmap(context:android.content.Context,uri:Uri):Bitmap? =
 context.contentResolver.openInputStream(uri)?.use{stream->
  val bytes=stream.readBytes()
  val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
  val sample=generateSequence(1){it*2}.takeWhile{
   options.outWidth/it>2300 || options.outHeight/it>2300
  }.lastOrNull()?:1
  BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
 }

@Composable
fun BookCapture(onClose:()->Unit,onAdd:(List<StudyQuery>)->Unit){
 val context=LocalContext.current
 var bitmap by remember{mutableStateOf<Bitmap?>(null)}
 var words by remember{mutableStateOf<List<BookWord>>(emptyList())}
 var pendingUri by remember{mutableStateOf<Uri?>(null)}
 var busy by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 var phase by remember{mutableIntStateOf(0)}
 var contextText by remember{mutableStateOf("")}
 var editText by remember{mutableStateOf("")}
 var contextDraft by remember{mutableStateOf("")}
 var termDraft by remember{mutableStateOf("")}
 var captured by remember{mutableStateOf<List<StudyQuery>>(emptyList())}
 var selected by remember{mutableStateOf<Set<Int>>(emptySet())}
 var strokes by remember{mutableStateOf<List<List<Offset>>>(emptyList())}
 var currentStroke by remember{mutableStateOf<List<Offset>>(emptyList())}
 var viewWidth by remember{mutableFloatStateOf(1f)}
 var viewHeight by remember{mutableFloatStateOf(1f)}
 val recognizer=remember{TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
 DisposableEffect(recognizer){onDispose{recognizer.close()}}
 fun recognize(uri:Uri){
  error="";busy=true;selected=emptySet();strokes=emptyList();currentStroke=emptyList();phase=0
  val loaded=runCatching{loadBookBitmap(context,uri)}.getOrNull()
  if(loaded==null){busy=false;error="Unable to open this image.";return}
  bitmap=loaded
  recognizer.process(InputImage.fromBitmap(loaded,0))
   .addOnSuccessListener{result->
    words=result.textBlocks.flatMap{it.lines}.flatMap{it.elements}.mapNotNull{element->
     element.boundingBox?.let{BookWord(element.text,it)}
    }.sortedWith(compareBy<BookWord>{it.box.centerY()/28}.thenBy{it.box.left})
    busy=false
    if(words.isEmpty())error="No printed text found. Try a sharper, well-lit photo."
   }.addOnFailureListener{e->busy=false;error="Text recognition failed: ${e.localizedMessage}"}
 }
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
  if(ok)pendingUri?.let{recognize(it)} else error="Camera capture cancelled."
 }
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
  if(uri!=null)recognize(uri)
 }
 fun collectText():String {
  if(selected.isEmpty())return ""
  val indices=selected.sortedWith(compareBy<Int>{words[it].box.top}.thenBy{words[it].box.left})
  val lines=mutableListOf<MutableList<Int>>()
  indices.forEach{i->
   val line=lines.lastOrNull()
   if(line==null || kotlin.math.abs(words[line.first()].box.centerY()-words[i].box.centerY())>words[i].box.height()*.7f)
    lines.add(mutableListOf(i))
   else line.add(i)
  }
  return lines.joinToString(" "){line->line.sortedBy{words[it].box.left}.joinToString(" "){words[it].value}}.trim()
 }
 fun confirm(){
  val text=editText.trim()
  if(text.isBlank()){error="Select or enter some text first.";return}
  error="";selected=emptySet();strokes=emptyList();currentStroke=emptyList()
  if(phase==0){contextDraft=text;phase=1}
  else {
   termDraft=text
   captured=captured+StudyQuery(term=text,sentence=contextDraft)
   phase=0;contextDraft="";termDraft=""
  }
  editText=""
 }
 Column(Modifier.fillMaxSize().background(Color(0xFFFCFBFE)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"Close capture")}
   Text("Book Capture",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
   Text("${captured.size} saved",fontSize=12.sp,color=Color.Gray)
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={
    runCatching{photoUri(context)}.onSuccess{pendingUri=it;camera.launch(it)}
     .onFailure{error="Camera unavailable: ${it.localizedMessage}"}
   }){Icon(Icons.Outlined.PhotoCamera,null);Text(" Camera")}
   OutlinedButton(onClick={gallery.launch("image/*")}){Icon(Icons.Outlined.Image,null);Text(" Gallery")}
  }
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
  Text(if(phase==0)"1 · Paint the complete sentence" else "2 · Paint the word or expression",fontWeight=FontWeight.SemiBold)
  Text("Paint across one or more lines, then confirm. Each pair creates one study request.",fontSize=12.sp,color=Color.Gray)
  val b=bitmap
  if(b!=null){
   BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)){
    val containerW=constraints.maxWidth.toFloat()
    val containerH=constraints.maxHeight.toFloat()
    val scale=min(containerW/b.width,containerH/b.height)
    val imageW=b.width*scale
    val imageH=b.height*scale
    viewWidth=imageW;viewHeight=imageH
    Box(Modifier.align(Alignment.Center).size(with(androidx.compose.ui.platform.LocalDensity.current){imageW.toDp()},with(androidx.compose.ui.platform.LocalDensity.current){imageH.toDp()})){
     Image(bitmap=b.asImageBitmap(),contentDescription="Book page",modifier=Modifier.fillMaxSize())
     Canvas(Modifier.fillMaxSize().pointerInput(words,phase,scale){
      detectDragGestures(onDragStart={point->currentStroke=listOf(point)},onDragEnd={
       if(currentStroke.isNotEmpty()){
        val stroke=currentStroke
        val newly=words.indices.filter{i->
         val box=words[i].box
         stroke.any{p->
          val x=p.x/scale;val y=p.y/scale
          x>=box.left-12 && x<=box.right+12 && y>=box.top-12 && y<=box.bottom+12
         }
        }.toSet()
        selected=selected+newly
        strokes=strokes+listOf(stroke)
       }
       currentStroke=emptyList()
      },onDragCancel={currentStroke=emptyList()},onDrag={change,_->
       currentStroke=currentStroke+change.position
       change.consume()
      })
     }){
      val ink=if(phase==0)Color(0xFFE3C354) else Color(0xFFB69BE9)
      selected.forEach{i->
       val r=words[i].box
       drawRect(ink.copy(alpha=.38f),topLeft=Offset(r.left*scale,r.top*scale),
        size=androidx.compose.ui.geometry.Size(r.width()*scale,r.height()*scale))
      }
      (strokes+listOf(currentStroke)).forEach{points->
       if(points.isNotEmpty()){
        val path=Path().apply{moveTo(points.first().x,points.first().y);points.drop(1).forEach{lineTo(it.x,it.y)}}
        drawPath(path,ink.copy(alpha=.43f),style=Stroke(width=24.dp.toPx(),cap=StrokeCap.Round))
       }
      }
     }
    }
   }
  }else{
   Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF1EAFB)),contentAlignment=Alignment.Center){
    Text("Take a photo of a printed book page to begin.",modifier=Modifier.padding(20.dp))
   }
  }
  if(words.isNotEmpty()){
   Row(verticalAlignment=Alignment.CenterVertically){
    Text("${selected.size} words selected",fontSize=12.sp,modifier=Modifier.weight(1f))
    TextButton(onClick={selected=emptySet();strokes=emptyList();currentStroke=emptyList();editText=""}){Text("Clear selection")}
   }
  }
  LaunchedEffect(selected){if(selected.isNotEmpty())editText=collectText()}
  OutlinedTextField(value=editText,onValueChange={editText=it},label={Text(if(phase==0)"Context sentence" else "Term / construction")},
   modifier=Modifier.fillMaxWidth(),maxLines=3,minLines=1)
  if(phase==1)Text("Context: $contextDraft",fontSize=12.sp,maxLines=2,color=Color.Gray)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(onClick={confirm},enabled=editText.isNotBlank(),modifier=Modifier.weight(1f)){Text(if(phase==0)"Confirm context" else "Add study")}
   if(captured.isNotEmpty())Button(onClick={onAdd(captured)},modifier=Modifier.weight(1f)){Text("Use ${captured.size} studies")}
  }
 }
}
