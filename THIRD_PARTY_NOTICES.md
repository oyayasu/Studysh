# Speech engine references

The Supertonic 3 inference pipeline and text preprocessing are adapted from
[ouor/supertonic-android](https://github.com/ouor/supertonic-android), revision
`747b9e0d8dfa160c270c29aead1f6245baa493a3`, and the official
[Supertone reference implementation](https://github.com/supertone-inc/supertonic).
Studysh adds bounded Japanese chunking, stricter validation, deterministic tensor
cleanup, cancellable playback and pinned, SHA-256 checked downloads.

Model weights and voice profiles are downloaded directly from
[Supertone/supertonic-3](https://huggingface.co/Supertone/supertonic-3), revision
`3cadd1ee6394adea1bd021217a0e650ede09a323`. They are distributed by Supertone Inc.
under the [model's OpenRAIL-M license](https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE).
Weights are not bundled in the APK. Studysh exposes Sarah (upstream F1) and Lily (upstream F2) for Japanese.

ONNX Runtime is provided by Microsoft under the MIT license. Piper uses the
[piper-tts-apps/piper-kotlin](https://github.com/piper-tts-apps/piper-kotlin)
Android integration and its upstream Piper, eSpeak NG and Sonic dependencies.
Their respective license notices remain applicable.
