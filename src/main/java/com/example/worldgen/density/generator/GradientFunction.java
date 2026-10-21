package com.example.worldgen.density.generator;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.Axis;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.TilingMode;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction} (26.3), JSON
 * {@code minecraft:gradient}: {@code axis}, optional {@code tiling} (default {@code clamp_to_edge}),
 * {@code from_coordinate}, {@code to_coordinate} (ints, must differ), {@code from_value}, {@code to_value}
 * (floats in +-1e6). In 26.3 it replaces {@code y_clamped_gradient}. With
 * {@code factor = (to - from value) / (float) (toCoordinate - fromCoordinate)} the value is
 * {@code fromValue + (float) relative * factor}, where {@code relative} is clamped, wrapped or mirrored per tiling.
 */
public record GradientFunction(Axis axis, TilingMode tiling, int fromCoordinate, int toCoordinate, float fromValue,
                               float toValue) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        int coordinateRange = toCoordinate - fromCoordinate;
        float coordinateFactor = (toValue - fromValue) / (float) coordinateRange;
        return switch (tiling) {
            case CLAMP_TO_EDGE -> new ClampedSampler(axis, fromCoordinate, Math.min(fromCoordinate, toCoordinate),
                    Math.max(fromCoordinate, toCoordinate), fromValue, coordinateFactor);
            case REPEAT -> new RepeatSampler(axis, fromCoordinate, coordinateRange, fromValue, coordinateFactor);
            case MIRRORED_REPEAT -> new MirroredRepeatSampler(axis, fromCoordinate, coordinateRange, fromValue, coordinateFactor);
        };
    }

    @Override
    public Interval range() {
        return Interval.encapsulating(fromValue, toValue);
    }

    @Override
    public int domainAxes() {
        return DensityFunction.axesFrom(axis);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    /** {@code GradientFunction.GradientSampler}: shared volume fill (one value per slice along the axis). */
    private interface GradientSampler extends DensitySampler {

        static void sampleVolume(GradientSampler sampler, DensityBuffer output, DensityVolume volume) {
            switch (sampler.axis()) {
                case X -> {
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        float value = sampler.compute(volume.blockX(x));
                        for (int z = 0; z < volume.sizeZ(); ++z) {
                            output.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
                        }
                    }
                }
                case Y -> {
                    for (int y = 0; y < volume.sizeY(); ++y) {
                        float value = sampler.compute(volume.blockY(y));
                        for (int z = 0; z < volume.sizeZ(); ++z) {
                            for (int x = 0; x < volume.sizeX(); ++x) {
                                output.set(volume.indexUnchecked(x, y, z), value);
                            }
                        }
                    }
                }
                case Z -> {
                    for (int z = 0; z < volume.sizeZ(); ++z) {
                        float value = sampler.compute(volume.blockZ(z));
                        output.setRange(volume.indexUnchecked(0, 0, z), volume.sizeX() * volume.sizeY(), value);
                    }
                }
            }
        }

        Axis axis();

        float compute(int coordinate);

        @Override
        default void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            sampleVolume(this, outputBuffer, volume);
        }

        @Override
        default float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return compute(axis().choose(blockX, blockY, blockZ));
        }
    }

    private record ClampedSampler(Axis axis, int fromCoordinate, int minCoordinate, int maxCoordinate, float fromValue,
                                  float coordinateFactor) implements GradientSampler {
        @Override
        public float compute(int coordinate) {
            int relativeCoordinate = Mth.clamp(coordinate, minCoordinate, maxCoordinate) - fromCoordinate;
            return fromValue + (float) relativeCoordinate * coordinateFactor;
        }
    }

    private record RepeatSampler(Axis axis, int fromCoordinate, int coordinateRange, float fromValue,
                                 float coordinateFactor) implements GradientSampler {
        @Override
        public float compute(int coordinate) {
            int relativeCoordinate = coordinate - fromCoordinate;
            return fromValue + (float) Math.floorMod(relativeCoordinate, coordinateRange) * coordinateFactor;
        }
    }

    private record MirroredRepeatSampler(Axis axis, int fromCoordinate, int coordinateRange, float fromValue,
                                         float coordinateFactor) implements GradientSampler {
        @Override
        public float compute(int coordinate) {
            int relativeCoordinate = coordinate - fromCoordinate;
            int tileIndex = Math.floorDiv(relativeCoordinate, coordinateRange);
            int localCoordinate = relativeCoordinate - tileIndex * coordinateRange;
            return (tileIndex & 1) == 0
                    ? fromValue + (float) localCoordinate * coordinateFactor
                    : fromValue + (float) (coordinateRange - localCoordinate) * coordinateFactor;
        }
    }
}
