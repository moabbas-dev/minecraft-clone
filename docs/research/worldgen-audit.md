# World generation audit (read-only inventory before the vanilla 26.3 rebuild)

Scope: `src/main/java/com/example/world`, its callers, tests and docs. Line numbers are from the working tree on master (clean at start).
Vanilla class names are the ones listed in `.claude/CLAUDE.md` section 14 or recalled; every vanilla name here still has to be confirmed in the 26.3 jar before use.
Legend: INVENTED = Minecraft-imitating, to delete after the vanilla replacement works. KEEP = project infrastructure or already-verified data.

## 1. Generation pipeline today

| Concern | Class.method | File:line |
|---|---|---|
| Entry (per chunk) | `World.generateChunk` -> load from disk, else `generator.generate`, `applyPendingSpill`, `computeTint` | World.java:111-128 |
| Generator choice (flat vs normal) | `Game.startNewWorld` / `Game.loadWorld` | Game.java:478-481, 541-544 |
| Chunk streaming | `Game.updateChunkStreaming` calls `world.generateChunk` | Game.java:975-1000 |
| Terrain height + column fill | `TerrainGenerator.generate` (blended `baseHeight + fbm*amplitude`, clamp 4..SIZE_Y-20) | TerrainGenerator.java:80-146 |
| Noise | `SimplexNoise.noise/fbm` (2D, seed-shuffled permutation) | SimplexNoise.java:21-104 |
| Climate (5 axes) | `TerrainGenerator.rawClimateAt` (5 simplex fbm, 2 octaves) | TerrainGenerator.java:66-73 |
| Biome pick | `Biome.closest` (weighted nearest point), `Biome.weights` (inverse distance^2) | Biome.java:127-163 |
| Surface / subsurface | `biome.surface`, `biome.subsurface`, "beach" if `height <= 64` -> sand | TerrainGenerator.java:119-131 |
| Bedrock | y=0 plus `rnd.nextInt(y+1)==0` for y<=3 | TerrainGenerator.java:123-127 |
| Ores / gravel | `TerrainGenerator.oreOrStone` | TerrainGenerator.java:384-391 |
| Water / sea level | `SEA_LEVEL = 63`, fill `height+1..63`, ICE on top for SNOWY_TUNDRA / FROZEN_OCEAN | TerrainGenerator.java:29, 138-144 |
| Caves, ravines | `CaveCarver.carve` (random-walk worms), called after column fill | CaveCarver.java:129-146, TerrainGenerator.java:152 |
| Water in caves | `TerrainGenerator.floodSubmergedCaves` | TerrainGenerator.java:167-176 |
| Trees: placement | `TerrainGenerator.placeTrees`, `suitableColumn`, `fitsAndIsSuitable`, `slopeOk`, `speciesFor`, `clusterChanceFor`, `clearingChanceFor`, `planWithRetries` | TerrainGenerator.java:192-348 |
| Trees: shapes | `TreePlanner.plan/commit` (port of 26.3 TreeFeature, trunk/foliage/root placers) | TreePlanner.java:11-33 |
| Tree border spill | `World.spill`, `applyPendingSpill`, `WorldIO.appendSpill/takeSpill` | World.java:79-108, WorldIO.java:211-241 |
| Sapling growth | `SaplingGrowth.tryGrowTree` uses `TreeSpecies.place` | SaplingGrowth.java:153-180 |
| Vegetation (grass, flowers, plants) | none exists | - |
| Grass / foliage / water tint | `TerrainGenerator.computeTint` -> `BiomeColors.blendGrass/Foliage/Water` | TerrainGenerator.java:350-367 |
| Sky / fog / underwater fog tint | `TerrainGenerator.climateAt` -> `BiomeColors.blendSky/Fog/UnderwaterFog` -> `ClimateSample` | TerrainGenerator.java:369-382, Game.java:2106-2108, 2118-2119 |
| Colormap sampling | `Colormap.sampleDefinition` (vanilla formula, KEEP) | Colormap.java:58-66 |
| Fixed leaf tints | `SPRUCE_TINT`, `CHERRY_TINT`; birch/oak/jungle/acacia/mangrove/dark oak use colormap | ChunkMeshBuilder.java:38, 44, 62-73 |
| Spawn position | highest column in chunk (0,0) interior 2..13 above sea level | Game.java:496-513 |
| Rain / snow by biome | none. Only `noPrecipitation` for DESERT/SAVANNA/BADLANDS scales rain fog | Game.java:2131-2133 |
| Rain on farmland | `sky light >= 15` test, not biome | Game.java:1802-1804 |
| Weather | global state machine, no biome input | Weather.java:11-73 |
| Passive mob spawn | 1-in-10 chunk packs, grass only, fixed weights pig5 cow4 chicken5 | PassiveSpawner.java:27, 63-68, 85; Game.java:990-991 |
| Hostile spawn | light-based, 4 species by `nextInt(4)`, cap 8, no biome | MobSpawner.java:46, 59 |
| Flat world | `FlatWorldGenerator` bedrock, 2 dirt, grass, fixed tints | FlatWorldGenerator.java:9-38 |
| Chunk RNG | `chunkRandom`: `new Random(seed + cx*341873128712L + cz*132897987541L)` | TerrainGenerator.java:55-60 |

