package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction} (26.3),
 * JSON {@code minecraft:constant} ({@code value}) or a bare number.
 */
public record ConstantFunction(float value) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(value);
    }

    @Override
    public Interval range() {
        return Interval.ofExact(value);
    }

    @Override
    public int domainAxes() {
        return NO_AXES;
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    /** {@code ConstantFunction.Sampler}. */
    public record Sampler(float value) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            outputBuffer.fill(value);
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return value;
        }
    }
}
