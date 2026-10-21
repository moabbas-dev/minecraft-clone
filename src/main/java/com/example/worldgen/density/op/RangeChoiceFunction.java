package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.density.generator.ConstantFunction;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction} (26.3), JSON
 * {@code minecraft:range_choice} ({@code input}, {@code min_inclusive}, {@code max_exclusive},
 * {@code when_in_range}, {@code when_out_of_range}). Two constant branches compile to a choosing sampler.
 */
public record RangeChoiceFunction(DensityFunction input, float minInclusive, float maxExclusive,
                                  DensityFunction whenInRange, DensityFunction whenOutOfRange) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler inputSampler = input.compileSampler(context);
        if (whenInRange instanceof ConstantFunction in && whenOutOfRange instanceof ConstantFunction out) {
            return new ConstSampler(inputSampler, minInclusive, maxExclusive, in.value(), out.value());
        }
        return new Sampler(inputSampler, minInclusive, maxExclusive, whenInRange.compileSampler(context),
                whenOutOfRange.compileSampler(context));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        DensityFunction newIn = rule.rewrite(whenInRange);
        DensityFunction newOut = rule.rewrite(whenOutOfRange);
        return newInput == input && newIn == whenInRange && newOut == whenOutOfRange
                ? this : new RangeChoiceFunction(newInput, minInclusive, maxExclusive, newIn, newOut);
    }

    @Override
    public Interval range() {
        return Interval.encapsulating(whenInRange.range(), whenOutOfRange.range());
    }

    @Override
    public int domainAxes() {
        return input.domainAxes() | whenInRange.domainAxes() | whenOutOfRange.domainAxes();
    }

    private record Sampler(DensitySampler input, float minInclusive, float maxExclusive, DensitySampler whenInRange,
                           DensitySampler whenOutOfRange) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            whenInRange.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(volume)) {
                input.sampleVolume(context, inputBuffer, volume);
                try (ScopedDensityBuffer whenOutOfRangeBuffer = context.acquireBuffer(volume)) {
                    whenOutOfRange.sampleVolume(context, whenOutOfRangeBuffer, volume);
                    for (int i = 0; i < outputBuffer.size(); ++i) {
                        float in = inputBuffer.get(i);
                        if (!(in >= minInclusive) || !(in < maxExclusive)) {
                            outputBuffer.set(i, whenOutOfRangeBuffer.get(i));
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float inputValue = input.sampleValue(context, blockX, blockY, blockZ);
            return inputValue >= minInclusive && inputValue < maxExclusive
                    ? whenInRange.sampleValue(context, blockX, blockY, blockZ)
                    : whenOutOfRange.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    private record ConstSampler(DensitySampler input, float minInclusive, float maxExclusive, float whenInRange,
                                float whenOutOfRange) implements DensitySampler {

        private float choose(float in) {
            return in >= minInclusive && in < maxExclusive ? whenInRange : whenOutOfRange;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, choose(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return choose(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }
}
