# 26.3 surface (material) rules: scope for the Java clone

Read-only scoping. In 26.3 `SurfaceRules` and `SurfaceSystem` no longer exist. They became the data-driven **material rule / material condition** system: `levelgen/material/MaterialSystem` (the old `SurfaceSystem`), `MaterialRuleContext`, `MaterialRules` (registry bootstrap and builders), `material/rule/*` (5 rule types) and `material/condition/*` (10 condition types), loaded from `data/minecraft/worldgen/material_rule/**` (42 files) and `material_condition/**` (8 files). Sources: the 26.3 client jar (`javap -c -p`), the extracted data, the repo. "UNVERIFIED" marks anything not read from bytecode or data. Tables marked "generated" were produced by script over the data. Date of the pass: 2026-10-19.

## 0. What changed versus the 1.21 `SurfaceRules`

- Rules and conditions are **registry-driven codecs with JSON references**: `MaterialRule.CODEC` and `MaterialCondition.CODEC` accept either an inline object or a string id of another file (`Holder`, see `lambda$static$0/1` in `MaterialRule`/`MaterialCondition`). The overworld rule tree (`noise_settings/overworld.json`: `"material_rule": "minecraft:overworld"`) is split over 37 reachable rule files and 7 named condition files.
- The *whole underground and ore-vein logic is in the same tree*: root `material_rule/overworld.json` is `sequence[ bedrock_floor, overworld/copper_ore_vein, overworld/iron_ore_vein, (above_preliminary_surface ? overworld/surface), overworld/underground ]`. So `buildSurface` also places bedrock, ore veins (`OreVeinRule`), deepslate and sulfur cave bands, not only grass and sand.
- Rule/condition ids keep a typo from the Java class names: the banded terracotta rule is registered as **`minecraft:bandlands`** (class `BandlandsRule`, not `badlands`).
- A rule evaluates `tryApply(x, y, z)` returning a `BlockState` or `null` (= "no change, try the next rule of the enclosing sequence"); there is a compile step (`MaterialRule.compile(MaterialRuleContext) -> RuleEvaluator`, `MaterialCondition.compile(ctx) -> ConditionEvaluator`) that binds noise samplers and caches.
- `minSurfaceLevel` comes from the density function `chunk_surface_level` sampled at `(x, 0, z)` (section 3.3), not from `NoiseChunk.preliminarySurfaceLevel`.

## 1. Registered types and exact semantics

`MaterialRules.bootstrapRules` (51 bytes) and `bootstrapConditions` (109 bytes).

### 1.1 Rules (5)

| JSON `type` | class | fields | behaviour (bytecode) |
|---|---|---|---|
| `minecraft:block` | `BlockRule` (record, itself the evaluator) | `result_state` (BlockState) | returns the state |
| `minecraft:bandlands` | `BandlandsRule` (enum singleton) | none | `ctx.getBand(x, y, z)` -> `MaterialSystem.getBand` (section 3.4) |
| `minecraft:sequence` | `SequenceRule` | `sequence` (list of rules; size 1 compiles to the element) | first non-null `tryApply` of the elements |
| `minecraft:condition` | `ConditionRule` | `if_true` (condition), `then_run` (rule) | `null` if the condition is false, else `then_run.tryApply` |
| `minecraft:ore_vein` | `OreVeinRule` | `ore_block, raw_ore_block, filler_block, raw_ore_chance (0..1), density, richness, filler_gap` (density functions) | see the carvers/aquifers report, section 7 |

### 1.2 Conditions (10) with the context fields they read

`MaterialRuleContext` (per chunk, mutable, fields set by `updateXZ(x, z, gradX, gradZ)` and `updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, y)`): `blockX, blockY, blockZ, surfaceGradientX, surfaceGradientZ, surfaceDepth (set in updateXZ = MaterialSystem.getSurfaceDepth(x, z)), stoneDepthAbove, stoneDepthBelow, waterHeight`, lazily: `getBiome()` (reset to null on every `updateY`; = `biomeGetter.apply(pos(blockX, blockY, blockZ))`), `getSurfaceSecondary()` (cached per XZ update), `getMinSurfaceLevel()` (cached per XZ update), `getSeaLevel()`, `possibleBiomes()`. Lazy conditions (`LazyXZCondition`, `LazyYCondition`) cache their boolean per XZ update or per Y update (monotone counters `lastUpdateXZ/lastUpdateY`).

