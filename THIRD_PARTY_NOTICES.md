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

## Offline Japanese readings

Kuromoji 0.9.0 (`kuromoji-core` and `kuromoji-ipadic`) is bundled for local
morphological analysis and kana readings. The Java implementation is distributed
under the Apache License 2.0; IPADIC dictionary data retains its upstream notices.
Sources and notices: https://github.com/atilika/kuromoji/tree/0.9.0.

## Desktop outline icons

The Studysh Desktop SVG symbols are adapted from the [Lucide](https://github.com/lucide-icons/lucide)
and Feather icon sets. Copyright (c) 2026 Lucide Icons and Contributors.
Lucide: ISC License.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

Lucide icons derived from Feather retain the MIT license:
Copyright (c) 2013-present Cole Bemis.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
