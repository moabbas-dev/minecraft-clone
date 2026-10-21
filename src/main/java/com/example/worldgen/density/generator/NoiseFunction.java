package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensityFunctions;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.Holder;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction} (26.3), JSON
 * {@code minecraft:noise}: {@code noise} (id or inline), {@code xz_scale}, {@code y_scale} (doubles), optional
 * {@code shift_x}/{@code shift_y}/{@code shift_z} (default the zero constant). In 26.3 the old
 * {@code shifted_noise} type is merged into this one. The value is
 * {@code noise.get(x * xzScale + shiftX, y * yScale + shiftY, z * xzScale + shiftZ)} with the shifts widened from
 * float; compilation picks the unshifted, xz-shifted or xyz-shifted sampler by comparing the shifts with
 * {@link DensityFunctions#zero()}.
 */
public record NoiseFunction(Holder<NormalNoise> noise, double xzScale, double yScale, DensityFunction shiftX,
                            DensityFunction shiftY, DensityFunction shiftZ) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        Noise sampledNoise = context.createNoiseSampler(noise);
        if (shiftX.equals(DensityFunctions.zero()) && shiftY.equals(DensityFunctions.zero()) && shiftZ.equals(DensityFunctions.zero())) {
            return new Sampler(sampledNoise, xzScale, yScale);
        }
        DensitySampler shiftXSampler = shiftX.compileSampler(context);
        DensitySampler shiftZSampler = shiftZ.compileSampler(context);
        if (shiftY.equals(DensityFunctions.zero())) {
            return new ShiftedXzSampler(shiftXSampler, shiftZSampler, sampledNoise, xzScale, yScale);
        }
        DensitySampler shiftYSampler = shiftY.compileSampler(context);
        return new ShiftedXyzSampler(shiftXSampler, shiftYSampler, shiftZSampler, sampledNoise, xzScale, yScale);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newShiftX = rule.rewrite(shiftX);
        DensityFunction newShiftY = rule.rewrite(shiftY);
        DensityFunction newShiftZ = rule.rewrite(shiftZ);
        return newShiftX == shiftX && newShiftY == shiftY && newShiftZ == shiftZ
                ? this : new NoiseFunction(noise, xzScale, yScale, newShiftX, newShiftY, newShiftZ);
    }

    @Override
    public Interval range() {
        return noise.value().range();
    }

    @Override
    public int domainAxes() {
        int axes = ALL_AXES;
        if (yScale == 0.0) {
            axes &= ~AXIS_Y;
        }
        if (xzScale == 0.0) {
            axes &= ~(AXIS_X | AXIS_Z);
        }
        return axes | shiftX.domainAxes() | shiftY.domainAxes() | shiftZ.domainAxes();
    }

    /** {@code NoiseFunction.Sampler}: unshifted; the volume path fills 0 then {@code addToVolume} with amplitude 1. */
    public record Sampler(Noise noise, double xzScale, double yScale) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            outputBuffer.fill(0.0F);
            noise.addToVolume(outputBuffer, volume, xzScale, yScale, 1.0F);
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return noise.get((double) blockX * xzScale, (double) blockY * yScale, (double) blockZ * xzScale);
        }
    }

    /** {@code NoiseFunction.ShiftedXzSampler}. */
    public record ShiftedXzSampler(DensitySampler shiftX, DensitySampler shiftZ, Noise noise, double xzScale,
                                   double yScale) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            shiftX.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer shiftZBuffer = context.acquireBuffer(volume)) {
                shiftZ.sampleVolume(context, shiftZBuffer, volume);
                int index = 0;
                for (int z = 0; z < volume.sizeZ(); ++z) {
                    double baseNoiseZ = (double) volume.blockZ(z) * xzScale;
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        double baseNoiseX = (double) volume.blockX(x) * xzScale;
                        for (int y = 0; y < volume.sizeY(); ++y) {
                            double noiseX = baseNoiseX + (double) outputBuffer.get(index);
                            double noiseY = (double) volume.blockY(y) * yScale;
                            double noiseZ = baseNoiseZ + (double) shiftZBuffer.get(index);
                            outputBuffer.set(index, noise.get(noiseX, noiseY, noiseZ));
                            ++index;
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            double x = (double) blockX * xzScale + (double) shiftX.sampleValue(context, blockX, blockY, blockZ);
            double y = (double) blockY * yScale;
            double z = (double) blockZ * xzScale + (double) shiftZ.sampleValue(context, blockX, blockY, blockZ);
            return noise.get(x, y, z);
        }
    }

    /** {@code NoiseFunction.ShiftedXyzSampler}. */
    public record ShiftedXyzSampler(DensitySampler shiftX, DensitySampler shiftY, DensitySampler shiftZ, Noise noise,
                                    double xzScale, double yScale) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            shiftX.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer shiftYBuffer = context.acquireBuffer(volume)) {
                shiftY.sampleVolume(context, shiftYBuffer, volume);
                try (ScopedDensityBuffer shiftZBuffer = context.acquireBuffer(volume)) {
                    shiftZ.sampleVolume(context, shiftZBuffer, volume);
                    int index = 0;
                    for (int z = 0; z < volume.sizeZ(); ++z) {
                        double baseNoiseZ = (double) volume.blockZ(z) * xzScale;
                        for (int x = 0; x < volume.sizeX(); ++x) {
                            double baseNoiseX = (double) volume.blockX(x) * xzScale;
                            for (int y = 0; y < volume.sizeY(); ++y) {
                                double noiseX = baseNoiseX + (double) outputBuffer.get(index);
                                double noiseY = (double) volume.blockY(y) * yScale + (double) shiftYBuffer.get(index);
                                double noiseZ = baseNoiseZ + (double) shiftZBuffer.get(index);
                                outputBuffer.set(index, noise.get(noiseX, noiseY, noiseZ));
                                ++index;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            double x = (double) blockX * xzScale + (double) shiftX.sampleValue(context, blockX, blockY, blockZ);
            double y = (double) blockY * yScale + (double) shiftY.sampleValue(context, blockX, blockY, blockZ);
            double z = (double) blockZ * xzScale + (double) shiftZ.sampleValue(context, blockX, blockY, blockZ);
            return noise.get(x, y, z);
        }
    }
}
