package com.example.worldgen.material;

import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.RandomState;
import com.example.worldgen.material.condition.ConditionEvaluator;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.random.PositionalRandomFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.MaterialRuleContext} (26.3): the per-chunk mutable state the
 * compiled rules and conditions read.
 * <ul>
 *     <li>{@code updateXZ(x, z, gradX, gradZ)} increments both update counters ({@code lastUpdateXZ}, {@code lastUpdateY})
 *     and computes {@code surfaceDepth = system.getSurfaceDepth(x, z)} eagerly;</li>
 *     <li>{@code updateY(stoneDepthAbove, stoneDepthBelow, waterHeight, y)} increments {@code lastUpdateY} and clears the
 *     cached biome;</li>
 *     <li>lazily, per XZ update: {@code getSurfaceSecondary()}, {@code getMinSurfaceLevel()} (= {@code floor(chunk surface
 *     level at (x, 0, z)) + surfaceDepth - 8}, the value taken from a buffer sampled once over the 16 x 1 x 16 volume at
 *     {@code y = 0} when {@code (x, 0, z)} lies in it, else sampled at the point); per Y update: {@code getBiome()};</li>
 *     <li>{@code getNoiseSampler(noise, is3d)}: one cached supplier per noise and dimensionality (2D at {@code (x, 0, z)}
 *     cached per XZ update, 3D at {@code (x, y, z)} cached per Y update), the noise from {@code RandomState.getOrCreateNoise};</li>
 *     <li>{@code getDensitiesInChunk(f, inChunk)}: {@code inChunk} samples {@code f} once over the expected volume into an
 *     unpooled buffer and reads it by {@code indexOfBlock} (point sample outside); otherwise a point sample per call.</li>
 * </ul>
 * The counters start at {@code -9223372036854775807L}; the lazy values start one behind.
 */
public final class MaterialRuleContext {

    private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;

    private final MaterialSystem system;
    private final RandomState randomState;
    private final DensityVolume expectedVolume;
    private final DensitySamplerSet densitySamplers;
    private final BiomeAt biomeGetter;
    private final BiomeClimate biomeClimate;
    private final WorldGenerationContext context;
    private final Set<String> possibleBiomes;
    private final DensityVolume preliminarySurfaceVolume;
    private DensityBuffer preliminarySurfaceBuffer;
    private final Map<String, DoubleSupplier> noiseSamplers2d = new HashMap<>();
    private final Map<String, DoubleSupplier> noiseSamplers3d = new HashMap<>();

    long lastUpdateXZ = -9223372036854775807L;
    private int blockX;
    private int blockZ;
    private int surfaceGradientX;
    private int surfaceGradientZ;
    private int surfaceDepth;
    private long lastSurfaceDepth2Update = lastUpdateXZ - 1L;
    private double surfaceSecondary;
    private long lastMinSurfaceLevelUpdate = lastUpdateXZ - 1L;
    private int minSurfaceLevel;
    long lastUpdateY = -9223372036854775807L;
    private String biome;
    private int blockY;
    private int waterHeight;
    private int stoneDepthBelow;
    private int stoneDepthAbove;

    MaterialRuleContext(MaterialSystem system, RandomState randomState, DensityVolume expectedVolume,
                        DensitySamplerSet densitySamplers, BiomeAt biomeGetter, BiomeClimate biomeClimate,
                        WorldGenerationContext context, Set<String> possibleBiomes) {
        this.system = system;
        this.randomState = randomState;
        this.expectedVolume = expectedVolume;
        this.densitySamplers = densitySamplers;
        this.biomeGetter = biomeGetter;
        this.biomeClimate = biomeClimate;
        this.context = context;
        this.possibleBiomes = possibleBiomes;
        this.preliminarySurfaceVolume = new DensityVolume(expectedVolume.sizeX(), 1, expectedVolume.sizeZ(),
                expectedVolume.minBlockX(), 0, expectedVolume.minBlockZ());
    }

    void updateXZ(int blockX, int blockZ, int surfaceGradientX, int surfaceGradientZ) {
        ++lastUpdateXZ;
        ++lastUpdateY;
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.surfaceGradientX = surfaceGradientX;
        this.surfaceGradientZ = surfaceGradientZ;
        this.surfaceDepth = system.getSurfaceDepth(blockX, blockZ);
    }

