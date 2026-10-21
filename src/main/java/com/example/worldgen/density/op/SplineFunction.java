package com.example.worldgen.density.op;

import com.example.worldgen.BoundedFloatFunction;
import com.example.worldgen.CubicSpline;
import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction} (26.3), JSON
 * {@code minecraft:spline} ({@code spline}: a {@link CubicSpline} whose coordinates are density functions). The
 * compiled sampler gives each distinct coordinate function (by value equality) an index and samples it at most once
 * per point (point path: NaN-marked cache; volume path: one buffer per coordinate, filled lazily).
 */
public record SplineFunction(CubicSpline<Coordinate> spline) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(context, spline);
    }

    @Override
    public Interval range() {
        return spline.range();
    }

    @Override
    public int domainAxes() {
        int[] axes = {0};
        spline.forEachCoordinate(coordinate -> axes[0] |= coordinate.function().domainAxes());
        return axes[0];
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        boolean[] changed = {false};
        CubicSpline<Coordinate> newSpline = spline.mapCoordinates(coordinate -> {
            DensityFunction function = coordinate.function();
            DensityFunction newFunction = rule.rewrite(function);
            if (newFunction != function) {
                changed[0] = true;
                return new Coordinate(newFunction);
            }
            return coordinate;
        });
        return !changed[0] ? this : new SplineFunction(newSpline);
    }

    @Override
    public String toString() {
        return spline.toString();
    }

    /** {@code SplineFunction.Coordinate}: a density function as a spline coordinate (its {@code apply} is unused). */
    public record Coordinate(DensityFunction function) implements BoundedFloatFunction<Object> {

        @Override
        public float apply(Object point) {
            return 0.0F;
        }

        @Override
        public Interval range() {
            return function.range();
        }
    }

    private static final class Sampler implements DensitySampler {

        private final BoundedFloatFunction<SplineInput> sampler;
        private final int coordinateCount;

        Sampler(CompileContext context, CubicSpline<Coordinate> spline) {
            Map<DensityFunction, SamplerCoordinate> coordinates = new HashMap<>();
            this.sampler = CubicSpline.asSampler(spline.mapCoordinates(coordinate -> coordinates.computeIfAbsent(coordinate.function(),
                    function -> {
                        int index = coordinates.size();
                        DensitySampler compiled = function.compileSampler(context);
                        return new SamplerCoordinate(compiled, index, function.range());
                    })));
            this.coordinateCount = coordinates.size();
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            try (BufferSplineInput input = new BufferSplineInput(context, volume, coordinateCount)) {
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    input.index = i;
                    outputBuffer.set(i, sampler.apply(input));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return sampler.apply(new PointSplineInput(context, blockX, blockY, blockZ, coordinateCount));
        }
    }

    private record SamplerCoordinate(DensitySampler sampler, int index, Interval range) implements BoundedFloatFunction<SplineInput> {
        @Override
        public float apply(SplineInput input) {
            return input.sampleCoordinate(sampler, index);
        }
    }

    private static final class BufferSplineInput implements SplineInput, AutoCloseable {

        private final SamplerContext context;
        private final DensityVolume volume;
        private final ScopedDensityBuffer[] buffers;
        private int index;

        private BufferSplineInput(SamplerContext context, DensityVolume volume, int coordinateCount) {
            this.context = context;
            this.volume = volume;
            this.buffers = new ScopedDensityBuffer[coordinateCount];
        }

        @Override
        public float sampleCoordinate(DensitySampler coordinateSampler, int coordinateIndex) {
            ScopedDensityBuffer buffer = buffers[coordinateIndex];
            if (buffer == null) {
                buffer = context.acquireBuffer(volume);
                coordinateSampler.sampleVolume(context, buffer, volume);
                buffers[coordinateIndex] = buffer;
            }
            return buffer.get(index);
        }

        @Override
        public void close() {
            for (ScopedDensityBuffer buffer : buffers) {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }

    private static final class PointSplineInput implements SplineInput {

        private final SamplerContext context;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final float[] cachedValues;

        private PointSplineInput(SamplerContext context, int blockX, int blockY, int blockZ, int coordinateCount) {
            this.context = context;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.cachedValues = new float[coordinateCount];
            Arrays.fill(cachedValues, Float.NaN);
        }

        @Override
        public float sampleCoordinate(DensitySampler coordinateSampler, int coordinateIndex) {
            float cachedValue = cachedValues[coordinateIndex];
            if (!Float.isNaN(cachedValue)) {
                return cachedValue;
            }
            float value = coordinateSampler.sampleValue(context, blockX, blockY, blockZ);
            cachedValues[coordinateIndex] = value;
            return value;
        }
    }

    @FunctionalInterface
    private interface SplineInput {
        float sampleCoordinate(DensitySampler coordinateSampler, int coordinateIndex);
    }
}
