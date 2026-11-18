package com.oyayasu.studysh.supertonic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random
import java.util.concurrent.CancellationException

/** Four-model Supertonic 3 pipeline, based on ouor/supertonic-android @ 747b9e0.
 * No Android or networking dependency: the same code is exercised by the JVM smoke test.
 * Call from one worker thread; the application streams one bounded chunk at a time.
 */
class SupertonicEngine private constructor(
    private val env: OrtEnvironment,
    private val config: TtsConfig,
    private val processor: UnicodeProcessor,
    private val sessions: List<OrtSession>,
) : Closeable {
    val sampleRate: Int get() = config.sampleRate

    fun synthesizeChunk(
        text: String,
        style: VoiceStyle,
        steps: Int = 8,
        speed: Float = 1.05f,
        cancelled: () -> Boolean = { false },
    ): FloatArray {
        require(text.isNotBlank()) { "Text is empty" }
        require(text.codePointCount(0, text.length) <= 120) { "Split Japanese text into 120-character chunks" }
        require(style.batchSize == 1 && style.dpShape[0] == 1L) { "Expected a single voice style" }
        require(steps in 1..32 && speed.isFinite() && speed in 0.5f..2f)
        fun checkActive() { if (cancelled()) throw CancellationException("Speech stopped") }
        checkActive()
        val tokens = processor.process(listOf(text), listOf("ja"))
        val n = tokens.textIds[0].size.toLong()
        val owned = mutableListOf<OnnxTensor>()
        fun floats(data: FloatArray, shape: LongArray): OnnxTensor =
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).also { owned += it }
        try {
            val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.textIds[0]), longArrayOf(1, n))
                .also { owned += it }
            val textMask = floats(tokens.textMask[0], longArrayOf(1, 1, n))
            val ttl = floats(style.ttlData, style.ttlShape)
            val dp = floats(style.dpData, style.dpShape)
            val duration = sessions[0].run(mapOf("text_ids" to ids, "style_dp" to dp, "text_mask" to textMask))
                .use { readFloats(it[0] as OnnxTensor).single() / speed }
            // Reject broken model outputs before allocating an unbounded latent/vocoder buffer.
            require(duration.isFinite() && duration > 0f && duration <= 60f) { "Invalid predicted duration: $duration" }
            checkActive()
            sessions[1].run(mapOf("text_ids" to ids, "style_ttl" to ttl, "text_mask" to textMask)).use { encoded ->
                val wavLength = (duration * sampleRate).toInt().coerceAtLeast(1)
                val chunkSize = config.baseChunkSize * config.chunkCompressFactor
                val length = (wavLength + chunkSize - 1) / chunkSize
                val channels = config.latentDim * config.chunkCompressFactor
                val shape = longArrayOf(1, channels.toLong(), length.toLong())
                val mask = floats(FloatArray(length) { 1f }, longArrayOf(1, 1, length.toLong()))
                val total = floats(floatArrayOf(steps.toFloat()), longArrayOf(1))
                val rng = Random()
                var latent = FloatArray(channels * length) { rng.nextGaussian().toFloat() }
                repeat(steps) { step ->
                    checkActive()
                    // Temporary tensors/results are closed even when inference fails or is cancelled.
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), shape).use { noisy ->
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(step.toFloat())), longArrayOf(1)).use { current ->
                            sessions[2].run(mapOf(
                                "noisy_latent" to noisy, "text_emb" to (encoded[0] as OnnxTensor), "style_ttl" to ttl,
                                "latent_mask" to mask, "text_mask" to textMask,
                                "current_step" to current, "total_step" to total,
                            )).use { latent = readFloats(it[0] as OnnxTensor) }
                        }
                    }
                }
                checkActive()
                OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), shape).use { finalLatent ->
                    sessions[3].run(mapOf("latent" to finalLatent)).use { result ->
                        val wav = readFloats(result[0] as OnnxTensor)
                        checkActive()
                        require(wav.isNotEmpty() && wav.all { it.isFinite() }) { "Invalid PCM output" }
                        return wav.copyOf(minOf(wavLength, wav.size))
                    }
                }
            }
        } finally { owned.asReversed().forEach { it.close() } }
    }

    override fun close() { sessions.asReversed().forEach { it.close() } }

    companion object {
        fun load(onnxDir: File): SupertonicEngine {
            val config = TtsConfig.load(File(onnxDir, "tts.json"))
            val arr = JSONArray(File(onnxDir, "unicode_indexer.json").readText())
            val processor = UnicodeProcessor(IntArray(arr.length()) { arr.getInt(it) })
            val env = OrtEnvironment.getEnvironment()
            val sessions = mutableListOf<OrtSession>()
            try {
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                    options.setInterOpNumThreads(1)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Four sessions otherwise retain large CPU arenas on memory-limited phones.
                    options.setCPUArenaAllocator(false)
                    options.setMemoryPatternOptimization(false)
                    for (name in listOf("duration_predictor", "text_encoder", "vector_estimator", "vocoder")) {
                        sessions += env.createSession(File(onnxDir, "$name.onnx").absolutePath, options)
                    }
                }
                return SupertonicEngine(env, config, processor, sessions)
            } catch (e: Throwable) {
                sessions.asReversed().forEach { runCatching { it.close() } }
                throw e
            }
        }

        private fun readFloats(tensor: OnnxTensor): FloatArray = tensor.floatBuffer.let { buffer ->
            FloatArray(buffer.remaining()).also { buffer.get(it) }
        }
    }
}
