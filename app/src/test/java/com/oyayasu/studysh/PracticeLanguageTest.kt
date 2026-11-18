package com.oyayasu.studysh

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PracticeLanguageTest {
    private fun entry(language: String, term: String = "gift") = JSONObject().put("term", term).put("language", language)
        .put("blocks", JSONArray().put(JSONObject().put("text", "A lesson")))
        .put("exercises", JSONArray().put(JSONObject().put("id", "meaning-1").put("skillId", "meaning")
            .put("type", "choice").put("options", JSONArray(listOf("a", "b"))).put("answer", "a")))
    private fun library(vararg entries: JSONObject) = JSONObject().put("entries", JSONArray(entries.toList())).toString()

    @Test fun identicalTermsAndSkillIdsInDifferentLanguagesHaveSeparateHistory() {
        val questions = PracticeData.questions(library(entry("English"), entry("Japanese"), entry("Spanish")))
        assertEquals(3, questions.map { it.key }.distinct().size)
        assertEquals(1, questions.count { it.language == "Japanese" })
        assertEquals("ja::gift:meaning", questions.single { it.language == "Japanese" }.key)
    }
    @Test fun legacyReviewHistoryIsReusedForUnambiguousTerms() {
        val data = library(entry("English"))
        val old = JSONObject().put("gift:meaning", JSONObject().put("dueAt", 123L))
        val migrated = PracticeData.migrateProgress(old.toString(), data, PracticeData.questions(data))
        assertEquals(123L, migrated.getJSONObject("en::gift:meaning").getLong("dueAt"))
    }
    @Test fun ambiguousOldHistoryIsNotCopiedAcrossLanguages() {
        val data = library(entry("English"), entry("Japanese"))
        val old = JSONObject().put("gift:meaning", JSONObject().put("dueAt", 123L))
            .put("ja::gift:meaning", JSONObject().put("dueAt", 456L))
        val migrated = PracticeData.migrateProgress(old.toString(), data, PracticeData.questions(data))
        assertFalse(migrated.has("en::gift:meaning"))
        assertEquals(456L, migrated.getJSONObject("ja::gift:meaning").getLong("dueAt"))
    }
    @Test fun importingTheSameTermInAnotherLanguageDoesNotReplaceTheOriginalLesson() {
        val old = entry("English").put("personalNotes", JSONArray().put(JSONObject().put("text", "Keep this")))
        val existing = parseEntries(library(old))
        val incoming = parseEntries(library(entry("Japanese")))
        val merged = StudyLibrary.merge(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals("Keep this", merged.single { it.optString("language") == "English" }
            .getJSONArray("personalNotes").getJSONObject(0).getString("text"))
    }
    @Test fun japaneseTilesDoNotAcquireEnglishWordSpacing() {
        val sentence = PracticeData.assemble(listOf("日本語", "を", "勉強します。"), "Japanese")
        assertEquals("日本語を勉強します。", sentence)
        assertTrue(PracticeData.matches(sentence, "日本語 を 勉強します。", "Japanese"))
        assertEquals("I study English.", PracticeData.assemble(listOf("I", "study", "English."), "English"))
    }
    @Test fun japanesePromptRequestsPlainJapaneseAndStructuredReadingsInsteadOfParentheses() {
        val japanese = studyPrompt(listOf(StudyQuery("よく眠れた？")), "Japanese")
        assertTrue(japanese.contains("Do NOT append hiragana or katakana readings in parentheses"))
        assertTrue(japanese.contains("\"furigana\""))
        assertTrue(japanese.contains("optional furigana above kanji"))
        assertFalse(studyPrompt(listOf(StudyQuery("book")), "English").contains("Do NOT append hiragana"))
    }
}
