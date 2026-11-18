#!/usr/bin/env python3
"""Fetch the production manifest for CI/desktop inference validation; no third-party packages."""
import argparse
import hashlib
from pathlib import Path
import re
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("directory", type=Path)
args = parser.parse_args()
source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/oyayasu/studysh/supertonic/SupertonicAssets.kt").read_text()
revision = re.search(r'REVISION = "([a-f0-9]+)"', source).group(1)
assets = re.findall(r'ModelAsset\("([^"]+)", (\d+)L, "([a-f0-9]+)"\)', source)
profiles = re.findall(r'SupertonicVoice\("([^"]+)", "[^"]+"\)', source)
core_paths = {
    "onnx/duration_predictor.onnx", "onnx/text_encoder.onnx",
    "onnx/vector_estimator.onnx", "onnx/vocoder.onnx",
    "onnx/tts.json", "onnx/unicode_indexer.json",
}
expected_paths = core_paths | {f"voice_styles/{voice}.json" for voice in profiles}
paths = [name for name, _, _ in assets]
if (not profiles or len(set(profiles)) != len(profiles)
        or len(set(paths)) != len(paths) or set(paths) != expected_paths
        or any(int(size) <= 0 or len(checksum) != 64 for _, size, checksum in assets)):
    raise ValueError("Incomplete or invalid production manifest for the configured voices")
for name, size, checksum in assets:
    target = args.directory / name
    target.parent.mkdir(parents=True, exist_ok=True)
    def valid(file):
        return (file.is_file() and file.stat().st_size == int(size)
                and hashlib.sha256(file.read_bytes()).hexdigest() == checksum)
    if valid(target):
        continue
    part = target.with_name(target.name + ".part")
    url = f"https://huggingface.co/Supertone/supertonic-3/resolve/{revision}/{name}?download=true"
    print(f"Downloading {name} ({size} bytes)", flush=True)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=120) as stream, part.open("wb") as dest:
                while chunk := stream.read(1024 * 1024):
                    dest.write(chunk)
            if not valid(part):
                raise ValueError(f"Size or SHA-256 mismatch: {name}")
            part.replace(target)
            break
        except Exception:
            if attempt == 2:
                raise
print(f"All {len(assets)} Supertonic assets verified for {', '.join(profiles)}.")
