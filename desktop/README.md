# Studysh Desktop (Windows)
An independently packaged Windows edition of Studysh using Tauri 2. The Android app is unchanged.

## Included in this initial desktop edition
- Four-tab interface: Home, Create, Lessons, Practice
- AI prompt generator for up to 5 study terms, with the Android JSON 3.0 prompt
- Import a JSON response, merge lessons preserving personal notes; search, read and annotate lessons
- Multiple target languages (English, Japanese, Spanish, Portuguese)
- Practice choice / complete / build exercises and a local FSRS-6 scheduler with language-scoped progress
- Native vector mascot reactions on correct/incorrect answers
- Local backup/export and restore, compatible with Android's \`studysh-backup-1\` structure
- Native Windows file dialogs, keyboard shortcuts and optional system speech synthesis
- Furigana display for lesson entries that supply explicit \`furigana\` metadata

## Known differences from Android
Desktop does not yet include Android OCR camera capture, offline Kuromoji automatic furigana, the bundled Piper voices or Japanese Supertonic Sarah/Lily model downloads. Speech uses Windows/WebView installed system voices when available, and is not guaranteed to work offline for every language. Desktop and Android have separate local storage. Move your library and FSRS history with an exported JSON backup, not automatic sync.

## Build on Windows
Requires Node.js 24+, Rust stable, Microsoft C++ Build Tools and WebView2.
\`\`\`powershell
cd desktop
npm install
npm run tauri build -- --bundles nsis
\`\`\`
Output: \`desktop/src-tauri/target/release/bundle/nsis/\`.
A dedicated GitHub Actions workflow builds an NSIS .exe installer. No Android SDK required.

## Storage
App data is stored in the WebView's local storage. Export backups regularly, especially before reinstalling or clearing app data.
