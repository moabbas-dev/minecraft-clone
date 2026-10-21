package com.example.worldgen.density;

import com.example.worldgen.Interval;

/**
 * The {@link SamplerContext} user-field keys the 26.3 density functions read, with vanilla's names:
 * {@code Blender.CONTEXT_KEY} ({@code minecraft:blender}), {@code Blender.ALPHA_KEY}
 * ({@code minecraft:blender/alpha}), {@code Blender.OFFSET_KEY} ({@code minecraft:blender/offset}) and
 * {@code Beardifier.CONTEXT_KEY} ({@code minecraft:beardifier}; vanilla's {@code Beardifier} is itself a
 * {@link DensitySampler}). {@link #BEARDIFIER_RANGE} is {@code Beardifier.RANGE = Interval.INFINITE}.
 * {@code NoiseChunk} fills these per chunk.
 */
public final class DensityContextKeys {

    public static final ContextKey<DensityBlender> BLENDER = ContextKey.vanilla("blender");
    public static final ContextKey<DensitySampler> BLENDER_ALPHA = ContextKey.vanilla("blender/alpha");
    public static final ContextKey<DensitySampler> BLENDER_OFFSET = ContextKey.vanilla("blender/offset");
    public static final ContextKey<DensitySampler> BEARDIFIER = ContextKey.vanilla("beardifier");
    public static final Interval BEARDIFIER_RANGE = Interval.INFINITE;

    private DensityContextKeys() {
    }
}