| JSON `type` | class | fields | boolean result (bytecode) |
|---|---|---|---|
| `minecraft:biome` | `BiomeCondition` | `biome_is` (biome id or list/HolderSet) | `biomes.contains(ctx.getBiome())`; compile-time shortcut: if `possibleBiomes` (the 3x3-chunk set) is non-null and disjoint from the set -> constant false; if it is a subset -> constant true (pure optimisation, same result when the set is complete) |
| `minecraft:noise_threshold` | `NoiseThresholdCondition` | `noise` (NormalNoise id), `min_threshold`, `max_threshold`, `is_3d` (default false) | `min <= v && v <= max` with `v = noise.get(blockX, 0 (2D) or blockY (3D), blockZ)` as `float` widened to double; the sampler is cached per XZ (2D) or per Y update (3D) |
| `minecraft:vertical_gradient` | `VerticalGradientCondition` | `random_name` (Identifier), `true_at_and_below`, `false_at_and_above` (VerticalAnchor) | `y <= trueAt -> true`; `y >= falseAt -> false`; else `p = Mth.map(y, trueAt, falseAt, 1.0, 0.0)`; `random.at(blockX, y, blockZ).nextFloat() < p` where `random = RandomState.getOrCreateRandomFactory(random_name)` = `base.fromHashOf(random_name).forkPositional()` |
| `minecraft:y_above` | `YCondition` | `anchor`, `surface_depth_multiplier` (int, -20..20), `add_stone_depth` | `blockY + (addStoneDepth ? stoneDepthAbove : 0) >= anchorY + surfaceDepth * multiplier` |
| `minecraft:water` | `WaterCondition` | `offset`, `surface_depth_multiplier` (-20..20), `add_stone_depth` | `waterHeight == Integer.MIN_VALUE` **or** `blockY + (addStoneDepth ? stoneDepthAbove : 0) >= waterHeight + offset + surfaceDepth * multiplier` |
| `minecraft:temperature` | `TemperatureCondition` (enum) | none | `ctx.getBiome().coldEnoughToSnow(blockPos, seaLevel)`; **not used by the overworld files** |
| `minecraft:steep` | `SteepCondition` (enum) | none | `gradientX <= -4 \|\| gradientZ >= 4` (gradients from `getSurfaceGradientX/Z`, section 3.1) |
| `minecraft:not` | `NotCondition` | `invert` (condition) | negation |
| `minecraft:hole` | `HoleCondition` (enum) | none | `surfaceDepth <= 0` |
| `minecraft:above_preliminary_surface` | `AbovePreliminarySurfaceCondition` (enum) | none | `blockY >= ctx.getMinSurfaceLevel()` |
| `minecraft:stone_depth` | `StoneDepthCondition` | `offset`, `add_surface_depth`, `secondary_depth_range`, `surface_type` (`floor`/`ceiling`; `CaveSurface`) | `depth = ceiling ? stoneDepthBelow : stoneDepthAbove`; `surf = addSurfaceDepth ? surfaceDepth : 0`; `sec = range == 0 ? 0 : (int) Mth.map(ctx.getSurfaceSecondary(), -1.0, 1.0, 0.0, range)`; result `depth <= 1 + offset + surf + sec` |

Named conditions in `material_condition/` (generated aliases of `stone_depth`/`water` with fixed arguments): `on_floor` (floor, offset 0, no surface depth), `on_ceiling`, `under_floor` (floor, add_surface_depth), `under_ceiling`, `deep_under_floor` (floor, add_surface_depth, secondary range 6), `very_deep_under_floor` (floor, add_surface_depth, secondary range 30), `not_underwater` (water, offset -1, multiplier 0, no stone depth), `not_under_deep_water` (water, offset -6, multiplier -1, add_stone_depth).

VerticalAnchor forms used in the overworld rule tree: `absolute` (y_above) and `above_bottom` / `absolute` (vertical_gradient: `bedrock_floor` 0..5 above bottom; `deepslate` absolute 0..8).

## 2. Data inventory (generated)

Reachable from `material_rule/overworld.json`: 37 rule files and 7 condition files. Not reachable from the overworld root (other dimensions or noise settings): `bedrock_roof`, `end`, `nether`, `overworld_caves`, `overworld_floating_islands`. Node totals over the reachable files (a shared file is counted once, not per reference): rule nodes: `bandlands` 2, `block` 69, `condition` 123, `ore_vein` 2, `sequence` 35; condition nodes: `above_preliminary_surface` 1, `biome` 43, `hole` 3, `noise_threshold` 32, `not` 4, `steep` 5, `stone_depth` 5, `vertical_gradient` 2, `water` 2, `y_above` 10.

