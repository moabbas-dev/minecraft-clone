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
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.RoundFunction} (26.3): {@code floor},
 * {@code round} ({@code Math.round(float)}), {@code ceil}, {@code truncate}; JSON {@code input} and optional
 * {@code multiple} (default constant 1). With a multiple {@code m != 0} the value is
 * {@code roundToInteger(input / m) * m}; {@code m == 0} returns the input; a constant multiple of exactly 1
 * compiles to the integer sampler.
 */
public record RoundFunction(Type type, DensityFunction input, DensityFunction multiple) implements DensityFunction {

    private static float roundToInteger(float input, Type type) {
        return switch (type) {
            case FLOOR -> (float) Math.floor(input);
            case ROUND -> (float) Math.round(input);
            case CEIL -> (float) Math.ceil(input);
            case TRUNCATE -> input > 0.0F ? (float) Math.floor(input) : (float) Math.ceil(input);
        };
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler inputSampler = input.compileSampler(context);
        if (multiple instanceof ConstantFunction c && c.value() == 1.0F) {
            return new IntegerMultipleSampler(type, inputSampler);
        }
        return new Sampler(type, inputSampler, multiple.compileSampler(context));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        DensityFunction newMultiple = rule.rewrite(multiple);
        return newInput == input && newMultiple == multiple ? this : new RoundFunction(type, newInput, newMultiple);
    }

    @Override
    public Interval range() {
        Interval multipleRange = multiple.range();
        return Interval.mul(Interval.mapMonotonic(Interval.div(input.range(), multipleRange), value -> roundToInteger(value, type)),
                multipleRange);
    }

    @Override
    public int domainAxes() {
        return input.domainAxes() | multiple.domainAxes();
    }

    /** {@code RoundFunction.Type} with its JSON id. */
    public enum Type {
        FLOOR("floor"),
        ROUND("round"),
        CEIL("ceil"),
        TRUNCATE("truncate");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    private record Sampler(Type type, DensitySampler input, DensitySampler multiple) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer multipleBuffer = context.acquireBuffer(volume)) {
                multiple.sampleVolume(context, multipleBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    outputBuffer.set(i, apply(outputBuffer.get(i), multipleBuffer.get(i)));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float in = input.sampleValue(context, blockX, blockY, blockZ);
            float m = multiple.sampleValue(context, blockX, blockY, blockZ);
            return apply(in, m);
        }

        private float apply(float in, float m) {
            return m == 0.0F ? in : roundToInteger(in / m, type) * m;
        }
    }

    private record IntegerMultipleSampler(Type type, DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, roundToInteger(outputBuffer.get(i), type));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return roundToInteger(input.sampleValue(context, blockX, blockY, blockZ), type);
        }
    }
}
