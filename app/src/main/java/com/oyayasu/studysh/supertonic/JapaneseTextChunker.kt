package com.oyayasu.studysh.supertonic

/** Bounded chunks, including Japanese with no spaces. Never split a surrogate pair. */
object JapaneseTextChunker {
    fun chunk(text: String, limit: Int = 120): List<String> {
        require(limit > 0)
        var remaining = text.trim()
        val out = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val count = remaining.codePointCount(0, remaining.length)
            if (count <= limit) { out += remaining; break }
            val end = remaining.offsetByCodePoints(0, limit)
            val candidate = remaining.substring(0, end)
            // Prefer sentence punctuation, then Japanese/Latin commas or whitespace.
            val sentence = candidate.indexOfLast { it in "。！？.!?\n" }
            val phrase = candidate.indexOfLast { it in "、,;；" || it.isWhitespace() }
            val split = when {
                sentence >= candidate.length / 3 -> sentence + 1
                phrase >= candidate.length / 3 -> phrase + 1
                else -> end
            }
            candidate.substring(0, split).trim().takeIf { it.isNotEmpty() }?.let { out += it }
            remaining = remaining.substring(split).trimStart()
        }
        return out
    }
}
