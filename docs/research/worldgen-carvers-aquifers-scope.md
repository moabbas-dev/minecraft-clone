# 26.3 carvers, aquifers, noise chunk and density engine: scope for the Java clone

Read-only scoping. Sources: the 26.3 client jar (`javap -c -p`, `javap -v` for bootstrap methods), the extracted `data/minecraft/worldgen/**`, and the repo (`src/main/java/com/example/worldgen/**`, which another agent is extending with `worldgen/density/**`, uncommitted at the time of writing). Every claim names its class or data file. "UNVERIFIED" marks anything not read from bytecode or data. Bytecode sizes are the `Code` length of the class's methods. Date of the pass: 2026-10-19.

## 0. Headline findings (26.3 differs from 1.21 in structure, not only in data)

- **There is no `NoiseSettings.size_horizontal/size_vertical`, no `NoiseInterpolator`, no `OreVeinifier`, no `SurfaceSystem`, no `CarverConfiguration`/`CarvingContext`.** `NoiseSettings` is `record(int minY, int height)` (`levelgen/NoiseSettings`). Cell sizes are per-node parameters of `minecraft:interpolated` density functions (`cell_size_xz`, `cell_size_y`) and of `find_top_surface` (`cell_height`); see section 4.
- Terrain is computed by a new **batch density engine** (`levelgen/densityfunction/**`: `DensityFunction.compileSampler` -> `DensitySampler.sampleVolume(SamplerContext, DensityBuffer, DensityVolume)`, float buffers, caches, rewrite rules, a `DensityFunctionCompiler`). `NoiseChunk` is now a thin holder (173-byte constructor). The repo's `worldgen/density/**` (untracked, in progress) is the port of exactly this.
- Ore veins are a **material rule** (`minecraft:ore_vein`, `OreVeinRule`) driven by density functions, not a hard-coded class. Surface rules are the `material_rule` JSON (`MaterialSystem`, see the surface report). Aquifers are configured by `noise_settings.aquifers` (6 density functions).
- Carvers only record a **mask**; blocks are changed afterwards by `applyCarvingMask` using the aquifer and the material rule. Carver JSON has no block lists, `lava_level` or `debug_settings`.
- All 56 reachable biomes carry the same carver list: `["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]` (checked on every biome JSON), so the per-biome carver lookup is a constant for this clone.

## 1. Chunk status pipeline in 26.3

`ChunkPyramid.GENERATION_PYRAMID` (`world/level/chunk/status/ChunkPyramid`, static init 284 bytes; step lambdas mapped to statuses by the bootstrap method table of `javap -v`), statuses from `ChunkStatus` (`getStatusList()`):

| order | status | requirements (`addRequirement(status, radius)`) | task (`ChunkStatusTasks`) | notes |
|---|---|---|---|---|
| 0 | EMPTY | none | (none) | |
| 1 | STRUCTURE_STARTS | none | `generateStructureStarts` | |
| 2 | STRUCTURE_REFERENCES | STRUCTURE_STARTS r8 | `generateStructureReferences` | |
| 3 | BIOMES | STRUCTURE_STARTS r8 | `generateBiomes` -> `ChunkGenerator.createBiomes` (`doCreateBiomes`, 115 bytes) | fills section biome palettes (quart resolution) |
| 4 | **TERRAIN** | STRUCTURE_STARTS r8, BIOMES r1; `blockStateWriteRadius(0)` | `buildTerrain` -> `ChunkGenerator.buildTerrain` -> `NoiseBasedChunkGenerator.buildTerrain` | **noise fill + surface + carvers all happen here** (there are no separate NOISE/SURFACE/CARVERS statuses) |
| 5 | FEATURES | STRUCTURE_STARTS r8, TERRAIN r1; `blockStateWriteRadius(1)` | `generateFeatures` -> `applyBiomeDecoration` (feature report) | |
| 6 | INITIALIZE_LIGHT | none | `initializeLight` | |
| 7 | LIGHT | INITIALIZE_LIGHT r1 | `light` | |
| 8 | SPAWN | BIOMES r1 | `generateSpawn` | |
| 9 | FULL | none | `full` | |

`ChunkStatusTasks.buildTerrain` (76 bytes) creates a `WorldGenRegion`, computes `collectPossibleBiomes(region, 1)` (a `Set<Holder<Biome>>` of the 3x3 area, used only as a pruning hint by `BiomeCondition`, see the surface report), calls the generator with `Blender.of(region)`, `RandomState`, a `StructureManager` bound to the region and the region's `BiomeManager`, then (`lambda$buildTerrain$0`, 57 bytes) applies `BelowZeroRetrogen` fixes for old chunks and primes the heightmaps `MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES, OCEAN_FLOOR, WORLD_SURFACE` from the finished blocks.

