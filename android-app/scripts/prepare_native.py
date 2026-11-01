#!/usr/bin/env python3
"""Fetch pinned native sources; verify archives before extraction. Python 3.10+."""
import hashlib
import io
import pathlib
import shutil
import tarfile
import urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
LLAMA_REV = "fb85a288d72abbd5e5daa8de96e6f8bfa7b5ab46"
SOURCES = [
    (f"https://codeload.github.com/ggml-org/llama.cpp/tar.gz/{LLAMA_REV}", "9fe1d67f6639b27aeb5bb94c2308cc8d5c05b3d257baf062003bfe9c4f928dba", ROOT/"third_party/llama.tar.gz"),
    ("https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar", "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7", ROOT/"app/libs/sherpa-onnx-2.0.1.aar"),
]
for url, expected, target in SOURCES:
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        print("Downloading", target.name, flush=True)
        temporary = target.with_suffix(target.suffix + ".part")
        urllib.request.urlretrieve(url, temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != expected:
            temporary.unlink()
            raise SystemExit("Checksum mismatch: " + target.name)
        temporary.replace(target)
archive = ROOT/"third_party/llama.tar.gz"
output = ROOT/"third_party/llama.cpp"
if not output.exists():
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            path = pathlib.PurePosixPath(member.name)
            if path.is_absolute() or ".." in path.parts or member.issym() or member.islnk():
                raise SystemExit("Unsafe source archive entry")
        tar.extractall(output.parent)
    (output.parent / ("llama.cpp-" + LLAMA_REV)).rename(output)
print("Native dependencies ready.")
