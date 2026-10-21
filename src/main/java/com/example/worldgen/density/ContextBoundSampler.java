package com.example.worldgen.density;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler} (26.3): delegates to the
 * sampler stored in the context under {@code key}, or to {@code fallbackSampler} when the context has none
 * (this is how {@code blend_alpha}, {@code blend_offset} and {@code beardifier} get their per-chunk values).
 */
public record ContextBoundSampler(ContextKey<? extends DensitySampler> key, DensitySampler fallbackSampler)
        implements DensitySampler {

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        DensitySampler sampler = context.getFieldOrDefault(key, fallbackSampler);
        sampler.sampleVolume(context, outputBuffer, volume);
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        DensitySampler sampler = context.getFieldOrDefault(key, fallbackSampler);
        return sampler.sampleValue(context, blockX, blockY, blockZ);
    }
}