| material rule / condition file | rule nodes | condition nodes |
|---|---|---|
| `bedrock_floor` | block 1, condition 1 | vertical_gradient 1 |
| `overworld` | condition 1, sequence 1 | above_preliminary_surface 1 |
| `overworld/biome_surface` | condition 18, sequence 2 | biome 18 |
| `overworld/biome_surface/dappled_forest` | block 1, condition 1 | noise_threshold 1 |
| `overworld/biome_surface/default` | block 2, condition 1, sequence 1 |  |
| `overworld/biome_surface/dripstone_caves` | block 1 |  |
| `overworld/biome_surface/frozen_peaks` | block 4, condition 4, sequence 1 | noise_threshold 2, steep 1 |
| `overworld/biome_surface/grove` | block 1, condition 1, sequence 1 |  |
| `overworld/biome_surface/ice_spikes` | block 1, condition 1 |  |
| `overworld/biome_surface/jagged_peaks` | block 2, condition 2, sequence 1 | steep 1 |
| `overworld/biome_surface/mangrove_swamp` | block 1 |  |
| `overworld/biome_surface/mushroom_fields` | block 1 |  |
| `overworld/biome_surface/old_growth_pine_taiga` | block 2, condition 2, sequence 1 | noise_threshold 2 |
| `overworld/biome_surface/snowy_slopes` | block 2, condition 2, sequence 1 | steep 1 |
| `overworld/biome_surface/stony_peaks` | block 2, condition 1, sequence 1 | noise_threshold 1 |
| `overworld/biome_surface/stony_shore` | block 1, condition 1, sequence 1 | noise_threshold 1 |
| `overworld/biome_surface/sulfur_caves` | block 1, sequence 1 |  |
| `overworld/biome_surface/windswept_gravelly_hills` | block 1, condition 3, sequence 1 | noise_threshold 3 |
| `overworld/biome_surface/windswept_hills` | block 1, condition 1 | noise_threshold 1 |
| `overworld/biome_surface/windswept_savanna` | block 2, condition 2, sequence 1 | noise_threshold 2 |
| `overworld/copper_ore_vein` | ore_vein 1 |  |
| `overworld/gravel_or_stone_if_ceiling` | block 2, condition 1, sequence 1 |  |
| `overworld/iron_ore_vein` | ore_vein 1 |  |
| `overworld/powder_snow_surface` | block 1, condition 2 | noise_threshold 1 |
| `overworld/powder_snow_under_surface` | block 1, condition 2 | noise_threshold 1 |
| `overworld/sand_or_sandstone_if_ceiling` | block 2, condition 1, sequence 1 |  |
| `overworld/sulfur_cave_bands` | block 3, condition 3, sequence 1 | noise_threshold 3 |
| `overworld/surface` | bandlands 2, block 20, condition 46, sequence 11 | biome 10, hole 3, noise_threshold 8, not 4, y_above 10 |
| `overworld/under_biome_surface` | condition 14, sequence 2 | biome 14 |
| `overworld/under_biome_surface/default` | block 1 |  |
| `overworld/under_biome_surface/frozen_peaks` | block 4, condition 4, sequence 1 | noise_threshold 2, steep 1 |
| `overworld/under_biome_surface/grove` | block 1, sequence 1 |  |
| `overworld/under_biome_surface/jagged_peaks` | block 1 |  |
| `overworld/under_biome_surface/snowy_slopes` | block 2, condition 2, sequence 1 | steep 1 |
| `overworld/under_biome_surface/windswept_gravelly_hills` | block 2, condition 3, sequence 1 | noise_threshold 3 |
| `overworld/under_biome_surface/windswept_savanna` | block 1, condition 1 | noise_threshold 1 |
| `overworld/underground` | block 1, condition 2, sequence 1 | biome 1, vertical_gradient 1 |
| (condition) `deep_under_floor` | - | stone_depth 1 |
| (condition) `not_under_deep_water` | - | water 1 |
| (condition) `not_underwater` | - | water 1 |
| (condition) `on_ceiling` | - | stone_depth 1 |
| (condition) `on_floor` | - | stone_depth 1 |
| (condition) `under_floor` | - | stone_depth 1 |
| (condition) `very_deep_under_floor` | - | stone_depth 1 |

### 2.1 Noise ids used by conditions (generated)

| noise id (condition `noise_threshold`) | dimensionality |
|---|---|
| `calcite` | 2d |
| `gravel` | 2d |
| `ice` | 2d |
| `packed_ice` | 2d |
| `powder_snow` | 2d |
| `small_patch` | 2d |
| `sulfur_cave_gradient` | 3d |
| `surface` | 2d |
| `surface_swamp` | 2d |

