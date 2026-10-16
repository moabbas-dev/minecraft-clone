#!/usr/bin/env python3
"""Restore Mojang's vanilla world generation data (data/minecraft/worldgen/** and data/minecraft/tags/**) into ./data/.

Run from anywhere; paths resolve against the repository root. Standard library only.

    python scripts/restore-worldgen-data.py --jar path/to/client.jar   # extract from a local client jar (offline)
    python scripts/restore-worldgen-data.py                            # download the newest release jar, then extract
    python scripts/restore-worldgen-data.py --version 26.3             # download a specific version, then extract
    python scripts/restore-worldgen-data.py --check                    # verify ./data has the noise definitions (offline)

What it does
  1. Either opens the given client jar, or: version_manifest_v2.json -> pick the version (default: latest.release)
     -> that version's JSON -> downloads.client (jar, SHA1-verified).
  2. Extracts every file under data/minecraft/worldgen/ and data/minecraft/tags/ to ./data/ with the same layout
     (data/minecraft/worldgen/noise/continentalness.json, ...). Nothing is substituted or generated.

The world generation port (src/main/java/com/example/worldgen) reads these files, e.g. NoiseParametersLoader reads
data/minecraft/worldgen/noise/*.json. The files are Mojang's: data/ is gitignored and must not be committed or published.
Exit code: 0 success, 1 nothing to extract / data missing, 2 network or integrity failure.
"""
import argparse
import hashlib
import io
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
PREFIXES = ("data/minecraft/worldgen/", "data/minecraft/tags/")
UA = {"User-Agent": "minecraft-clone-worldgen-restore/1.0"}


# --------------------------------------------------------------------------- network helpers

def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def fetch_json(url):
    return json.loads(fetch(url).decode("utf-8"))


def sha1(data):
    return hashlib.sha1(data).hexdigest()


def pick_version(manifest, wanted):
    vid = wanted or manifest["latest"]["release"]
    for v in manifest["versions"]:
        if v["id"] == vid:
            return v
    sys.exit(f"Version {vid!r} is not in the version manifest")


def download_jar(version):
    manifest = fetch_json(MANIFEST_URL)
    v = pick_version(manifest, version)
    print(f"Using Minecraft {v['id']} ({v.get('type', '?')})")
    dl = fetch_json(v["url"])["downloads"]["client"]
    print(f"Downloading client jar ({dl['size'] / 1e6:.1f} MB) ...")
    jar = fetch(dl["url"])
    if sha1(jar) != dl["sha1"]:
        raise ValueError("Client jar SHA1 mismatch - refusing to extract")
    return jar


# --------------------------------------------------------------------------- extraction

def extract(jar_bytes, dest):
    counts = {p: 0 for p in PREFIXES}
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as zf:
        for name in sorted(zf.namelist()):
            if name.endswith("/"):
                continue
            prefix = next((p for p in PREFIXES if name.startswith(p)), None)
            if prefix is None:
                continue
            if ".." in name.split("/"):
                continue  # zip-slip guard
            target = dest / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(zf.read(name))
            counts[prefix] += 1
    for prefix, n in counts.items():
        print(f"Extracted {n} files under {prefix}")
    return sum(counts.values())


def check(dest):
    noise_dir = dest / "data" / "minecraft" / "worldgen" / "noise"
    files = sorted(noise_dir.glob("*.json")) if noise_dir.is_dir() else []
    print(f"{len(files)} noise definitions in {noise_dir}")
    return len(files) > 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", help="extract from this local client jar instead of downloading")
    ap.add_argument("--version", help="Minecraft version id to download (default: latest release in the manifest)")
    ap.add_argument("--check", action="store_true", help="verify ./data contains the noise definitions and exit (offline)")
    ap.add_argument("--dest", default=str(ROOT), help=argparse.SUPPRESS)
    args = ap.parse_args()
    dest = Path(args.dest).resolve()

    if args.check:
        return 0 if check(dest) else 1
    try:
        jar = Path(args.jar).read_bytes() if args.jar else download_jar(args.version)
    except (OSError, ValueError, KeyError) as e:  # URLError/HTTPError are OSError subclasses
        print(f"Network or integrity failure: {e}", file=sys.stderr)
        print("If you are behind a corporate web filter, pass --jar with a locally available client jar.", file=sys.stderr)
        return 2
    if extract(jar, dest) == 0:
        print("The jar contains no data/minecraft/worldgen or data/minecraft/tags files", file=sys.stderr)
        return 1
    return 0 if check(dest) else 1


if __name__ == "__main__":
    sys.exit(main())
