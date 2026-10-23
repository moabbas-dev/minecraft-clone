# Biome atmosphere and weather in 26.3 (client)

Sources: the 26.3 client jar (Fernflower + `javap`), `data/minecraft/dimension_type/overworld.json`,
`data/minecraft/timeline/day.json`, `data/minecraft/worldgen/biome/*.json`,
`flat_level_generator_preset/classic_flat.json`. Engine port: `world/BiomeAttributes`, `world/AtmosphereProbe`,
`world/World.getPrecipitationAt`, `game/Game.renderWorld` / `drawWeatherColumns`, `game/SkyColors`.

## Environment attribute system (`net.minecraft.world.attribute`)

- `EnvironmentAttributes` registers every attribute with a default, a type and flags. Atmosphere ones (default, all
  `spatiallyInterpolated`): `visual/sky_color` #000000, `visual/fog_color` #000000, `visual/water_fog_color` #050533
  (`-16448205`), `visual/water_fog_start_distance` -8, `visual/water_fog_end_distance` 96 (NON_NEGATIVE),
  `visual/fog_start_distance` 0, `visual/fog_end_distance` 1024, `visual/sky_fog_end_distance` 512,
  `visual/cloud_fog_end_distance` 2048, `visual/cloud_color` 0, `visual/sunrise_sunset_color` 0, `visual/star_brightness` 0.
