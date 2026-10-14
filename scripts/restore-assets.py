#!/usr/bin/env python3
"""Restore the game's gitignored Mojang assets (textures/ and sounds/) from Mojang's official servers.

Run from anywhere; paths resolve against the repository root. Standard library only.

    python scripts/restore-assets.py              # newest release, download + verify
    python scripts/restore-assets.py --list       # print what the game expects (offline)
    python scripts/restore-assets.py --check      # verify what is on disk (offline)
    python scripts/restore-assets.py --version 1.21.4
    python scripts/restore-assets.py --all-sounds # every sound in the asset index (large)

What it does
  1. version_manifest_v2.json -> pick the version (default: latest.release).
  2. That version's JSON -> downloads.client (jar, SHA1-verified) and assetIndex.
  3. Extracts assets/minecraft/textures/{block,item,gui,colormap} plus the entity skins the game names,
     into ./textures/ (the layout the code expects).
  4. For each sound the game names, looks up minecraft/sounds/<path> in the asset index, downloads
     https://resources.download.minecraft.net/<hash[:2]>/<hash>, verifies the SHA1 and saves it to ./sounds/<path>.
  5. Reports anything the code expects that this version does not have. Nothing is substituted.

The expected paths are derived from the Java sources on every run (TextureAtlas, HudTextures, CrackOverlay,
TerrainGenerator, Game, SoundRegistry), so the list cannot drift from the code. See docs/ASSETS.md.
The downloaded files are Mojang's: they are gitignored and must not be committed or published.
Exit code: 0 all present, 1 something the code expects is missing, 2 network/integrity failure.
"""
import argparse
import hashlib
import io
import json
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "main" / "java" / "com" / "example"
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES_URL = "https://resources.download.minecraft.net"
JAR_PREFIX = "assets/minecraft/"
BULK_TEXTURE_DIRS = ("textures/block/", "textures/item/", "textures/gui/", "textures/colormap/", "textures/particle/",
                     "textures/environment/", "textures/entity/chest/", "textures/entity/player/wide/",
                     "textures/entity/equipment/humanoid/", "textures/entity/equipment/humanoid_leggings/", "textures/entity/experience/",
                     "textures/font/")
UA = {"User-Agent": "minecraft-clone-asset-restore/1.0"}
EXTRA_FILES = ("texts/splashes.txt",)  # plain data files outside textures/ that the game reads
DEST = ROOT  # where textures/ and sounds/ are written (--dest overrides, used by the offline test)


# --------------------------------------------------------------------------- expected assets (from source)

def _read(rel):
    return (SRC / rel).read_text(encoding="utf-8")


def _files(directory, prefix, count):
    return [f"{directory}/{prefix}{i + 1}.ogg" for i in range(count)]


def expected_sounds():
    """Sound file paths (relative to sounds/) by group, mirroring SoundRegistry's files()/mobSounds() helpers."""
    src = _read("audio/SoundRegistry.java")
    groups = {}
    for m in re.finditer(r'register\(new SoundGroup\("([^"]+)",\s*(.*?),\s*AudioCategory\.', src, re.S):
        gid, expr = m.group(1), m.group(2).strip()
        if gid.startswith("armor.equip.") or "+ m" in expr:
            continue
        if expr.startswith("files("):
            a = re.match(r'files\("([^"]+)",\s*"([^"]+)",\s*(\d+)\)', expr)
            groups[gid] = _files(a[1], a[2], int(a[3]))
        elif expr.startswith("new String[]"):
            groups[gid] = re.findall(r'"([^"]+)"', expr)
    mats = re.search(r'for \(String m : new String\[\]\{([^}]*)\}', src)
    for mat in re.findall(r'"(\w+)"', mats.group(1)) if mats else []:
        groups["armor.equip." + mat] = _files("item/armor", "equip_" + mat, 6)
    pat = (r'mobSounds\(AudioCategory\.\w+,\s*"(\w+)",\s*"([^"]+)",\s*"(\w+)",\s*(\d+),\s*"(\w+)",\s*(\d+),'
           r'\s*"(\w+)",\s*(\d+),\s*"(\w+)",\s*(\d+),')
    for m in re.finditer(pat, src):
        name, d, sp, sn, hp, hn, dp, dn, stp, stn = m.groups()
        groups[f"mob.{name}.say"] = _files(d, sp, int(sn))
        groups[f"mob.{name}.hurt"] = _files(d, hp, int(hn))
        groups[f"mob.{name}.death"] = [d + "/death.ogg"] if int(dn) == 1 and dp == "death" else _files(d, dp, int(dn))
        groups[f"mob.{name}.step"] = _files(d, stp, int(stn))
    return groups


