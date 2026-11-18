package com.oyayasu.studysh.supertonic

/** Sizes and SHA-256 verified against the actual pinned upstream files. */
data class ModelAsset(val path: String, val bytes: Long, val sha256: String)
data class SupertonicVoice(val id: String, val name: String)

object SupertonicAssets {
    const val REVISION = "3cadd1ee6394adea1bd021217a0e650ede09a323"
    const val BASE_URL = "https://huggingface.co/Supertone/supertonic-3/resolve/$REVISION/"
    val profiles = listOf(
        SupertonicVoice("F1", "Sarah"),
        SupertonicVoice("F2", "Lily"),
    )
    val voices = profiles.map { it.id }
    fun voiceName(id: String) = profiles.single { it.id == id }.name
    val all = listOf(
        ModelAsset("onnx/duration_predictor.onnx", 3700147L, "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
        ModelAsset("onnx/text_encoder.onnx", 36416150L, "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
        ModelAsset("onnx/tts.json", 8253L, "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
        ModelAsset("onnx/unicode_indexer.json", 277676L, "9bf7346e43883a81f8645c81224f786d43c5b57f3641f6e7671a7d6c493cb24f"),
        ModelAsset("onnx/vector_estimator.onnx", 256534781L, "883ac868ea0275ef0e991524dc64f16b3c0376efd7c320af6b53f5b780d7c61c"),
        ModelAsset("onnx/vocoder.onnx", 101424195L, "085de76dd8e8d5836d6ca66826601f615939218f90e519f70ee8a36ed2a4c4ba"),
        ModelAsset("voice_styles/F1.json", 292046L, "bbdec6ee00231c2c742ad05483df5334cab3b52fda3ba38e6a07059c4563dbc2"),
        ModelAsset("voice_styles/F2.json", 292423L, "7c722c6a72707b1a77f035d67f0d1351ba187738e06f7683e8c72b1df3477fc6"),
    )
    val core = all.filter { it.path.startsWith("onnx/") }
    fun forVoice(voice: String): List<ModelAsset> {
        require(voice in voices) { "Unknown Supertonic voice: $voice" }
        return core + all.single { it.path == "voice_styles/$voice.json" }
    }
}