Plus the noises `MaterialSystem` itself creates through `RandomState.getOrCreateNoise`: `surface`, `surface_secondary`, `clay_bands_offset`, `badlands_pillar`, `badlands_pillar_roof`, `badlands_surface`, `iceberg_pillar`, `iceberg_pillar_roof`, `iceberg_surface` (all 9 present in `data/minecraft/worldgen/noise/`). Every noise id above exists as a file under `worldgen/noise/`; parameters (examples): `surface` {firstOctave -6, amplitudes [0.938, 3 octaves]}, `surface_secondary` {amplitudes 1,1,0,1}, `sulfur_cave_gradient` {amplitudes 1,0,1, first octave -5}, `small_patch` {amplitudes [3.0], first octave -3}; repo loader: `worldgen/noise/NoiseParametersLoader`. Positional random names used: `minecraft:bedrock_floor`, `minecraft:deepslate` (vertical gradients), `minecraft:ore` (ore veins), `minecraft:clay_bands` (band generation), plus the base factory (`RandomState.random`) used directly for `getSurfaceDepth` and the frozen ocean extension.

### 2.2 Distinct block states written (generated)

32 distinct block ids are written by the reachable rules (15 are already in `BlockType`); none of the result states in the data sets properties. Texture files are looked up under `textures/block/` through `assets/minecraft/blockstates` -> models, as in the features report; all are present. `air` (frozen ocean hole) and `water`/`lava` are written as states, not textures.

| block | in `BlockType`? | texture file(s) | animated (.mcmeta) | #rule files |
|---|---|---|---|---|
| `air` | yes |  MISSING missingno |  | 1 |
| `bedrock` | yes | `bedrock` |  | 1 |
| `calcite` | NO | `calcite` |  | 1 |
| `cinnabar` | NO | `cinnabar` |  | 1 |
| `coarse_dirt` | NO | `coarse_dirt` |  | 4 |
| `copper_ore` | yes | `copper_ore` |  | 1 |
| `deepslate` | NO | `deepslate`, `deepslate_top` |  | 1 |
| `deepslate_iron_ore` | NO | `deepslate_iron_ore` |  | 1 |
| `dirt` | yes | `dirt` |  | 4 |
| `granite` | NO | `granite` |  | 1 |
| `grass_block` | yes | `dirt`, `grass_block_side`, `grass_block_side_overlay`, `grass_block_snow`, `grass_block_top` |  | 1 |
| `gravel` | yes | `gravel` |  | 1 |
| `ice` | yes | `ice` |  | 2 |
| `mud` | yes | `mud` |  | 1 |
| `mycelium` | NO | `dirt`, `grass_block_snow`, `grass_block_top`, `mycelium_side`, `mycelium_top` |  | 1 |
| `orange_terracotta` | NO | `orange_terracotta` |  | 1 |
| `packed_ice` | NO | `packed_ice` |  | 2 |
| `podzol` | NO | `dirt`, `grass_block_snow`, `grass_block_top`, `podzol_side`, `podzol_top` |  | 1 |
| `powder_snow` | NO | `powder_snow` |  | 2 |
| `raw_copper_block` | NO | `raw_copper_block` |  | 1 |
| `raw_iron_block` | NO | `raw_iron_block` |  | 1 |
| `red_sand` | yes | `red_sand` |  | 1 |
| `red_sandstone` | NO | `red_sandstone`, `red_sandstone_bottom`, `red_sandstone_top` |  | 1 |
| `sand` | yes | `sand` |  | 1 |
| `sandstone` | yes | `sandstone`, `sandstone_bottom`, `sandstone_top` |  | 2 |
| `snow_block` | yes | `snow` |  | 7 |
| `stone` | yes | `stone` |  | 15 |
| `sulfur` | NO | `sulfur` |  | 1 |
| `terracotta` | yes | `terracotta` |  | 1 |
| `tuff` | NO | `tuff` |  | 1 |
| `water` | yes | `water_still` | block/water_still | 1 |
| `white_terracotta` | NO | `white_terracotta` |  | 1 |

Blocks written outside the JSON by `MaterialSystem` code: `terracotta`, `orange_terracotta`, `yellow_terracotta`, `brown_terracotta`, `red_terracotta`, `white_terracotta`, `light_gray_terracotta` (clay bands), `snow_block`, `packed_ice` (frozen ocean icebergs), `defaultBlock` (stone, badlands pillars), `water` tests. `BlockType` has `TERRACOTTA` only.

