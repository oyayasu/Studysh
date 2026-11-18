"""Local, CPU-compatible Qwen3-TTS CustomVoice bridge for Studysh Desktop.

The model is downloaded only by an explicit user action. During synthesis
HF_HUB_OFFLINE prevents unexpected network access. One long-lived Python
process holds the model in RAM to avoid reloading for every flashcard.
"""
import argparse
import base64
import io
import json
import os
from pathlib import Path
import sys

MODEL_ID = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"
SPEAKERS = {"Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee"}
LANGUAGES = {"English", "Japanese", "Spanish", "Portuguese"}


def prepare(model_dir: Path):
    from huggingface_hub import snapshot_download
    model_dir.mkdir(parents=True, exist_ok=True)
    marker = model_dir / ".studysh-ready"
    marker.unlink(missing_ok=True)
    snapshot_download(repo_id=MODEL_ID, local_dir=str(model_dir))
    if not (model_dir / "config.json").is_file():
        raise RuntimeError("Qwen3 model download did not contain config.json")
    marker.write_text(MODEL_ID, encoding="utf-8")


def serve(model_dir: Path):
    # stdout is reserved for JSON-lines IPC. Divert third-party progress and
    # diagnostics to stderr to keep the Rust/WebView protocol intact.
    protocol = sys.stdout
    sys.stdout = sys.stderr
    if not (model_dir / ".studysh-ready").is_file():
        protocol.write('{"error":"Qwen3 model has not been downloaded yet."}\\n')
        protocol.flush()
        return 1
    try:
        os.environ["HF_HUB_OFFLINE"] = "1"
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
        os.environ["TOKENIZERS_PARALLELISM"] = "false"
        import torch
        import soundfile as sf
        from qwen_tts import Qwen3TTSModel

        torch.set_num_threads(min(8, max(1, (os.cpu_count() or 4) // 2)))
        model = Qwen3TTSModel.from_pretrained(
            str(model_dir),
            device_map="cpu",
            dtype=torch.float32,
            attn_implementation="eager",
        )
        protocol.write('{"ready":true}\\n')
        protocol.flush()
    except Exception as exc:
        protocol.write(json.dumps({"error": f"Unable to load Qwen3-TTS: {exc}"}, ensure_ascii=False) + "\\n")
        protocol.flush()
        return 1

    for line in sys.stdin:
        try:
            payload = json.loads(line)
            sentence = payload.get("text", "")
            language = payload.get("language", "English")
            speaker = payload.get("speaker", "Aiden")
            if not isinstance(sentence, str) or not sentence.strip() or len(sentence) > 1200:
                raise ValueError("Enter a phrase of at most 1200 characters.")
            if language not in LANGUAGES or speaker not in SPEAKERS:
                raise ValueError("Invalid language or voice.")
            with torch.inference_mode():
                waves, rate = model.generate_custom_voice(
                    text=sentence,
                    language=language,
                    speaker=speaker,
                )
            wav = io.BytesIO()
            sf.write(wav, waves[0], rate, format="WAV", subtype="PCM_16")
            result = {"audio_base64": base64.b64encode(wav.getvalue()).decode("ascii")}
        except Exception as exc:
            result = {"error": str(exc)}
        protocol.write(json.dumps(result, ensure_ascii=False) + "\\n")
        protocol.flush()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--prepare", action="store_true")
    group.add_argument("--serve", action="store_true")
    parser.add_argument("--model-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        sys.exit(serve(args.model_dir) if args.serve else (prepare(args.model_dir) or 0))
    except Exception as error:
        print(f"Qwen3-TTS error: {error}", file=sys.stderr)
        sys.exit(1)
