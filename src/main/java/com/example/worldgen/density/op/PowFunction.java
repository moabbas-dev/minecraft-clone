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
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.PowFunction} (26.3), JSON
 * {@code minecraft:pow} ({@code base}, {@code exponent}): {@code (float) Math.pow(base, exponent)} in double. A
 * constant base or exponent is specialised; a constant exponent of +-0.5, +-1, +-2, +-3 becomes sqrt / identity /
 * square / cube (wrapped in a reciprocal when negative).
 */
public record PowFunction(DensityFunction base, DensityFunction exponent) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler baseSampler = base.compileSampler(context);
        DensitySampler exponentSampler = exponent.compileSampler(context);
        if (base instanceof ConstantFunction c) {
            return new ConstBaseSampler((double) c.value(), exponentSampler);
        }
        if (exponent instanceof ConstantFunction c) {
            return compileConstExponent(baseSampler, c.value());
        }
        return new Sampler(baseSampler, exponentSampler);
    }

    private static DensitySampler compileConstExponent(DensitySampler base, float exponent) {
        float absExponent = Math.abs(exponent);
        DensitySampler specialSampler;
        if (absExponent == 0.5F) {
            specialSampler = new UnaryFunction.SqrtSampler(base);
        } else if (absExponent == 1.0F) {
            specialSampler = base;
        } else if (absExponent == 2.0F) {
            specialSampler = new UnaryFunction.SquareSampler(base);
        } else {
            if (absExponent != 3.0F) {
                return new ConstExponentSampler(base, (double) exponent);
            }
            specialSampler = new UnaryFunction.CubeSampler(base);
        }
        return exponent >= 0.0F ? specialSampler : new UnaryFunction.ReciprocalSampler(specialSampler);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newBase = rule.rewrite(base);
        DensityFunction newExponent = rule.rewrite(exponent);
        return newBase == base && newExponent == exponent ? this : new PowFunction(newBase, newExponent);
    }

    @Override
    public Interval range() {
        return Interval.pow(base.range(), exponent.range());
    }

    @Override
    public int domainAxes() {
        return base.domainAxes() | exponent.domainAxes();
    }

    private record Sampler(DensitySampler base, DensitySampler exponent) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            base.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer exponentBuffer = context.acquireBuffer(volume)) {
                exponent.sampleVolume(context, exponentBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    float b = outputBuffer.get(i);
                    float e = exponentBuffer.get(i);
                    outputBuffer.set(i, (float) Math.pow(b, e));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) Math.pow(base.sampleValue(context, blockX, blockY, blockZ), exponent.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    private record ConstBaseSampler(double base, DensitySampler exponent) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            exponent.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, (float) Math.pow(base, outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) Math.pow(base, exponent.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    private record ConstExponentSampler(DensitySampler base, double exponent) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            base.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, (float) Math.pow(outputBuffer.get(i), exponent));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) Math.pow(base.sampleValue(context, blockX, blockY, blockZ), exponent);
        }
    }
}
