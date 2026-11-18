package com.oyayasu.studysh

import com.oyayasu.studysh.supertonic.*
import org.junit.Assert.*
import org.junit.Test

class SupertonicTextTest {
    @Test fun japaneseCatalogOffersOnlySarahAndLily() {
        assertEquals(mapOf("F1" to "Sarah", "F2" to "Lily"), SupertonicAssets.profiles.associate { it.id to it.name })
        assertEquals(setOf("voice_styles/F1.json", "voice_styles/F2.json"),
            SupertonicAssets.all.filter { it.path.startsWith("voice_styles/") }.map { it.path }.toSet())
        assertEquals(8, SupertonicAssets.all.size)
        assertThrows(IllegalArgumentException::class.java) { SupertonicAssets.forVoice("F3") }
    }
    @Test fun longJapaneseWithoutSpacesIsBoundedAndLossless() {
        val input = "今日は日本語を勉強します。".repeat(60)
        val chunks = JapaneseTextChunker.chunk(input)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 120 })
        assertEquals(input, chunks.joinToString(""))
    }
    @Test fun unpunctuatedJapaneseAndSurrogatePairsAreNeverSplit() {
        val input = "あ".repeat(119) + "𠮷" + "い".repeat(121)
        val chunks = JapaneseTextChunker.chunk(input)
        assertEquals(input, chunks.joinToString(""))
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 120 })
        assertTrue(chunks.none { Character.isLowSurrogate(it.first()) || Character.isHighSurrogate(it.last()) })
    }
    @Test fun blankTextHasNoInferenceChunks() {
        assertTrue(JapaneseTextChunker.chunk(" \n ").isEmpty())
    }
    @Test fun japaneseTagAndNormalizationMatchReference() {
        val processor = UnicodeProcessor(IntArray(65536) { it })
        val result = processor.process(listOf("がっこう😊"), listOf("ja"))
        val encoded = result.textIds[0].map { it.toInt().toChar() }.joinToString("")
        assertEquals("<ja>か\u3099っこう.</ja>", encoded)
        assertTrue(result.textMask[0].all { it == 1f })
    }
    @Test fun unsupportedSupplementaryKanjiIsNotMappedToWrongCharacter() {
        assertThrows(IllegalArgumentException::class.java) {
            UnicodeProcessor(IntArray(65536) { it }).process(listOf("𠮷"), listOf("ja"))
        }
    }
}
