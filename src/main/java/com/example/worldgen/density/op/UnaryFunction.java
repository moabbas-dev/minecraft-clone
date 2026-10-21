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
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.UnaryFunction} (26.3): the one-input
 * operators, JSON field {@code input}: {@code abs}, {@code square}, {@code cube}, {@code sqrt},
 * {@code half_negative} / {@code quarter_negative} (negative values times 0.5 / 0.25), {@code reciprocal},
 * {@code negate}, {@code squeeze} ({@code c / 2 - c^3 / 24} of the input clamped to [-1, 1]), {@code log}
 * ({@code (float) Math.log}) and {@code sign} ({@code Math.signum}). All float arithmetic.
 */
public record UnaryFunction(Type type, DensityFunction input) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler inputSampler = input.compileSampler(context);
        return switch (type) {
            case ABS -> new AbsSampler(inputSampler);
            case SQUARE -> new SquareSampler(inputSampler);
            case CUBE -> new CubeSampler(inputSampler);
            case SQRT -> new SqrtSampler(inputSampler);
            case HALF_NEGATIVE -> new LeakyReLUSampler(inputSampler, 0.5F);
            case QUARTER_NEGATIVE -> new LeakyReLUSampler(inputSampler, 0.25F);
            case RECIPROCAL -> new ReciprocalSampler(inputSampler);
            case NEGATE -> new NegateSampler(inputSampler);
            case SQUEEZE -> new SqueezeSampler(inputSampler);
            case LOG -> new LogSampler(inputSampler);
            case SIGN -> new SignSampler(inputSampler);
        };
    }

    @Override
    public UnaryFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new UnaryFunction(type, newInput);
    }

    @Override
    public Interval range() {
        Interval in = input.range();
        return switch (type) {
            case ABS -> Interval.abs(in);
            case SQUARE -> Interval.square(in);
            case CUBE -> Interval.mapMonotonic(in, Mth::cube);
            case SQRT -> Interval.pow(in, Interval.ofExact(0.5F));
            case HALF_NEGATIVE -> Interval.mapMonotonic(in, value -> LeakyReLUSampler.apply(0.5F, value));
            case QUARTER_NEGATIVE -> Interval.mapMonotonic(in, value -> LeakyReLUSampler.apply(0.25F, value));
            case RECIPROCAL -> Interval.reciprocal(in);
            case NEGATE -> Interval.sub(Interval.ofExact(0.0F), in);
            case SQUEEZE -> Interval.mapMonotonic(in, SqueezeSampler::apply);
            case LOG -> Interval.log(in);
            case SIGN -> Interval.sign(in);
        };
    }

    @Override
    public int domainAxes() {
        return input.domainAxes();
    }

    /** {@code UnaryFunction.Type} with its JSON id. */
    public enum Type {
        ABS("abs"),
        SQUARE("square"),
        CUBE("cube"),
        SQRT("sqrt"),
        HALF_NEGATIVE("half_negative"),
        QUARTER_NEGATIVE("quarter_negative"),
        RECIPROCAL("reciprocal"),
        NEGATE("negate"),
        SQUEEZE("squeeze"),
        LOG("log"),
        SIGN("sign");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    public record AbsSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Math.abs(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Math.abs(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record SquareSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Mth.square(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Mth.square(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record SqrtSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Mth.sqrt(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Mth.sqrt(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record ReciprocalSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, 1.0F / outputBuffer.get(i));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return 1.0F / input.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record NegateSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, -outputBuffer.get(i));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return -input.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record CubeSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Mth.cube(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Mth.cube(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record LeakyReLUSampler(DensitySampler input, float negativeFactor) implements DensitySampler {

        private static float apply(float negativeFactor, float input) {
            return input > 0.0F ? input : input * negativeFactor;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, apply(negativeFactor, outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return apply(negativeFactor, input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record SqueezeSampler(DensitySampler input) implements DensitySampler {

        private static float apply(float input) {
            float clampedInput = Mth.clamp(input, -1.0F, 1.0F);
            return clampedInput / 2.0F - Mth.cube(clampedInput) / 24.0F;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, apply(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return apply(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record LogSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, (float) Math.log(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) Math.log(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record SignSampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, Math.signum(outputBuffer.get(i)));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Math.signum(input.sampleValue(context, blockX, blockY, blockZ));
        }
    }
}
