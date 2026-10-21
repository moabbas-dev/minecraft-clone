package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction} (26.3), JSON
 * {@code minecraft:interpolated}: {@code input} and positive {@code cell_size_xz} / {@code cell_size_y} (in 26.3 the
 * cell size is part of the function, not of the noise settings). The input is sampled at cell corners (multiples
 * of the cell size) and trilinearly interpolated in float:
 * <ul>
 *     <li>point: on a corner the input itself; otherwise the 2x2x2 corner volume is sampled and
 *     {@code Mth.lerp3(xIn / cellXz, yIn / cellY, zIn / cellXz, ...)};</li>
 *     <li>volume aligned to the cell grid: the input volume directly; step-1 volumes: corners sampled as one
 *     cell-stepped volume, then per cell z then x lerps and an incremental y walk
 *     ({@code value += (v1 - v0) * (1 / cellY)}); other steps: computed at block step and subsampled.</li>
 * </ul>
 * The point and volume paths round differently, as in vanilla.
 */
public record InterpolatedFunction(DensityFunction input, int cellSizeXz, int cellSizeY) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), cellSizeXz, cellSizeY, 1.0F / (float) cellSizeXz, 1.0F / (float) cellSizeY);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new InterpolatedFunction(newInput, cellSizeXz, cellSizeY);
    }

    @Override
    public Interval range() {
        return input.range();
    }

    @Override
    public int domainAxes() {
        return input.domainAxes();
    }

    private record Sampler(DensitySampler input, int cellSizeXz, int cellSizeY, float cellSizeXzInv, float cellSizeYInv)
            implements DensitySampler {

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            if ((volume.stepBlockX() == cellSizeXz || volume.sizeX() == 1)
                    && (volume.stepBlockY() == cellSizeY || volume.sizeY() == 1)
                    && (volume.stepBlockZ() == cellSizeXz || volume.sizeZ() == 1)
                    && Math.floorMod(volume.minBlockX(), cellSizeXz) == 0
                    && Math.floorMod(volume.minBlockY(), cellSizeY) == 0
                    && Math.floorMod(volume.minBlockZ(), cellSizeXz) == 0) {
                input.sampleVolume(context, outputBuffer, volume);
            } else if (volume.stepBlockX() == 1 && volume.stepBlockY() == 1 && volume.stepBlockZ() == 1) {
                sampleWithBlockStep(context, outputBuffer, volume);
            } else {
                sampleWithNonBlockStep(context, outputBuffer, volume);
            }
        }

        private void sampleWithNonBlockStep(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            DensityVolume blockVolume = new DensityVolume(volume.sizeX() * volume.stepBlockX(), volume.sizeY() * volume.stepBlockY(),
                    volume.sizeZ() * volume.stepBlockZ(), volume.minBlockX(), volume.minBlockY(), volume.minBlockZ(), 1, 1, 1);
            try (ScopedDensityBuffer blockBuffer = context.acquireBuffer(blockVolume)) {
                sampleWithBlockStep(context, blockBuffer, blockVolume);
                for (int z = 0; z < volume.sizeZ(); ++z) {
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        for (int y = 0; y < volume.sizeY(); ++y) {
                            float value = blockBuffer.get(blockVolume.indexUnchecked(x * volume.stepBlockX(), y * volume.stepBlockY(),
                                    z * volume.stepBlockZ()));
                            outputBuffer.set(volume.indexUnchecked(x, y, z), value);
                        }
                    }
                }
            }
        }

        private void sampleWithBlockStep(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            int minCellX = Mth.floorDiv(volume.minBlockX(), cellSizeXz);
            int minCellY = Mth.floorDiv(volume.minBlockY(), cellSizeY);
            int minCellZ = Mth.floorDiv(volume.minBlockZ(), cellSizeXz);
            int maxCellX = Mth.floorDiv(volume.maxBlockX(), cellSizeXz);
            int maxCellY = Mth.floorDiv(volume.maxBlockY(), cellSizeY);
            int maxCellZ = Mth.floorDiv(volume.maxBlockZ(), cellSizeXz);
            int cellCountX = maxCellX - minCellX + 1;
            int cellCountY = maxCellY - minCellY + 1;
            int cellCountZ = maxCellZ - minCellZ + 1;
            DensityVolume cellVolume = new DensityVolume(
                    Math.floorMod(volume.maxBlockX(), cellSizeXz) == 0 ? cellCountX : cellCountX + 1,
                    Math.floorMod(volume.maxBlockY(), cellSizeY) == 0 ? cellCountY : cellCountY + 1,
                    Math.floorMod(volume.maxBlockZ(), cellSizeXz) == 0 ? cellCountZ : cellCountZ + 1,
                    minCellX * cellSizeXz, minCellY * cellSizeY, minCellZ * cellSizeXz, cellSizeXz, cellSizeY, cellSizeXz);
            try (ScopedDensityBuffer cellBuffer = context.acquireBuffer(cellVolume)) {
                input.sampleVolume(context, cellBuffer, cellVolume);
                for (int cellZ = 0; cellZ < cellCountZ; ++cellZ) {
                    int nextCellZ = Math.min(cellZ + 1, cellVolume.sizeZ() - 1);
                    for (int cellX = 0; cellX < cellCountX; ++cellX) {
                        int nextCellX = Math.min(cellX + 1, cellVolume.sizeX() - 1);
                        float v000 = cellBuffer.get(cellVolume.indexUnchecked(cellX, 0, cellZ));
                        float v100 = cellBuffer.get(cellVolume.indexUnchecked(nextCellX, 0, cellZ));
                        float v001 = cellBuffer.get(cellVolume.indexUnchecked(cellX, 0, nextCellZ));
                        float v101 = cellBuffer.get(cellVolume.indexUnchecked(nextCellX, 0, nextCellZ));
                        for (int cellY = 0; cellY < cellCountY; ++cellY) {
                            int nextCellY = Math.min(cellY + 1, cellVolume.sizeY() - 1);
                            float v010 = cellBuffer.get(cellVolume.indexUnchecked(cellX, nextCellY, cellZ));
                            float v110 = cellBuffer.get(cellVolume.indexUnchecked(nextCellX, nextCellY, cellZ));
                            float v011 = cellBuffer.get(cellVolume.indexUnchecked(cellX, nextCellY, nextCellZ));
                            float v111 = cellBuffer.get(cellVolume.indexUnchecked(nextCellX, nextCellY, nextCellZ));
                            fillCell(outputBuffer, volume, cellVolume, cellX, cellY, cellZ, v000, v100, v010, v110, v001, v101, v011, v111);
                            v000 = v010;
                            v100 = v110;
                            v001 = v011;
                            v101 = v111;
                        }
                    }
                }
            }
        }

        private void fillCell(DensityBuffer outputBuffer, DensityVolume outputVolume, DensityVolume cellVolume, int cellX, int cellY,
                              int cellZ, float v000, float v100, float v010, float v110, float v001, float v101, float v011, float v111) {
            int cellOutputX = cellVolume.blockX(cellX) - outputVolume.minBlockX();
            int cellOutputY = cellVolume.blockY(cellY) - outputVolume.minBlockY();
            int cellOutputZ = cellVolume.blockZ(cellZ) - outputVolume.minBlockZ();
            int x0 = Math.max(0, -cellOutputX);
            int y0 = Math.max(0, -cellOutputY);
            int z0 = Math.max(0, -cellOutputZ);
            int x1 = Math.min(cellSizeXz, outputVolume.sizeX() - cellOutputX) - 1;
            int y1 = Math.min(cellSizeY, outputVolume.sizeY() - cellOutputY) - 1;
            int z1 = Math.min(cellSizeXz, outputVolume.sizeZ() - cellOutputZ) - 1;
            for (int z = z0; z <= z1; ++z) {
                int outputZ = cellOutputZ + z;
                float alphaZ = (float) z * cellSizeXzInv;
                float v00 = Mth.lerp(alphaZ, v000, v001);
                float v01 = Mth.lerp(alphaZ, v010, v011);
                float v10 = Mth.lerp(alphaZ, v100, v101);
                float v11 = Mth.lerp(alphaZ, v110, v111);
                for (int x = x0; x <= x1; ++x) {
                    int outputX = cellOutputX + x;
                    float alphaX = (float) x * cellSizeXzInv;
                    float v0 = Mth.lerp(alphaX, v00, v10);
                    float v1 = Mth.lerp(alphaX, v01, v11);
                    float valueStep = (v1 - v0) * cellSizeYInv;
                    float value = v0 + valueStep * (float) y0;
                    int outputIndex = outputVolume.indexUnchecked(outputX, cellOutputY + y0, outputZ);
                    for (int y = y0; y <= y1; ++y) {
                        outputBuffer.set(outputIndex++, value);
                        value += valueStep;
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            int xInCell = Math.floorMod(blockX, cellSizeXz);
            int yInCell = Math.floorMod(blockY, cellSizeY);
            int zInCell = Math.floorMod(blockZ, cellSizeXz);
            if (xInCell == 0 && yInCell == 0 && zInCell == 0) {
                return input.sampleValue(context, blockX, blockY, blockZ);
            }
            DensityVolume volume = new DensityVolume(2, 2, 2, blockX - xInCell, blockY - yInCell, blockZ - zInCell,
                    cellSizeXz, cellSizeY, cellSizeXz);
            try (ScopedDensityBuffer inputBuffer = context.acquireBuffer(volume)) {
                input.sampleVolume(context, inputBuffer, volume);
                return Mth.lerp3((float) xInCell / (float) cellSizeXz, (float) yInCell / (float) cellSizeY,
                        (float) zInCell / (float) cellSizeXz,
                        inputBuffer.get(volume.indexUnchecked(0, 0, 0)), inputBuffer.get(volume.indexUnchecked(1, 0, 0)),
                        inputBuffer.get(volume.indexUnchecked(0, 1, 0)), inputBuffer.get(volume.indexUnchecked(1, 1, 0)),
                        inputBuffer.get(volume.indexUnchecked(0, 0, 1)), inputBuffer.get(volume.indexUnchecked(1, 0, 1)),
                        inputBuffer.get(volume.indexUnchecked(0, 1, 1)), inputBuffer.get(volume.indexUnchecked(1, 1, 1)));
            }
        }
    }
}
