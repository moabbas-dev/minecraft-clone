package com.example.worldgen.density;

/** Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet} (26.3). */
@FunctionalInterface
public interface DensitySamplerSet {

    DensitySampler.Bound get(DensityFunction function);

    default float sampleValue(DensityFunction function, int blockX, int blockY, int blockZ) {
        return get(function).sampleValue(blockX, blockY, blockZ);
    }
}
