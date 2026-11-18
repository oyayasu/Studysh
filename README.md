# Studysh

An offline-first vocabulary learning workspace for Android, with a future Windows edition.

## Features
- Native Android interface in English, rounded cards, pastel notes, minimal Material icons.
- Up to five search terms; generates a reusable prompt for ChatGPT, Gemini, and other AI models.
- AI response mode B: JSON only, rendered locally as a flexible collection of notes.
- Paste JSON or import a .json file.
- Local persistent library and merging of additional notes into existing entries.
- Personal notes attached to vocabulary entries.

## Usage
1. Open Search and add up to five expressions.
2. Copy the prompt into your preferred AI.
3. Copy the resulting JSON into **Import AI response**, or save it as a .json file and use the import icon.
4. Browse imported terms in Library.

## Offline Japanese audio (0.10.0)

Open **Settings → Voice settings → 日本語 · Supertonic 3**, download **F1 + models**,
then tap **Test voice**. After installation, lesson term and example audio buttons
use the selected Supertonic profile entirely on device, including in airplane mode.
F1–F5 are female profiles; shared ONNX models download once (~399 MB), with each
additional profile requiring ~0.3 MB. No server address or Android speech engine is
needed. Internet is used only for model downloads; other languages continue to use
Piper. The production models are pinned and checked by size and SHA-256, and partial
downloads resume on retry. Existing compatible Supertonic files are reused after
checksum verification. Keep the app open during the initial download.

## Building

Requires JDK 17, Gradle 8.11.1, Android SDK 35 / build-tools 35.0.0, NDK
27.0.12077973, CMake 3.22.1, and host CMake, a C compiler and make for the eSpeak data.

```bash
git clone --recurse-submodules https://github.com/piper-tts-apps/piper-kotlin.git
git -C piper-kotlin checkout 287b89079336d0d02f02d2bc96e93b7ca1e72bc5
git -C piper-kotlin submodule update --init --recursive
bash scripts/prepare-piper.sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

Piper's native library and the Java ONNX interface both use **ONNX Runtime 1.31.0**.
Keep these versions aligned; the APK packages one shared `libonnxruntime.so` per ABI.
The debug APK contains arm64-v8a, armeabi-v7a and x86_64 binaries and is installable
on Android 8.0/API 26 or newer. The models are not bundled in the APK.

For real model validation on a desktop JVM, using the exact same Kotlin inference code:

```bash
python3 scripts/download-supertonic-models.py /tmp/studysh-supertonic
SUPERTONIC_MODEL_DIR=/tmp/studysh-supertonic gradle :speech-smoke:test
```

This checks all pinned assets and synthesizes Japanese words, expressions and
sentences with F1–F5, asserting finite non-silent PCM. It exports WAV samples and
metrics to `speech-smoke/build/voice-samples/`. Without the model directory this
heavy test is explicitly skipped. CI always downloads models and runs it, plus the
Android unit tests, before publishing the debug-signed APK, test reports and samples
as Actions artifacts. The development release is updated when publishing is available.

Physical-device acceptance remains separate: install/upgrade the APK, download F1
with interruption/retry, test voice selection persistence after restarting, play
lesson terms and examples in airplane mode, test stop/repeated taps and audio routing,
and play a downloaded English/Portuguese/Spanish Piper voice. Check latency, memory
use and Japanese pronunciation quality on the target device; host inference cannot
validate those properties.

## JSON schema
```json
{
  "schemaVersion": "1.0",
  "entries": [{
    "term": "understatement",
    "pronunciation": "/ˌʌndərˈsteɪtmənt/",
    "status": "Learning",
    "notes": [{
      "id": "concept",
      "type": "concept",
      "title": "Core idea",
      "content": "A detailed explanation in Markdown."
    }],
    "exercises": [{
      "question": "Write your own example.",
      "answer": "Example answer",
      "explanation": "Explanation"
    }]
  }]
}
```

This is an early Android development version. Rich exercise playback, freeform note dragging, export, and Windows support remain future milestones.
