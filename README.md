# Minecraft clone

A Java/LWJGL Minecraft-style game. Build and run with Maven from the repository root (the game loads assets
relative to the working directory):

```
mvn -o exec:java
```

See `TESTING.md` for the test suite and the live test controller.

## Game assets (textures, sounds, options)

The real textures and sounds are Mojang's files. They are **not in git** (`textures/`, `sounds/`, `minecraft-sounds/`
and `options.txt` are gitignored) and must not be committed or published. Without them the game still runs,
using procedural tiles, a plain HUD and silent sounds.

To regenerate everything from Mojang's official servers (needs Python 3, no packages):

```
python scripts/restore-assets.py              # newest release: client jar -> textures/, asset index -> sounds/
python scripts/restore-assets.py --version 1.21.4   # or pin a version
python scripts/restore-assets.py --check      # offline: list expected files that are missing on disk
python scripts/restore-assets.py --list       # offline: print every path the code expects
```

The script derives the expected file list from the Java sources, verifies every download against Mojang's SHA1
hashes, and prints any file the code expects that the chosen version no longer has (nothing is substituted;
exit code 1). It must reach `piston-meta.mojang.com`, `piston-data.mojang.com` and
`resources.download.minecraft.net`; a corporate web filter that blocks these will make it fail with exit code 2.
The full manifest of expected paths is in [`docs/ASSETS.md`](docs/ASSETS.md).

`options.txt` is a per-user settings file in vanilla's `key:value` format, written by the in-game Options screens.
Delete it to get defaults; the key list and defaults are in `docs/ASSETS.md`.
