package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.noise.SimplexNoise;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.EndIslandFunction} (26.3), JSON
 * {@code minecraft:end_outer_islands} (no fields; renamed from {@code end_islands}). Compilation takes
 * {@code createEndIslandRandom()} (a {@code LegacyRandomSource(seed)}), skips 17292 draws and builds a zero-offset
 * {@link SimplexNoise}. The value at a block is {@code (getHeightValue(x / 8, z / 8) - 8) / 128}, independent of y.
 */
public record EndIslandFunction() implements DensityFunction {

    private static final float ISLAND_THRESHOLD = -0.9F;

    private static float getHeightValue(SimplexNoise islandNoise, int sectionX, int sectionZ) {
        int chunkX = sectionX / 2;
        int chunkZ = sectionZ / 2;
        int subSectionX = sectionX % 2;
        int subSectionZ = sectionZ % 2;
        float doffs = -100.0F;
        for (int xo = -12; xo <= 12; ++xo) {
            for (int zo = -12; zo <= 12; ++zo) {
                long totalChunkX = (long) (chunkX + xo);
                long totalChunkZ = (long) (chunkZ + zo);
                if (totalChunkX * totalChunkX + totalChunkZ * totalChunkZ > 4096L
                        && islandNoise.get((double) totalChunkX, (double) totalChunkZ) < ISLAND_THRESHOLD) {
                    float islandSize = (Math.abs((float) totalChunkX) * 3439.0F + Math.abs((float) totalChunkZ) * 147.0F) % 13.0F + 9.0F;
                    float xd = (float) (subSectionX - xo * 2);
                    float zd = (float) (subSectionZ - zo * 2);
                    float newDoffs = 100.0F - Mth.sqrt(xd * xd + zd * zd) * islandSize;
                    newDoffs = Mth.clamp(newDoffs, -100.0F, 80.0F);
                    doffs = Math.max(doffs, newDoffs);
                }
            }
        }
        return doffs;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        RandomSource islandRandom = context.createEndIslandRandom();
        islandRandom.consumeCount(17292);
        return new Sampler(new SimplexNoise(islandRandom, true));
    }

    @Override
    public Interval range() {
        return Interval.of(-0.84375F, 0.5625F);
    }

    @Override
    public int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    private record Sampler(SimplexNoise islandNoise) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); ++x) {
                    int blockX = volume.blockX(x);
                    float value = sampleValue(context, blockX, 0, blockZ);
                    outputBuffer.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (getHeightValue(islandNoise, blockX / 8, blockZ / 8) - 8.0F) / 128.0F;
        }
    }
}