    void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockY) {
        ++lastUpdateY;
        this.biome = null;
        this.blockY = blockY;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
    }

    public double getSurfaceSecondary() {
        if (lastSurfaceDepth2Update != lastUpdateXZ) {
            lastSurfaceDepth2Update = lastUpdateXZ;
            surfaceSecondary = system.getSurfaceSecondary(blockX, blockZ);
        }
        return surfaceSecondary;
    }

    public String getBiome() {
        if (biome == null) {
            biome = biomeGetter.getBiome(blockX, blockY, blockZ);
        }
        return biome;
    }

    /** {@code getBiome().value().coldEnoughToSnow(pos, seaLevel)} for {@code TemperatureCondition}. */
    public boolean coldEnoughToSnow(String biome, int x, int y, int z, int seaLevel) {
        return biomeClimate.coldEnoughToSnow(biome, x, y, z, seaLevel);
    }

    public int getSeaLevel() {
        return system.getSeaLevel();
    }

    public int getMinSurfaceLevel() {
        if (lastMinSurfaceLevelUpdate != lastUpdateXZ) {
            lastMinSurfaceLevelUpdate = lastUpdateXZ;
            int index = preliminarySurfaceVolume.indexOfBlock(blockX, 0, blockZ);
            float preliminarySurface;
            if (index != DensityVolume.NO_BLOCK) {
                if (preliminarySurfaceBuffer == null) {
                    preliminarySurfaceBuffer = DensityBuffer.createUnpooled(preliminarySurfaceVolume.size());
                    densitySamplers.get(system.preliminarySurfaceFunction()).sampleVolume(preliminarySurfaceBuffer,
                            preliminarySurfaceVolume);
                }
                preliminarySurface = preliminarySurfaceBuffer.get(index);
            } else {
                preliminarySurface = densitySamplers.sampleValue(system.preliminarySurfaceFunction(), blockX, 0, blockZ);
            }
            minSurfaceLevel = Mth.floor(preliminarySurface) + surfaceDepth - HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE;
        }
        return minSurfaceLevel;
    }

    public DoubleSupplier getNoiseSampler(String noise, boolean is3d) {
        if (is3d) {
            return noiseSamplers3d.computeIfAbsent(noise, this::createNoiseSampler3d);
        }
        return noiseSamplers2d.computeIfAbsent(noise, this::createNoiseSampler2d);
    }

    /** {@code MaterialRuleContext$1}: the 2D noise at {@code (blockX, 0, blockZ)}, cached per XZ update. */
    private DoubleSupplier createNoiseSampler2d(String id) {
        Noise noise = randomState.getOrCreateNoise(id);
        return new DoubleSupplier() {
            private long lastUpdateXZ = MaterialRuleContext.this.lastUpdateXZ - 1L;
            private double lastNoise;

            @Override
            public double getAsDouble() {
                if (lastUpdateXZ != MaterialRuleContext.this.lastUpdateXZ) {
                    lastNoise = noise.get((double) blockX, 0.0, (double) blockZ);
                    lastUpdateXZ = MaterialRuleContext.this.lastUpdateXZ;
                }
                return lastNoise;
            }
        };
    }

    /** {@code MaterialRuleContext$2}: the 3D noise at {@code (blockX, blockY, blockZ)}, cached per Y update. */
    private DoubleSupplier createNoiseSampler3d(String id) {
        Noise noise = randomState.getOrCreateNoise(id);
        return new DoubleSupplier() {
            private long lastUpdateY = MaterialRuleContext.this.lastUpdateY - 1L;
            private double lastNoise;

            @Override
            public double getAsDouble() {
                if (lastUpdateY != MaterialRuleContext.this.lastUpdateY) {
                    lastNoise = noise.get((double) blockX, (double) blockY, (double) blockZ);
                    lastUpdateY = MaterialRuleContext.this.lastUpdateY;
                }
                return lastNoise;
            }
        };
    }

    public PositionalRandomFactory getOrCreateRandomFactory(String name) {
        return randomState.getOrCreateRandomFactory(name);
    }

    public DensityGetter getDensitiesInChunk(DensityFunction function, boolean inChunk) {
        DensitySampler.Bound sampler = densitySamplers.get(function);
        if (inChunk) {
            DensityBuffer buffer = DensityBuffer.createUnpooled(expectedVolume.size());
            sampler.sampleVolume(buffer, expectedVolume);
            return () -> {
                int index = expectedVolume.indexOfBlock(blockX, blockY, blockZ);
                if (index == DensityVolume.NO_BLOCK) {
                    return sampler.sampleValue(blockX, blockY, blockZ);
                }
                return buffer.get(index);
            };
        }
        return () -> sampler.sampleValue(blockX, blockY, blockZ);
    }

    public Set<String> possibleBiomes() {
        return possibleBiomes;
    }

    public int stoneDepthAbove() {
        return stoneDepthAbove;
    }

    public int stoneDepthBelow() {
        return stoneDepthBelow;
    }

    public int surfaceDepth() {
        return surfaceDepth;
    }

    public int waterHeight() {
        return waterHeight;
    }

    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    public int surfaceGradientX() {
        return surfaceGradientX;
    }

    public int surfaceGradientZ() {
        return surfaceGradientZ;
    }

    public int resolveAnchorY(VerticalAnchor anchor) {
        return anchor.resolveY(context);
    }

    public StateRef getBand(int x, int y, int z) {
        return system.getBand(x, y, z);
    }

    /** {@code MaterialRules.DensityGetter}. */
    @FunctionalInterface
    public interface DensityGetter {
        float get();
    }

    /** {@code MaterialRuleContext.LazyXZCondition}: the result is recomputed once per XZ update. */
    public abstract static class LazyXZCondition implements ConditionEvaluator {
        protected final MaterialRuleContext context;
        private long lastUpdate;
        private Boolean result;

        protected LazyXZCondition(MaterialRuleContext context) {
            this.context = context;
            this.lastUpdate = context.lastUpdateXZ - 1L;
        }

        @Override
        public boolean test() {
            long update = context.lastUpdateXZ;
            if (update == lastUpdate) {
                if (result == null) {
                    throw new IllegalStateException("Update triggered but the result is null");
                }
                return result;
            }
            lastUpdate = update;
            result = compute();
            return result;
        }

        protected abstract boolean compute();
    }

    /** {@code MaterialRuleContext.LazyYCondition}: the result is recomputed once per Y update. */
    public abstract static class LazyYCondition implements ConditionEvaluator {
        protected final MaterialRuleContext context;
        private long lastUpdate;
        private Boolean result;

        protected LazyYCondition(MaterialRuleContext context) {
            this.context = context;
            this.lastUpdate = context.lastUpdateY - 1L;
        }

        @Override
        public boolean test() {
            long update = context.lastUpdateY;
            if (update == lastUpdate) {
                if (result == null) {
                    throw new IllegalStateException("Update triggered but the result is null");
                }
                return result;
            }
            lastUpdate = update;
            result = compute();
            return result;
        }

        protected abstract boolean compute();
    }
}