def expected_textures():
    """Texture paths relative to the repo root (textures/...), plus which HUD sprites are all-or-nothing."""
    tex = set(re.findall(r'REAL_TEXTURE_PATHS\.put\(Tile\.\w+,\s*"([^"]+)"\)', _read("engine/TextureAtlas.java")))
    tex |= {f"textures/block/destroy_stage_{i}.png" for i in range(10)}  # CrackOverlay, all-or-nothing
    tex |= set(re.findall(r'"(textures/colormap/[^"]+)"', _read("world/TerrainGenerator.java")))
    tex |= set(re.findall(r'EntityTexture\.load\("(textures/entity/[^"]+)"', _read("game/Game.java")))
    # real files the game reads by literal path: realTexture(key, "textures/...", ...) and guiSprite("textures/...")
    tex |= set(re.findall(r'(?:realTexture\([^,]+,\s*|guiSprite\()"(textures/[^"]+\.png)"', _read("game/Game.java")))
    tex |= set(re.findall(r'CHEST_SKIN = "(textures/[^"]+)"', _read("engine/TextureAtlas.java")))
    hud = _read("engine/HudTextures.java")
    hud_dir = re.search(r'DIR = "([^"]+)"', hud).group(1)
    tex |= {hud_dir + n for n in re.findall(r'(?:load|optional)\("([^"]+\.png)"\)', hud)}
    return sorted(tex)


def expected_alternatives():
    """Skins the game can find under several file names (newest first): at least one of each group must exist.
    Minecraft 1.21.5 renamed the pig, cow and chicken skins, so the code lists both the new and the old name."""
    groups = []
    for body in re.findall(r'EntityTexture\.loadFirst\(\s*new String\[\]\{([^}]*)\}', _read("game/Game.java")):
        names = tuple(re.findall(r'"(textures/entity/[^"]+)"', body))
        if names:
            groups.append(names)
    return groups


def expected_all():
    sounds = expected_sounds()
    textures = expected_textures()
    if not textures or not sounds:
        sys.exit("Could not derive the expected asset lists from the sources (has the code layout changed?)")
    return textures, sounds


# --------------------------------------------------------------------------- network helpers

def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def fetch_json(url):
    return json.loads(fetch(url).decode("utf-8"))


def sha1(data):
    return hashlib.sha1(data).hexdigest()


def sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# --------------------------------------------------------------------------- steps

def pick_version(manifest, wanted):
    vid = wanted or manifest["latest"]["release"]
    for v in manifest["versions"]:
        if v["id"] == vid:
            return v
    sys.exit(f"Version {vid!r} is not in the version manifest")


def restore_textures(version_json, textures, report):
    dl = version_json["downloads"]["client"]
    print(f"Downloading client jar ({dl['size'] / 1e6:.1f} MB) ...")
    jar = fetch(dl["url"])
    if sha1(jar) != dl["sha1"]:
        sys.exit("Client jar SHA1 mismatch - refusing to extract")
    groups = expected_alternatives()
    wanted_entity = {t for t in textures if t.startswith("textures/entity/")} | {n for g in groups for n in g}
    extracted = 0
    with zipfile.ZipFile(io.BytesIO(jar)) as zf:
        names = set(zf.namelist())
        for name in sorted(names):
            if not name.startswith(JAR_PREFIX) or name.endswith("/"):
                continue
            rel = name[len(JAR_PREFIX):]
            if ".." in rel.split("/"):
                continue  # zip-slip guard
            if rel.startswith(BULK_TEXTURE_DIRS) or rel in wanted_entity or rel in EXTRA_FILES:
                target = DEST / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(zf.read(name))
                extracted += 1
        report["textures_missing"] = [t for t in textures if (JAR_PREFIX + t) not in names]
        report["textures_missing"] += [" | ".join(g) for g in groups if not any((JAR_PREFIX + n) in names for n in g)]
    print(f"Extracted {extracted} texture files")


