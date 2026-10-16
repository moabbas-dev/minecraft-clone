# Decision: engine migration for vanilla 26.3 world generation

Status: decided, implementation in progress. Inputs: `docs/research/worldgen-audit.md` (read-only audit), the 26.3 client jar (`data/minecraft/worldgen/**`), minecraft.wiki ("Noise settings", "Tutorial:Adding a new dimension": overworld settings assume Y=-64 to Y=320, "Custom world generation").

## Current representation

- A chunk is 16 x 256 x 16. `Chunk.SIZE_Y = 256`, index `(y*16+z)*16+x`, `y` starts at 0. Blocks are `byte[65536]`, one `BlockType.id` per cell (ids are explicit, not ordinals; `BY_ID[256]`).
- 198 enum entries (blocks and ~120 items share the enum), max id 252, 58 free ids below 256.
- Packed keys with 8-bit y (`World.posKey`, `FluidSim.key`, `LightEngine` entries), 12-bit (`SaplingGrowth`), `y << 8` in `World.localKey`.
- `.chunk` save = the raw `blocks` array, no header. Spill records: `byte lx, short y, byte lz, byte id`.
- Void thresholds: entities -16, items -16, player -64.

## Verified vanilla requirement (26.3 `noise_settings/overworld.json`)

- `noise.min_y = -64`, `noise.height = 384` (so Y = -64..319), `sea_level = 63`, `default_block = stone`, `default_fluid = water`, `legacy_random_source = false`.
- Worldgen data of the jar refers to 69 distinct block ids in features/carvers/surface rules alone (deepslate family, tuff, granite, diorite, andesite, all ores and their deepslate variants, calcite/dripstone etc. through the biome and feature lists, snow, powder snow, packed ice, blue ice, mud, podzol, sculk vein, glow lichen, saplings, flowers ...). One byte per block cannot hold them next to the items already in the enum.

## Decision

1. **Vertical range.** `Chunk` gets `MIN_Y = -64`, `SIZE_Y = 384` (the height, as in vanilla), `MAX_Y = 319`. Every world API (`World.getBlock/setBlock`, `Chunk.getLocal/setLocal`, entities, rays, light, fluids, save/spill) uses real Minecraft Y, negative included. Array indexing subtracts `MIN_Y` inside `Chunk` only. No remapping, cropping or shifting of vanilla terrain.
2. **Block storage.** Chunk blocks become `short[]` (ids 0..32767, 2 bytes per cell). `BlockType` ids stay explicit; new vanilla blocks get new ids from 256 upward; `BY_ID` is sized from the highest id. Block type, block state (properties), storage and rendering stay separate concepts: this step only widens the storage type, state-like variants that exist today (farmland moisture, fluid levels, sapling stages) keep their current encoding.
3. **Save format.** Versioned chunk file (magic + format version + min_y + height + id width + payload). Spill records get int id and int y. Worlds written by the old generator (headerless, 256 high, invented terrain) are legacy: they are detected by the missing header and not read as chunks (they cannot be converted without keeping the invented terrain). The world is reported as incompatible rather than silently corrupted. The saves folder was already emptied at the user's request.
4. **Central constants, not search-and-replace.** `Chunk.MIN_Y/MAX_Y/SIZE_Y`, `World.SEA_LEVEL`-style values come from one place. Each literal 255/256/63/64/0 is examined for what it means (world top, sea level, void, array size, packed key bits) before changing.
5. **Packed keys** are rebuilt with a sign-safe y field (e.g. `y - MIN_Y` in a 9+ bit field, or wider longs) in `World`, `FluidSim`, `LightEngine`, `SaplingGrowth`, `TreePlanner`.
6. **Void/kill height** follows vanilla: below `min_y - 64` (read from the verified source before changing; entities and items currently use -16, player -64).
7. **Flat world.** Vanilla flat starts at min_y too (`flat_level_generator_preset` in the jar); `FlatWorldGenerator` is moved to its vanilla layers/positions via a single `GROUND_Y` style constant and the 24 test files that used y 0..3 are updated to use it.
8. **Generator replacement** happens after the engine accepts the new range: vanilla RNG/noise -> density functions + router -> climate + biome source -> `NoiseChunk` interpolation -> aquifers -> surface rules -> carvers -> features/ores/vegetation/trees -> structures. `TerrainGenerator`, `Biome`, `CaveCarver`, `SimplexNoise`, `ClimateSample`'s invented fields, the per-biome tree placement rules and their tests are deleted when their replacement works (not before: the game must keep compiling and running during the migration).

## Risks

- Lighting: y range and key encoding (`LightEngine`) and 1.5x memory per chunk (3 arrays, blocks now 2 bytes). Mitigation: unit tests of `LightEngineTest`/`CrossChunkLightingTest` re-run at negative y.
- Hidden `y >= 0` assumptions (heightMap value 0 meaning "empty", `getColumnHeightAt`, mesh builder loops, `MobSpawner`, `RandomTicks` sections, `TreePlanner y0 >= 1`). Mitigation: grep every `SIZE_Y`, `0`/`255` bound; tests with blocks at y -64, -1, 0, 319.
- Test churn: 24 flat-world tests, `BlockTypeTest` id checks, `WorldIoRoundTripTest.chunkRoundTripsExactBytes`.
- Rendering: chunk mesh height 384 doubles vertex work only where blocks exist (the builder scans up to the column top).

## Order

1. Engine: Chunk constants + short storage + World/keys/light/fluid/sapling/tree/mesh/entities void + WorldIO versioned format + FlatWorldGenerator constant; run the existing suite; add negative-Y tests.
2. New blocks needed by worldgen data are added with the stage that needs them (deepslate etc.), textures from the user's `textures/` only.
3. Vanilla pipeline stages (agents + main), each with deterministic tests; then cut over `World.generateChunk` and delete the invented generator.