### 2.3 Biome handling (generated)

29 biome ids appear in `biome_is` conditions of the reachable tree: `badlands`, `beach`, `dappled_forest`, `deep_frozen_ocean`, `deep_lukewarm_ocean`, `desert`, `dripstone_caves`, `eroded_badlands`, `frozen_ocean`, `frozen_peaks`, `grove`, `ice_spikes`, `jagged_peaks`, `lukewarm_ocean`, `mangrove_swamp`, `mushroom_fields`, `old_growth_pine_taiga`, `old_growth_spruce_taiga`, `snowy_beach`, `snowy_slopes`, `stony_peaks`, `stony_shore`, `sulfur_caves`, `swamp`, `warm_ocean`, `windswept_gravelly_hills`, `windswept_hills`, `windswept_savanna`, `wooded_badlands`. All of them are in the parameter list (unreachable ids referenced: none). The remaining 27 of the 56 biomes use only the default branches (`biome_surface/default` = `not_underwater ? grass_block : dirt`, `under_biome_surface/default` = `dirt`, `gravel_or_stone_if_ceiling`): `bamboo_jungle`, `birch_forest`, `cherry_grove`, `cold_ocean`, `dark_forest`, `deep_cold_ocean`, `deep_dark`, `deep_ocean`, `flower_forest`, `forest`, `frozen_river`, `jungle`, `lush_caves`, `meadow`, `ocean`, `old_growth_birch_forest`, `pale_garden`, `plains`, `river`, `savanna`, `savanna_plateau`, `snowy_plains`, `snowy_taiga`, `sparse_jungle`, `sunflower_plains`, `taiga`, `windswept_forest`.
Not referenced as a *name* but handled by code: `eroded_badlands` (`erodedBadlandsExtension`), `frozen_ocean`/`deep_frozen_ocean` (`frozenOceanExtension`, plus JSON `hole` handling).

## 3. `MaterialSystem` (`levelgen/material/MaterialSystem`, the old `SurfaceSystem`)

Constructed by `RandomState` (144-byte constructor) as `new MaterialSystem(randomState, defaultBlock, seaLevel, noiseRouter.chunkSurfaceLevel(), randomState.random)`: arguments `(RandomState, BlockState defaultBlock = stone, int seaLevel = 63, DensityFunction preliminarySurfaceFunction = overworld/chunk_surface_level, PositionalRandomFactory noiseRandom = the base positional factory of the world seed)` (constructor 144 bytes).

Constructor effects: `clayBandsOffsetNoise`, `surfaceNoise` (`surface`), `surfaceSecondaryNoise`, `badlandsPillarNoise`, `badlandsPillarRoofNoise`, `badlandsSurfaceNoise`, `icebergPillarNoise`, `icebergPillarRoofNoise`, `icebergSurfaceNoise` via `RandomState.getOrCreateNoise`; `clayBands = generateBands(noiseRandom.fromHashOf("minecraft:clay_bands"))`.

### 3.1 `buildSurface(RandomState, BiomeManager, WorldGenerationContext, ChunkAccess, NoiseChunk, MaterialRule, Set<Holder<Biome>>)` (659 bytes), exact flow

Setup: `minX/minZ` of the chunk; `column` = a `BlockColumn` view (anonymous `MaterialSystem$1`) with `getBlock(y)` = `chunk.getBlockState(x, y, z)` and `setBlock(y, state)` = `if (minY <= y <= maxY) { chunk.setBlockState(pos, state); if (state has fluid) chunk.markPosForPostProcessing(pos) }`. `highestFilledSectionIndex` -> `topY = sectionY * 16 + 15`. `volume` for the context = `DensityVolume(noiseChunk volume sizeX, max(topY - volume.minBlockY + 1, 1), sizeZ, volume.minBlockX, minBlockY, minBlockZ)`. `ctx = new MaterialRuleContext(this, randomState, volume, noiseChunk.cachingSamplers(), biomeManager::getBiome, worldGenContext, possibleBiomes)`; `evaluator = rule.compile(ctx)`.

