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
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.FindTopSurfaceFunction} (26.3), JSON
 * {@code minecraft:find_top_surface}: {@code density}, {@code upper_bound}, {@code lower_bound} (int in
 * [2 * DimensionType.MIN_Y, 2 * DimensionType.MAX_Y]) and positive {@code cell_height}. Starting from
 * {@code floor(upperBound / cellHeight) * cellHeight} it steps down by {@code cellHeight} to {@code lowerBound} and
 * returns the first y where {@code density > 0} (else {@code lowerBound}). The result does not depend on y: the
 * compiled sampler is wrapped in a y=0 {@link SliceFunction.YSampler}.
 */
public record FindTopSurfaceFunction(DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight)
        implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        Sampler sampler = new Sampler(density.compileSampler(context), upperBound.compileSampler(context), lowerBound, cellHeight);
        return new SliceFunction.YSampler(sampler, 0);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newDensity = rule.rewrite(density);
        DensityFunction newUpperBound = rule.rewrite(upperBound);
        return newDensity == density && newUpperBound == upperBound ? this : new FindTopSurfaceFunction(newDensity, newUpperBound, lowerBound, cellHeight);
    }

    @Override
    public Interval range() {
        return Interval.of((float) lowerBound, Math.max((float) lowerBound, upperBound.range().max()));
    }

    @Override
    public int domainAxes() {
        return (density.domainAxes() | upperBound.domainAxes()) & ~AXIS_Y;
    }

    private record Sampler(DensitySampler density, DensitySampler upperBound, int lowerBound, int cellHeight) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if (volume.sizeY() != 1) {
                throw new IllegalArgumentException("Cannot sample with sizeY=" + volume.sizeY());
            }
            upperBound.sampleVolume(context, outputBuffer, volume);
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); ++z) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); ++x) {
                    int blockX = volume.blockX(x);
                    float upper = outputBuffer.get(index);
                    outputBuffer.set(index, (float) findSurfaceFrom(context, blockX, blockZ, upper));
                    ++index;
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float upper = upperBound.sampleValue(context, blockX, blockY, blockZ);
            return (float) findSurfaceFrom(context, blockX, blockZ, upper);
        }

        private int findSurfaceFrom(SamplerContext context, int x, int z, float upper) {
            int topY = Mth.floor(upper / (float) cellHeight) * cellHeight;
            if (topY <= lowerBound) {
                return lowerBound;
            }
            for (int probeY = topY; probeY >= lowerBound; probeY -= cellHeight) {
                if (density.sampleValue(context, x, probeY, z) > 0.0F) {
                    return probeY;
                }
            }
            return lowerBound;
        }
    }
}
