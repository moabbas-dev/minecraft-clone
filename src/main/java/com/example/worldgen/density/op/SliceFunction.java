package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.Axis;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction} (26.3), JSON
 * {@code minecraft:slice} ({@code axis}, {@code coordinate}, {@code input}): evaluates the input with one block
 * coordinate fixed. A direct X-in-Z or Z-in-X nesting compiles to one xz sampler. The optimiser also inserts
 * slices ({@link DfRewriteRule#SLICE_UNIFORM_AXES}).
 */
public record SliceFunction(Axis axis, int coordinate, DensityFunction input) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        if (input instanceof SliceFunction inner
                && (axis == Axis.X && inner.axis() == Axis.Z || axis == Axis.Z && inner.axis() == Axis.X)) {
            int x;
            int z;
            if (axis == Axis.X) {
                x = coordinate;
                z = inner.coordinate();
            } else {
                x = inner.coordinate();
                z = coordinate;
            }
            return new XzSampler(inner.input().compileSampler(context), x, z);
        }
        DensitySampler inputSampler = input.compileSampler(context);
        return switch (axis) {
            case X -> new XSampler(inputSampler, coordinate);
            case Y -> new YSampler(inputSampler, coordinate);
            case Z -> new ZSampler(inputSampler, coordinate);
        };
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new SliceFunction(axis, coordinate, newInput);
    }

    @Override
    public Interval range() {
        return input.range();
    }

    @Override
    public int domainAxes() {
        return input.domainAxes() & ~DensityFunction.axesFrom(axis);
    }

    /** {@code SliceFunction.XSampler}. */
    public record XSampler(DensitySampler input, int x) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if (volume.sizeX() == 1 && volume.minBlockX() == x) {
                input.sampleVolume(context, outputBuffer, volume);
                return;
            }
            DensityVolume inputVolume = new DensityVolume(1, volume.sizeY(), volume.sizeZ(), x, volume.minBlockY(), volume.minBlockZ(),
                    volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(inputVolume)) {
                input.sampleVolume(context, inputBuffer, inputVolume);
                int index = 0;
                for (int iz = 0; iz < volume.sizeZ(); ++iz) {
                    for (int ix = 0; ix < volume.sizeX(); ++ix) {
                        for (int iy = 0; iy < volume.sizeY(); ++iy) {
                            outputBuffer.set(index, inputBuffer.get(inputVolume.indexUnchecked(0, iy, iz)));
                            ++index;
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return input.sampleValue(context, x, blockY, blockZ);
        }
    }

    /** {@code SliceFunction.YSampler}. */
    public record YSampler(DensitySampler input, int y) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if (volume.sizeY() == 1 && volume.minBlockY() == y) {
                input.sampleVolume(context, outputBuffer, volume);
                return;
            }
            DensityVolume inputVolume = new DensityVolume(volume.sizeX(), 1, volume.sizeZ(), volume.minBlockX(), y, volume.minBlockZ(),
                    volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(inputVolume)) {
                input.sampleVolume(context, inputBuffer, inputVolume);
                for (int iz = 0; iz < volume.sizeZ(); ++iz) {
                    for (int ix = 0; ix < volume.sizeX(); ++ix) {
                        float value = inputBuffer.get(inputVolume.indexUnchecked(ix, 0, iz));
                        outputBuffer.setRange(volume.indexUnchecked(ix, 0, iz), volume.sizeY(), value);
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return input.sampleValue(context, blockX, y, blockZ);
        }
    }

    /** {@code SliceFunction.ZSampler}. */
    public record ZSampler(DensitySampler input, int z) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if (volume.sizeZ() == 1 && volume.minBlockZ() == z) {
                input.sampleVolume(context, outputBuffer, volume);
                return;
            }
            DensityVolume inputVolume = new DensityVolume(volume.sizeX(), volume.sizeY(), 1, volume.minBlockX(), volume.minBlockY(), z,
                    volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(inputVolume)) {
                input.sampleVolume(context, inputBuffer, inputVolume);
                int index = 0;
                for (int iz = 0; iz < volume.sizeZ(); ++iz) {
                    for (int ix = 0; ix < volume.sizeX(); ++ix) {
                        for (int iy = 0; iy < volume.sizeY(); ++iy) {
                            outputBuffer.set(index, inputBuffer.get(inputVolume.indexUnchecked(ix, iy, 0)));
                            ++index;
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return input.sampleValue(context, blockX, blockY, z);
        }
    }

    /** {@code SliceFunction.XzSampler}. */
    public record XzSampler(DensitySampler input, int x, int z) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if (volume.sizeX() == 1 && volume.sizeZ() == 1 && volume.minBlockX() == x && volume.minBlockZ() == z) {
                input.sampleVolume(context, outputBuffer, volume);
                return;
            }
            DensityVolume inputVolume = new DensityVolume(1, volume.sizeY(), 1, x, volume.minBlockY(), z,
                    volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(inputVolume)) {
                input.sampleVolume(context, inputBuffer, inputVolume);
                for (int iy = 0; iy < volume.sizeY(); ++iy) {
                    float value = inputBuffer.get(inputVolume.indexUnchecked(0, iy, 0));
                    int index = volume.indexUnchecked(0, iy, 0);
                    for (int iz = 0; iz < volume.sizeZ(); ++iz) {
                        for (int ix = 0; ix < volume.sizeX(); ++ix) {
                            outputBuffer.set(index, value);
                            index += volume.sizeY();
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return input.sampleValue(context, x, blockY, z);
        }
    }
}
