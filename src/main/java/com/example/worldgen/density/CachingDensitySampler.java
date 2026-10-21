package com.example.worldgen.density;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler} (26.3): the compiled
 * form of a deduplicated {@code minecraft:cache}; it routes through the context's cache cell {@code id}.
 */
public record CachingDensitySampler(int id, DensitySampler input) implements DensitySampler {

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        context.sampleVolumeCached(id, input, outputBuffer, volume);
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return context.sampleValueCached(id, input, blockX, blockY, blockZ);
    }
}