## 2. `NoiseBasedChunkGenerator.buildTerrain` order (exact)

`NoiseBasedChunkGenerator` (final, 26.3). `buildTerrain` (78 bytes) clamps `NoiseSettings` to the height accessor (`NoiseSettings.clampToHeightAccessor`), returns the chunk unchanged if `height <= 0` or `debugVoidTerrain`, else runs the supplier `lambda$buildTerrain$0` (439 bytes) on `Util.backgroundExecutor`:

1. `noiseChunk = createNoiseChunk(chunk, structureManager, blender, randomState, noiseSettings)` (52 bytes): `Beardifier.forStructuresInChunk(structureManager, chunkPos)`, then `new NoiseChunk(randomState, beardifier, generatorSettings, globalFluidPicker, blender, chunkVolume)`; `chunkVolume` = `DensityVolume(16, settings.height, 16, chunk.minBlockX, settings.minY, chunk.minBlockZ)` (a whole-chunk column volume).
2. Acquire (`acquire()`) all `LevelChunkSection`s from `volume.maxBlockY` down to `volume.minBlockY`, for the duration of the fill.
3. `doFill(noiseChunk, chunk)` (381 bytes), section 3.
4. `buildSurface(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule)` (49 bytes) -> `randomState.surfaceSystem().buildSurface(...)` where `surfaceSystem()` is `material/MaterialSystem` (surface report). `materialRule` is `NoiseGeneratorSettings.materialRule().value()` (`noise_settings.material_rule`, `"minecraft:overworld"`).
5. `generateCarvers(chunk, blender, noiseChunk, randomState, biomeManager, region, materialRule)` (448 bytes), section 5.
6. `noiseChunk.close()` (returns the `DensityBufferPool` to `RandomState`).

**Order relative to the other stages: fill -> surface -> carve (+ mask apply) -> [later status] features.** The old three-step order of 1.21 (`fillFromNoise`, `buildSurface`, `applyCarvers`) is preserved; carvers see already-built surface blocks, and the mask application calls the material rule again to re-top exposed dirt.

## 3. `doFill` (381 bytes) and the fluid picker

- Heightmaps `OCEAN_FLOOR_WG` and `WORLD_SURFACE_WG` are fetched with `getOrCreateHeightmapUnprimed`.
- `finalDensity = noiseChunk.cachingSamplers().get(noiseRouter.finalDensity())`; `buffer = finalDensity.sampleVolume(volume)` samples **the whole 16 x height x 16 volume at once** (`ScopedDensityBuffer`, floats).
- Loop order: `z` (volume index 0..sizeZ-1), then `x`, then `y` from top (`sizeY - 1`) down to 0. For each cell: `density = buffer.get(volume.indexUnchecked(x, y, z))` (float), `state = aquifer.computeSubstance(blockX, blockY, blockZ, (double) density)`; if `null` -> `settings.defaultBlock()` (stone); `debugPreliminarySurfaceLevel` (debug only); if `state == AIR` skip; else `section.setBlockState(x & 15, y & 15, z & 15, state, false)`, update both heightmaps, and if `aquifer.shouldScheduleFluidUpdate()` and the state has a fluid, `chunk.markPosForPostProcessing(pos)`.
- `createFluidPicker(settings)` (64 bytes): `lava = FluidStatus(-54, lava)`, `sea = FluidStatus(seaLevel, defaultFluid)` (`noise_settings`: sea_level 63, default_fluid water), `air = FluidStatus(DimensionType.MIN_Y * 2, air)`; the picker returns `lava` if `y < min(-54, seaLevel)`, else `sea` (the `air` status is returned only when `SharedConstants.DEBUG_DISABLE_FLUID_GENERATION`). It is the `globalFluidPicker` memoised in the constructor and also the fallback when `noise_settings.aquifers` is absent (`Aquifer.createDisabled`).

## 4. Cell sizes and the density engine

Question: "how does 26.3 derive cell width/height; noise_settings has no size keys?" Answer: it does not. `data/minecraft/worldgen/noise_settings/overworld.json` has `noise: { min_y: -64, height: 384 }` only (`NoiseSettings` codec: 2 ints; `NoiseSettings.create`/`guardY` validate them, the exact rule was not transcribed). The interpolation grid is stated in the density function JSON:

