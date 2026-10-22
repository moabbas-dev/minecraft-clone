package com.example.worldgen.material;

import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.level.GenChunk;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.Noises;
import com.example.worldgen.levelgen.RandomState;
import com.example.worldgen.material.rule.MaterialRule;
import com.example.worldgen.material.rule.RuleEvaluator;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.MaterialSystem} (26.3, the former {@code SurfaceSystem}),
 * transcribed from the client jar's bytecode. {@code RandomState} builds it as {@code new MaterialSystem(randomState,
 * settings.defaultBlock(), settings.seaLevel(), router.chunkSurfaceLevel(), randomState.random)}
 * ({@link #create(RandomState, StateRef, int)}).
 * <ul>
 *     <li>constructor: noises {@code clay_bands_offset}, {@code surface}, {@code surface_secondary}, {@code badlands_pillar},
 *     {@code badlands_pillar_roof}, {@code badlands_surface}, {@code iceberg_pillar}, {@code iceberg_pillar_roof},
 *     {@code iceberg_surface} via {@code RandomState.getOrCreateNoise} (in that order: the clay bands are generated from
 *     {@code noiseRandom.fromHashOf("minecraft:clay_bands")} right after the first noise);</li>
 *     <li>{@link #buildSurface}: per column ({@code localX} outer, {@code localZ} inner) the eroded badlands pillar, the
 *     top-down rule evaluation with stone depths and water height, the frozen ocean iceberg;</li>
 *     <li>{@link #topMaterial}: the single-block evaluation the carver mask application uses.</li>
 * </ul>
 */
public final class MaterialSystem {

    private static final StateRef WHITE_TERRACOTTA = StateRef.of("minecraft:white_terracotta");
    private static final StateRef ORANGE_TERRACOTTA = StateRef.of("minecraft:orange_terracotta");
    private static final StateRef TERRACOTTA = StateRef.of("minecraft:terracotta");
    private static final StateRef YELLOW_TERRACOTTA = StateRef.of("minecraft:yellow_terracotta");
    private static final StateRef BROWN_TERRACOTTA = StateRef.of("minecraft:brown_terracotta");
    private static final StateRef RED_TERRACOTTA = StateRef.of("minecraft:red_terracotta");
    private static final StateRef LIGHT_GRAY_TERRACOTTA = StateRef.of("minecraft:light_gray_terracotta");
    private static final StateRef PACKED_ICE = StateRef.of("minecraft:packed_ice");
    private static final StateRef SNOW_BLOCK = StateRef.of("minecraft:snow_block");
    private static final String WATER = "minecraft:water";
    /** {@code DimensionType.WAY_BELOW_MIN_Y = DimensionType.MIN_Y << 4}. */
    static final int WAY_BELOW_MIN_Y = com.example.worldgen.DimensionType.MIN_Y << 4;

    private final StateRef defaultBlock;
    private final int seaLevel;
    private final DensityFunction preliminarySurfaceFunction;
    private final StateRef[] clayBands;
    private final Noise clayBandsOffsetNoise;
    private final Noise badlandsPillarNoise;
    private final Noise badlandsPillarRoofNoise;
    private final Noise badlandsSurfaceNoise;
    private final Noise icebergPillarNoise;
    private final Noise icebergPillarRoofNoise;
    private final Noise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final Noise surfaceNoise;
    private final Noise surfaceSecondaryNoise;

    public MaterialSystem(RandomState randomState, StateRef defaultBlock, int seaLevel, DensityFunction preliminarySurfaceFunction,
                          PositionalRandomFactory noiseRandom) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.preliminarySurfaceFunction = preliminarySurfaceFunction;
        this.noiseRandom = noiseRandom;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(noiseRandom.fromHashOf("minecraft:clay_bands"));
        this.surfaceNoise = randomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = randomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = randomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    /** As {@code RandomState}'s constructor builds it: the router's {@code chunk_surface_level} and the base factory. */
    public static MaterialSystem create(RandomState randomState, StateRef defaultBlock, int seaLevel) {
        return new MaterialSystem(randomState, defaultBlock, seaLevel, randomState.router().chunkSurfaceLevel(),
                randomState.random());
    }

    /**
     * {@code buildSurface(randomState, biomeManager, context, chunk, noiseChunk, rule, possibleBiomes)}.
     *
     * @param noiseChunkVolume {@code noiseChunk.volume()} (the chunk's fill volume)
     * @param cachingSamplers  {@code noiseChunk.cachingSamplers()} (the same sampler set the fill used)
     * @param possibleBiomes   biome ids of the 3 x 3 chunk area ({@code collectPossibleBiomes}), or {@code null}
     */
    public void buildSurface(RandomState randomState, BiomeAt biomeManager, BiomeClimate biomeClimate,
                             WorldGenerationContext context, GenChunk chunk, DensityVolume noiseChunkVolume,
                             DensitySamplerSet cachingSamplers, MaterialRule rule, Set<String> possibleBiomes) {
        int minBlockX = chunk.minBlockX();
        int minBlockZ = chunk.minBlockZ();
        int minY = chunk.minY();
        int maxY = chunk.maxY();
        Column column = new Column(chunk);
        int topY = (highestFilledSectionY(chunk)) * 16 + 15;
        DensityVolume volume = new DensityVolume(noiseChunkVolume.sizeX(), Math.max(topY - noiseChunkVolume.minBlockY() + 1, 1),
                noiseChunkVolume.sizeZ(), noiseChunkVolume.minBlockX(), noiseChunkVolume.minBlockY(), noiseChunkVolume.minBlockZ());
        MaterialRuleContext ctx = new MaterialRuleContext(this, randomState, volume, cachingSamplers, biomeManager, biomeClimate,
                context, possibleBiomes);
        RuleEvaluator evaluator = rule.compile(ctx);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = minBlockX + localX;
                int z = minBlockZ + localZ;
                column.x = x;
                column.z = z;
                int startHeight = chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, x, z) + 1;
                String biome = biomeManager.getBiome(x, startHeight, z);
                if (biome.equals("minecraft:eroded_badlands")) {
                    erodedBadlandsExtension(column, x, z, startHeight, minY);
                }
                int height = chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, x, z) + 1;
                int gradientX = getSurfaceGradientX(chunk, localX, localZ);
                int gradientZ = getSurfaceGradientZ(chunk, localX, localZ);
                ctx.updateXZ(x, z, gradientX, gradientZ);
                int stoneAboveDepth = 0;
                int waterHeight = Integer.MIN_VALUE;
                int bottomOfRun = Integer.MAX_VALUE;
                int columnMinY = chunk.minY();
                for (int y = height; y >= columnMinY; y--) {
                    StateRef old = column.getBlock(y);
                    if (MaterialBlocks.isAir(old)) {
                        stoneAboveDepth = 0;
                        waterHeight = Integer.MIN_VALUE;
                    } else if (MaterialBlocks.hasFluid(old)) {
                        if (waterHeight == Integer.MIN_VALUE) {
                            waterHeight = y + 1;
                        }
                    } else {
                        if (bottomOfRun >= y) {
                            bottomOfRun = WAY_BELOW_MIN_Y;
                            for (int lookaheadY = y - 1; lookaheadY >= columnMinY - 1; lookaheadY--) {
                                StateRef nextState = column.getBlock(lookaheadY);
                                if (!isStone(nextState)) {
                                    bottomOfRun = lookaheadY + 1;
                                    break;
                                }
                            }
                        }
                        stoneAboveDepth++;
                        int stoneBelowDepth = y - bottomOfRun + 1;
                        ctx.updateY(stoneAboveDepth, stoneBelowDepth, waterHeight, y);
                        if (y >= minY && y <= maxY) {
                            StateRef state = evaluator.tryApply(x, y, z);
                            if (state != null) {
                                column.setBlock(y, state);
                            }
                        }
                    }
                }
                if (biome.equals("minecraft:frozen_ocean") || biome.equals("minecraft:deep_frozen_ocean")) {
                    frozenOceanExtension(ctx.getMinSurfaceLevel(), biome, biomeClimate, column, x, z, startHeight);
                }
            }
        }
    }

    /**
     * {@code getHighestFilledSectionIndex} turned into a section Y: the section of the highest non-air block, which is the
     * section of {@code WORLD_SURFACE_WG - 1} (that heightmap counts every non-air state, as {@code LevelChunkSection.hasOnlyAir}
     * does); an empty chunk gives the section below the lowest one, as vanilla's index {@code -1} does.
     */
    private static int highestFilledSectionY(GenChunk chunk) {
        int highest = chunk.minY();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                highest = Math.max(highest, chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG,
                        chunk.minBlockX() + localX, chunk.minBlockZ() + localZ));
            }
        }
        return Math.floorDiv(highest - 1, 16);
    }

    private static int getSurfaceGradientX(GenChunk chunk, int localX, int localZ) {
        return chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, chunk.minBlockX() + Math.min(localX + 1, 15), chunk.minBlockZ() + localZ)
                - chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, chunk.minBlockX() + Math.max(localX - 1, 0), chunk.minBlockZ() + localZ);
    }

    private static int getSurfaceGradientZ(GenChunk chunk, int localX, int localZ) {
        return chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, chunk.minBlockX() + localX, chunk.minBlockZ() + Math.min(localZ + 1, 15))
                - chunk.getHeight(GenChunk.HeightmapType.WORLD_SURFACE_WG, chunk.minBlockX() + localX, chunk.minBlockZ() + Math.max(localZ - 1, 0));
    }

    /** {@code getSurfaceDepth}: {@code (int) (surface(x, 0, z) * 2.75 + 3.0 + noiseRandom.at(x, 0, z).nextDouble() * 0.25)}. */
    int getSurfaceDepth(int blockX, int blockZ) {
        double noise = surfaceNoise.get((double) blockX, 0.0, (double) blockZ);
        return (int) (noise * 2.75 + 3.0 + noiseRandom.at(blockX, 0, blockZ).nextDouble() * 0.25);
    }

    double getSurfaceSecondary(int blockX, int blockZ) {
        return surfaceSecondaryNoise.get((double) blockX, 0.0, (double) blockZ);
    }

    private static boolean isStone(StateRef state) {
        return !MaterialBlocks.isAir(state) && !MaterialBlocks.hasFluid(state);
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    public DensityFunction preliminarySurfaceFunction() {
        return preliminarySurfaceFunction;
    }

    /**
     * {@code topMaterial(rule, randomState, context, biomeGetter, chunk, samplers, pos, fluidAbove)}: a 1 x 1 x 1 volume at
     * {@code pos}, no possible-biome set, {@code updateXZ} with the chunk's gradients, {@code updateY(1, 1, fluidAbove ? y + 1 :
     * Integer.MIN_VALUE, y)}, then one {@code tryApply}.
     */
    public Optional<StateRef> topMaterial(MaterialRule rule, RandomState randomState, WorldGenerationContext context,
                                          BiomeAt biomeGetter, BiomeClimate biomeClimate, GenChunk chunk,
                                          DensitySamplerSet samplers, int x, int y, int z, boolean fluidAbove) {
        DensityVolume volume = new DensityVolume(1, 1, 1, x, y, z);
        MaterialRuleContext ctx = new MaterialRuleContext(this, randomState, volume, samplers, biomeGetter, biomeClimate, context, null);
        RuleEvaluator evaluator = rule.compile(ctx);
        int gradientX = getSurfaceGradientX(chunk, x & 15, z & 15);
        int gradientZ = getSurfaceGradientZ(chunk, x & 15, z & 15);
        ctx.updateXZ(x, z, gradientX, gradientZ);
        ctx.updateY(1, 1, fluidAbove ? y + 1 : Integer.MIN_VALUE, y);
        return Optional.ofNullable(evaluator.tryApply(x, y, z));
    }

    private void erodedBadlandsExtension(Column column, int blockX, int blockZ, int height, int minY) {
        double pillarBuffer = Math.min(Math.abs((double) badlandsSurfaceNoise.get((double) blockX, 0.0, (double) blockZ) * 8.25),
                (double) (badlandsPillarNoise.get((double) blockX * 0.2, 0.0, (double) blockZ * 0.2) * 15.0F));
        if (pillarBuffer <= 0.0) {
            return;
        }
        double pillarFloor = Math.abs((double) badlandsPillarRoofNoise.get((double) blockX * 0.75, 0.0, (double) blockZ * 0.75) * 1.5);
        double extensionTop = 64.0 + Math.min(pillarBuffer * pillarBuffer * 2.5, Math.ceil(pillarFloor * 50.0) + 24.0);
        int startY = Mth.floor(extensionTop);
        if (height > startY) {
            return;
        }
        for (int y = startY; y >= minY; y--) {
            StateRef state = column.getBlock(y);
            if (state.is(defaultBlock.id())) {
                break;
            }
            if (state.is(WATER)) {
                return;
            }
        }
        for (int y = startY; y >= minY && MaterialBlocks.isAir(column.getBlock(y)); y--) {
            column.setBlock(y, defaultBlock);
        }
    }

    private void frozenOceanExtension(int minSurfaceLevel, String biome, BiomeClimate biomeClimate, Column column,
                                      int blockX, int blockZ, int height) {
        double icebergHeight = Math.min(Math.abs((double) icebergSurfaceNoise.get((double) blockX, 0.0, (double) blockZ) * 8.25),
                (double) (icebergPillarNoise.get((double) blockX * 1.28, 0.0, (double) blockZ * 1.28) * 15.0F));
        if (icebergHeight <= 1.8) {
            return;
        }
        double icebergRoof = Math.abs((double) icebergPillarRoofNoise.get((double) blockX * 1.17, 0.0, (double) blockZ * 1.17) * 1.5);
        double top = Math.min(icebergHeight * icebergHeight * 1.2, Math.ceil(icebergRoof * 40.0) + 14.0);
        if (biomeClimate.shouldMeltFrozenOceanIcebergSlightly(biome, blockX, seaLevel, blockZ, seaLevel)) {
            top -= 2.0;
        }
        if (top <= 2.0) {
            return;
        }
        double bottom = (double) seaLevel - top - 7.0;
        top += (double) seaLevel;
        double topCopy = top;
        RandomSource random = noiseRandom.at(blockX, 0, blockZ);
        int maxSnowDepth = 2 + random.nextInt(4);
        int minSnowHeight = seaLevel + 18 + random.nextInt(10);
        int snowDepth = 0;
        for (int y = Math.max(height, (int) topCopy + 1); y >= minSurfaceLevel; y--) {
            if (MaterialBlocks.isAir(column.getBlock(y)) && y < (int) topCopy && random.nextDouble() > 0.01
                    || column.getBlock(y).is(WATER) && y > (int) bottom && y < seaLevel && random.nextDouble() > 0.15) {
                if (snowDepth <= maxSnowDepth && y > minSnowHeight) {
                    column.setBlock(y, SNOW_BLOCK);
                    snowDepth++;
                } else {
                    column.setBlock(y, PACKED_ICE);
                }
            }
        }
    }

    /** {@code generateBands(random)}: 192 entries. */
    private static StateRef[] generateBands(RandomSource random) {
        StateRef[] bands = new StateRef[192];
        Arrays.fill(bands, TERRACOTTA);
        for (int i = 0; i < bands.length; i++) {
            i += random.nextInt(5) + 1;
            if (i < bands.length) {
                bands[i] = ORANGE_TERRACOTTA;
            }
        }
        makeBands(random, bands, 1, YELLOW_TERRACOTTA);
        makeBands(random, bands, 2, BROWN_TERRACOTTA);
        makeBands(random, bands, 1, RED_TERRACOTTA);
        int whiteBandCount = random.nextIntBetweenInclusive(9, 15);
        int i = 0;
        for (int start = 0; i < whiteBandCount && start < bands.length; start += random.nextInt(16) + 4) {
            bands[start] = WHITE_TERRACOTTA;
            if (start - 1 > 0 && random.nextBoolean()) {
                bands[start - 1] = LIGHT_GRAY_TERRACOTTA;
            }
            if (start + 1 < bands.length && random.nextBoolean()) {
                bands[start + 1] = LIGHT_GRAY_TERRACOTTA;
            }
            i++;
        }
        return bands;
    }

    private static void makeBands(RandomSource random, StateRef[] bands, int baseWidth, StateRef state) {
        int bandCount = random.nextIntBetweenInclusive(6, 15);
        for (int i = 0; i < bandCount; i++) {
            int width = baseWidth + random.nextInt(3);
            int start = random.nextInt(bands.length);
            for (int p = 0; start + p < bands.length && p < width; p++) {
                bands[start + p] = state;
            }
        }
    }

    /** {@code getBand}: {@code bands[(y + Math.round(clayBandsOffset(x, 0, z) * 4.0F) + 192) % 192]}. */
    StateRef getBand(int blockX, int y, int blockZ) {
        int offset = Math.round(clayBandsOffsetNoise.get((double) blockX, 0.0, (double) blockZ) * 4.0F);
        return clayBands[(y + offset + clayBands.length) % clayBands.length];
    }

    /** The clay band table (for tests). */
    StateRef[] clayBands() {
        return clayBands.clone();
    }

    /**
     * {@code MaterialSystem$1}, the {@code BlockColumn} over the chunk: {@code getBlock(y)} is the chunk's state
     * ({@code void_air} outside the build height, as {@code ProtoChunk.getBlockState}); {@code setBlock(y, state)} writes only
     * inside {@code [minY, maxY]} and marks fluid states for post-processing.
     */
    private static final class Column {
        private final GenChunk chunk;
        private final int minY;
        private final int maxY;
        int x;
        int z;

        Column(GenChunk chunk) {
            this.chunk = chunk;
            this.minY = chunk.minY();
            this.maxY = chunk.maxY();
        }

        StateRef getBlock(int y) {
            if (y < minY || y > maxY) {
                return MaterialBlocks.VOID_AIR;
            }
            return chunk.getState(x, y, z);
        }

        void setBlock(int y, StateRef state) {
            if (y >= minY && y <= maxY) {
                chunk.setState(x, y, z, state);
                if (MaterialBlocks.hasFluid(state) && chunk instanceof FluidPostProcessing marks) {
                    marks.markPosForPostProcessing(x, y, z);
                }
            }
        }
    }
}
