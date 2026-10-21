package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.density.generator.ConstantFunction;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction} (26.3), JSON
 * {@code minecraft:lerp} ({@code alpha}, {@code first}, {@code second}): {@code first} when alpha is exactly 0,
 * {@code second} when exactly 1, else {@code Mth.lerp(alpha, first, second)}. The point sampler only evaluates the
 * inputs it needs. Constant {@code first} or {@code second} get specialised samplers.
 */
public record LerpFunction(DensityFunction alpha, DensityFunction first, DensityFunction second) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler alphaSampler = alpha.compileSampler(context);
        DensitySampler firstSampler = first.compileSampler(context);
        DensitySampler secondSampler = second.compileSampler(context);
        if (first instanceof ConstantFunction c) {
            return new ConstFirstSampler(alphaSampler, c.value(), secondSampler);
        }
        if (second instanceof ConstantFunction c) {
            return new ConstSecondSampler(alphaSampler, firstSampler, c.value());
        }
        return new Sampler(alphaSampler, firstSampler, secondSampler);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newAlpha = rule.rewrite(alpha);
        DensityFunction newFirst = rule.rewrite(first);
        DensityFunction newSecond = rule.rewrite(second);
        return newAlpha == alpha && newFirst == first && newSecond == second ? this : new LerpFunction(newAlpha, newFirst, newSecond);
    }

    @Override
    public Interval range() {
        return Interval.lerp(alpha.range(), first.range(), second.range());
    }

    @Override
    public int domainAxes() {
        return alpha.domainAxes() | first.domainAxes() | second.domainAxes();
    }

    /** {@code LerpFunction.Sampler}. */
    public record Sampler(DensitySampler alpha, DensitySampler first, DensitySampler second) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            alpha.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer firstBuffer = context.acquireBuffer(volume)) {
                first.sampleVolume(context, firstBuffer, volume);
                try (ScopedDensityBuffer secondBuffer = context.acquireBuffer(volume)) {
                    second.sampleVolume(context, secondBuffer, volume);
                    for (int i = 0; i < outputBuffer.size(); ++i) {
                        float a = outputBuffer.get(i);
                        float f = firstBuffer.get(i);
                        float s = secondBuffer.get(i);
                        if (a == 0.0F) {
                            outputBuffer.set(i, f);
                        } else if (a == 1.0F) {
                            outputBuffer.set(i, s);
                        } else {
                            outputBuffer.set(i, Mth.lerp(a, f, s));
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float a = alpha.sampleValue(context, blockX, blockY, blockZ);
            if (a == 0.0F) {
                return first.sampleValue(context, blockX, blockY, blockZ);
            }
            return a == 1.0F ? second.sampleValue(context, blockX, blockY, blockZ)
                    : Mth.lerp(a, first.sampleValue(context, blockX, blockY, blockZ), second.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    /** {@code LerpFunction.ConstFirstSampler}. */
    public record ConstFirstSampler(DensitySampler alpha, float first, DensitySampler second) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            alpha.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer secondBuffer = context.acquireBuffer(volume)) {
                second.sampleVolume(context, secondBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    float a = outputBuffer.get(i);
                    float s = secondBuffer.get(i);
                    if (a == 0.0F) {
                        outputBuffer.set(i, first);
                    } else if (a == 1.0F) {
                        outputBuffer.set(i, s);
                    } else {
                        outputBuffer.set(i, Mth.lerp(a, first, s));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float a = alpha.sampleValue(context, blockX, blockY, blockZ);
            if (a == 0.0F) {
                return first;
            }
            return a == 1.0F ? second.sampleValue(context, blockX, blockY, blockZ)
                    : Mth.lerp(a, first, second.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    /** {@code LerpFunction.ConstSecondSampler}. */
    public record ConstSecondSampler(DensitySampler alpha, DensitySampler first, float second) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            alpha.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer firstBuffer = context.acquireBuffer(volume)) {
                first.sampleVolume(context, firstBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    float a = outputBuffer.get(i);
                    float f = firstBuffer.get(i);
                    if (a == 0.0F) {
                        outputBuffer.set(i, f);
                    } else if (a == 1.0F) {
                        outputBuffer.set(i, second);
                    } else {
                        outputBuffer.set(i, Mth.lerp(a, f, second));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float a = alpha.sampleValue(context, blockX, blockY, blockZ);
            if (a == 0.0F) {
                return first.sampleValue(context, blockX, blockY, blockZ);
            }
            return a == 1.0F ? second : Mth.lerp(a, first.sampleValue(context, blockX, blockY, blockZ), second);
        }
    }
}
