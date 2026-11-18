package com.oyayasu.studysh

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.*
import java.util.Locale

/** Device-provided Japanese speech, independent of the Piper/Supertonic models. */
class JapaneseDeviceSpeech(context: Context) {
 private val prefs=context.getSharedPreferences("studysh_japanese_tts",Context.MODE_PRIVATE)
 private var tts:TextToSpeech?=null
 private var ready=false
 private var queued:String?=null
 var status by mutableStateOf("Initializing Japanese speech…");private set
 var voices by mutableStateOf<List<Voice>>(emptyList());private set
 var selected by mutableStateOf(prefs.getString("voice","")?:"");private set
 init {
  tts=TextToSpeech(context.applicationContext){ result->
   ready=result==TextToSpeech.SUCCESS
   if(ready){
    val engine=tts
    val available=engine?.voices.orEmpty().filter{it.locale.language=="ja"}
      .sortedWith(compareBy<Voice>({it.isNetworkConnectionRequired},{it.name}))
    voices=available
    val support=engine?.isLanguageAvailable(Locale.JAPAN)?:TextToSpeech.LANG_NOT_SUPPORTED
    status=if(support>=TextToSpeech.LANG_AVAILABLE) "Japanese speech ready" else "Japanese voice data missing. Install Japanese speech data in Android text-to-speech settings."
    queued?.let{queued=null;speak(it)}
   }else status="Android speech engine could not initialize."
  }
 }
 fun choose(name:String){selected=name;prefs.edit().putString("voice",name).apply()}
 fun speak(text:String){
  if(text.isBlank())return
  if(!ready){queued=text;return}
  val engine=tts?:return
  if(engine.isLanguageAvailable(Locale.JAPAN)<TextToSpeech.LANG_AVAILABLE){
   status="Japanese voice unavailable. Install Japanese language in the Android speech engine."
   return
  }
  engine.language=Locale.JAPAN
  val chosen=voices.firstOrNull{it.name==selected}
    ?:voices.firstOrNull{!it.isNetworkConnectionRequired}
    ?:voices.firstOrNull()
  if(chosen!=null)engine.voice=chosen
  val result=engine.speak(text.take(TextToSpeech.getMaxSpeechInputLength()),TextToSpeech.QUEUE_FLUSH,null,"studysh-ja-"+System.nanoTime())
  if(result==TextToSpeech.ERROR)status="Japanese speech could not be started."
 }
 fun stop(){tts?.stop()}
 fun close(){tts?.stop();tts?.shutdown();tts=null;ready=false}
}