def restore_sounds(version_json, sounds, resources_url, all_sounds, report):
    index = fetch_json(version_json["assetIndex"]["url"])["objects"]
    prefix = "minecraft/sounds/"
    if all_sounds:
        wanted = sorted(k[len(prefix):] for k in index if k.startswith(prefix))
    else:
        wanted = sorted({f for fl in sounds.values() for f in fl})
    missing, bad, done, skipped = [], [], 0, 0
    for rel in wanted:
        entry = index.get(prefix + rel)
        if entry is None:
            missing.append(rel)
            continue
        target = DEST / "sounds" / rel
        if target.is_file() and sha1_file(target) == entry["hash"]:
            skipped += 1
            continue
        data = fetch(f"{resources_url}/{entry['hash'][:2]}/{entry['hash']}")
        if sha1(data) != entry["hash"]:
            bad.append(rel)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        done += 1
    report["sounds_missing"] = missing
    report["sounds_bad_hash"] = bad
    print(f"Sounds: {done} downloaded, {skipped} already correct, {len(missing)} not in this version, {len(bad)} failed SHA1")


def check_disk(textures, sounds):
    miss_t = [t for t in textures if not (DEST / t).is_file()]
    miss_t += [" | ".join(g) for g in expected_alternatives() if not any((DEST / n).is_file() for n in g)]
    wanted = sorted({f for fl in sounds.values() for f in fl})
    miss_s = [s for s in wanted if not (DEST / "sounds" / s).is_file()]
    return miss_t, miss_s


def summarize(report, version_id):
    mt, ms = report.get("textures_missing", []), report.get("sounds_missing", [])
    print(f"\n=== Restore report (Minecraft {version_id}) ===")
    print(f"Textures the code expects but this version lacks: {len(mt)}")
    for t in mt:
        print("  -", t)
    print(f"Sounds the code expects but this version lacks: {len(ms)}")
    for s in ms:
        print("  -", s)
    for s in report.get("sounds_bad_hash", []):
        print("  ! SHA1 mismatch:", s)
    return not (mt or ms or report.get("sounds_bad_hash"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--version", help="Minecraft version id (default: latest release in the manifest)")
    ap.add_argument("--list", action="store_true", help="print the expected assets from the source and exit (offline)")
    ap.add_argument("--check", action="store_true", help="verify the expected files exist on disk and exit (offline)")
    ap.add_argument("--all-sounds", action="store_true", help="download every sound in the asset index, not only the ones the code uses")
    ap.add_argument("--skip-textures", action="store_true")
    ap.add_argument("--skip-sounds", action="store_true")
    ap.add_argument("--dest", default=str(ROOT), help=argparse.SUPPRESS)
    ap.add_argument("--manifest-url", default=MANIFEST_URL, help=argparse.SUPPRESS)
    ap.add_argument("--resources-url", default=RESOURCES_URL, help=argparse.SUPPRESS)
    args = ap.parse_args()
    global DEST
    DEST = Path(args.dest).resolve()

    textures, sounds = expected_all()
    unique_sounds = sorted({f for fl in sounds.values() for f in fl})
    if args.list:
        print(f"{len(textures)} textures, {len(unique_sounds)} sound files in {len(sounds)} groups")
        for t in textures:
            print(t)
        for g in expected_alternatives():
            print("any of: " + " | ".join(g))
        for s in unique_sounds:
            print("sounds/" + s)
        return 0
    if args.check:
        mt, ms = check_disk(textures, sounds)
        print(f"Missing on disk: {len(mt)}/{len(textures)} textures, {len(ms)}/{len(unique_sounds)} sounds")
        for p in mt + ["sounds/" + s for s in ms]:
            print("  -", p)
        return 1 if (mt or ms) else 0

    try:
        manifest = fetch_json(args.manifest_url)
        v = pick_version(manifest, args.version)
        print(f"Using Minecraft {v['id']} ({v.get('type', '?')})")
        version_json = fetch_json(v["url"])
        report = {}
        if not args.skip_textures:
            restore_textures(version_json, textures, report)
        if not args.skip_sounds:
            restore_sounds(version_json, sounds, args.resources_url, args.all_sounds, report)
    except (OSError, ValueError, KeyError) as e:  # URLError/HTTPError are OSError subclasses
        print(f"Network or integrity failure: {e}", file=sys.stderr)
        print("If you are behind a corporate web filter, run this on an unfiltered network.", file=sys.stderr)
        return 2
    ok = summarize(report, v["id"])
    mt, ms = check_disk(textures, sounds)
    print(f"On disk now: {len(textures) - len(mt)}/{len(textures)} textures, {len(unique_sounds) - len(ms)}/{len(unique_sounds)} sounds")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
