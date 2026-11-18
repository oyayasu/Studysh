package com.oyayasu.studysh

import com.atilika.kuromoji.ipadic.Tokenizer
import org.json.JSONObject

data class RubySegment(val text: String, val reading: String? = null)

/** Bundled dictionary: Japanese readings work without a network or another download. */
object JapaneseReadings {
    private val tokenizer by lazy { Tokenizer() }
    private data class CacheKey(val text: String, val overrides: Map<String, String>)
    private val cache = object : LinkedHashMap<CacheKey, List<RubySegment>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, List<RubySegment>>?) = size > 128
    }

    fun hasKanji(text: String) = text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
    fun hiragana(text: String): String = text.map { if (it in '\u30a1'..'\u30f6') (it.code - 0x60).toChar() else it }.joinToString("")
    fun overridesFrom(entry: JSONObject): Map<String, String> {
        val readings = entry.optJSONArray("furigana") ?: return emptyMap()
        return (0 until readings.length()).mapNotNull { i ->
            val item = readings.optJSONObject(i) ?: return@mapNotNull null
            val text = item.optString("text")
            val reading = hiragana(item.optString("reading")).trim()
            if (text.isBlank() || reading.isBlank() || !hasKanji(text)) null else text to reading
        }.toMap()
    }

    @Synchronized fun annotate(source: String, overrides: Map<String, String> = emptyMap()): List<RubySegment> {
        val key = CacheKey(source, overrides.toMap())
        cache[key]?.let { return it }
        if (!hasKanji(source)) return listOf(RubySegment(source))
        val text = removeRedundantReadings(source)
        val out = mutableListOf<RubySegment>()
        val special = overrides.keys.filter { text.contains(it) }.sortedByDescending { it.length }
        var cursor = 0
        var plainStart = 0
        while (cursor < text.length) {
            val match = special.firstOrNull { text.startsWith(it, cursor) }
            if (match == null) { cursor++; continue }
            if (plainStart < cursor) out += automatic(text.substring(plainStart, cursor))
            out += align(match, hiragana(overrides.getValue(match)))
            cursor += match.length
            plainStart = cursor
        }
        if (plainStart < text.length) out += automatic(text.substring(plainStart))
        return out.toList().also { cache[key] = it }
    }

    private fun automatic(text: String): List<RubySegment> {
        if (!hasKanji(text)) return listOf(RubySegment(text))
        val out = mutableListOf<RubySegment>()
        var position = 0
        tokenizer.tokenize(text).forEach { token ->
            if (token.position > position) out += RubySegment(text.substring(position, token.position))
            val reading = token.reading
            if (hasKanji(token.surface) && !reading.isNullOrBlank() && reading != "*")
                out += align(token.surface, hiragana(reading))
            else out += RubySegment(token.surface)
            position = token.position + token.surface.length
        }
        if (position < text.length) out += RubySegment(text.substring(position))
        return out
    }

    /** Keep okurigana and matching kana prefixes out of the ruby annotation. */
    private fun align(surface: String, reading: String): List<RubySegment> {
        val kana = hiragana(surface)
        var start = 0
        while (start < surface.length && start < reading.length && kana[start] == reading[start] && !hasKanji(surface[start].toString())) start++
        var suffix = 0
        while (suffix < surface.length - start && suffix < reading.length - start &&
            kana[surface.lastIndex - suffix] == reading[reading.lastIndex - suffix] &&
            !hasKanji(surface[surface.lastIndex - suffix].toString())) suffix++
        val base = surface.substring(start, surface.length - suffix)
        val ruby = reading.substring(start, reading.length - suffix)
        return buildList {
            if (start > 0) add(RubySegment(surface.take(start)))
            if (base.isNotEmpty()) add(RubySegment(base, ruby.takeIf { it.isNotEmpty() && hasKanji(base) }))
            if (suffix > 0) add(RubySegment(surface.takeLast(suffix)))
        }
    }

    /** Remove only dictionary-confirmed duplicates; keep meaningful parenthetical content. */
    private fun removeRedundantReadings(text: String): String =
        Regex("([\\p{IsHan}ぁ-ゖァ-ヺ々ー]+)[（(]([ぁ-ゖァ-ヺー ]+)[）)]").replace(text) { match ->
            val surface = match.groupValues[1]
            val reading = hiragana(match.groupValues[2]).replace(" ", "")
            val tokens = tokenizer.tokenize(surface)
            var suffix = ""
            var redundant = false
            for (token in tokens.asReversed()) {
                val part = token.reading.takeIf { !it.isNullOrBlank() && it != "*" } ?: token.surface
                suffix = hiragana(part) + suffix
                if (suffix == reading) { redundant = true; break }
                if (suffix.length > reading.length) break
            }
            if (redundant && hasKanji(surface)) surface else match.value
        }
}