- `overworld/final_density`: the main terrain term is wrapped in `{ "type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": ... }` (the 1.21 equivalent of size_horizontal 1, size_vertical 2).
- `overworld/ore_vein/toggle` and `mask` use the same `interpolated` 4/8; `overworld/chunk_surface_level` is `interpolated` with `cell_size_xz: 16, cell_size_y: 1` around `preliminary_surface_level`; `overworld/preliminary_surface_level` is `find_top_surface { cell_height: 8, lower_bound: -64, upper_bound: <expr>, density: <expr> }`.
- `InterpolatedFunction` (record: `input, cellSizeXz, cellSizeY`; sampler class 1839 bytes total): `sampleVolume` picks `sampleWithBlockStep` (560 bytes) or `sampleWithNonBlockStep` (211) depending on the `DensityVolume.step*` of the caller, with `fillCell` (327 bytes) trilinear filling between sampled corner values; `sampleValue` (295 bytes) does a single-position trilinear lerp. The exact interpolation (plain lerp vs smoothstep) is UNVERIFIED here; port from `InterpolatedFunction$Sampler.fillCell`/`sampleValue`.
- `DensityVolume(sizeX, sizeY, sizeZ, minBlockX, minBlockY, minBlockZ [, stepBlockX, stepBlockY, stepBlockZ])` (562 bytes); `indexOfBlock` returns -1 outside.

