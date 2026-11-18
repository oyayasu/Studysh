package com.oyayasu.studysh

import java.util.Locale
import org.json.JSONObject

object StudyLanguage {
    val supported = listOf("English", "Japanese", "Spanish", "Portuguese")
    fun canonical(value: String): String = when (value.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')) {
        "en", "english" -> "English"
        "ja", "japanese", "日本語" -> "Japanese"
        "es", "spanish", "español" -> "Spanish"
        "pt", "portuguese", "português" -> "Portuguese"
        else -> value.trim().ifBlank { "English" }
    }
    fun id(language: String) = when (canonical(language)) {
        "English" -> "en"; "Japanese" -> "ja"; "Spanish" -> "es"; "Portuguese" -> "pt"
        else -> canonical(language).lowercase(Locale.ROOT)
    }
    fun lessonKey(language: String, term: String) = "${id(language)}::${term.trim().lowercase(Locale.ROOT)}"
    fun lessonKey(entry: JSONObject) = lessonKey(entry.optString("language", "English"), entry.optString("term"))
    fun progressPrefix(language: String, term: String) = lessonKey(language, term) + ":"
    fun showFurigana(studyLanguage: String, lessonLanguage: String? = null, practiceLanguage: String? = null) =
        listOfNotNull(studyLanguage, lessonLanguage, practiceLanguage).any { canonical(it) == "Japanese" }
}
