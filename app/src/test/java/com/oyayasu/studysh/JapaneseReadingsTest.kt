package com.oyayasu.studysh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JapaneseReadingsTest {
    @Test fun dictionaryAddsActualReadingsAboveKanjiWithoutChangingTheSentence() {
        val sentence = "今日は学校で日本語を勉強します。"
        val annotated = JapaneseReadings.annotate(sentence)
        assertEquals(sentence, annotated.joinToString("") { it.text })
        assertTrue(annotated.any { it.text == "学校" && it.reading == "がっこう" })
        assertTrue(annotated.any { it.text == "勉強" && it.reading == "べんきょう" })
    }
    @Test fun okuriganaRemainsBesideTheKanjiAndReadingsUseHiragana() {
        val annotated = JapaneseReadings.annotate("食べる")
        assertTrue(annotated.contains(RubySegment("食", "た")))
        assertEquals("食べる", annotated.joinToString("") { it.text })
        assertTrue(annotated.filter { it.reading != null }.all { it.reading!!.none { c -> c in '\u30a1'..'\u30f6' } })
    }
    @Test fun authorReadingsResolveAmbiguousKanji() {
        val entry = JSONObject().put("furigana", JSONArray().put(JSONObject().put("text", "生").put("reading", "なま")))
        val result = JapaneseReadings.annotate("生の魚", JapaneseReadings.overridesFrom(entry))
        assertTrue(result.contains(RubySegment("生", "なま")))
    }
    @Test fun existingRedundantKanaCanBeRemovedWithoutRemovingGrammarOrTranslationNotes() {
        assertEquals("日本語を勉強します。", JapaneseReadings.annotate("日本語（にほんご）を勉強(べんきょう)します。").joinToString("") { it.text })
        val meaningful = "助詞（は） e palavra (opcional)"
        assertEquals(meaningful, JapaneseReadings.annotate(meaningful).joinToString("") { it.text })
    }
    @Test fun portugueseTextWhitespaceAndPunctuationArePreserved() {
        val input = "  **日本語** — uma língua.\n\nBom dia!"
        assertEquals(input, JapaneseReadings.annotate(input).joinToString("") { it.text })
        assertEquals(listOf(RubySegment("Olá!")), JapaneseReadings.annotate("Olá!"))
    }
    @Test fun furiganaButtonIsAvailableForJapaneseLessonsRegardlessOfGlobalStudyLanguage() {
        assertFalse(StudyLanguage.showFurigana("English"))
        assertTrue(StudyLanguage.showFurigana("Japanese"))
        assertTrue(StudyLanguage.showFurigana("English", lessonLanguage = "ja-JP"))
        assertTrue(StudyLanguage.showFurigana("Spanish", practiceLanguage = "Japanese"))
        assertFalse(StudyLanguage.showFurigana("Spanish", lessonLanguage = "English"))
    }
}
