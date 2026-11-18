package com.oyayasu.studysh

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads the actual Supertonic 3 ONNX model and F1/F2 voice-style profiles.
 * This manages model assets only; inference must be provided by a compatible ONNX engine.
 */
class SupertonicModels(context:Context) {
 private val root=File(context.filesDir,"supertonic-3").apply{mkdirs()}
 private val executor=Executors.newSingleThreadExecutor()
 private val revision="aafc6e32416a594460b32413efc49d7fe4ce6d46"
 private val base="https://huggingface.co/supertone-oss-archive/supertonic-3/resolve/$revision"
 private val files=linkedMapOf(
  "onnx/duration_predictor.onnx" to 3700147L,
  "onnx/text_encoder.onnx" to 36416150L,
  "onnx/vector_estimator.onnx" to 256534781L,
  "onnx/vocoder.onnx" to 101424195L,
  "onnx/tts.json" to 8253L,
  "onnx/unicode_indexer.json" to 277676L,
  "voice_styles/F1.json" to 292046L,
  "voice_styles/F2.json" to 292423L
 )
 var busy by mutableStateOf(false);private set
 var progress by mutableFloatStateOf(0f);private set
 var status by mutableStateOf("");private set
 var revisionState by mutableIntStateOf(0);private set
 fun installed():Boolean{
  revisionState
  return files.all{(name,size)->File(root,name).let{it.isFile && it.length()==size}}
 }
 fun modelDirectory():File?=root.takeIf{installed()}
 fun install(){
  if(busy || installed())return
  busy=true;progress=0f;status="Downloading Supertonic 3…"
  executor.execute {
   try{
    val total=files.values.sum()
    var completed=0L
    files.forEach{(name,size)->
     val target=File(root,name)
     if(target.isFile && target.length()==size){completed+=size;progress=completed.toFloat()/total;return@forEach}
     target.parentFile?.mkdirs()
     val part=File(target.absolutePath+".part")
     part.delete()
     val connection=URL("$base/$name?download=true").openConnection() as HttpURLConnection
     connection.connectTimeout=30000;connection.readTimeout=90000
     try{
      if(connection.responseCode !in 200..299)error("HTTP ${connection.responseCode}: $name")
      connection.inputStream.use{input->
       part.outputStream().buffered().use{out->
        val buffer=ByteArray(65536)
        while(true){
         val count=input.read(buffer)
         if(count<0)break
         out.write(buffer,0,count)
         progress=((completed+part.length()).toDouble()/total).toFloat().coerceIn(0f,1f)
        }
       }
      }
     }finally{connection.disconnect()}
     if(part.length()!=size)error("Incomplete asset: $name (${part.length()} / $size)")
     if(!part.renameTo(target))error("Cannot save $name")
     completed+=size
     progress=completed.toFloat()/total
    }
    for(name in listOf("voice_styles/F1.json","voice_styles/F2.json")){
     if(JSONObject(File(root,name).readText()).length()==0)error("Invalid voice style $name")
    }
    revisionState++
    status="Supertonic 3 models downloaded. Speech engine integration is still required."
   }catch(e:Exception){status="Supertonic download failed: ${e.localizedMessage}"}
   finally{busy=false}
  }
 }
}
