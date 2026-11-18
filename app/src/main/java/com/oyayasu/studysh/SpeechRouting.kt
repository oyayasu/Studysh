package com.oyayasu.studysh

object SpeechRouting {
    fun isJapanese(language: String): Boolean = language.trim().lowercase().let {
        it == "japanese" || it == "日本語" || it == "ja" || it.startsWith("ja-") || it.startsWith("ja_")
    }
}
