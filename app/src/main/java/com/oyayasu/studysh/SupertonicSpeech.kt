package com.oyayasu.studysh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import com.oyayasu.studysh.supertonic.JapaneseTextChunker
import com.oyayasu.studysh.supertonic.SupertonicAssets
import com.oyayasu.studysh.supertonic.SupertonicEngine
import com.oyayasu.studysh.supertonic.VoiceStyle
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Owns local Japanese synthesis and PCM playback. No network or Android TTS fallback. */
class SupertonicSpeech(context: Context) {
    val models = SupertonicModels(context)
    private val prefs = context.getSharedPreferences("studysh_supertonic", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val request = AtomicLong()
    private val audioLock = Any()
    private var track: AudioTrack? = null
    private var engine: SupertonicEngine? = null // worker-thread confined
    private var style: VoiceStyle? = null
    private var loadedVoice: String? = null
    @Volatile private var closed = false
    var activeVoice by mutableStateOf(prefs.getString("voice", "F1").takeIf { it in SupertonicAssets.voices } ?: "F1")
        private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set

    fun select(voice: String) {
        if (!models.installed(voice)) { error = "Download $voice in Voice settings first."; return }
        stop()
        activeVoice = voice
        prefs.edit().putString("voice", voice).apply()
        message = "$voice selected for Japanese lessons"; error = null
    }

    fun preview(voice: String = activeVoice) {
        select(voice)
        if (models.installed(voice)) speak("こんにちは。日本語を一緒に勉強しましょう。")
    }

    fun speak(text: String) {
        if (closed || text.isBlank()) return
        stop()
        if (models.busy && !models.installed(activeVoice)) {
            error = "Japanese voice files are being checked or downloaded. Please wait."; return
        }
        if (!models.installed(activeVoice)) {
            error = "Download Supertonic 3 $activeVoice in Voice settings to enable offline Japanese audio."; return
        }
        val voice = activeVoice
        val id = request.get()
        busy = true; error = null; message = "Preparing $voice · Supertonic 3…"
        worker.execute {
            fun cancelled() = closed || request.get() != id
            fun update(action: () -> Unit) { main.post { if (!cancelled()) action() } }
            try {
                if (cancelled()) return@execute
                if (engine == null) engine = SupertonicEngine.load(File(models.root, "onnx"))
                if (loadedVoice != voice) {
                    style = VoiceStyle.load(File(models.root, "voice_styles/$voice.json"))
                    loadedVoice = voice
                }
                val chunks = JapaneseTextChunker.chunk(text)
                for ((index, chunk) in chunks.withIndex()) {
                    if (cancelled()) throw CancellationException()
                    update { message = "Generating $voice · ${index + 1}/${chunks.size}…" }
                    val pcm = engine!!.synthesizeChunk(chunk, style!!, cancelled = ::cancelled)
                    update { message = "Playing $voice · Offline · ${index + 1}/${chunks.size}" }
                    play(pcm, engine!!.sampleRate, ::cancelled)
                }
                update { message = "$voice · Offline playback finished" }
            } catch (_: CancellationException) {
                // A newer tap, stop, or close owns the visible state.
            } catch (e: Exception) {
                runCatching { engine?.close() }; engine = null
                update { error = "Supertonic playback failed: ${e.localizedMessage}"; message = "" }
            } catch (_: OutOfMemoryError) {
                runCatching { engine?.close() }; engine = null
                update { error = "Not enough memory for Supertonic. Close other apps and try again."; message = "" }
            } finally { update { busy = false } }
        }
    }

    private fun play(pcm: FloatArray, sampleRate: Int, cancelled: () -> Boolean) {
        if (cancelled()) throw CancellationException()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        require(minBytes > 0) { "Float PCM playback is unavailable on this device" }
        val output = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(minBytes * 2, 16_384))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            synchronized(audioLock) {
                if (cancelled()) throw CancellationException()
                check(output.state == AudioTrack.STATE_INITIALIZED) { "Could not open audio output" }
                track = output
                output.play()
            }
            var offset = 0
            var lastProgress = System.nanoTime()
            while (offset < pcm.size) {
                if (cancelled()) throw CancellationException()
                val written = synchronized(audioLock) {
                    output.write(pcm, offset, minOf(4096, pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                }
                check(written >= 0) { "Audio output failed ($written)" }
                if (written > 0) { offset += written; lastProgress = System.nanoTime() }
                else {
                    check(System.nanoTime() - lastProgress < 5_000_000_000L) { "Audio output stalled" }
                    Thread.sleep(10)
                }
            }
            // Drain the buffered tail so the final syllable is not cut off.
            var position = -1
            lastProgress = System.nanoTime()
            while (output.playbackHeadPosition < pcm.size) {
                if (cancelled()) throw CancellationException()
                val current = output.playbackHeadPosition
                if (current != position) { position = current; lastProgress = System.nanoTime() }
                check(System.nanoTime() - lastProgress < 5_000_000_000L) { "Audio output stalled" }
                Thread.sleep(10)
            }
        } finally {
            synchronized(audioLock) {
                if (track === output) track = null
                runCatching { output.stop() }
                output.release()
            }
        }
    }

    fun stop() {
        request.incrementAndGet()
        synchronized(audioLock) { track?.let { runCatching { it.pause(); it.flush() } } }
        busy = false
        message = ""
    }

    /** Release the cached engine before Piper loads another language. */
    fun releaseForPiper() {
        if (closed) return
        stop()
        worker.execute { engine?.close(); engine = null; style = null; loadedVoice = null }
    }

    fun close() {
        if (closed) return
        closed = true; stop(); models.close()
        worker.execute { engine?.close(); engine = null; style = null }
        worker.shutdown()
    }
}