For `localX` 0..15 (outer), `localZ` 0..15 (inner); `x, z` absolute:
1. `h = chunk.getHeight(WORLD_SURFACE_WG, localX, localZ)`; `y0 = h + 1`; `biome = biomeManager.getBiome(x, y0, z)`.
2. If `biome` is `eroded_badlands`: `erodedBadlandsExtension(column, x, z, y0, chunk)` (3.5).
3. `yStart = chunk.getHeight(WORLD_SURFACE_WG, localX, localZ) + 1` (read again after step 2).
4. `gradX = h(min(localX + 1, 15), localZ) - h(max(localX - 1, 0), localZ)`, `gradZ = h(localX, min(localZ + 1, 15)) - h(localX, max(localZ - 1, 0))` with `h` = `getHeight(WORLD_SURFACE_WG, ...)` (without the +1). `ctx.updateXZ(x, z, gradX, gradZ)`.
5. `stoneDepthAbove = 0; waterHeight = Integer.MIN_VALUE; runBottom = Integer.MAX_VALUE`. For `y` from `yStart` down to `chunk.getMinY()`:
   - `state = column.getBlock(y)`.
   - air: `stoneDepthAbove = 0; waterHeight = MIN_VALUE`.
   - non-empty fluid state: `if (waterHeight == MIN_VALUE) waterHeight = y + 1` (no rule is applied to fluid blocks).
   - otherwise (a block that is not air and has no fluid; `isStone()` is exactly `!isAir() && fluidState.isEmpty()`, i.e. any solid): `if (runBottom >= y) { runBottom = DimensionType.WAY_BELOW_MIN_Y; for (j = y - 1; j >= minY - 1; j--) { if (!isStone(column.getBlock(j))) { runBottom = j + 1; break; } } }`; `stoneDepthAbove++`; `stoneDepthBelow = y - runBottom + 1`; `ctx.updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, y)`; if `heightAccessor.minY <= y <= heightAccessor.maxY`: `r = evaluator.tryApply(x, y, z)`; if `r != null` `column.setBlock(y, r)`.
6. If `biome` is `frozen_ocean` or `deep_frozen_ocean`: `frozenOceanExtension(ctx.getMinSurfaceLevel(), biome.value(), column, pos, x, z, y0)` (3.6).

Notes: the biome for the two extension decisions is taken **once at (x, y0, z)**; inside the loop the condition biome is `getBiome(blockX, blockY, blockZ)` per block. `chunk.getHeight(WORLD_SURFACE_WG, ...)` is the first free y over the column as written by `doFill` (heightmap updated for every placed block). The loop writes through the chunk, so later blocks of the same column see earlier rule results only through `column.getBlock(j)` in the `runBottom` scan.

### 3.2 `getSurfaceDepth(x, z)` (48 bytes) and `getSurfaceSecondary(x, z)` (15 bytes)

`surfaceDepth = (int) (surfaceNoise.get(x, 0, z) * 2.75 + 3.0 + noiseRandom.at(x, 0, z).nextDouble() * 0.25)` (`d2i` truncation); `surfaceSecondary = surfaceSecondaryNoise.get(x, 0, z)` (float widened). `Noise.get(double, double, double) -> float` is the 26.3 noise interface (`synth/Noise`, 115 bytes; repo: `worldgen/noise/Noise`).

### 3.3 `getMinSurfaceLevel` (in `MaterialRuleContext`, 148 bytes)

`minSurfaceLevel = floor(preliminarySurfaceFunction(x, 0, z)) + surfaceDepth - 8`, cached per XZ update. The function value comes from `DensitySamplerSet.sampleValue(chunk_surface_level, x, 0, z)` (float), or, when `(x, z)` is inside the surface volume, from a buffer filled once for the whole column volume. `overworld/chunk_surface_level` is `interpolated(cell_size_xz 16, cell_size_y 1, overworld/preliminary_surface_level)`, with `preliminary_surface_level` a `find_top_surface` (density engine, other report). So the surface step **depends on the density engine and on the samplers bound to the chunk's `SamplerContext`** (the same `NoiseChunk.cachingSamplers()` used for the fill).

### 3.4 Terracotta bands: `generateBands(random)` (188 bytes), `makeBands` (83), `getBand(x, y, z)` (44)

- `bands = new BlockState[192]` filled with `terracotta`. `i = 0; while (i < 192) { i += random.nextInt(5) + 1; if (i < 192) bands[i] = orange_terracotta; i++; }`.
- `makeBands(random, bands, 1, yellow)`, `(…, 2, brown)`, `(…, 1, red)`, each: `n = random.nextIntBetweenInclusive(6, 15)`; repeat `n`: `size = minSize + random.nextInt(3)`, `start = random.nextInt(192)`; for `m` while `start + m < 192 && m < size`: `bands[start + m] = state`.
- White bands: `count = random.nextIntBetweenInclusive(9, 15)`; `i = 0, j = 0`; while `i < count && j < 192`: `bands[j] = white`; if `j - 1 > 0 && random.nextBoolean()` `bands[j - 1] = light_gray`; if `j + 1 < 192 && random.nextBoolean()` `bands[j + 1] = light_gray`; `i++`; `j += random.nextInt(16) + 4`.
- `getBand(x, y, z) = bands[(y + Math.round(clayBandsOffsetNoise.get(x, 0, z) * 4.0f) + 192) % 192]`.

