package com.example.worldgen.terrain;

import com.example.worldgen.BlockPos;
import com.example.worldgen.DimensionType;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.AquiferConfig;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.Aquifer} (26.3) with {@code Aquifer.createDisabled},
 * {@code Aquifer.Config.create}, {@code Aquifer.FluidStatus}, {@code Aquifer.FluidPicker} and
 * {@code Aquifer.NoiseBasedAquifer}. Block states are {@link StateRef}s; vanilla's identity comparisons with
 * {@code Blocks.LAVA.defaultBlockState()} are {@code equals} with {@link BlockProperties#LAVA}.
 */
public interface Aquifer {

    /** {@code DimensionType.WAY_BELOW_MIN_Y = MIN_Y << 4} (26.3 {@code DimensionType.<clinit>}). */
    int WAY_BELOW_MIN_Y = DimensionType.MIN_Y << 4;

    /** {@code computeSubstance(x, y, z, density)}: the fluid or air at a cell, or {@code null} for the solid default. */
    StateRef computeSubstance(int blockX, int blockY, int blockZ, double density);

    boolean shouldScheduleFluidUpdate();

    /** {@code Aquifer.createDisabled(fluidRule)} (anonymous {@code Aquifer$1}). */
    static Aquifer createDisabled(FluidPicker fluidRule) {
        return new Aquifer() {
            @Override
            public StateRef computeSubstance(int blockX, int blockY, int blockZ, double density) {
                return density > 0.0 ? null : fluidRule.computeFluid(blockX, blockY, blockZ).at(blockY);
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    /** {@code Aquifer.Config.create(cachingSamplers, positionalRandomFactory, volume, fluidRule)}. */
    static Aquifer create(AquiferConfig config, DensitySamplerSet cachingSamplers, PositionalRandomFactory positionalRandomFactory,
                          DensityVolume volume, FluidPicker fluidRule) {
        return new NoiseBasedAquifer(cachingSamplers, config, positionalRandomFactory, volume, fluidRule);
    }

    /** {@code Aquifer.FluidStatus}: fluid {@code fluidType} below {@code fluidLevel}, air from it up. */
    record FluidStatus(int fluidLevel, StateRef fluidType) {
        public StateRef at(int blockY) {
            return blockY < fluidLevel ? fluidType : BlockProperties.AIR;
        }
    }

    /** {@code Aquifer.FluidPicker}. */
    @FunctionalInterface
    interface FluidPicker {
        FluidStatus computeFluid(int blockX, int blockY, int blockZ);
    }

    /** {@code Aquifer.NoiseBasedAquifer}, ported method by method from the 26.3 bytecode. */
    final class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int X_SPACING_SHIFT = 4;
        private static final int Z_SPACING_SHIFT = 4;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private static final int SAMPLE_OFFSET_X = -5;
        private static final int SAMPLE_OFFSET_Y = 1;
        private static final int SAMPLE_OFFSET_Z = -5;
        private static final int MIN_CELL_SAMPLE_X = 0;
        private static final int MIN_CELL_SAMPLE_Y = -1;
        private static final int MIN_CELL_SAMPLE_Z = 0;
        private static final int MAX_CELL_SAMPLE_X = 1;
        private static final int MAX_CELL_SAMPLE_Y = 1;
        private static final int MAX_CELL_SAMPLE_Z = 1;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = {{0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1},
                {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}};

        private final DensitySampler.Bound barrierNoise;
        private final DensitySampler.Bound fluidLevelFloodednessNoise;
        private final DensitySampler.Bound fluidLevelSpreadNoise;
        private final DensitySampler.Bound lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final FluidPicker globalFluidPicker;
        private final DensitySampler.Bound exclusion;
        private boolean shouldScheduleFluidUpdate;
        private final int skipSamplingAboveY;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private final DensitySampler.Bound surfaceLevel;
        /** {@code Long2IntOpenHashMap} keyed by {@code ChunkPos.pack(quantizedX, quantizedZ)}. */
        private final Map<Long, Integer> surfaceLevelCache = new HashMap<>();

        private NoiseBasedAquifer(DensitySamplerSet cachingSamplers, AquiferConfig config, PositionalRandomFactory positionalRandomFactory,
                                  DensityVolume volume, FluidPicker globalFluidPicker) {
            this.barrierNoise = cachingSamplers.get(config.barrierNoise());
            this.fluidLevelFloodednessNoise = cachingSamplers.get(config.fluidLevelFloodednessNoise());
            this.fluidLevelSpreadNoise = cachingSamplers.get(config.fluidLevelSpreadNoise());
            this.lavaNoise = cachingSamplers.get(config.lavaNoise());
            this.exclusion = cachingSamplers.get(config.exclusion());
            this.surfaceLevel = cachingSamplers.get(config.surfaceLevel());
            this.positionalRandomFactory = positionalRandomFactory;
            this.minGridX = gridX(volume.minBlockX() + SAMPLE_OFFSET_X) + MIN_CELL_SAMPLE_X;
            this.globalFluidPicker = globalFluidPicker;
            int maxGridX = gridX(volume.maxBlockX() + SAMPLE_OFFSET_X) + MAX_CELL_SAMPLE_X;
            this.gridSizeX = maxGridX - this.minGridX + 1;
            this.minGridY = gridY(volume.minBlockY() + SAMPLE_OFFSET_Y) + MIN_CELL_SAMPLE_Y;
            int maxGridY = gridY(volume.maxBlockY() + SAMPLE_OFFSET_Y) + MAX_CELL_SAMPLE_Y;
            int gridSizeY = maxGridY - this.minGridY + 1;
            this.minGridZ = gridZ(volume.minBlockZ() + SAMPLE_OFFSET_Z) + MIN_CELL_SAMPLE_Z;
            int maxGridZ = gridZ(volume.maxBlockZ() + SAMPLE_OFFSET_Z) + MAX_CELL_SAMPLE_Z;
            this.gridSizeZ = maxGridZ - this.minGridZ + 1;
            int totalGridSize = this.gridSizeX * gridSizeY * this.gridSizeZ;
            this.aquiferCache = new FluidStatus[totalGridSize];
            this.aquiferLocationCache = new long[totalGridSize];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
            int maxAdjustedSurfaceLevel = adjustSurfaceLevel(maxSurfaceLevel(fromGridX(this.minGridX, 0), fromGridZ(this.minGridZ, 0),
                    fromGridX(maxGridX, X_RANGE - 1), fromGridZ(maxGridZ, Z_RANGE - 1)));
            int skipSamplingAboveGridY = gridY(maxAdjustedSurfaceLevel + Y_SPACING) - MIN_CELL_SAMPLE_Y;
            this.skipSamplingAboveY = fromGridY(skipSamplingAboveGridY, Y_SPACING - 1) - 1;
        }

        /** {@code BlockPos.getX(long)}: {@code (int) (packed << (64 - 38 - 26) >> (64 - 26))}. */
        private static int unpackX(long packed) {
            return (int) (packed << 64 - (BlockPos.PACKED_Y_LENGTH + BlockPos.PACKED_HORIZONTAL_LENGTH) - BlockPos.PACKED_HORIZONTAL_LENGTH
                    >> 64 - BlockPos.PACKED_HORIZONTAL_LENGTH);
        }

        /** {@code BlockPos.getY(long)}. */
        private static int unpackY(long packed) {
            return (int) (packed << 64 - BlockPos.PACKED_Y_LENGTH >> 64 - BlockPos.PACKED_Y_LENGTH);
        }

        /** {@code BlockPos.getZ(long)} ({@code Z_OFFSET = PACKED_Y_LENGTH}). */
        private static int unpackZ(long packed) {
            return (int) (packed << 64 - BlockPos.PACKED_Y_LENGTH - BlockPos.PACKED_HORIZONTAL_LENGTH >> 64 - BlockPos.PACKED_HORIZONTAL_LENGTH);
        }

        /** {@code ChunkPos.pack(x, z)}. */
        private static long packColumn(int x, int z) {
            return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
        }

        private int surfaceLevel(int blockX, int blockZ) {
            int quantizedX = (blockX >> 2) << 2;
            int quantizedZ = (blockZ >> 2) << 2;
            long key = packColumn(quantizedX, quantizedZ);
            Integer cached = surfaceLevelCache.get(key);
            if (cached != null) return cached;
            int value = Mth.floor(surfaceLevel.sampleValue(quantizedX, 0, quantizedZ));
            surfaceLevelCache.put(key, value);
            return value;
        }

        private int maxSurfaceLevel(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
            int minQuartX = minBlockX >> 2;
            int maxQuartX = maxBlockX >> 2;
            int minQuartZ = minBlockZ >> 2;
            int maxQuartZ = maxBlockZ >> 2;
            DensityVolume volume = new DensityVolume(maxQuartX - minQuartX + 1, 1, maxQuartZ - minQuartZ + 1,
                    minQuartX << 2, 0, minQuartZ << 2, 4, 1, 4);
            try (ScopedDensityBuffer buffer = surfaceLevel.sampleVolume(volume)) {
                int maxY = Integer.MIN_VALUE;
                for (int z = 0; z < volume.sizeZ(); ++z) {
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        int level = Mth.floor(buffer.get(volume.indexUnchecked(x, 0, z)));
                        surfaceLevelCache.put(packColumn(volume.blockX(x), volume.blockZ(z)), level);
                        if (level > maxY) maxY = level;
                    }
                }
                return maxY;
            }
        }

        private int getIndex(int gridX, int gridY, int gridZ) {
            int x = gridX - this.minGridX;
            int y = gridY - this.minGridY;
            int z = gridZ - this.minGridZ;
            return (y * this.gridSizeZ + z) * this.gridSizeX + x;
        }

        @Override
        public StateRef computeSubstance(int blockX, int blockY, int blockZ, double density) {
            if (density > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
            FluidStatus globalFluid = this.globalFluidPicker.computeFluid(blockX, blockY, blockZ);
            if (blockY > this.skipSamplingAboveY) {
                this.shouldScheduleFluidUpdate = false;
                return globalFluid.at(blockY);
            }
            if (globalFluid.at(blockY).is(BlockProperties.LAVA.id())) {
                this.shouldScheduleFluidUpdate = false;
                return BlockProperties.LAVA;
            }
            int xAnchor = gridX(blockX + SAMPLE_OFFSET_X);
            int yAnchor = gridY(blockY + SAMPLE_OFFSET_Y);
            int zAnchor = gridZ(blockZ + SAMPLE_OFFSET_Z);
            int distanceSqr1 = Integer.MAX_VALUE;
            int distanceSqr2 = Integer.MAX_VALUE;
            int distanceSqr3 = Integer.MAX_VALUE;
            int distanceSqr4 = Integer.MAX_VALUE;
            int closestIndex1 = 0;
            int closestIndex2 = 0;
            int closestIndex3 = 0;
            int closestIndex4 = 0;
            for (int x1 = MIN_CELL_SAMPLE_X; x1 <= MAX_CELL_SAMPLE_X; ++x1) {
                for (int y1 = MIN_CELL_SAMPLE_Y; y1 <= MAX_CELL_SAMPLE_Y; ++y1) {
                    for (int z1 = MIN_CELL_SAMPLE_Z; z1 <= MAX_CELL_SAMPLE_Z; ++z1) {
                        int spacedGridX = xAnchor + x1;
                        int spacedGridY = yAnchor + y1;
                        int spacedGridZ = zAnchor + z1;
                        int index = getIndex(spacedGridX, spacedGridY, spacedGridZ);
                        long existingLocation = this.aquiferLocationCache[index];
                        long location;
                        if (existingLocation != Long.MAX_VALUE) {
                            location = existingLocation;
                        } else {
                            RandomSource random = this.positionalRandomFactory.at(spacedGridX, spacedGridY, spacedGridZ);
                            location = BlockPos.asLong(fromGridX(spacedGridX, random.nextInt(X_RANGE)),
                                    fromGridY(spacedGridY, random.nextInt(Y_RANGE)), fromGridZ(spacedGridZ, random.nextInt(Z_RANGE)));
                            this.aquiferLocationCache[index] = location;
                        }
                        int dx = unpackX(location) - blockX;
                        int dy = unpackY(location) - blockY;
                        int dz = unpackZ(location) - blockZ;
                        int newDistance = dx * dx + dy * dy + dz * dz;
                        if (distanceSqr1 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = closestIndex2;
                            closestIndex2 = closestIndex1;
                            closestIndex1 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = distanceSqr2;
                            distanceSqr2 = distanceSqr1;
                            distanceSqr1 = newDistance;
                        } else if (distanceSqr2 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = closestIndex2;
                            closestIndex2 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = distanceSqr2;
                            distanceSqr2 = newDistance;
                        } else if (distanceSqr3 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = newDistance;
                        } else if (distanceSqr4 >= newDistance) {
                            closestIndex4 = index;
                            distanceSqr4 = newDistance;
                        }
                    }
                }
            }
            FluidStatus closestStatus1 = getAquiferStatus(closestIndex1);
            double similarity12 = similarity(distanceSqr1, distanceSqr2);
            StateRef fluidState = closestStatus1.at(blockY);
            if (similarity12 <= 0.0) {
                if (similarity12 >= FLOWING_UPDATE_SIMULARITY) {
                    FluidStatus closestStatus2 = getAquiferStatus(closestIndex2);
                    this.shouldScheduleFluidUpdate = !closestStatus1.equals(closestStatus2);
                } else {
                    this.shouldScheduleFluidUpdate = false;
                }
                return fluidState;
            }
            if (fluidState.is(BlockProperties.WATER.id())
                    && this.globalFluidPicker.computeFluid(blockX, blockY - 1, blockZ).at(blockY - 1).is(BlockProperties.LAVA.id())) {
                this.shouldScheduleFluidUpdate = true;
                return fluidState;
            }
            double[] barrierNoiseValue = {Double.NaN};
            FluidStatus closestStatus2 = getAquiferStatus(closestIndex2);
            double barrier12 = similarity12 * calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus1, closestStatus2);
            if (density + barrier12 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
            FluidStatus closestStatus3 = getAquiferStatus(closestIndex3);
            double similarity13 = similarity(distanceSqr1, distanceSqr3);
            if (similarity13 > 0.0) {
                double barrier13 = similarity12 * similarity13
                        * calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus1, closestStatus3);
                if (density + barrier13 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }
            double similarity23 = similarity(distanceSqr2, distanceSqr3);
            if (similarity23 > 0.0) {
                double barrier23 = similarity12 * similarity23
                        * calculatePressure(blockX, blockY, blockZ, barrierNoiseValue, closestStatus2, closestStatus3);
                if (density + barrier23 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }
            boolean mayFlow12 = !closestStatus1.equals(closestStatus2);
            boolean mayFlow23 = similarity23 >= FLOWING_UPDATE_SIMULARITY && !closestStatus2.equals(closestStatus3);
            boolean mayFlow13 = similarity13 >= FLOWING_UPDATE_SIMULARITY && !closestStatus1.equals(closestStatus3);
            if (!mayFlow12 && !mayFlow23 && !mayFlow13) {
                this.shouldScheduleFluidUpdate = similarity13 >= FLOWING_UPDATE_SIMULARITY
                        && similarity(distanceSqr1, distanceSqr4) >= FLOWING_UPDATE_SIMULARITY
                        && !closestStatus1.equals(getAquiferStatus(closestIndex4));
            } else {
                this.shouldScheduleFluidUpdate = true;
            }
            return fluidState;
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(int distanceSqr1, int distanceSqr2) {
            return 1.0 - (double) (distanceSqr2 - distanceSqr1) / 25.0;
        }

        private double calculatePressure(int blockX, int blockY, int blockZ, double[] barrierNoiseValue,
                                         FluidStatus statusClosest1, FluidStatus statusClosest2) {
            StateRef type1 = statusClosest1.at(blockY);
            StateRef type2 = statusClosest2.at(blockY);
            String lava = BlockProperties.LAVA.id();
            String water = BlockProperties.WATER.id();
            if ((!type1.is(lava) || !type2.is(water)) && (!type1.is(water) || !type2.is(lava))) {
                int fluidYDiff = Math.abs(statusClosest1.fluidLevel() - statusClosest2.fluidLevel());
                if (fluidYDiff == 0) {
                    return 0.0;
                }
                double averageFluidY = 0.5 * (double) (statusClosest1.fluidLevel() + statusClosest2.fluidLevel());
                double howFarAboveAverageFluidPoint = (double) blockY + 0.5 - averageFluidY;
                double baseValue = (double) fluidYDiff / 2.0;
                double distanceFromBarrierEdgeTowardsMiddle = baseValue - Math.abs(howFarAboveAverageFluidPoint);
                double gradient;
                if (howFarAboveAverageFluidPoint > 0.0) {
                    double centerPoint = 0.0 + distanceFromBarrierEdgeTowardsMiddle;
                    gradient = centerPoint > 0.0 ? centerPoint / 1.5 : centerPoint / 2.5;
                } else {
                    double centerPoint = 3.0 + distanceFromBarrierEdgeTowardsMiddle;
                    gradient = centerPoint > 0.0 ? centerPoint / 3.0 : centerPoint / 10.0;
                }
                double noiseValue;
                if (!(gradient < -2.0) && !(gradient > 2.0)) {
                    double currentNoiseValue = barrierNoiseValue[0];
                    if (Double.isNaN(currentNoiseValue)) {
                        double barrier = (double) this.barrierNoise.sampleValue(blockX, blockY, blockZ);
                        barrierNoiseValue[0] = barrier;
                        noiseValue = barrier;
                    } else {
                        noiseValue = currentNoiseValue;
                    }
                } else {
                    noiseValue = 0.0;
                }
                return 2.0 * (noiseValue + gradient);
            }
            return 2.0;
        }

        private static int gridX(int blockCoord) {
            return blockCoord >> X_SPACING_SHIFT;
        }

        private static int fromGridX(int gridCoord, int blockOffset) {
            return (gridCoord << X_SPACING_SHIFT) + blockOffset;
        }

        private static int gridY(int blockCoord) {
            return Math.floorDiv(blockCoord, Y_SPACING);
        }

        private static int fromGridY(int gridCoord, int blockOffset) {
            return gridCoord * Y_SPACING + blockOffset;
        }

        private static int gridZ(int blockCoord) {
            return blockCoord >> Z_SPACING_SHIFT;
        }

        private static int fromGridZ(int gridCoord, int blockOffset) {
            return (gridCoord << Z_SPACING_SHIFT) + blockOffset;
        }

        private FluidStatus getAquiferStatus(int index) {
            FluidStatus oldStatus = this.aquiferCache[index];
            if (oldStatus != null) return oldStatus;
            long location = this.aquiferLocationCache[index];
            FluidStatus status = computeFluid(unpackX(location), unpackY(location), unpackZ(location));
            this.aquiferCache[index] = status;
            return status;
        }

        private FluidStatus computeFluid(int x, int y, int z) {
            FluidStatus globalFluid = this.globalFluidPicker.computeFluid(x, y, z);
            int lowestPreliminarySurface = Integer.MAX_VALUE;
            int topOfAquiferCell = y + Y_SPACING;
            int bottomOfAquiferCell = y - Y_SPACING;
            boolean surfaceAtCenterIsUnderGlobalFluidLevel = false;
            for (int[] offset : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int sampleX = x + (offset[0] << 4);
                int sampleZ = z + (offset[1] << 4);
                int level = surfaceLevel(sampleX, sampleZ);
                int adjustedSurfaceLevel = adjustSurfaceLevel(level);
                boolean start = offset[0] == 0 && offset[1] == 0;
                if (start && bottomOfAquiferCell > adjustedSurfaceLevel) {
                    return globalFluid;
                }
                boolean topOfAquiferCellPokesAboveSurface = topOfAquiferCell > adjustedSurfaceLevel;
                if (topOfAquiferCellPokesAboveSurface || start) {
                    FluidStatus globalFluidAtSurface = this.globalFluidPicker.computeFluid(sampleX, adjustedSurfaceLevel, sampleZ);
                    if (!BlockProperties.isAir(globalFluidAtSurface.at(adjustedSurfaceLevel))) {
                        if (start) {
                            surfaceAtCenterIsUnderGlobalFluidLevel = true;
                        }
                        if (topOfAquiferCellPokesAboveSurface) {
                            return globalFluidAtSurface;
                        }
                    }
                }
                lowestPreliminarySurface = Math.min(lowestPreliminarySurface, level);
            }
            int fluidSurfaceLevel = computeSurfaceLevel(x, y, z, globalFluid, lowestPreliminarySurface, surfaceAtCenterIsUnderGlobalFluidLevel);
            return new FluidStatus(fluidSurfaceLevel, computeFluidType(x, y, z, globalFluid, fluidSurfaceLevel));
        }

        private int adjustSurfaceLevel(int preliminarySurfaceLevel) {
            return preliminarySurfaceLevel + 8;
        }

        private int computeSurfaceLevel(int x, int y, int z, FluidStatus globalFluid, int lowestSurfaceLevel,
                                        boolean surfaceAtCenterIsUnderGlobalFluidLevel) {
            double partiallyFloodedness;
            double fullyFloodidness;
            if ((double) this.exclusion.sampleValue(x, y, z) > 0.0) {
                partiallyFloodedness = -1.0;
                fullyFloodidness = -1.0;
            } else {
                int distanceBelowSurface = adjustSurfaceLevel(lowestSurfaceLevel) - y;
                double floodednessFactor = surfaceAtCenterIsUnderGlobalFluidLevel
                        ? Mth.clampedMap((double) distanceBelowSurface, 0.0, 64.0, 1.0, 0.0) : 0.0;
                double floodednessNoiseValue = Mth.clamp((double) this.fluidLevelFloodednessNoise.sampleValue(x, y, z), -1.0, 1.0);
                double fullyFloodedThreshold = Mth.map(floodednessFactor, 1.0, 0.0, -0.3, 0.8);
                double partiallyFloodedThreshold = Mth.map(floodednessFactor, 1.0, 0.0, -0.8, 0.4);
                partiallyFloodedness = floodednessNoiseValue - partiallyFloodedThreshold;
                fullyFloodidness = floodednessNoiseValue - fullyFloodedThreshold;
            }
            if (fullyFloodidness > 0.0) {
                return globalFluid.fluidLevel();
            }
            if (partiallyFloodedness > 0.0) {
                return computeRandomizedFluidSurfaceLevel(x, y, z, lowestSurfaceLevel);
            }
            return WAY_BELOW_MIN_Y;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int lowestSurfaceLevel) {
            int fluidLevelCellX = Math.floorDiv(x, 16);
            int fluidLevelCellY = Math.floorDiv(y, 40);
            int fluidLevelCellZ = Math.floorDiv(z, 16);
            int fluidCellMiddleY = fluidLevelCellY * 40 + 20;
            double fluidLevelSpread = (double) (this.fluidLevelSpreadNoise.sampleValue(fluidLevelCellX, fluidLevelCellY, fluidLevelCellZ) * 10.0F);
            int fluidLevelSpreadQuantized = Mth.floor(fluidLevelSpread / (double) 3) * 3;
            int targetFluidSurfaceLevel = fluidCellMiddleY + fluidLevelSpreadQuantized;
            return Math.min(lowestSurfaceLevel, targetFluidSurfaceLevel);
        }

        private StateRef computeFluidType(int x, int y, int z, FluidStatus globalFluid, int fluidSurfaceLevel) {
            StateRef fluidType = globalFluid.fluidType();
            if (fluidSurfaceLevel <= -10 && fluidSurfaceLevel != WAY_BELOW_MIN_Y && !globalFluid.fluidType().equals(BlockProperties.LAVA)) {
                int fluidTypeCellX = Math.floorDiv(x, 64);
                int fluidTypeCellY = Math.floorDiv(y, 40);
                int fluidTypeCellZ = Math.floorDiv(z, 64);
                double lavaNoiseValue = (double) this.lavaNoise.sampleValue(fluidTypeCellX, fluidTypeCellY, fluidTypeCellZ);
                if (Math.abs(lavaNoiseValue) > 0.3) {
                    fluidType = BlockProperties.LAVA;
                }
            }
            return fluidType;
        }
    }
}
