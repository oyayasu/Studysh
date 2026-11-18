"""Local, CPU-compatible Qwen3-TTS CustomVoice bridge for Studysh Desktop.

The model is downloaded only by an explicit user action. During synthesis
HF_HUB_OFFLINE prevents unexpected network access. One long-lived Python
process holds the model in RAM to avoid reloading for every flashcard.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import time
from urllib.request import Request, urlopen

MODEL_ID = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"
SPEAKERS = {"Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee"}
LANGUAGES = {"English", "Japanese", "Spanish", "Portuguese"}


REVISION = "85e237c12c027371202489a0ec509ded67b5e4b5"
# Size and Git blob ID (small files) or LFS SHA-256 (weights) at REVISION.
FILES = {
    "config.json": (4908, "5e8d5fc100536984e3a89bd87ef08e59e322b29b"),
    "generation_config.json": (245, "1872b16d4535564abf2db5d6debe6cddd82b7f2e"),
    "merges.txt": (1671839, "20024bfe7c83998e9aeaf98a0cd6a2ce6306c2f0"),
    "model.safetensors": (1811626576, "bc3c7e785eb961179c25450d1acff03f839e0002f2f3a5aeb67b5735c0fa2adb"),
    "preprocessor_config.json": (127, "0525dd953bb9241912f7147666f0d535165d5d4f"),
    "speech_tokenizer/config.json": (2336, "06cc8dc4c5ec8a1929086b71b98c313020d9268b"),
    "speech_tokenizer/configuration.json": (76, "ab58e2eaf53cd14a1a2a7527d9261ceea93a24cd"),
    "speech_tokenizer/model.safetensors": (682293092, "836b7b357f5ea43e889936a3709af68dfe3751881acefe4ecf0dbd30ba571258"),
    "speech_tokenizer/preprocessor_config.json": (234, "ba40914f4f49ab98a8ca545d4892ef7291a39592"),
    "tokenizer_config.json": (7344, "6ff9fd60cc623bb54bbd603cbd418c97a11528d7"),
    "vocab.json": (2776833, "4783fe10ac3adce15ac8f358ef5462739852c569"),
}

def valid_file(path, size, digest):
    if not path.is_file() or path.stat().st_size != size:
        return False
    checksum = hashlib.sha256() if len(digest) == 64 else hashlib.sha1(f"blob {size}\0".encode())
    with path.open("rb") as source:
        for block in iter(lambda: source.read(4 * 1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest() == digest


def download_file(url, dest, size, digest, report):
    if valid_file(dest, size, digest):
        report(size)
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    partial = dest.with_name(dest.name + "." + digest + ".part")
    offset = partial.stat().st_size if partial.exists() else 0
    if offset >= size:
        if valid_file(partial, size, digest):
            partial.replace(dest)
            report(size)
            return
        partial.unlink()
        offset = 0
    headers = {"Accept-Encoding": "identity", "User-Agent": "Studysh-Desktop"}
    if offset:
        headers["Range"] = f"bytes={offset}-"
    with urlopen(Request(url, headers=headers), timeout=90) as response:
        if response.status == 206:
            if response.headers.get("Content-Range") != f"bytes {offset}-{size - 1}/{size}":
                raise IOError("Unexpected download range for " + dest.name)
        elif response.status == 200:
            offset = 0
        else:
            raise IOError(f"Unexpected HTTP {response.status} for {dest.name}")
        with partial.open("ab" if offset else "wb") as target:
            report(offset)
            for block in iter(lambda: response.read(1024 * 1024), b""):
                target.write(block)
                offset += len(block)
                report(offset)
    if offset != size:
        raise IOError(f"Incomplete {dest.name} ({offset}/{size} bytes). Retry to resume.")
    if not valid_file(partial, size, digest):
        partial.unlink()
        raise IOError("Checksum mismatch for " + dest.name + ". Retry to download it again.")
    partial.replace(dest)


def prepare(model_dir: Path):
    total = sum(size for size, _ in FILES.values())
    completed = 0
    for name, (size, digest) in FILES.items():
        print("Checking or downloading " + name, file=sys.stderr, flush=True)
        last = 0
        def report(position):
            nonlocal last
            now = time.monotonic()
            if now - last >= 0.25 or position == size:
                print(json.dumps(dict(done=completed + position, total=total, file=name)), flush=True)
                last = now
        url = f"https://huggingface.co/{MODEL_ID}/resolve/{REVISION}/{name}"
        download_file(url, model_dir / name, size, digest, report)
        completed += size


def hide_helper_windows():
    # Packages such as python-sox run external probes during import.
    if os.name == "nt":
        import subprocess

        class HiddenProcess(subprocess.Popen):
            def __init__(self, *args, **kwargs):
                kwargs["creationflags"] = kwargs.get("creationflags", 0) | subprocess.CREATE_NO_WINDOW
                super().__init__(*args, **kwargs)

        subprocess.Popen = HiddenProcess


def load_model(model_dir):
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    import torch
    from qwen_tts import Qwen3TTSModel
    torch.set_num_threads(min(8, max(1, (os.cpu_count() or 4) // 2)))
    return Qwen3TTSModel.from_pretrained(
        str(model_dir), device_map="cpu", dtype=torch.float32, attn_implementation="eager"
    )


def serve(model_dir: Path):
    # stdout is reserved for JSON-lines IPC. Divert third-party progress and
    # diagnostics to stderr to keep the Rust/WebView protocol intact.
    protocol = sys.stdout
    sys.stdout = sys.stderr
    if not (model_dir / ".studysh-ready").is_file():
        protocol.write('{"error":"Qwen3 model has not been downloaded yet."}\n')
        protocol.flush()
        return 1
    try:
        import torch
        import soundfile as sf
        model = load_model(model_dir)
        protocol.write('{"ready":true}\n')
        protocol.flush()
    except Exception as exc:
        protocol.write(json.dumps({"error": f"Unable to load Qwen3-TTS: {exc}"}, ensure_ascii=False) + "\n")
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
        protocol.write(json.dumps(result, ensure_ascii=False) + "\n")
        protocol.flush()
    return 0


if __name__ == "__main__":
    hide_helper_windows()
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--prepare", action="store_true")
    group.add_argument("--verify", action="store_true")
    group.add_argument("--serve", action="store_true")
    parser.add_argument("--model-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.serve:
            sys.exit(serve(args.model_dir))
        if args.verify:
            load_model(args.model_dir)
        else:
            prepare(args.model_dir)
    except Exception as error:
        print(f"Qwen3-TTS error: {error}", file=sys.stderr)
        sys.exit(1)
