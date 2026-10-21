package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.Holder;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.density.op.BinaryFunction;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction} (26.3): the
 * coordinate-shift noises {@code minecraft:shift} (xyz at 1/4), {@code minecraft:shift_a} (x, 0, z at 1/4) and
 * {@code minecraft:shift_b} (z, x, 0 at 1/4), each times 4; JSON field {@code noise}. Range is
 * {@code noise.range * [4, 4]}.
 */
public sealed interface ShiftNoiseFunction extends DensityFunction
        permits ShiftNoiseFunction.Shift, ShiftNoiseFunction.ShiftA, ShiftNoiseFunction.ShiftB {

    double COORDINATE_FACTOR = 0.25;
    float VALUE_FACTOR = 4.0F;

    Holder<NormalNoise> offsetNoise();

    @Override
    default Interval range() {
        return Interval.mul(offsetNoise().value().range(), Interval.ofExact(VALUE_FACTOR));
    }

    /** {@code ShiftNoiseFunction.Shift}: {@code ConstMul(NoiseFunction.Sampler(noise, 0.25, 0.25), 4)}. */
    record Shift(Holder<NormalNoise> offsetNoise) implements ShiftNoiseFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            Noise noise = context.createNoiseSampler(offsetNoise);
            return new BinaryFunction.ConstMulSampler(new NoiseFunction.Sampler(noise, 0.25, 0.25), 4.0F);
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new Shift(offsetNoise);
        }

        @Override
        public int domainAxes() {
            return ALL_AXES;
        }
    }

    /** {@code ShiftNoiseFunction.ShiftA}: {@code ConstMul(NoiseFunction.Sampler(noise, 0.25, 0.0), 4)}. */
    record ShiftA(Holder<NormalNoise> offsetNoise) implements ShiftNoiseFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            Noise noise = context.createNoiseSampler(offsetNoise);
            return new BinaryFunction.ConstMulSampler(new NoiseFunction.Sampler(noise, 0.25, 0.0), 4.0F);
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new ShiftA(offsetNoise);
        }

        @Override
        public int domainAxes() {
            return AXIS_X | AXIS_Z;
        }
    }

    /**
     * {@code ShiftNoiseFunction.ShiftB}: point value {@code noise.get(z * 0.25, x * 0.25, 0) * 4}; the volume path
     * samples a transposed (z, x, 1) volume with {@code addToVolume(..., 0.25, 0.25, 4)} and spreads each column
     * over y.
     */
    record ShiftB(Holder<NormalNoise> offsetNoise) implements ShiftNoiseFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            Noise noise = context.createNoiseSampler(offsetNoise);
            return new DensitySampler() {
                @Override
                public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
                    DensityVolume transposedVolume = new DensityVolume(volume.sizeZ(), volume.sizeX(), 1,
                            volume.minBlockZ(), volume.minBlockX(), 0, volume.stepBlockZ(), volume.stepBlockX(), 1);
                    try (ScopedDensityBuffer transposedBuffer = context.acquireBuffer(transposedVolume)) {
                        transposedBuffer.fill(0.0F);
                        noise.addToVolume(transposedBuffer, transposedVolume, 0.25, 0.25, 4.0F);
                        for (int z = 0; z < volume.sizeZ(); ++z) {
                            for (int x = 0; x < volume.sizeX(); ++x) {
                                float value = transposedBuffer.get(transposedVolume.indexUnchecked(z, x, 0));
                                outputBuffer.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
                            }
                        }
                    }
                }

                @Override
                public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                    return noise.get((double) blockZ * 0.25, (double) blockX * 0.25, 0.0) * 4.0F;
                }
            };
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return new ShiftB(offsetNoise);
        }

        @Override
        public int domainAxes() {
            return AXIS_X | AXIS_Z;
        }
    }
}
