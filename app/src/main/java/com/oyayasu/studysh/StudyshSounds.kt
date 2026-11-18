package com.oyayasu.studysh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class StudyshSounds(context: Context) {
    private val prefs = context.getSharedPreferences("studysh_ui", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val samples = ConcurrentHashMap<SoundCue, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var closed = false
    var enabled by mutableStateOf(prefs.getBoolean("sound_effects", true)); private set

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            try {
                SoundCue.entries.forEach { cue ->
                    if (closed) return@execute
                    val file = File(context.cacheDir, "studysh-cue-${cue.name}-v1.wav")
                    file.writeBytes(CartoonSound.wav(cue))
                    synchronized(lock) { if (!closed) samples[cue] = pool.load(file.path, 1) }
                }
            } catch (_: Exception) { /* Effects must never block practice or speech. */ }
        }
        worker.shutdown()
    }
    fun changeEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("sound_effects", value).apply()
        if (value) play(SoundCue.Tap)
    }
    fun play(cue: SoundCue) {
        if (!enabled || closed || audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = samples[cue] ?: return
        if (id !in ready) return
        synchronized(lock) { if (!closed) pool.play(id, .38f, .38f, 1, 0, 1f) }
    }
    fun close() = synchronized(lock) { if (!closed) { closed = true; pool.release() } }
}
