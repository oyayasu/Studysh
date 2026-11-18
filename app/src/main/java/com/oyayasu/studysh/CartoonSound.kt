package com.oyayasu.studysh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

enum class SoundCue(val seconds: Double) { Tap(.055), Start(.20), Correct(.24), Retry(.19), Continue(.075), Complete(.46) }

/** Original, quiet cartoon cues. Pure PCM synthesis, with no downloaded sound assets. */
object CartoonSound {
    const val SAMPLE_RATE = 22050
    fun wav(cue: SoundCue): ByteArray {
        val count = (cue.seconds * SAMPLE_RATE).toInt()
        val buffer = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + count * 2); buffer.put("WAVEfmt ".toByteArray())
        buffer.putInt(16); buffer.putShort(1); buffer.putShort(1); buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 2); buffer.putShort(2); buffer.putShort(16)
        buffer.put("data".toByteArray()); buffer.putInt(count * 2)
        var phase = 0.0
        repeat(count) { index ->
            val p = index.toDouble() / (count - 1)
            val frequency = when (cue) {
                SoundCue.Tap -> 680.0 - 300.0 * p
                SoundCue.Start -> 450.0 + 230.0 * p + 55.0 * sin(p * PI * 2)
                SoundCue.Correct -> if (p < .47) 587.33 else 783.99
                SoundCue.Retry -> 430.0 - 125.0 * p + 50.0 * sin(p * PI * 4) * exp(-4 * p)
                SoundCue.Continue -> 510.0 + 180.0 * p
                SoundCue.Complete -> when { p < .29 -> 523.25; p < .57 -> 659.25; else -> 783.99 }
            }
            phase += 2 * PI * frequency / SAMPLE_RATE
            val edge = sin(PI * p).pow(.8)
            val envelope = edge * exp(-2.3 * p)
            val sample = (sin(phase) + .11 * sin(phase * 2)) * envelope * .20
            buffer.putShort((sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
        return buffer.array()
    }
}
