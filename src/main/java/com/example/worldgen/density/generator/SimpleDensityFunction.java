package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.density.ContextBoundSampler;
import com.example.worldgen.density.DensityContextKeys;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DfRewriteRule;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.SimpleDensityFunction} (26.3): the
 * argument-less context functions {@code minecraft:blend_alpha} (default 1, range [0, 1], axes X|Z),
 * {@code minecraft:blend_offset} (default 0, infinite range, axes X|Z) and {@code minecraft:beardifier}
 * (default 0, {@code Beardifier.RANGE}, all axes). Each compiles to a {@link ContextBoundSampler} reading the
 * per-chunk sampler from the context.
 */
public enum SimpleDensityFunction implements DensityFunction {
    BLEND_ALPHA("blend_alpha"),
    BLEND_OFFSET("blend_offset"),
    BEARDIFIER("beardifier");

    private final String id;

    SimpleDensityFunction(String id) {
        this.id = id;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return switch (this) {
            case BLEND_ALPHA -> new ContextBoundSampler(DensityContextKeys.BLENDER_ALPHA, new ConstantFunction.Sampler(1.0F));
            case BLEND_OFFSET -> new ContextBoundSampler(DensityContextKeys.BLENDER_OFFSET, new ConstantFunction.Sampler(0.0F));
            case BEARDIFIER -> new ContextBoundSampler(DensityContextKeys.BEARDIFIER, new ConstantFunction.Sampler(0.0F));
        };
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return switch (this) {
            case BLEND_ALPHA -> Interval.of(0.0F, 1.0F);
            case BLEND_OFFSET -> Interval.INFINITE;
            case BEARDIFIER -> DensityContextKeys.BEARDIFIER_RANGE;
        };
    }

    @Override
    public int domainAxes() {
        return switch (this) {
            case BLEND_ALPHA, BLEND_OFFSET -> AXIS_X | AXIS_Z;
            case BEARDIFIER -> ALL_AXES;
        };
    }

    public String id() {
        return id;
    }
}
