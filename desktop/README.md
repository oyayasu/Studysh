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
- Native Windows file dialogs, keyboard shortcuts, crisp SVG icons and optional system speech synthesis
- Optional local Qwen3-TTS 0.6B CustomVoice: nine preset speakers, offline generation after model download, and in-app voice tests
- Furigana display for lesson entries that supply explicit \`furigana\` metadata

## Known differences from Android
Desktop does not yet include Android OCR camera capture, offline Kuromoji automatic furigana, the bundled Piper voices or Japanese Supertonic Sarah/Lily model downloads. System speech uses Windows/WebView installed voices when available. Qwen3-TTS is a separate optional engine that runs on CPU (including AMD Windows computers) rather than claiming unverified RX 7600 GPU acceleration. Desktop and Android have separate local storage. Move your library and FSRS history with an exported JSON backup, not automatic sync.

## Build on Windows
Requires Node.js 24+, Rust stable, Microsoft C++ Build Tools and WebView2.
\`\`\`powershell
cd desktop
npm install
npm run build
\`\`\`
Output: \`desktop/src-tauri/target/release/bundle/nsis/\`.
A dedicated GitHub Actions workflow builds an NSIS .exe installer. No Android SDK required.

## Storage
App data is stored in the WebView's local storage. Export backups regularly, especially before reinstalling or clearing app data.

## Qwen3-TTS setup
Open **Settings → Voice options → Qwen3-TTS** and choose **Install & download**. The desktop app creates a private Python virtual environment under its local app data folder; it does not touch the Android build. **No Python installation is required**: the Windows installer includes a verified copy of Astral uv, which downloads a managed Python 3.12 only for Studysh, independent of the Python already installed on Windows. The first setup downloads CPU PyTorch, qwen-tts packages, the Qwen3-TTS 0.6B CustomVoice model and tokenizer. Expect several GB of disk use, an internet connection for setup, and slower-than-realtime speech on some CPUs. There is no external speech API or per-sentence charge. Subsequent synthesis uses the local model offline, kept loaded in a worker for reuse; Studysh caches recently generated phrases in memory. Choose **Auto** to use Ono Anna for Japanese and Aiden for the other supported Studysh languages. Note that the currently available preset voices do not have native Portuguese or Spanish speakers.

The model and engine are not bundled into the installer. The Windows installer can be built without Python, and end users do not need Python installed. The Windows build embeds the verified uv.exe binary via `desktop/scripts/prepare-uv.ps1`; Qwen3 setup creates an isolated Python 3.12 environment under application data. The Build script regenerates `src-tauri/icons/icon.ico` from `app-icon.svg` to avoid the old low-resolution placeholder.