## 2. Invented / unsupported Minecraft behavior

### 2.1 INVENTED (replace with vanilla system)

| Item | Location | Pretends to be | Vanilla system |
|---|---|---|---|
| 16-entry `Biome` enum with hand-tuned temp/humid/cont/eros/weird points | Biome.java:21-70 | multi-noise biome placement | `MultiNoiseBiomeSource` + `Climate.ParameterList` built by `OverworldBiomeBuilder` (~60 biomes) |
| `closest` / `weights` metric (`PRIMARY_AXIS_WEIGHT=2`, EPSILON 1e-4, IDW) | Biome.java:75-76, 116-163 | `Climate` distance fit | `Climate.RTree` nearest search over 7 params incl. depth and offset |
| baseHeight/amplitude per biome and the blend | Biome.java:23-70, TerrainGenerator.java:109 | terrain shape | `NoiseRouter` final density, `NoiseChunk`, spline-driven `initial_density_without_jaggedness` |
| Clamp `max(4, min(SIZE_Y-20, h))` | TerrainGenerator.java:116 | world limits | noise settings `height`, `min_y` |
| `heightNoise` 5-octave simplex at 1/96, climate at 1/300 and 1/800, seeds `seed+1000..5000` | TerrainGenerator.java:30-31, 44-49, 67-77 | vanilla noises | `NormalNoise` (temperature, vegetation, continentalness, erosion, ridge/weirdness) from `RandomState`, positional splitter |
| `SimplexNoise` (2D, `Random`-shuffled permutation, `fbm` normalised) | SimplexNoise.java:1-104 | Perlin/Simplex used by vanilla | `ImprovedNoise`, `PerlinNoise`, `NormalNoise`, `SimplexNoise` (vanilla's own, 3D, Xoroshiro/Legacy RNG). Only the 26.3 `BiomeColors` swamp special case in vanilla uses a simplex-type noise |
| `CaveCarver` (all constants lines 42-105, salts 54-56, `isCarveable` 115-121, class doc claims "1.17.1-era") | CaveCarver.java:1-369 | caves and ravines | `CaveWorldCarver` / `CanyonWorldCarver` seeded by `WorldgenRandom.setLargeFeatureSeed`, plus noise caves (cheese, spaghetti, noodle) in `NoiseRouter`, plus `Aquifer`s |
| `floodSubmergedCaves` | TerrainGenerator.java:167-176 | water in caves | `Aquifer.NoiseBasedAquifer` (fluid picker, lava below the lava level) |
| Sea level 63 hard-coded; `SEA_LEVEL` used by Game and tests | TerrainGenerator.java:29; Game.java:505 | sea level | `NoiseGeneratorSettings.seaLevel` (63 in the overworld, to verify) |
| `beach = height <= SEA_LEVEL+1` -> sand; "4 deep" subsurface (`y > height-4`) | TerrainGenerator.java:119-131 | beaches / surface depth | `SurfaceRules` (`SurfaceRuleData.overworld`), surface-depth noise, `stoneDepthAbove`, beach biomes from `OverworldBiomeBuilder` |
| Bedrock gradient (y=0, probability `1/(y+1)` for y<=3) | TerrainGenerator.java:123-127 | bedrock floor | surface rule `verticalGradient("bedrock_floor")`, 5 layers from min_y |
| `oreOrStone` (diamond 1/200 y<16, gold 1/120 y<32, iron 1/70 y<64, coal 1/40 y<128, gravel 1/500) per block | TerrainGenerator.java:384-391 | ore generation | `OrePlacements` / `OreFeature` (veins with size, `discard_chance_on_air_exposure`, trapezoid/uniform `HeightRangePlacement`, count), ore veins in `NoiseRouter` (`OreVeinifier`), disks (clay, sand, gravel), blobs (granite, diorite, andesite, tuff, dirt, gravel) |
| Stone only (no deepslate, tuff, etc.) | TerrainGenerator.java:133 | rock layers | surface rule deepslate gradient (y -8..0) plus ore/blob features |
| Snow surface block `SNOW_BLOCK` for SNOWY_TUNDRA, STONE for MOUNTAINS, TERRACOTTA/RED_SAND for BADLANDS (no banding) | Biome.java:28, 31-32 | biome surfaces | surface rules (grass + `snow` layer, `SurfaceRuleData.badlands` bands, windswept gravel/stone rules) |
| Ice at sea level only for two biomes | TerrainGenerator.java:138-143 | freezing | `Biome.shouldFreeze` / `SnowAndFreezeFeature` (`freeze_top_layer`), `TemperatureModifier.FROZEN`; no snow layer block exists in `BlockType` |
| Tree density `1/treeChance` blended by IDW | Biome.java:23-70, TerrainGenerator.java:110-113 | tree counts | `VegetationPlacements` / `TreePlacements`: `CountPlacement`/`PlacementUtils.countExtra`, `RarityFilter`, `InSquarePlacement`, `HeightmapPlacement`, `BiomeFilter`, `SurfaceWaterDepthFilter` |
| `speciesFor` (e.g. PLAINS 10% birch, FOREST 40% birch, spruce for MOUNTAINS) | TerrainGenerator.java:307-324 | tree species by biome | per-biome placed features (`trees_plains`, `trees_birch_and_oak`, `trees_taiga` ...) with `WeightedPlacedFeature` |
| Cluster satellites (`clusterChanceFor`, 1-3 satellites within `minSpacing*2`) | TerrainGenerator.java:225-229, 245-264, 326-339 | clumping | none in vanilla; clumping comes from forest placement counts and noise-based count (`ExtraCount`) |
| Clearing chance 0.15 for BIRCH_FOREST | TerrainGenerator.java:214, 346-348 | clearings | none |
| `SLOPE_LIMIT = 4`, `slopeOk` | TerrainGenerator.java:291-303 | slope rule | none (vanilla has `wouldSurvive` + `BlockPredicateFilter` only) |
| `TreeSpecies.canopyRadius / maxTotalHeight / minSpacing` and min-spacing rejection | TreeSpecies.java:19-27, TerrainGenerator.java:279-289 | spacing | none (vanilla trees can overlap) |
| `suitableColumn`: top must be GRASS_BLOCK / SNOW_BLOCK / MUD | TerrainGenerator.java:267-275 | sapling survival | `BlockPredicate.wouldSurvive(sapling)`; `TreeFeature` ground check |
| `planWithRetries` (5 tries) | TerrainGenerator.java:234-243 | repeated feature attempts | placement modifiers repeat the feature per attempt |
| `chunkRandom` formula and one shared `Random` for columns, ores, trees | TerrainGenerator.java:55-60, 83 | decoration seed | `WorldgenRandom.setDecorationSeed(worldSeed, chunkMinX, chunkMinZ)` then `setFeatureSeed(seed, index, step)`; random-consumption order matters |
| `SaplingGrowth` random: `new Random(random.nextLong())` | SaplingGrowth.java:60, 180 | sapling RNG | `SaplingBlock.advanceTree` with `RandomSource` (growth rules are not world generation but share `TreePlanner`) |
| Spawn: best column of chunk 0,0 interior | Game.java:496-513 | world spawn | `Climate.Sampler.findSpawnPosition` / spawn target from the noise router, then a heightmap search (names to verify) |
| `BiomeColors` IDW blend of tints (`BLEND_SHARPNESS`) | BiomeColors.java:30, 189-264 | biome blend | `BiomeColors` colour cache, "Biome Blend" box average over a block radius (default 5) of each column's own biome colour |
| Swamp grass: lerp of 0x4C763C / 0x6A7039 by seeded fbm at 1/60 | BiomeColors.java:37-40, 183-187 | swamp grass | `GrassColorModifier.SWAMP` (threshold on `Biome.BIOME_INFO_NOISE`, to verify) |
| Fixed SPRUCE_TINT (0.38,0.50,0.38) and CHERRY_TINT (0.97,0.75,0.84); the CHERRY comment says not verified | ChunkMeshBuilder.java:36-44 | static leaf colours | `FoliageColor` constants for spruce/birch (to verify); cherry leaves are untinted textures (to verify). BIRCH_LEAVES currently uses the colormap (ChunkMeshBuilder.java:62) |
| `defTemperature/defDownfall` per biome, "sourced from long-stable data ... not extracted from any file" | Biome.java:89-97; CHERRY_GROVE "best-effort" 64-67 | biome climate values | each biome JSON `temperature`, `downfall`, `temperature_modifier` |
| `ClimateSample` record (5 fields + dominant + weights + 6 colours) | ClimateSample.java:17-20 | climate snapshot | `Climate.TargetPoint`, `BiomeSource.getNoiseBiome` |
| Fog, water, sky, underwater-fog tables keyed by `Biome` ordinal (16 entries) | BiomeColors.java:72-138 | biome visuals | lines 69-71 say read from the 26.3 jar = real DATA, KEEP values, re-key by biome id. Stale comments at 47-67 still say "ADAPTED/artistic" and contradict this |
| Water fog scale 0.85 for SWAMP/MANGROVE via weights | Game.java:2140-2150 | biome attribute | biome `water_fog_end_distance` attribute (verified in PARITY 26); reads `Biome.SWAMP.ordinal()` |
| Flat-world tints `{0.62,0.75,0.38}`, `{0.42,0.68,0.20}` | FlatWorldGenerator.java:25-26 | plains tint | unsourced; use the plains colormap sample |
| Mob spawning ignores biome; weights comment "grassy-biome table" | PassiveSpawner.java:17, 27 | spawn lists | `MobSpawnSettings` per biome, `NaturalSpawner.spawnMobsForChunkGeneration` (`creature_spawn_probability`) |
| `Weather` thunder 35%, single state machine | Weather.java:12, 18, 65-73 | rain/thunder | `ServerLevel.advanceWeatherCycle` has separate rain and thunder timers (to verify). Outside worldgen scope but mentioned in section 1 |
| `Game` dark disc uses literal 63 for non-flat | Game.java:2189 | horizon height | dimension/level data horizon height |

### 2.2 KEEP (not Minecraft behavior, or verified)

- `Chunk` storage API, `heightMap`, `skyBlocker`, tint arrays (Chunk.java), `World` chunk map, `Chunk.key`.
- `World.spill` / `pendingSpill` / `WorldIO` spill files: needed while features are written chunk-by-chunk. Review once real multi-chunk feature generation exists.
- `TreePlanner` shapes and `TreePlanner.Plan` staging (ported from the jar, PARITY 27). RNG consumption order must be rechecked when wired to vanilla feature seeds. Forms not ported: fancy oak, mega jungle/spruce (PARITY.md:517).
- `Colormap.sampleDefinition` (vanilla formula); `Colormap.sample` (Colormap.java:48) has no callers.
- `ChunkMeshBuilder` tint application, `LightEngine`, `FluidSim`, `RandomTicks`, `BlockSupport`.
- `FlatWorldGenerator` layout and `WorldSettings` (infrastructure; its Y positions change with min_y).
- `SkyColors` and the BiomeColors numeric tables (verified from jar per BiomeColors.java:69-71).
- `Game` F3 climate overlay (Game.java:3582-3660) and test-controller dump (4100-4128): infrastructure that must be re-pointed.

## 3. Dependents (what breaks if the API changes)

### Biome (enum, 152 textual hits)
- Production: `TerrainGenerator` (surface, subsurface, baseHeight, amplitude, treeChance, `closest`, `weights`, ordinal-indexed arrays; ~20 hits), `BiomeColors` (16 hits; ordinal-indexed tables lines 76-138, `b.defTemperature/defDownfall` 162-165, `Biome.SWAMP/BADLANDS` 162-180), `ClimateSample` (field `dominant`), `Game` (2131-2132 `DESERT/SAVANNA/BADLANDS`; 2147 `SWAMP/MANGROVE.ordinal()` over `weights`; 3597, 3645-3660 overlay lists all biomes; 4114-4121 dump), comments in `CaveCarver`, `SaplingSpecies`.
- Tests: `BiomeTest` (4 tests on `closest`, `weights`, ordinal), `BiomeAtmosphereTest` (36 hits), `TerrainGeneratorTreeTest` (33), `NewBiomesTest` (21).

### ClimateSample / `climateAt`
- `WorldGenerator.climateAt` default null (WorldGenerator.java:15); `World.climateAt` (World.java:32); `TerrainGenerator.climateAt` (369).
- `Game`: `skyTint()`, `fogColor()` (2107-2108), `underwaterFogColor()` (2118-2119), `dominant()` for precipitation (2131), `weights()` (2144), overlay (3592-3650, uses temperature..weirdness, grass/foliage/water colours), dump (4104-4127). `climate == null` is also the "flat world" signal (2189, 4109).
- Tests: `WorldGenerationTest` (31-65: `dominant`, `waterColor`), `NewBiomesTest` (23, 112-113, 130, 232-235: `dominant()`, `weights()`).

### TerrainGenerator (97 hits outside itself)
- Production: `Game` (480, 505 `SEA_LEVEL`, 543), `Biome`/`ClimateSample`/`CaveCarver` (doc refs), `SaplingGrowth` (docs 59, 145, 163), `ChunkMeshBuilder` (doc 158), `TreeSpecies` (doc 12).
- Tests: `TerrainGeneratorTreeTest` (static methods `speciesFor`, `clusterChanceFor`, `clearingChanceFor`, `slopeOk`, `fitsAndIsSuitable`, `placeTrees`), `NewBiomesTest` (29), `WorldGenerationTest` (10), `CaveCarverTest` (5), `SubmergedCaveTest` (`floodSubmergedCaves`, `SEA_LEVEL`), `SaplingGrowthTest` (23, 207: builds a `World` on a real generator).

### WorldGenerator (interface)
- Implemented by `TerrainGenerator`, `FlatWorldGenerator`; used by `World` (13, 26) and `Game` (478, 541).
- `FlatWorldGenerator` is constructed in 24 test files (e.g. `BucketTest`, `FluidSimTest`, `TreeSpillTest`, `LightEngineTest`, `CrossChunkLightingTest`, `RaycastTest`, `BlockPlacerTest`, `MiningControllerTest`, `SubmersionTest`, `PlayerSurvivalTest`, `PlayerMovementGameplayTest`, `SwimmingGameplayTest`, `MobSystemTest` (uses `GRASS_HEIGHT`), `HostileMobTest`, `PassiveMobTest`, `BreedingTest`, `BabyBehaviourTest`, `BowTest`, `StatusEffectsTest`, `VanillaAlignmentTest`, `ItemEntityManagerTest`, `CookingStationManagerTest`, `TorchAndFarmlandTest`). They depend on ground at y 0..3 (flat layout); keep the flat generator contract stable or these break.

### CaveCarver
- Only caller: `TerrainGenerator.generate` (152). Tests: `CaveCarverTest` (7 tests: determinism, order independence, bedrock, `neverThrowsOrWritesOutsideValidWorldHeight`), `SubmergedCaveTest`.

### SimplexNoise
- Production: `TerrainGenerator` (6 fields), `BiomeColors` (swampNoise, line 149). Tests: `SimplexNoiseTest` (5 tests, properties of the class itself).

### TreeSpecies / TreePlanner / SaplingSpecies
- `TerrainGenerator` (species selection and planning), `SaplingSpecies.treeSpecies` (SaplingSpecies.java:14), `SaplingGrowth` (154, 180), `World` (`TreePlanner.replaceableByTree`, 83, 104), `Game.spawnTree` (3802-3806: `TreeSpecies.valueOf`, `TreePlanner.plan(...).commit`), `testctl/Commands.java:138` and `GameAccess.java:36` (species name from the API), `BlockPlacer` (SaplingSpecies, 77), `ChunkMeshBuilder` (leaf types).
- Tests: `VanillaTreeTest` (11 tests), `TreeSpillTest`, `TerrainGeneratorTreeTest`, `SaplingGrowthTest`, `NewBiomesTest`.

## 4. World-size assumptions

Height is `Chunk.SIZE_Y = 256` (Chunk.java:9). `SIZE_Y` appears 47 times in 16 files: Chunk 8 (9, 13, 18, 19, 53, 115, 120, 151), World 6 (80, 140, 165, 172, 207, 255), LightEngine 10 (28 alias, 60, 67, 75, 80, 90, 116, 176, 214, 264), TerrainGenerator 2 (116, 281), CaveCarver 2 (258, 337), TreePlanner 3 (62, 92, 148), FluidSim 2 (138, 195), SaplingGrowth 2 (86, 168), ChunkMeshBuilder 1 (114), RandomTicks 1 (35, sections of 16), BlockPlacer 1 (75), MobSpawner 1 (74), tests 9 (SaplingGrowthTest 4, TerrainGeneratorTreeTest 2, NewBiomesTest 1, WorldGenerationTest 1).

Other literal limits:
- Y index math: `Chunk.index = (y*16+z)*16+x` with y from 0 (Chunk.java:48-50); `getLocal` returns AIR for y<0 (53). `heightMap` default 0 means "empty" and also "y=0"; `getColumnHeightAt` returns 0 for an unloaded chunk (World.java:149-153); `skyBlocker` uses -1 for none.
- Packed keys with 8-bit y: `World.posKey` (World.java:219-221, y & 0xFF), `FluidSim.key` (FluidSim.java:60-73), `LightEngine` entry encoding (LightEngine.java:387-404, y & 0xFF at bit 52). 12-bit: `SaplingGrowth.packKey` (64, y & 0xFFF). `World.localKey` uses `y << 8` and decodes `y = k >> 8` (World.java:69-71, 102): negative y breaks. `TreePlanner.key` offsets by 4096 (103-116): fine.
- Void/world bottom: `Entity.VOID_Y = -16` (Entity.java:25, 109), `ItemEntityManager.VOID_Y = -16` (43, 132), `PlayerSurvival.VOID_Y = -64` (45, 165). With min_y -64 all three must change (entities at y < -16 would die inside deep caves).
- Sea / horizon: `SEA_LEVEL 63`, `Game.java:2189` literal 63f, `FluidSim.java:194` `y < 0 -> BEDROCK`, `FluidSim.java:153` `y > 0`.
- Spawn/world: `MobSpawner` range `max(1, ...)`..`SIZE_Y-3` (74); `ChunkMeshBuilder` loops `ly = 0..min(SIZE_Y-1, colHeight+12)` (108-115); `CaveCarver` `y < 1`, `MIN_CAVE_Y 5`, `MAX_CAVE_Y 100`, ravine 10..90; `TerrainGenerator` bedrock at y=0; `FlatWorldGenerator` bedrock at 0, grass at 3 (`GRASS_HEIGHT`).
- Per chunk memory: `blocks` byte[65536], `skyLight` byte[65536], `blockLight` byte[65536] (Chunk.java:13, 18, 19). At 384 tall: 98304 each; blocks as short: 196608.
- Save format: `.chunk` file is the raw `blocks` array with no header or version (`WorldIO.saveChunk` 195-204, `loadChunk` 247-253, `Chunk.exportBlocks/importBlocks` 141-160; `importBlocks` does `System.arraycopy(data, 0, blocks, 0, blocks.length)`, so a shorter old file throws). Spill file records: `byte lx, short y, byte lz, byte id` (WorldIO.java:215-218, 231): y read unsigned, id one byte. Tree spill carries no negative y.
- Block ids: explicit `BlockType.id` field, not ordinal (BlockType.java:267, `BY_ID[256]` 349-360). 198 enum entries, max id 252, 58 of 0..255 unused: 6, 8, 10, 11, 19, 21-23, 25-40, 43, 44, 50-53, 55, 59, 60, 62-78, 81, 83-85, 88, 253-255. Only 3 consecutive ids above the top. The enum mixes blocks and about 120 items (tools, food, armor), so vanilla terrain blocks cannot fit well.
- Id ranges hard-coded in code: `isWater` 231-238 (303), `isLava` 239-243 (307), `fluidAmount` (327-328), `fluidBlock` (337-339), `BlockSupport.byId(246 + moisture)` (35), `WorldIO` item stacks write `type.id` as int (269), same in `Inventory.java:241`, `PlayerArmor.java:68`. Inventory/armor already use int, so only the chunk array, spill record and `BY_ID` need widening.
- `BlockTypeTest` asserts `byId(255) == AIR` and `byId(6) == AIR` (lines 41, 44).

Ripple of y = -64..319 (384) plus `short` ids: `Chunk` (arrays, index, heightMap origin, all bounds), `World` (bounds, keys), `LightEngine` (alias, key encoding, loops), `FluidSim` (key, bounds), `SaplingGrowth` (key, scan), `RandomTicks` (section loop origin), `ChunkMeshBuilder` (loop origin), `WorldIO` (new versioned chunk format and spill), `TreePlanner` (`y0 >= 1`), all void thresholds, `MobSpawner`, `Game` literals, `FlatWorldGenerator` and its 24 test files (Y positions), plus tests listed in section 6.

## 5. Block roster gaps

Present in `BlockType`: STONE, GRASS_BLOCK, DIRT, COBBLESTONE, BEDROCK, WATER (+flow), LAVA (+flow), SAND, GRAVEL, GOLD_ORE, IRON_ORE, COAL_ORE, COPPER_ORE, DIAMOND_ORE, SANDSTONE, CLAY, SNOW_BLOCK, ICE, TERRACOTTA, RED_SAND, MUD, MANGROVE_ROOTS, OBSIDIAN, NETHERRACK, the 9 tree log/leaf pairs, 4 oak sapling stages, FARMLAND (+7 levels).

Missing from `BlockType` and texture present in `textures/block/` (checked by file existence):
- deepslate (`deepslate.png`, `deepslate_top.png`), tuff, granite, diorite, andesite, calcite, dripstone_block, pointed_dripstone (`pointed_dripstone_up_tip.png` found; other dripstone variants not checked), packed_mud, coarse_dirt, rooted_dirt, moss_block, podzol (`podzol_top.png` found; `podzol.png` absent, so side/top names differ), mycelium (`mycelium_top.png`), packed_ice, blue_ice, powder_snow, snow (layer, `snow.png` is the block texture), red_sandstone, amethyst_block, budding_amethyst, smooth_basalt, basalt (`basalt_top.png`), magma, sculk, glow_lichen, vine, azalea leaves and flowering azalea leaves.
- Ores: lapis_ore, redstone_ore, emerald_ore, and all deepslate ores (coal, iron, copper, gold, lapis, redstone, emerald, diamond): all textures exist. `raw_iron_block` exists.
- Terracotta bands: white, orange, yellow, brown, red, light_gray terracotta textures exist; only plain TERRACOTTA is a block.
- Plants: short_grass, tall_grass_top, fern, large_fern_top, dead_bush, cactus_side, sugar_cane, kelp, seagrass, lily_pad, other saplings (birch, spruce, jungle, acacia, dark_oak, cherry), mangrove_propagule, flowers (dandelion, poppy, azure_bluet, cornflower, lily_of_the_valley, oxeye_daisy, allium, blue_orchid, red_tulip), pink_petals, bamboo_stalk, sweet_berry_bush_stage3, pumpkin_side, melon_side: textures exist, no blocks.
- Textures NOT found: `moss_carpet.png`, `infested_stone.png`. Texture count in `textures/block/`: 1405 files.
- Also absent as blocks: snow layer, water/lava cauldron variants, ladder-type blocks, stone bricks, sandstone variants, fluids are present. Waterlogging, block states and axes (logs have no axis) are not modelled (TreePlanner.java:29-30).

## 6. Tests encoding invented behavior

DELETE or REWRITE with the old generator:
- `NewBiomesTest` (all 11 tests): reachability of OCEAN/FROZEN_OCEAN/MANGROVE/BIRCH/DARK/CHERRY in the 5-axis space (32, 193), ice at sea level (42, 62), mud surface (142), species per biome (205, 265, 278), reload tests (241, 291) built on the generator.
- `BiomeTest` (all 4): `closest`/`weights` of the invented enum.
- `TerrainGeneratorTreeTest` (all 16): `speciesFor`, cluster/clearing chance, `slopeOk`, `fitsAndIsSuitable`, `placeTrees` determinism and spacing, desert never grows trees.
- `CaveCarverTest` (7) and `SubmergedCaveTest` (2): carve and flood behaviour. The determinism / order independence ideas should be re-asserted on the new pipeline, not these methods.
- `SimplexNoiseTest` (5): tests only the class to be removed (replace with tests of the vanilla noise against jar-derived reference values).
- `WorldGenerationTest`: `sameSeedProducesIdenticalChunkBlocks...` (17), `generatingTheSameChunkTwiceIsIdempotent` (69), `chunkHeightMapMatches...` (85), `everyGeneratedColumnHasBedrockAtTheBottom` (107, y=0 assumption): keep the property, change the data; `climateAtIsAPureFunction...` (31) and `bakedWaterTintMatches...` (50) use `ClimateSample`.
- `BiomeAtmosphereTest` (13 tests): the "real biome file values" tests (158, 167, 176, 183) check verified data and should be ported to the new biome keys; blending tests (28, 39, 57, 65, 93, 106, 117, 136, 146) encode the IDW blend and ordinal indexing.
- `SaplingGrowthTest` lines 21-23, 207-219 depend on `TerrainGenerator` and `SIZE_Y`.

SURVIVE (infrastructure, vanilla data, or flat-world based): `VanillaTreeTest` (tree shapes from the jar), `TreeSpillTest`, `WorldIoRoundTripTest` (except `chunkRoundTripsExactBytes` 148, which assumes the raw format), `WorldIoDayCountTest`, `WorldIoSanitizeTest`, `BlockTypeTest` (adjust ids 41, 44), `ChunkCoordTest`, `LightEngineTest`, `CrossChunkLightingTest`, `FluidSimTest`, `BucketTest`, `RaycastTest`, `WeatherTest`, `SaplingGrowthTest` (rest), `ChunkMeshBuilderFaceCullingTest`, all gameplay/entity tests using `FlatWorldGenerator` (24 files; need updating only if flat Y positions move).

## 7. Docs and comments mentioning the old system

Docs:
- `docs/PARITY.md`: section 14 (357-366, "sky colour is still this game's own blend", no snow-biome particles), section 18 (422-431, biome tables real; ties `BiomeAtmosphereTest` to the 16-biome enum), section 25 (488-495, no snow-biome rain particles), section 26 (497-508: Swamp/Mangrove water fog by weights, dark disc height 63), section 27 (510-521: trees real; "Forms not made" 517). Lines 221-236 list missing ores (redstone, lapis, emerald). No section describes terrain/caves/biome placement as a parity item, so nothing records them as "invented".
- `docs/ASSETS.md`: line 39 (colormap files read by `TerrainGenerator` -> `Colormap`; fallback tint), lines 198-203 (colormap section). Line 111 lists `biomeBlendRadius` as unread option.
- `docs/research/vanilla-deviations.md:17` ("Biome Blend ... TODO"). `docs/vanilla-rules.md` has no worldgen section.

Code comments (words approximate/roughly/starting point/best-effort/hand-tuned/ADAPTED):
- Biome.java:13 ("hand-tuned"), 33-70 (long narrative comments on invented placement), 64-67 ("best-effort"), 89-95 ("not extracted from any file").
- BiomeColors.java:35-36, 47-67 (stale "ADAPTED/artistic" lines versus verified lines 69-71), 21-30 (blend sharpness "analog of Biome Blend").
- CaveCarver.java:8-10 ("1.17.1-era", "not 1.18"), 30-31 ("project-specific starting point, not a verified exact constant"), 62-65, 99-101 ("approximated characteristic").
- TerrainGenerator.java:6-25 (class doc), 54 ("Mirrors vanilla's classic chunk decoration-seed formula", which it does not), 148-151, 178-190, 209-215, 341-345.
- ChunkMeshBuilder.java:36-50 (spruce/cherry tints, "this project's own tuning" for wind), Colormap.java:11-19 (says colour is sampled from raw noise; no longer true), ClimateSample.java:13-15 (explains why there is no depth axis), WorldGenerator.java:14, FlatWorldGenerator.java:23-29, SaplingGrowth.java:26, 59, 145, 163, TreeSpecies.java:6-16 (mixes a verified port with invented placement terms), BlockType.java:342 ("Approximate hand break time", not worldgen).

## Summary (10 lines)
1. Normal worlds are made by `TerrainGenerator.generate` (351 lines) over a 16-entry `Biome` enum, 2D simplex noise, `CaveCarver` worms and per-block ore dice; nothing uses vanilla noise, density functions, aquifers, surface rules or features.
2. Almost every constant in `TerrainGenerator`, `Biome`, `CaveCarver` and the tree placement rules (density, clusters, clearings, slope 4, min spacing) is invented; the only verified parts are `TreePlanner` shapes, colormap sampling and the BiomeColors sky/fog/water tables.
3. Sea level 63, spawn choice, beach rule, bedrock gradient and ice rules are also hand-made; weather, snow and mob spawning have no biome input at all.
4. Dependents: `Biome` in `TerrainGenerator`, `BiomeColors`, `ClimateSample`, `Game` (precipitation, water fog, F3 overlay, dump) and 4 test classes; `ClimateSample` in `Game` and 2 test classes; `TerrainGenerator` in `Game` plus 6 test classes.
5. Height 256 is baked into 47 `SIZE_Y` uses in 16 files, 8-bit/12-bit packed y keys (`World`, `FluidSim`, `LightEngine`, `SaplingGrowth`), void thresholds (-16 for entities, -64 for the player) and a headerless raw-byte chunk save; moving to y -64..319 needs a versioned save format.
6. Block ids: 198 enum entries, max id 252, 58 free ids, blocks and ~120 items share one enum, ids stored as one byte in chunks and spill files; widening to short touches `Chunk`, `WorldIO` spill, `BY_ID` and `BlockTypeTest`.
7. Missing terrain blocks with textures present: deepslate, tuff, granite, diorite, andesite, calcite, dripstone, packed mud, moss, podzol, mycelium, packed/blue ice, powder snow, amethyst, basalt, magma, sculk, lapis/redstone/emerald and all eight deepslate ores, terracotta band colours, plants and flowers.
8. Tests that encode the old generator: `NewBiomesTest`, `BiomeTest`, `TerrainGeneratorTreeTest`, `CaveCarverTest`, `SubmergedCaveTest`, `SimplexNoiseTest`, parts of `WorldGenerationTest` and `BiomeAtmosphereTest`; `VanillaTreeTest`, `TreeSpillTest`, lighting, fluid, IO and the 24 flat-world tests are infrastructure.
9. Docs: PARITY.md sections 14, 18, 25-27 and ASSETS.md line 39 touch this system; no doc admits terrain, biomes or caves are invented, and several code comments (`BiomeColors` 47-67, `Colormap` 11-19, `TerrainGenerator` 54) are stale or misleading.
10. Open risks to confirm in the 26.3 jar: the vanilla names above (marked "to verify"), fixed spruce/birch/cherry leaf colours, swamp grass modifier, the weather timers, and the spawn-finding method.
