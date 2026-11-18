package com.oyayasu.studysh.supertonic

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Real ONNX execution with official weights; no synthesis mocks. */
class SupertonicInferenceTest {
    @Test fun synthesizeJapaneseWordExpressionAndSentencesWithSarahAndLily() {
        val path = System.getenv("SUPERTONIC_MODEL_DIR")
        assumeTrue("Set SUPERTONIC_MODEL_DIR to run real inference", !path.isNullOrBlank())
        val root = File(path!!)
        val verified = ModelStore(root).verifiedPaths(SupertonicAssets.all)
        assertEquals(SupertonicAssets.all.map { it.path }.toSet(), verified)
        val output = File("build/voice-samples").apply { deleteRecursively(); mkdirs() }
        val report = StringBuilder("voice\ttext\tsamples\tduration_seconds\trms\tinference_ms\n")
        SupertonicEngine.load(File(root, "onnx")).use { engine ->
            assertEquals(44100, engine.sampleRate)
            for (profile in SupertonicAssets.profiles) {
                val voice = profile.id
                val style = VoiceStyle.load(File(root, "voice_styles/$voice.json"))
                val texts = listOf("猫", "お疲れさまです", "こんにちは。日本語を一緒に勉強しましょう。")
                for ((index, text) in texts.withIndex()) {
                    val start = System.nanoTime()
                    val pcm = engine.synthesizeChunk(text, style)
                    val millis = (System.nanoTime() - start) / 1_000_000
                    val seconds = pcm.size.toDouble() / engine.sampleRate
                    val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
                    assertTrue("$voice/$text has audible finite PCM", pcm.all { it.isFinite() } && rms > 0.00001)
                    assertTrue("$voice/$text duration $seconds", seconds in 0.05..60.0)
                    writeWav(File(output, "${profile.name}-$index.wav"), pcm, engine.sampleRate)
                    report.append("${profile.name}\t$text\t${pcm.size}\t$seconds\t$rms\t$millis\n")
                }
            }
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                engine.synthesizeChunk("停止", VoiceStyle.load(File(root, "voice_styles/F1.json")), cancelled = { true })
            }
        }
        File(output, "inference.tsv").writeText(report.toString())
        println(report)
    }

    private fun writeWav(file: File, pcm: FloatArray, sampleRate: Int) {
        val bytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + bytes); buffer.put("WAVEfmt ".toByteArray())
        buffer.putInt(16); buffer.putShort(1); buffer.putShort(1)
        buffer.putInt(sampleRate); buffer.putInt(sampleRate * 2); buffer.putShort(2); buffer.putShort(16)
        buffer.put("data".toByteArray()); buffer.putInt(bytes)
        pcm.forEach { buffer.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        file.writeBytes(buffer.array())
    }
}
