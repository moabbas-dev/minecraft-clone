package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction} (26.3), JSON
 * {@code minecraft:clamp} ({@code input}, {@code min}, {@code max}; {@code max >= min} required):
 * {@code Mth.clamp(float)}.
 */
public record ClampFunction(DensityFunction input, float min, float max) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), min, max);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new ClampFunction(newInput, min, max);
    }

    @Override
    public Interval range() {
        return Interval.clamp(input.range(), min, max);
    }

    @Override
    public int domainAxes() {
        return input.domainAxes();
    }

    /** {@code ClampFunction.Sampler}. */
    public record Sampler(DensitySampler input, float min, float max) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Mth.clamp(outputBuffer.get(i), min, max));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Mth.clamp(input.sampleValue(context, blockX, blockY, blockZ), min, max);
        }
    }
}