### 3.5 `erodedBadlandsExtension(column, x, z, y0, heightAccessor)` (268 bytes)

`pillar = min(|badlandsSurface.get(x, 0, z) * 8.25|, badlandsPillar.get(x * 0.2, 0, z * 0.2) * 15.0f)`; return if `pillar <= 0`. `roof = |badlandsPillarRoof.get(x * 0.75, 0, z * 0.75) * 1.5|`. `top = 64.0 + min(pillar * pillar * 2.5, ceil(roof * 50.0) + 24.0)`; `maxY = Mth.floor(top)`; return if `y0 > maxY`. Scan `j = maxY` down while `j >= minY`: block is `defaultBlock` -> stop; block is `water` -> return the whole extension. Then from `j = maxY` down: while `j >= minY` and the block is air: `setBlock(j, defaultBlock)`. (Float/double mixing as in the bytecode: the pillar noise product is a float; port the exact casts.)

### 3.6 `frozenOceanExtension(minSurfaceLevel, biome, column, pos, x, z, y0)` (414 bytes)

`iceberg = min(|icebergSurface.get(x, 0, z) * 8.25|, icebergPillar.get(x * 1.28, 0, z * 1.28) * 15.0f)`; return if `iceberg <= 1.8`. `roof = |icebergPillarRoof.get(x * 1.17, 0, z * 1.17) * 1.5|`. `top = min(iceberg * iceberg * 1.2, ceil(roof * 40.0) + 14.0)`; if `biome.shouldMeltFrozenOceanIcebergSlightly(pos(x, seaLevel, z), seaLevel)` (`getTemperature(pos, seaLevel) > 0.1`) `top -= 2.0`; return if `top <= 2.0`. `bottom = seaLevel - top - 7.0`; `top += seaLevel`. `random = noiseRandom.at(x, 0, z)`; `snowLayers = 2 + random.nextInt(4)`; `snowStart = seaLevel + 18 + random.nextInt(10)`; `count = 0`. For `y` from `max(y0, (int) top + 1)` down to `minSurfaceLevel` (inclusive; `y0` and `minSurfaceLevel` are the 7th and 1st parameters): if (air and `y < (int) top` and `random.nextDouble() > 0.01`) or (water and `y > (int) bottom` and `y < seaLevel` and `random.nextDouble() > 0.15`): place `snow_block` while `count <= snowLayers && y > snowStart` (then `count++`) else `packed_ice`. The two `nextDouble` draws are short-circuited as described; port from `MaterialSystem.frozenOceanExtension` verbatim.

### 3.7 `topMaterial(rule, randomState, ctx, biomeGetter, chunk, samplers, pos, boolean fluidAbove)` (180 bytes)

Used by the carver mask application (carvers report, 5.5): builds a 1x1x1 `DensityVolume` at `pos`, a context with `possibleBiomes = null`, compiles the rule, computes `gradX/gradZ` from the chunk heightmaps, `updateXZ`, then `updateY(1, 1, fluidAbove ? y + 1 : Integer.MIN_VALUE, y)` and returns `Optional.ofNullable(evaluator.tryApply(x, y, z))`.

## 4. Minimum information the surface step needs

