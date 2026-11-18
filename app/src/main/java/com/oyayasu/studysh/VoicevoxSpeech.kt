package com.oyayasu.studysh

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * VOICEVOX Engine HTTP adapter. The engine runs on a computer on the same network.
 * This is not Android-native inference and does not contact a public TTS service.
 * Speaker 2: Shikoku Metan, normal style (female Japanese voice).
 */
class VoicevoxSpeech(private val context:Context){
 private val prefs=context.getSharedPreferences("studysh_voicevox",Context.MODE_PRIVATE)
 private val executor=Executors.newSingleThreadExecutor()
 private var player:MediaPlayer?=null
 var endpoint by mutableStateOf(prefs.getString("endpoint","")?:"");private set
 var enabled by mutableStateOf(prefs.getBoolean("enabled",false));private set
 var message by mutableStateOf("");private set
 var busy by mutableStateOf(false);private set
 fun configure(address:String,active:Boolean){
  val clean=address.trim().trimEnd('/')
  endpoint=clean;enabled=active
  prefs.edit().putString("endpoint",clean).putBoolean("enabled",active).apply()
 }
 fun speak(text:String){
  if(text.isBlank())return
  if(!enabled || endpoint.isBlank()){message="Configure your VOICEVOX Engine address first.";return}
  val root=endpoint
  if(!root.startsWith("http://") && !root.startsWith("https://")){message="Engine address must begin with http:// or https://";return}
  busy=true
  executor.execute {
   try{
    val encoded=URLEncoder.encode(text,"UTF-8")
    val queryUrl="$root/audio_query?text=$encoded&speaker=2"
    val query=(URL(queryUrl).openConnection() as HttpURLConnection).run{
     connectTimeout=10000;readTimeout=30000;requestMethod="POST"
     try{if(responseCode !in 200..299)error("Query HTTP $responseCode");inputStream.bufferedReader().use{it.readText()}}
     finally{disconnect()}
    }
    val audio=(URL("$root/synthesis?speaker=2").openConnection() as HttpURLConnection).run{
     connectTimeout=10000;readTimeout=120000;requestMethod="POST"
     setRequestProperty("Content-Type","application/json")
     doOutput=true
     try{
      outputStream.use{it.write(query.toByteArray(Charsets.UTF_8))}
      if(responseCode !in 200..299)error("Synthesis HTTP $responseCode")
      inputStream.use{it.readBytes()}
     }finally{disconnect()}
    }
    val wav=File.createTempFile("voicevox_",".wav",context.cacheDir)
    wav.writeBytes(audio)
    android.os.Handler(android.os.Looper.getMainLooper()).post {
     try{
      player?.release()
      player=MediaPlayer().apply{
       setDataSource(context,Uri.fromFile(wav))
       setOnCompletionListener{it.release();if(player===it)player=null;wav.delete()}
       prepare();start()
      }
      message="Playing Shikoku Metan (VOICEVOX)"
     }catch(e:Exception){message="Audio playback error: ${e.localizedMessage}";wav.delete()}
    }
   }catch(e:Exception){message="VOICEVOX unavailable: ${e.localizedMessage}"}
   finally{busy=false}
  }
 }
 fun stop(){player?.stop();player?.release();player=null}
 fun close(){stop();executor.shutdown()}
}