**Density engine classes** (`levelgen/densityfunction/**`, class-local method bytes; repo status: see last column; the repo's `worldgen/density/` already contains `DensityFunction`, `DensityFunctionCompiler`, `DensityFunctions`, samplers, buffers, `SamplerContext`, `DensityVolume`, `DfRewriteRule`, `Constant/Gradient/SimpleDensityFunction`; the other generators/ops are not there yet):

| class | bytes | role |
|---|---|---|
| `DensityFunction` (interface) | 459 | `compileSampler(CompileContext)`, `rewriteChildren(DfRewriteRule)`, `range()` (`util/Interval`), `domainAxes()` bitmask, `codec()`, default arithmetic helpers |
| `DensityFunctionCompiler` (+`PreparedCache`) | 383 | `getSampler(df)`: `optimizeAndCompile` (applies `DfRewriteRule`s such as `SliceUniformAxes`), `reuseOrPrepareCache(CacheFunction)`; constructed in `RandomState` with an anonymous `CompileContext` (`RandomState$1(seed, legacy)`: `createNoiseSampler(Holder<NormalNoise>)`, `createRandom(Identifier)`, `createEndIslandRandom()`) |
| `DensitySampler`, `DensitySampler$Bound`, `DensitySamplerSet`, `CachingDensitySampler`, `ContextBoundSampler` | 200 / - / 14 / 71 / 97 | runtime sampling; `ContextBoundSampler` looks a sampler up in the `SamplerContext` user fields (used for `beardifier`, `blend_alpha`, `blend_offset`) with a fallback |
| `SamplerContext` (+`Builder`, `CacheCell`) | 470 | per-chunk context: user fields (`ContextMap`), buffer arena, `enableCaches()`; `sampleValueCached`/`sampleVolumeCached` implement `cache` nodes |
| `DensityVolume`, `DensityBuffer`, `ScopedDensityBuffer`, `DensityBufferArena`, `DensityBufferPool` | 562 / 130 / 100 / 24 / 238 | float volumes and pooled buffers (`RandomState.acquireDensityBufferPool`) |
| `CacheId`, `TilingMode`, `DistanceMetric` | 32 / 117 / 322 | helpers |
| `DensityFunctions` | 1352 | registry bootstrap (44 types) and factory methods |

**Density function types registered** (`DensityFunctions.bootstrap`, 373 bytes): `constant`, `noise`, `end_outer_islands`, `distance_to_point`, `gradient`, `shift_a`, `shift_b`, `shift`, `pow`, `spline`, `lerp`, `clamp`, `range_choice`, `interval_select`, `cache`, `blend_density`, `interpolated`, `slice`, `find_top_surface`, `old_blended_noise` (20), plus `UnaryFunction$Type`: `abs, square, cube, sqrt, half_negative, quarter_negative, reciprocal, negate, squeeze, log, sign` (11), `BinaryFunction$Type`: `add, sub, mul, div, min, max` (6), `RoundFunction$Type`: `floor, round, ceil, truncate` (4), `SimpleDensityFunction`: `blend_alpha, blend_offset, beardifier` (3) = 44.

**Used by the overworld** (closure of `noise_settings/overworld.json` `noise_router.*`, `aquifers.*` and the ore-vein material rules: 30 named functions under `density_function/`, of 55 files; the rest are end, nether, amplified and large-biomes variants): `interpolated` 9, `find_top_surface` 1, `add` 37, `mul` 39, `lerp` 7, `gradient` 9, `clamp` 9, `quarter_negative` 2, `half_negative` 1, `cache` 19, `blend_alpha` 3, `blend_offset` 1, `blend_density` 1, `spline` 3, `noise` 45, `shift_a` 1, `shift_b` 1, `abs` 12, `sub` 11, `div` 1, `min` 8, `max` 6, `squeeze` 1, `range_choice` 17, `old_blended_noise` 1, `interval_select` 3, `square` 1, `cube` 2, `beardifier` 1, `negate` 1 (counts are node occurrences in those 30 files). 30 distinct types; **not needed for the overworld**: `end_outer_islands`, `distance_to_point`, `shift`, `pow`, `slice`, `constant` (as a node; bare numbers are constants), `round`/`floor`/`ceil`/`truncate`, `reciprocal`, `sqrt`, `log`, `sign`.

Named functions reached: `overworld/{base_3d_noise, caves/entrances, caves/noodle, caves/pillars, caves/spaghetti_2d, caves/spaghetti_2d_thickness_modulator, caves/spaghetti_roughness_function, chunk_surface_level, continents, depth, erosion, factor, final_density, jaggedness, offset, ore_vein/{copper_density, gap, iron_density, mask, richness, toggle}, preliminary_surface_level, ridges, ridges_folded, sloped_cheese, temperature, vegetation}`, plus `shift_x`, `shift_z`, `y`.

**NormalNoise ids needed** by those functions (all must exist in `data/minecraft/worldgen/noise/`, 64 files; repo loader: `worldgen/noise/NoiseParametersLoader`): `continentalness`, `erosion`, `ridge`, `offset`, `jagged`, `temperature`, `vegetation`, `cave_entrance`, `cave_layer`, `cave_cheese`, `spaghetti_2d`, `spaghetti_2d_modulator`, `spaghetti_2d_thickness`, `spaghetti_2d_elevation`, `spaghetti_3d_1`, `spaghetti_3d_2`, `spaghetti_3d_rarity`, `spaghetti_3d_thickness`, `spaghetti_roughness`, `spaghetti_roughness_modulator`, `pillar`, `pillar_rareness`, `pillar_thickness`, `noodle`, `noodle_thickness`, `noodle_ridge_a`, `noodle_ridge_b`, `ore_veininess`, `ore_vein_a`, `ore_vein_b`, `ore_gap`, `aquifer_barrier`, `aquifer_fluid_level_floodedness`, `aquifer_fluid_level_spread`, `aquifer_lava` (35). Surface-rule noises are in the surface report. `old_blended_noise` uses `synth/BlendedNoise` (907 bytes; repo: `worldgen/noise/BlendedNoise`).

## 5. Carvers

### 5.1 Data (`data/minecraft/worldgen/carver/`, 4 files)

| file | type | probability | y | other config |
|---|---|---|---|---|
| `cave.json` | `minecraft:cave` | 0.15 | uniform `above_bottom 8` .. `absolute 180` | `count` very_biased_to_bottom 0..14; `floor_level` uniform [-1.0, -0.4); `horizontal_radius_multiplier` uniform [0.7, 1.4); `vertical_radius_multiplier` uniform [0.8, 1.3); `room_vertical_radius_multiplier` uniform [0.1, 0.9); `thickness` trapezoid min 0, max 3, plateau 1; `weird_thickness_bias` true |
| `cave_extra_underground.json` | `minecraft:cave` | 0.07 | uniform `above_bottom 8` .. `absolute 47` | same values as `cave.json` |
| `canyon.json` | `minecraft:canyon` | 0.01 | uniform `absolute 10` .. `absolute 67` | `vertical_rotation` uniform [-0.125, 0.125); `shape`: `distance_factor` uniform [0.75, 1.0), `horizontal_radius_factor` uniform [0.75, 1.0), `thickness` trapezoid 0..6 plateau 2, `vertical_radius_center_factor` 0.0, `vertical_radius_default_factor` 1.0, `width_smoothness` 3, `y_scale` 3.0 |
| `nether_cave.json` | `minecraft:cave` | 0.2 | not overworld | listed for completeness (nether only) |

Registry (`WorldCarverTypes.bootstrap`): `cave` -> `CaveWorldCarver`, `canyon` -> `CanyonWorldCarver`. Both are **records that hold their own config** (no `CarverConfiguration`, no `CarvingContext`): `CaveWorldCarver(float probability, HeightProvider y, IntProvider count, FloatProvider thickness, boolean weirdThicknessBias, FloatProvider roomVerticalRadiusMultiplier, FloatProvider horizontalRadiusMultiplier, FloatProvider verticalRadiusMultiplier, FloatProvider startVerticalRadiusMultiplier, FloatProvider floorLevel)`; `CanyonWorldCarver(float probability, HeightProvider y, FloatProvider verticalRotation, CanyonWorldCarver$Shape shape)`. Optional JSON fields missing in the overworld files (`start_vertical_radius_multiplier`) take a codec default (value UNVERIFIED; the overworld cave files do not set it, and `createTunnel` receives it as parameter `dload 19`).

### 5.2 Classes and sizes

| class | method sizes (bytes) |
|---|---|
| `WorldCarver` (interface) | `carve` abstract, `isStartChunk` abstract, `getRange()` default (1 byte of code), `carveEllipsoid(ChunkPos, x, y, z, hRadius, vRadius, CarverOutput, CarveSkipChecker)` 340, `canReach(ChunkPos, x, z, fromStep, toStep, thickness)` 75 |
| `CaveWorldCarver` (class total 1297) | `isStartChunk` 19, `carve` 319, `getThickness` 51, `createRoom` 42, `createTunnel` 438, `shouldSkip` 32 |
| `CanyonWorldCarver` (class total 1148) | `isStartChunk` 19, `carve` 166, `doCarve` 318, `initWidthFactors` 78, `updateVerticalRadius` 51, `shouldSkip` 46 |
| `CarvingMask` (`world/level/chunk`) | constructor 42, `carve(x,y,z)` 14, `visit(Visitor)` 46, `visitSegment` 107, `isEmpty` 7; implements `CarverOutput` (`carve`, `minY`, `maxY`) |

Inputs a carver needs: `WorldGenerationContext` (min gen Y, gen depth, sea level; for `HeightProvider`/`VerticalAnchor` resolution), a `RandomSource`, the source and target `ChunkPos`, and a `CarverOutput` (the mask). They need **no** noise, aquifer or biome input.

### 5.3 Seeding and loop (`NoiseBasedChunkGenerator.generateCarvers`, 448 bytes)

1. Return if `DEBUG_DISABLE_CARVERS` or `debugVoidTerrain`.
2. `resolver = biomeSource.createUncachedResolver(randomState)`; `random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()))` (a **legacy LCG** source, unlike decoration).
3. `mask = new CarvingMask(minGenY + 1, minGenY + genDepth - 1 - (chunk.isUpgrading() ? 0 : 7))` (for the overworld: y from -63 to 312). The mask range is exactly what `CarverOutput.minY()/maxY()` returns inside `carveEllipsoid`. The reason for the `-7` top margin is UNVERIFIED.
4. For `dx` in -8..8, then `dz` in -8..8 (outer loop dx, inner dz; `bipush 8` constants): `source = ChunkPos(chunk.x + dx, chunk.z + dz)`. Carver list = `BiomeGenerationSettings.getCarvers()` of the biome at `QuartPos.fromBlock(source.minBlockX)`, `y = 0`, `QuartPos.fromBlock(source.minBlockZ)` from the uncached resolver (via `getBiomeGenerationSettingsForCarver`, 31 bytes; cached on the source chunk with `ChunkAccess.carverBiome(Supplier)` when a `WorldGenRegion` is present). Here every biome gives the same list, so the lookup is constant.
5. For each carver at index `i` (position in the biome's carver list): `random.setLargeFeatureSeed(randomState.seed() + i, source.x, source.z)`, i.e. `setSeed(seed + i); a = nextLong(); b = nextLong(); setSeed(x * a ^ z * b ^ (seed + i))` (`WorldgenRandom.setLargeFeatureSeed`, 39 bytes, uses the wrapped Legacy LCG). Then `if (carver.isStartChunk(random)) carver.carve(ctx, random, chunk.getPos(), source, mask)`. `isStartChunk` is `random.nextFloat() <= probability` for both types. The `<=` (not `<`) comes from `fcmpg ... ifgt` in `CaveWorldCarver.isStartChunk`.
6. After all 17 x 17 source chunks: if the mask is not empty, `biomeManager.withDifferentSource(resolver)` and `applyCarvingMask(...)` (60 bytes) visits every carved column.

### 5.4 Cave carve (read from bytecode, `CaveWorldCarver.carve`, 319 bytes) in random order

`bound = sectionToBlockCoord(getRange() * 2 - 1)`; `n = count.sample(random)`; repeat `n` times: `x = source.getBlockX(random.nextInt(16))`, `y = y.sample(random, ctx)`, `z = source.getBlockZ(random.nextInt(16))`; sample in this order `horizontalRadiusMultiplier`, `verticalRadiusMultiplier`, `startVerticalRadiusMultiplier`, `floorLevel`; build the skip checker from `floorLevel` (`shouldSkip(dx, dy, dz, floor)`: skip if `dy <= floor` or `dx*dx + dy*dy + dz*dz >= 1`); `tunnels = 1`; if `random.nextInt(4) == 0`: sample `roomVerticalRadiusMultiplier`, `roomRadius = 1.0f + random.nextFloat() * 6.0f`, `createRoom(...)`, `tunnels += random.nextInt(4)`; for each tunnel: `yaw = nextFloat * 2pi`, `pitch = (nextFloat - 0.5f) / 4.0f`, `thickness = getThickness(random)` (sample, and with `weirdThicknessBias` and `nextInt(10) == 0`, multiply by `nextFloat * nextFloat * 3 + 1`), `length = bound - random.nextInt(bound / 4)`, `createTunnel(source, random.nextLong(), x, y, z, hMult, vMult, thickness, yaw, pitch, 0, length, startVMult, mask, skip)`. `createTunnel` (438 bytes) re-seeds a thread-local random from its `long` argument (`RandomSource.createThreadLocalInstance`), picks a branch step, and may recurse into two tunnels (`nextLong` each). All these constants were read from the bytecode; port the methods 1:1.

### 5.5 `applyCarvingMask` (lambda 254 bytes) per carved column

For `y` from `maxY` down to `minY`: skip if the optional `Blender` carving filter rejects; read the chunk block; skip if it is in tag `minecraft:uncarvable`; remember `grass_block` or `mycelium` (`remembered = true`); `state = aquifer.computeSubstance(x, y, z, 0.0)`; skip if `null`; `chunk.setBlockState(pos, state)`; mark post-processing if the aquifer wants fluid updates and the state has a fluid; if `remembered` and the block below is `dirt`: `topMaterial = materialSystem.topMaterial(materialRule, randomState, ctx, biomeAt, chunk, samplers, belowPos, fluidAbove?)` and set it. So carving needs: the finished aquifer of the chunk's `NoiseChunk`, the `uncarvable` tag, and the material rule (`MaterialSystem.topMaterial`, 180 bytes).

### 5.6 Data the carver stage needs from earlier stages

The terrain blocks, the `NoiseChunk.aquifer()` instance, `RandomState.seed()`, the heightmap `WORLD_SURFACE_WG` (for `topMaterial` gradients), the biome source (only for the constant carver list here), and the tag `minecraft:uncarvable`. No dependence on the neighbouring chunks' *blocks*, only on neighbour *chunk coordinates* (the 17x17 random sampling), which makes carving independent per chunk and easy to oracle-test.

## 6. Aquifer (`levelgen/Aquifer` + `Aquifer$NoiseBasedAquifer`)

- `Aquifer` (interface): `computeSubstance(int x, int y, int z, double density)` -> `BlockState` or `null`; `shouldScheduleFluidUpdate()`; `static createDisabled(FluidPicker)` (anonymous `Aquifer$1`: returns the picker's fluid when `density <= 0`, never schedules updates; 25 bytes).
- `Aquifer$Config` record (`noise_settings.aquifers`, codec 121 bytes): `barrier`, `fluid_level_floodedness`, `fluid_level_spread`, `lava`, `exclusion`, `surface_level`, all `DensityFunction`s; `create(DensitySamplerSet, PositionalRandomFactory, DensityVolume, FluidPicker)` builds the aquifer. Overworld data: barrier = `noise aquifer_barrier` (xz 1.0, y 0.5); floodedness = `aquifer_fluid_level_floodedness` (y 0.67); spread = `aquifer_fluid_level_spread` (y 0.7142857); lava = `aquifer_lava` (y 1.0); **exclusion** = `min(-0.225 - overworld/erosion, max(overworld/depth - 0.9, 0))` (new in 26.3; sampled inside `computeFluid`; exact effect UNVERIFIED); surface_level = `overworld/preliminary_surface_level`.
- `NoiseChunk` builds it with `RandomState.getOrCreateRandomFactory(Identifier "aquifer")` = `random.fromHashOf("minecraft:aquifer").forkPositional()` (`RandomState` lambda 15 bytes); disabled (`Optional.empty`) for noise settings without `aquifers`.
- Method sizes of `Aquifer$NoiseBasedAquifer` (class total ~3300): constructor 347 (samples `surface_level` over the chunk volume, builds the grid caches), `surfaceLevel(x,z)` 41 (cache keyed by `ChunkPos.pack` of the **quart-aligned** block x/z), `maxSurfaceLevel` 219, `getIndex` 42, **`computeSubstance` 944**, `similarity` 14, `calculatePressure` 323, `getAquiferStatus` 48, **`computeFluid` 257**, `computeSurfaceLevel` 175, `computeRandomizedFluidSurfaceLevel` 88, `computeFluidType` 106, `adjustSurfaceLevel` 4 (`+8`), grid helpers.
- Constants read: grid cells **16 x 12 x 16** (`gridX(x) = x >> 4`, `gridY(y) = floorDiv(y, 12)`, `gridZ(z) = z >> 4`); `similarity(a, b) = 1.0 - (b - a) / 25.0`; `FLOWING_UPDATE_SIMULARITY = similarity(10^2, 12^2)`; `SURFACE_SAMPLING_OFFSETS_IN_CHUNKS` = 13 (dx, dz) pairs `(0,0) (-2,-1) (-1,-1) (0,-1) (1,-1) (-3,0) (-2,0) (-1,0) (1,0) (-2,1) (-1,1) (0,1) (1,1)`; `computeRandomizedFluidSurfaceLevel`: cell 16 x 40, `y0 = floorDiv(y,40)*40 + 20`, spread noise `* 10.0`, `quantize(..., 3)`, `min(surfaceLevel, y0 + spread)`; a method of the class draws three `nextInt` from `positionalRandom.at(...)` (apparently the per-grid-cell x, y, z jitter of the aquifer centres; which method and the bounds are UNVERIFIED). Full algorithm: port from bytecode with the oracle; the structure matches the pre-26.3 `NoiseBasedAquifer`, but the inputs are `DensitySampler$Bound` objects sampled at integer block positions instead of `NoiseChunk`-interpolated lambdas.
- Fluid lava level: lava below `min(-54, seaLevel)`; water at `seaLevel` (63) otherwise (fluid picker, section 3).
- Inputs the aquifer needs: `DensitySamplerSet` (to bind the 6 functions of the config), a `PositionalRandomFactory` ("aquifer"), the chunk `DensityVolume`, the global `FluidPicker`, and the float density from `final_density` at each block (passed to `computeSubstance`).

## 7. Ore veins (`material/rule/OreVeinRule`, 109-byte `compile` + 109-byte lambda)

`minecraft:ore_vein` rule record: `oreBlock, rawOreBlock, fillerBlock, rawOreChance (0..1), density, richness, fillerGap`. `compile(ctx)`: `density` and `richness` are sampled over the **chunk volume** (`ctx.getDensitiesInChunk(df, true)`), `fillerGap` per point (`false`), random factory `"minecraft:ore"` (`getOrCreateRandomFactory(withDefaultNamespace("ore"))`). Per block `(x,y,z)` (lambda): `d = density.get()`; if `d <= 0` return `null` (rule does not apply); `r = ore.at(x,y,z)`; if `r.nextFloat() > d` return `null`; `rich = richness.get()`; if `r.nextFloat() < rich && fillerGap.get() < 0` return (`r.nextFloat() < rawOreChance ? rawOreBlock : oreBlock`) else return `fillerBlock`. (The `null` returns go on to the next rule of the `material_rule` sequence.) Debug flags `DEBUG_DISABLE_ORE_VEINS`/`DEBUG_ORE_VEINS` swap the outputs.
Data: `material_rule/overworld/copper_ore_vein.json` (ore `copper_ore`, raw `raw_copper_block`, filler `granite`, chance 0.02) and `iron_ore_vein.json` (ore `deepslate_iron_ore`, raw `raw_iron_block`, filler `tuff`, chance 0.02), both with `filler_gap = overworld/ore_vein/gap` (`-0.3 - noise ore_gap`) and `richness = overworld/ore_vein/richness` (`clamp(abs(toggle), 0.4, 0.6) - 0.3`). Densities: `copper_density` active for `0 <= y < 50`, `iron_density` for `-60 <= y < -8` (range_choice nests on `overworld/ore_vein/mask` and `toggle`; `toggle = cache(interpolated 4/8 (noise ore_veininess xz 1.5 y 1.5))` for `-64 <= y < 57`, `mask` uses `ore_vein_a`/`ore_vein_b` noises with scale 4). The rules are the 2nd and 3rd entries of the root `material_rule/overworld.json` sequence: `bedrock_floor`, copper vein, iron vein, then `above_preliminary_surface ? surface : nothing`, then `underground` (deepslate gradient and `sulfur_cave_bands`).

## 8. `NoiseChunk`, `RandomState`, `Beardifier`, `Blender`

| class | bytes | responsibility / inputs |
|---|---|---|
| `NoiseChunk` | ctor 173 | holds `RandomState`, `volume`, a pooled buffer set, `cachingSamplers` (`DensitySamplerSet` bound to a `SamplerContext` that has `Beardifier.CONTEXT_KEY`, and, only if the `Blender` is non-empty, `Blender.CONTEXT_KEY/ALPHA_KEY/OFFSET_KEY`), and the `aquifer`. Implements `AutoCloseable`. |
| `RandomState` | ctor 144 | `seed`, `random` = `(legacy ? LEGACY : XOROSHIRO).newInstance(seed).forkPositional()` (legacy iff `noise_settings.legacy_random_source`, false for the overworld), noise instance map (`Noises.instantiate(holderGetter, random, key)` per `getOrCreateNoise(ResourceKey)`), positional factory map (`random.fromHashOf(id).forkPositional()`), the `MaterialSystem` (created with `defaultBlock`, `seaLevel`, `router.chunkSurfaceLevel()` as its preliminary surface function, and the base `random`), the `DensityFunctionCompiler`, density buffer pools (lock + list). `createClimateSampler(SamplerContext)` -> `NoiseRouter.createClimateSampler(DensitySamplerSet)` (67 bytes). `sampleBlockValueUncached(df, x, y, z)` for debug/spawn. |
| `NoiseRouter` (record, 8 functions) | 49 | `temperature, vegetation, continents, erosion, depth, ridges, chunkSurfaceLevel, finalDensity` (the old `barrier`, `fluid_level_*`, `lava`, `vein_*`, `initial_density_without_jaggedness` fields are gone; they live in `aquifers` and in the material rules). The overworld JSON maps them to `overworld/{temperature, vegetation, continents, erosion, depth, ridges, chunk_surface_level, final_density}`. |
| `Beardifier` (implements `DensitySampler`) | 446 `forStructuresInChunk`, 292 `sampleVolume`, 434 `sampleValueUnchecked` | terrain adaptation around structures (`terrain_adaptation` of structures, jigsaw junctions); `forStructuresInChunk(StructureManager, ChunkPos)` returns an empty beardifier when no structure start is in range. **Only needed once structures exist; with no structures it contributes 0**, but `final_density` references the `beardifier` leaf, so the clone needs the leaf returning 0 (the repo's `SimpleDensityFunction` is the place). |
| `Blender` (+ `BlendingData`) | package `levelgen/blending`, 14 classes | blends new terrain against old-version chunks; `Blender.of(region)` is empty for fresh worlds and `NoiseChunk` then registers nothing. `blend_alpha`, `blend_offset` leaves evaluate to the empty-blender values (the repo's `density/DensityBlender` exists). Skip until world upgrade is a requirement. |
| `OverworldFunctionSet<T>` (record of 12) | 73 | helper used by `NoiseRouterData` (the Java builder of the vanilla router that the datapack JSON replaced for the overworld); not needed when loading the JSON. |

## 9. What the clone needs, in dependency order

1. Density engine complete for the 30 types listed in section 4 (port in progress in `worldgen/density/**`), plus `NormalNoise` instances per noise id (done in `worldgen/noise/**`) and `RandomState` (seed, positional factories, compiler, buffer pools).
2. `NoiseSettings`/`NoiseGeneratorSettings` loader (JSON: `noise`, `default_block`, `default_fluid`, `sea_level`, `noise_router`, `aquifers`, `material_rule`, `legacy_random_source`, `spawn_target`, `debug_functions`).
3. `DensityVolume` fill (`doFill`) + `Aquifer` + `createFluidPicker`: first stage that produces terrain; verify by comparing a chunk's block array with the real 26.3 `NoiseBasedChunkGenerator.doFill` under the oracle.
4. Material rules (surface report) including `OreVeinRule`.
5. Carvers + `CarvingMask` + `applyCarvingMask` (needs 3 and 4).
6. Heightmaps `WORLD_SURFACE_WG`, `OCEAN_FLOOR_WG` during fill, the four decoration heightmaps after TERRAIN.
7. Features (feature report).

## 10. Risks and UNVERIFIED items

- `Aquifer$NoiseBasedAquifer.computeSubstance` (944 bytes), `computeFluid` and the use of `exclusion` were not transcribed here (constants and structure only); port directly from bytecode.
- Interpolation formula of `InterpolatedFunction$Sampler` (lerp or smoothed) and the `SamplerContext` cache-cell semantics are not transcribed here.
- Behaviour of optional carver fields not present in the overworld JSON (`start_vertical_radius_multiplier` default) is UNVERIFIED.
- The legacy `LegacyRandomSource` (LCG) for carvers, versus Xoroshiro for decoration and noise, is an easy source of silent mismatch; the repo has both (`worldgen/random/LegacyRandomSource`, `XoroshiroRandomSource`).
- The `-7` top margin of the `CarvingMask` range is read from bytecode but its purpose is UNVERIFIED.
