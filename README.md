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

## Building
Requires JDK 17 and Android SDK 35.

```bash
gradle :app:assembleDebug
```

The GitHub Actions workflow builds a debug-signed APK and publishes a development prerelease.

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
