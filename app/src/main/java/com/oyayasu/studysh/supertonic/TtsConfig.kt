// Adapted from ouor/supertonic-android @ 747b9e0; see THIRD_PARTY_NOTICES.md.
package com.oyayasu.studysh.supertonic

import org.json.JSONObject
import java.io.File

/**
 * Model configuration loaded from `tts.json`.
 * Mirrors the `ae` / `ttl` fields read in the reference implementations.
 */
data class TtsConfig(
    val sampleRate: Int,
    val baseChunkSize: Int,
    val chunkCompressFactor: Int,
    val latentDim: Int,
) {
    companion object {
        fun load(file: File): TtsConfig {
            val root = JSONObject(file.readText())
            val ae = root.getJSONObject("ae")
            val ttl = root.getJSONObject("ttl")
            return TtsConfig(
                sampleRate = ae.getInt("sample_rate"),
                baseChunkSize = ae.getInt("base_chunk_size"),
                chunkCompressFactor = ttl.getInt("chunk_compress_factor"),
                latentDim = ttl.getInt("latent_dim"),
            ).also {
                require(it.sampleRate == 44100 && it.baseChunkSize == 512 &&
                    it.chunkCompressFactor == 6 && it.latentDim == 24) { "Incompatible Supertonic 3 configuration" }
            }
        }
    }
}