- `EnvironmentAttributeSystem.addDefaultLayers` (layer order = application order):
  1. dimension constant layer (`DimensionType.attributes`; overworld: sky #78a7ff, fog #c0d8ff, cloud #ccffffff, cloud
     height 192.33, ambient light #0a0a0a);
  2. biome positional layer, only for attributes some biome sets (`addBiomeLayerForAttribute`): with a
     `SpatialAttributeInterpolator` and a spatially interpolated attribute -> `biomeWeights.applyAttributeLayer`, else the
     single biome (`getNoiseBiomeAtPosition`, or `getBiome` for `fullResolutionBiomes` such as mob spawns);
  3. timeline layers (`dimensionType.timelines()`, `day.json`, `moon.json`, ... -> already ported in `SkyColors`/`SkyDetails`);
  4. weather layers (`WeatherAttributes.RAIN/THUNDER`, `stateChangeLerp` by `rainLevel - thunderLevel`, then by
     `thunderLevel`): sky `blend_to_gray(0.6, 0.75)` / `(0.24, 0.94)`, fog `multiply(0.5,0.5,0.6)` / `(0.25,0.25,0.3)`,
     clouds `blend_to_gray(0.24,0.5)` / `(0.095,0.94)`, star brightness 0, sunrise multiply like fog;
  5. `ClientLevel.addEnvironmentAttributeLayers`: sky flash lerps the sky 0.22 toward (0.8, 0.8, 1).
- Biome entries (`EnvironmentAttributeMap.Entry`): a plain value = `override`, or `{"modifier": op, "argument": v}`;
  ops `AttributeModifier.OperationId` (override, alpha_blend, add, subtract, multiply, blend_to_gray, minimum, maximum, ...).
  26.3 biome files use only colour overrides and `water_fog_end_distance {multiply 0.85}` (swamp, mangrove_swamp).
- Overworld biome colours present: `sky_color` in every overworld biome, `fog_color` only in dappled_forest, mangrove_swamp
  (#c0d8ff), pale_garden, sulfur_caves; `water_fog_color` in cherry_grove, dappled_forest, (deep_)lukewarm/warm ocean,
  mangrove_swamp, pale_garden, sulfur_caves, swamp. Cloud colour and star brightness are not biome attributes.

## Blending around the camera

- `Camera.tick` -> `EnvironmentAttributeProbe.tick(level, cameraPosition)`: clears the interpolator, then
  `GaussianSampler.sample(pos * 0.25, biomeManager::getNoiseBiomeAtQuart, accumulate(weight, biome.getAttributes()))`.
- `GaussianSampler`: `p -= 0.5`; for 6 x 6 x 6 quarts from `floor(p) - 2`, weight per axis
  `Mth.lerp(frac, K[i+1], K[i])`, kernel `{0,1,4,6,4,1,0}` (sum 16 per axis, 4096 total); loop order z, x, y.
- `SpatialAttributeInterpolator.applyAttributeLayer`: weights merged per biome attribute map (first-seen order); empty ->
  base; one -> that biome's modifier; several -> fold `lerp((float)(w / accumulated), result, value)`. For rgb and float
  types every lerp (spatial, state change, keyframe, partial tick) is linear (`ARGB.srgbLerp(Vector3fc)` = `Vector3f.lerp`).
- `ValueProbe.get(attribute, partialTicks)`: `partialTickLerp(partialTicks, lastValue, newValue)` where `newValue` is computed
  at the position of the latest tick and `lastValue` is the previous tick's value.
- Client `getNoiseBiome` for an unloaded chunk is `ClientLevel.getUncachedNoiseBiome` = plains.

## Consumers

- `SkyRenderer.extractRenderState`: sky colour = probe `SKY_COLOR`.
- `FogRenderer` + `FogEnvironment`s (first applicable: Lava, PowderedSnow, Blindness, Darkness, Water, Atmospheric):
  - `AtmosphericFogEnvironment.getBaseColor`: probe `FOG_COLOR`, sunrise tint when facing the sun (render distance >= 4),
    then lerp toward the weather-darkened sky (`1 - clampedLerp(min(skyFogEnd/16, rd)/32, 0.25, 1)^0.25`).
    `setupFog`: environmental start/end from the probe (0 / 1024) plus the rain fog (`-160 * m`, `max(min(96, end),
    end - 256 * m)`), `m += (target - m) * deltaTicks * 0.2`, `target = rainLevel * clamp((skyLight(camera block) - 8)/7)
    * (biome.hasPrecipitation() ? 1 : 0.5)` (biome = `level.getBiome(camera.blockPosition())`).
  - `WaterFogEnvironment`: colour probe `WATER_FOG_COLOR`, start probe `WATER_FOG_START_DISTANCE`, end probe
    `WATER_FOG_END_DISTANCE * max(0.25, waterVision)`; sky and cloud end = that end.
  - `FogRenderer.computeFogColor`: void darkness, darken-world, water vision / night vision brightening (unchanged here).
- `ClientLevelData.getHorizonHeight`: flat worlds `minY`, else 63 (dark disc below it).

## Precipitation

- `Biome.getPrecipitationAt(pos, seaLevel)`: `has_precipitation false` -> NONE; else `coldEnoughToSnow` (height-adjusted
  temperature < 0.15, frozen modifier noise, `seaLevel + 17` snow line) -> SNOW, else RAIN (already in `BiomeDefinition`).
- `ClientLevel.getPrecipitationAt(pos)`: NONE when the chunk is not loaded.
- `WeatherEffectRenderer.extractRenderState`: per column within `weatherRadius` (option, default 10): MOTION_BLOCKING height,
  `y0 = max(camY - r, h)`, `y1 = max(camY + r, h)`, precipitation at `(x, camY, z)`; RAIN -> `rain.png` column (speed
  `3 + nextFloat`, offset `(x*x*3121 + x*45238971 + z*z*418711 + z*13761) & 255`, `% 32`), SNOW -> `snow.png` column
  (`u = nextDouble + t*0.01*nextGaussian`, `v = nextDouble + t*nextGaussian*0.001`, `-(ticks&511)/512`, light
  `(l*3+15)/4`); seed `x*x*3121 + x*45238971 ^ z*z*418711 + z*13761`. Alpha `lerp(min(d^2/r^2,1), max, 0.5) * rainLevel`,
  max 1 (rain) / 0.8 (snow). The camera's own column has a 0/0 (NaN) size, so no quad.
- `ClientLevel.tickWeatherEffects`: rain splash particles and the rain sounds only where precipitation at the heightmap
  position is RAIN (`weather.rain` 0.2/1.0, `weather.rain.above` 0.1/0.5 when under a roof); SMOKE over lava/magma/campfires.
- `Level.precipitationAt/isRainingAt`: raining, sky visible, at or above MOTION_BLOCKING, biome precipitation.

## Flat worlds

`FlatLevelSource` uses `FixedBiomeSource(settings.biome)`; classic flat = `minecraft:plains`; `getSeaLevel()` = -63
(`bipush -63`). Tint, atmosphere and precipitation come from `plains.json` like any biome.

## F3

`DebugEntryBiome`: `"Biome: " + id` at the feet (inside build height). `DebugEntryChunkGeneration` (server level):
`NoiseBasedChunkGenerator.addDebugScreenInfo` ("Density " + `label: value` per `debug_functions`, `DecimalFormat("0.000",
Locale.ROOT)`) and `MultiNoiseBiomeSource.addDebugInfo` ("Biome builder PV: .. C: .. E: .. T: .. H: ..").
