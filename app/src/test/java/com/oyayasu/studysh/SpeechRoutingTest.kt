package com.oyayasu.studysh

import org.junit.Assert.*
import org.junit.Test

class SpeechRoutingTest {
    @Test fun japaneseNamesAndLocaleCodesUseSupertonic() {
        listOf("Japanese", "japanese", " ja ", "ja-JP", "ja_JP", "日本語").forEach {
            assertTrue(it, SpeechRouting.isJapanese(it))
        }
    }
    @Test fun otherLessonLanguagesStillUsePiper() {
        listOf("English", "Portuguese", "Spanish", "en-US", "pt-BR", "es", "").forEach {
            assertFalse(it, SpeechRouting.isJapanese(it))
        }
    }
}
