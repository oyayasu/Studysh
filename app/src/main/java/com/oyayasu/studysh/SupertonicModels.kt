package com.oyayasu.studysh

import android.content.Context
import androidx.compose.runtime.*
import com.oyayasu.studysh.supertonic.ModelStore
import com.oyayasu.studysh.supertonic.SupertonicAssets
import kotlinx.coroutines.*
import java.io.File

/** Download state shared by settings and lesson playback, owned for the app's lifetime. */
class SupertonicModels(context: Context) {
    val root = File(context.filesDir, "supertonic-3")
    private val store = ModelStore(root)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var verified by mutableStateOf(emptySet<String>())
    var busy by mutableStateOf(true); private set
    var progress by mutableFloatStateOf(0f); private set
    var downloadedBytes by mutableLongStateOf(0L); private set
    var totalBytes by mutableLongStateOf(0L); private set
    var status by mutableStateOf("Checking installed Supertonic models…"); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        job = scope.launch {
            val operation = currentCoroutineContext()
            try {
                verified = withContext(Dispatchers.IO) { store.verifiedPaths(SupertonicAssets.all) { !operation.isActive } }
                status = if (SupertonicAssets.voices.any { installed(it) }) "Supertonic 3 · Offline ready" else "Download F1 to enable Japanese audio."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "Could not check voice files: ${e.localizedMessage}"
            } finally { busy = false }
        }
    }

    fun installed(voice: String = "F1") = SupertonicAssets.forVoice(voice).all { it.path in verified }
    fun coreInstalled() = SupertonicAssets.core.all { it.path in verified }

    fun install(voice: String = "F1") {
        if (busy || installed(voice)) return
        busy = true; progress = 0f; downloadedBytes = 0L
        totalBytes = SupertonicAssets.forVoice(voice).sumOf { it.bytes }
        error = null
        status = "Downloading Supertonic 3 · $voice…"
        job = scope.launch {
            val operation = currentCoroutineContext()
            try {
                withContext(Dispatchers.IO) {
                    var lastUpdate = 0L
                    store.install(SupertonicAssets.forVoice(voice), { !operation.isActive }) { state ->
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 150_000_000L || state.downloaded == state.total) {
                            lastUpdate = now
                            scope.launch {
                                if (!operation.isActive) return@launch
                                downloadedBytes = state.downloaded; totalBytes = state.total
                                progress = (state.downloaded.toDouble() / state.total).toFloat().coerceIn(0f, 1f)
                                status = "Downloading / verifying ${state.path}"
                            }
                        }
                    }
                }
                verified = verified + SupertonicAssets.forVoice(voice).map { it.path }
                status = "$voice installed · Ready for offline Japanese playback"
                progress = 1f
            } catch (e: CancellationException) {
                status = "Download paused. Tap Download to resume."
                throw e
            } catch (e: Exception) {
                error = "Supertonic download failed: ${e.localizedMessage}"
                status = "Not installed · Tap Download to retry / resume"
            } finally { busy = false }
        }
    }

    fun cancel() { job?.cancel() }
    fun close() { scope.cancel() }
}
