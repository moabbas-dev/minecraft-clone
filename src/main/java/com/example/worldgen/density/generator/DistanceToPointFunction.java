package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.DistanceMetric;
import com.example.worldgen.density.SamplerContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.DistanceToPointFunction} (26.3), JSON
 * {@code minecraft:distance_to_point}: {@code point} ({@code [x, y, z]} ints, vanilla's {@code Vec3i}) and
 * {@code metric}. Value: {@code metric.compute((float) (px - x), (float) (py - y), (float) (pz - z))}; range
 * {@code [0, +inf]}.
 */
public record DistanceToPointFunction(int pointX, int pointY, int pointZ, DistanceMetric metric) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(pointX, pointY, pointZ, metric);
    }

    @Override
    public Interval range() {
        return Interval.of(0.0F, Float.POSITIVE_INFINITY);
    }

    @Override
    public int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    private record Sampler(int x, int y, int z, DistanceMetric metric) implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            int index = 0;
            for (int iz = 0; iz < volume.sizeZ(); ++iz) {
                int blockZ = volume.blockZ(iz);
                for (int ix = 0; ix < volume.sizeX(); ++ix) {
                    int blockX = volume.blockX(ix);
                    for (int iy = 0; iy < volume.sizeY(); ++iy) {
                        int blockY = volume.blockY(iy);
                        outputBuffer.set(index++, sampleValue(context, blockX, blockY, blockZ));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return metric.compute((float) (x - blockX), (float) (y - blockY), (float) (z - blockZ));
        }
    }
}