- **Per column:** `WORLD_SURFACE_WG` heightmap of the chunk (written by `doFill`) and its 4 neighbours inside the chunk (clamped to 0..15, no cross-chunk reads); the block column of the chunk (read and write); `y0`.
- **Biome per block:** `BiomeManager.getBiome(x, y, z)` (class `world/level/biome/BiomeManager`, `getBiome(int,int,int)` 334 bytes): `bx = x - 2, by = y - 2, bz = z - 2`; quart cell `(bx >> 2, by >> 2, bz >> 2)`; fractions `(bx & 3) / 4.0` per axis; for each of the 8 neighbouring quart cells choose the one minimising `getFiddledDistance(biomeZoomSeed, cx, cy, cz, fx, fy, fz)`; result = `noiseBiomeSource.getNoiseBiome(cellX, cellY, cellZ)` (the winning cell). `getFiddle(seed) = (floorMod(seed >> 24, 1024) / 1024.0 - 0.5) * 0.9`; `getFiddledDistance` chains `LinearCongruentialGenerator.next(seed, coord)` over the three coordinates twice, then draws three fiddles with further `next(rand, zoomSeed)` calls, summing squared distances. `biomeZoomSeed = BiomeManager.obfuscateSeed(worldSeed)`. **Repo status: not ported** (`worldgen/biome/` has Climate, MultiNoiseBiomeSource, OverworldBiomeBuilder, no BiomeManager). The quart biome grid itself comes from the `BIOMES` status (`createBiomes` stores `MultiNoiseBiomeSource.createResolverForChunk` results per 4x4x4 cell) of the chunk and of its neighbours (TERRAIN requires BIOMES radius 1; `getBiome` can read up to 2 blocks beyond the chunk through the neighbour chunks).
- **Climate for biome-dependent temperature:** `Biome.coldEnoughToSnow`/`shouldMeltFrozenOceanIcebergSlightly` use `Biome.getTemperature(pos, seaLevel)` = base temperature after `TemperatureModifier` (`frozen` modifier uses `FROZEN_TEMPERATURE_NOISE`, a `SimplexNoise` seeded `LegacyRandomSource(3456)`; `TEMPERATURE_NOISE` is `SimplexNoise(LegacyRandomSource(1234))`; repo: `worldgen/noise/SimplexNoise`) and, above `seaLevel + 17`, minus `(TEMPERATURE_NOISE.get(x/8, z/8) * 8 + y - (seaLevel + 17)) * 0.05 / 40` (`getHeightAdjustedTemperature`, 84 bytes; the `frozen` modifier body was not dumped, UNVERIFIED). `warmEnoughToRain = temperature >= 0.15`; `coldEnoughToSnow = !warmEnoughToRain`. Only the `temperature` condition and the iceberg melt use this, and the overworld files do not use the `temperature` condition, so only `frozenOceanExtension` needs it.
- **Density engine:** `chunk_surface_level` sampler (section 3.3).
- **Noise instances** (section 2.1) and the base positional random factory.
- **Block tags:** none in the surface step itself (`uncarvable` belongs to the carvers).
- **Aquifer:** not used by `buildSurface`.

## 5. Proposed port order

1. `BlockState`-free first cut is possible because all rule results have no properties: a `MaterialRuleContext` with the per-column fields, the 10 conditions and 5 rules over the existing JSON loader (`worldgen/json/Json`), plus the 3 compile-time lazies. Smallest verifiable slice: `bedrock_floor` + `underground` (vertical gradients with `fromHashOf` random names) on flat input columns.
2. `getSurfaceDepth`/`getSurfaceSecondary` and the `surface`/`surface_secondary` noises, `stone_depth`, `water`, `y_above`, `hole`, `steep` conditions; compare the full default overworld column (grass/dirt/stone) against the oracle with synthetic heightmaps.
3. `BiomeManager` (fiddle) over a stored quart grid; biome conditions.
4. `bandlands` + `generateBands` (needs `clay_bands_offset`) + `erodedBadlandsExtension`; badlands, wooded badlands, eroded badlands.
5. `frozenOceanExtension` (needs biome temperature: `SimplexNoise` temperature field).
6. `ore_vein` (needs the density engine) and `above_preliminary_surface`/`getMinSurfaceLevel` (needs `find_top_surface` and `interpolated`); until the density engine is complete, the rule tree can be evaluated with `above_preliminary_surface` forced by a supplied function but that would deviate from vanilla, so keep it behind the engine.
7. Sulfur cave bands and peaks (`noise_threshold` 3D, packed ice, powder snow) are data-only once 1-2 exist.

## 6. UNVERIFIED / risks

- `TemperatureModifier.FROZEN` body, `Biome.getBaseTemperature`, `Holder` set semantics of `biome_is` with tags (the overworld files use only ids/lists).
- Whether `ChunkAccess.setBlockState` inside `column.setBlock` updates the proto-chunk heightmaps (the second `getHeight` read in step 3 depends on it).
- `BiomeCondition`'s `possibleBiomes` pruning must never make a condition constant when the set is incomplete; build the 3x3 set exactly as `ChunkStatusTasks.collectPossibleBiomes(region, 1)` (76 bytes, not transcribed) or disable the shortcut (results are the same when the set is complete).
- The `bandlands` rule is evaluated for any `y`; the `% 192` index uses `y` possibly negative: `(y + offset + 192) % 192` can be negative for `y < -192`, impossible in a -64..319 world.
