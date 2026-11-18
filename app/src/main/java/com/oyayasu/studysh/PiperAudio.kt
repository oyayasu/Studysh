package com.oyayasu.studysh

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ihorshevchuk.piper.engine.PiperCreateOptions
import dev.ihorshevchuk.piper.engine.PiperEngine
import dev.ihorshevchuk.piper.player.PiperPlayer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class PiperVoice(val id:String,val label:String,val region:String,val bytes:Long,val sha256:String?=null){
 val base:String get()=id
 private val family:String get()=region.substringBefore('_')
 private val speaker:String get()=id.removePrefix("$region-").substringBeforeLast('-')
 private val quality:String get()=id.substringAfterLast('-')
 private val directory:String get()="$family/$region/$speaker/$quality"
 val modelUrl:String get()="https://huggingface.co/rhasspy/piper-voices/resolve/main/$directory/$base.onnx?download=true"
 val configUrl:String get()="https://huggingface.co/rhasspy/piper-voices/resolve/main/$directory/$base.onnx.json?download=true"
}
val PIPER_VOICES=listOf(
 PiperVoice("en_US-libritts-high","LibriTTS · High (United States)","en_US",137_000_000L),
 PiperVoice("en_GB-cori-high","Cori · High (United Kingdom)","en_GB",114_000_000L),
 PiperVoice("pt_BR-jeff-medium","Jeff · Medium (Brasil)","pt_BR",63_221_984L),
 PiperVoice("pt_PT-tugão-medium","Tugão · Medium (Portugal)","pt_PT",63_221_984L),
 PiperVoice("es_AR-daniela-high","Daniela · High (Argentina)","es_AR",114_199_011L),
 PiperVoice("es_ES-davefx-medium","DaveFX · Medium (España)","es_ES",63_221_984L)
)
class StudyshPiper(private val context:Context){
 val japanese=JapaneseDeviceSpeech(context)
 private val preferences=context.getSharedPreferences("piper",Context.MODE_PRIVATE)
 private val worker=Executors.newSingleThreadExecutor()
 private val player=PiperPlayer()
 private var engine:PiperEngine?=null
 private var engineVoice:String?=null
 var activeVoice by mutableStateOf(preferences.getString("voice",PIPER_VOICES[0].id)?:PIPER_VOICES[0].id)
  private set
 var downloading by mutableStateOf<String?>(null);private set
 var progress by mutableFloatStateOf(0f);private set
 var message by mutableStateOf("");private set
 var revision by mutableIntStateOf(0);private set
 private val root=File(context.filesDir,"piper-voices").apply{mkdirs()}
 private fun ensureEspeakData(){
  val destination=File(context.filesDir,"espeak-ng-data")
  val marker=File(destination,".studysh-ready")
  if(marker.isFile)return
  fun copyAssets(assetPath:String,target:File){
   val children=context.assets.list(assetPath)?:emptyArray()
   if(children.isEmpty()){
    target.parentFile?.mkdirs()
    context.assets.open(assetPath).use{input->target.outputStream().use{input.copyTo(it)}}
   }else{
    target.mkdirs()
    children.forEach{copyAssets("$assetPath/$it",File(target,it))}
   }
  }
  copyAssets("espeak-ng-data",destination)
  marker.writeText("ready")
 }
 private fun model(voice:PiperVoice)=File(root,voice.base+".onnx")
 private fun config(voice:PiperVoice)=File(root,voice.base+".onnx.json")
 fun installed(voice:PiperVoice):Boolean{
  revision
  return model(voice).isFile && model(voice).length()>voice.bytes*0.8 && config(voice).isFile && config(voice).length()>500
 }
 fun select(voice:PiperVoice){
  if(!installed(voice)){message="Download this voice before selecting it.";return}
  player.stop()
  if(engineVoice!=voice.id){engine?.close();engine=null;engineVoice=null}
  activeVoice=voice.id;preferences.edit().putString("voice",voice.id).apply();message=""
 }
 fun download(voice:PiperVoice){
  if(downloading!=null)return
  downloading=voice.id;progress=0f;message=""
  worker.execute{
   val tmpModel=File(root,voice.base+".onnx.part")
   val tmpConfig=File(root,voice.base+".json.part")
   try{
    fun fetch(url:String,target:File,modelDownload:Boolean){
     val connection=URL(url).openConnection() as HttpURLConnection
     connection.connectTimeout=20000;connection.readTimeout=60000
     connection.instanceFollowRedirects=true
     try{
      if(connection.responseCode !in 200..299)error("Download HTTP ${connection.responseCode}")
      val length=connection.contentLengthLong
      connection.inputStream.use{input->
       target.outputStream().buffered().use{output->
        val buf=ByteArray(64*1024);var done=0L
        while(true){
         val n=input.read(buf);if(n<0)break
         output.write(buf,0,n);done+=n
         if(modelDownload && length>0)progress=(done.toDouble()/length).toFloat().coerceIn(0f,1f)
        }
       }
      }
     }finally{connection.disconnect()}
    }
    fetch(voice.modelUrl,tmpModel,true)
    if(tmpModel.length()<voice.bytes*0.8)error("Downloaded model is incomplete.")
    voice.sha256?.let{expected->
     val digest=MessageDigest.getInstance("SHA-256")
     tmpModel.inputStream().buffered().use{input->
      val bytes=ByteArray(65536)
      while(true){val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n)}
     }
     val actual=digest.digest().joinToString(""){"%02x".format(it)}
     if(actual!=expected)error("Model checksum mismatch.")
    }
    fetch(voice.configUrl,tmpConfig,false)
    val json=org.json.JSONObject(tmpConfig.readText())
    if(json.optJSONObject("phoneme_id_map")==null)error("Invalid Piper voice configuration.")
    if(!tmpModel.renameTo(model(voice)))error("Unable to save voice model.")
    if(!tmpConfig.renameTo(config(voice)))error("Unable to save voice configuration.")
    revision++
    message="${voice.label} is ready for offline playback."
   }catch(e:Exception){
    tmpModel.delete();tmpConfig.delete()
    message="Download failed: ${e.localizedMessage}"
   }finally{downloading=null;progress=0f}
  }
 }
 fun preview(voice:PiperVoice){
  if(!installed(voice)){message="Download this voice first.";return}
  select(voice)
  val sample=when(voice.region.substringBefore('_')){
   "pt"->"Este é um exemplo de pronúncia em português."
   "es"->"Este es un ejemplo de pronunciación en español."
   "ja"->"これは日本語の音声サンプルです。"
   else->"The artist uses overlapping lines to suggest depth."
  }
  speak(sample)
 }
 fun speak(text:String,language:String="English"){
  if(language=="Japanese"){japanese.speak(text);return}
  if(text.isBlank())return
  val voice=PIPER_VOICES.firstOrNull{it.id==activeVoice}?:PIPER_VOICES[0]
  if(!installed(voice)){message="Download ${voice.label} in Voice settings first.";return}
  worker.execute{
   try{
    if(engineVoice!=voice.id){
     ensureEspeakData()
     player.stop();engine?.close()
     engine=PiperEngine(PiperCreateOptions(modelPath=model(voice).absolutePath,
      configPath=config(voice).absolutePath,
      espeakDataPath=File(context.filesDir,"espeak-ng-data").absolutePath),context.filesDir)
     engineVoice=voice.id
    }
    player.stop()
    player.play(engine!!,text,speakerId=0)
   }catch(e:Exception){message="Piper playback failed: ${e.localizedMessage}"}
  }
 }
 fun stop(){player.stop();japanese.stop()}
 fun close(){player.stop();japanese.close();worker.execute{engine?.close();engine=null};worker.shutdown()}
}
@Composable
fun PiperVoiceSettings(piper:StudyshPiper,onBack:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 val supertonic=remember { SupertonicModels(context.applicationContext) }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
  verticalArrangement=Arrangement.spacedBy(18.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Back")}
   Text(ui("Voice settings"),style=MaterialTheme.typography.headlineSmall)
  }
  Text("Piper TTS · On-device, offline speech after downloading a voice.")
  PIPER_VOICES.groupBy{it.region.substringBefore('_')}.forEach{(family,voices)->
   Text(when(family){"pt"->"Português";"es"->"Español";"ja"->"日本語";else->"English"},style=MaterialTheme.typography.titleLarge)
   voices.forEach{voice->
   val installed=piper.installed(voice)
   ElevatedCard(Modifier.fillMaxWidth()){
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Text(voice.label,style=MaterialTheme.typography.titleMedium)
     Text(if(installed)"Installed · Offline ready" else "Download approximately ${voice.bytes/1_000_000} MB",
      style=MaterialTheme.typography.bodySmall)
     if(piper.downloading==voice.id)LinearProgressIndicator(progress={piper.progress},modifier=Modifier.fillMaxWidth())
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      if(installed){
       Button(onClick={piper.select(voice)},enabled=piper.activeVoice!=voice.id){
        Text(if(piper.activeVoice==voice.id)"Selected" else "Select")
       }
       OutlinedButton(onClick={piper.preview(voice)}){Text(ui("Preview"))}
      }else Button(onClick={piper.download(voice)},enabled=piper.downloading==null){Text(ui("Download"))}
     }
    }
   }
  }
  }
  Text("日本語 · Android speech (available now)",style=MaterialTheme.typography.titleLarge)
  Text(piper.japanese.status,style=MaterialTheme.typography.bodySmall)
  if(piper.japanese.voices.isEmpty())Text("Install Japanese voice data through your Android text-to-speech settings.",style=MaterialTheme.typography.bodySmall)
  piper.japanese.voices.forEach { voice ->
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    RadioButton(selected=piper.japanese.selected==voice.name,onClick={piper.japanese.choose(voice.name)})
    Text(voice.name+(if(voice.isNetworkConnectionRequired)" · Internet required" else " · Offline"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={piper.japanese.speak("こんにちは。日本語の発音を練習しましょう。")}){Text("Listen")}
   }
  }
  Text("日本語 · Supertonic 3 (experimental)",style=MaterialTheme.typography.titleLarge)
  Text(if(supertonic.installed())"Supertonic model files installed" else "Supertonic 3 model download · approximately 400 MB",style=MaterialTheme.typography.bodySmall)
  if(supertonic.busy)LinearProgressIndicator(progress={supertonic.progress},modifier=Modifier.fillMaxWidth())
  if(!supertonic.installed())Button(onClick={supertonic.install()},enabled=!supertonic.busy){Text("Download Supertonic 3")}
  if(supertonic.status.isNotBlank())Text(supertonic.status,style=MaterialTheme.typography.bodySmall)
  Text("Sarah and Lily use the Supertonic engine (not Piper). Model installation and offline playback require a separate engine integration.",style=MaterialTheme.typography.bodySmall)
  listOf("Sarah" to "F1","Lily" to "F2").forEach{(name,id)->
   ElevatedCard(Modifier.fillMaxWidth()){
    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){
      Text("$name · Supertonic $id",style=MaterialTheme.typography.titleMedium)
      Text(if(supertonic.installed())"Japanese · Model downloaded · Playback not yet implemented" else "Japanese · Download engine assets first",style=MaterialTheme.typography.bodySmall)
     }
     OutlinedButton(onClick={android.content.Intent(android.content.Intent.ACTION_VIEW,
      android.net.Uri.parse("https://huggingface.co/spaces/Supertone/supertonic-3")).also{
       context.startActivity(it)
      }}){Text("Listen")}
    }
   }
  }
  if(piper.message.isNotBlank())Text(piper.message,color=MaterialTheme.colorScheme.secondary)
  Text("LibriTTS High contains multiple speakers; this version uses speaker 0. Models are stored privately on your device.",style=MaterialTheme.typography.bodySmall)
 }
}
