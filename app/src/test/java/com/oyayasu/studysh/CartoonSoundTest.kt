package com.oyayasu.studysh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class CartoonSoundTest {
    @Test fun everyCueIsShortQuietAndHasAValidPcmWavHeader() {
        SoundCue.entries.forEach { cue ->
            val bytes = CartoonSound.wav(cue)
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
            assertEquals(CartoonSound.SAMPLE_RATE, input.getInt(24))
            assertEquals(16, input.getShort(34).toInt())
            assertEquals(bytes.size - 44, input.getInt(40))
            assertTrue(cue.seconds < .5)
            val samples = (44 until bytes.size step 2).map { input.getShort(it).toInt() }
            assertEquals(0, samples.first()); assertEquals(0, samples.last())
            assertTrue(samples.maxOf { abs(it) } in 100..8000)
        }
    }
}
