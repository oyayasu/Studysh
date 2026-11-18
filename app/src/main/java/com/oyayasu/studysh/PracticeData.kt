package com.oyayasu.studysh

import org.json.JSONArray
import org.json.JSONObject

data class PracticeQuestion(val key: String, val legacyKey: String, val term: String, val language: String,
    val type: String, val instruction: String, val sentence: String, val prompt: String,
    val choices: List<String>, val answer: String, val explanation: String, val skill: String,
    val readings: Map<String, String> = emptyMap())

object PracticeData {
    fun questions(raw: String): List<PracticeQuestion> {
        val entries = runCatching { JSONObject(raw).optJSONArray("entries") }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PracticeQuestion>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val term = entry.optString("term")
            val language = StudyLanguage.canonical(entry.optString("language", "English"))
            val exercises = entry.optJSONArray("exercises") ?: continue
            for (j in 0 until exercises.length()) {
                val ex = exercises.optJSONObject(j) ?: continue
                val type = ex.optString("type")
                if (type !in listOf("complete", "build", "choice")) continue
                val list = if (type == "build") ex.optJSONArray("tiles") else ex.optJSONArray("options")
                val choices = list?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
                val answer = ex.optString("answer")
                if (choices.isEmpty() || answer.isBlank() || (type != "build" && answer !in choices)) continue
                val skill = ex.optString("skillId").ifBlank { ex.optString("id", "question-$j") }
                val legacy = term.lowercase(java.util.Locale.ROOT) + ":" + skill
                out += PracticeQuestion(StudyLanguage.progressPrefix(language, term) + skill, legacy, term,
                    language, type, ex.optString("instruction", if (type == "build") "Build the sentence" else "Complete the sentence"),
                    ex.optString("sentence"), ex.optString("ptPrompt"), choices, answer,
                    ex.optString("explanation"), skill, JapaneseReadings.overridesFrom(entry))
            }
        }
        return out
    }

    /** Old unscoped history is safe to reuse only if that term belongs to one language. */
    fun migrateProgress(raw: String, library: String, questions: List<PracticeQuestion>): JSONObject {
        val progress = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val entries = runCatching { JSONObject(library).optJSONArray("entries") }.getOrNull() ?: JSONArray()
        val languagesByTerm = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            .groupBy { it.optString("term").lowercase(java.util.Locale.ROOT) }
            .mapValues { (_, values) -> values.map { StudyLanguage.canonical(it.optString("language", "English")) }.toSet() }
        questions.forEach { q ->
            if (!progress.has(q.key) && languagesByTerm[q.term.lowercase(java.util.Locale.ROOT)]?.size == 1) {
                progress.optJSONObject(q.legacyKey)?.let { progress.put(q.key, JSONObject(it.toString())) }
            }
        }
        return progress
    }

    fun assemble(tiles: List<String>, language: String): String =
        tiles.joinToString(if (StudyLanguage.canonical(language) == "Japanese") "" else " ")

    fun matches(actual: String, expected: String, language: String): Boolean =
        if (StudyLanguage.canonical(language) == "Japanese")
            actual.replace(Regex("\\s+"), "") == expected.replace(Regex("\\s+"), "")
        else actual.trim() == expected.trim()
}
