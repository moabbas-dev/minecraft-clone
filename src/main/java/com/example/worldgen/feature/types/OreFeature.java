package com.example.worldgen.feature.types;

import com.example.worldgen.Mth;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BulkSectionAccess;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.RuleTest;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

import java.util.BitSet;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.OreFeature} and {@code AbstractOreFeature} (26.3, bytecode):
 * <ul>
 *     <li>{@code place}: {@code dir = nextFloat() * PI} (float), the vein's two ends at {@code +- sin/cos(dir) * size / 8},
 *     {@code y0, y1 = origin.y + nextInt(3) - 2}; a box of {@code 2 * (ceil(size / 8) + maxRadius)} by
 *     {@code 2 * (2 + maxRadius)} ({@code maxRadius = ceil((size / 16 * 2 + 1) / 2)}); the vein is placed only if some column
 *     of the box has {@code yStart <= getHeight(OCEAN_FLOOR_WG, x, z)} (x outer, z inner; a region chunk at TERRAIN keeps
 *     only the final heightmaps, so this primes {@code OCEAN_FLOOR_WG} from the chunk's current blocks);</li>
 *     <li>{@code doPlace}: {@code size} spheres along the line ({@code nextDouble()} per sphere for its size), spheres inside a
 *     larger one are dropped, then every not yet tested cell inside a sphere and inside the build height and the write zone is
 *     tested once ({@code BitSet}); the targets are tried in order with {@code canPlaceOre}; the first that passes is written
 *     straight into the section ({@code BulkSectionAccess}, no heightmap update);</li>
 *     <li>{@code canPlaceOre}: the target rule test, then {@code shouldSkipAirCheck} (true for a discard chance {@code <= 0},
 *     false for {@code >= 1}, else {@code nextFloat() >= chance}), then not adjacent to air (six neighbours in
 *     {@code Direction.values()} order through the section access, air outside the sections).</li>
 * </ul>
 */
public final class OreFeature implements Feature {

    /** {@code OreConfiguration.TargetBlockState} / {@code BlockReplacement}: a rule test and the state to write. */
    public record BlockReplacement(RuleTest target, StateRef state) {
    }

    private final List<BlockReplacement> targetStates;
    private final int size;
    private final float discardChanceOnAirExposure;

    public OreFeature(List<BlockReplacement> targetStates, int size, float discardChanceOnAirExposure) {
        this.targetStates = targetStates;
        this.size = size;
        this.discardChanceOnAirExposure = discardChanceOnAirExposure;
    }

    @Override
    public String type() {
        return "minecraft:ore";
    }

    public int size() {
        return size;
    }

    @Override
    public boolean place(GenRegion level, com.example.worldgen.feature.DecorationGenerator generator, RandomSource random, Pos origin) {
        float dir = random.nextFloat() * (float) Math.PI;
        float spreadXY = (float) size / 8.0F;
        int maxRadius = (int) Math.ceil((double) (((float) size / 16.0F * 2.0F + 1.0F) / 2.0F));
        double x0 = (double) origin.x() + Math.sin((double) dir) * (double) spreadXY;
        double x1 = (double) origin.x() - Math.sin((double) dir) * (double) spreadXY;
        double z0 = (double) origin.z() + Math.cos((double) dir) * (double) spreadXY;
        double z1 = (double) origin.z() - Math.cos((double) dir) * (double) spreadXY;
        double y0 = (double) (origin.y() + random.nextInt(3) - 2);
        double y1 = (double) (origin.y() + random.nextInt(3) - 2);
        int ceilSpread = (int) Math.ceil((double) spreadXY);
        int xStart = origin.x() - ceilSpread - maxRadius;
        int yStart = origin.y() - 2 - maxRadius;
        int zStart = origin.z() - ceilSpread - maxRadius;
        int sizeXZ = 2 * (ceilSpread + maxRadius);
        int sizeY = 2 * (2 + maxRadius);
        for (int xprobe = xStart; xprobe <= xStart + sizeXZ; ++xprobe) {
            for (int zprobe = zStart; zprobe <= zStart + sizeXZ; ++zprobe) {
                if (yStart <= level.getHeight(HeightmapType.OCEAN_FLOOR_WG, xprobe, zprobe)) {
                    return doPlace(level, random, x0, x1, z0, z1, y0, y1, xStart, yStart, zStart, sizeXZ, sizeY);
                }
            }
        }
        return false;
    }

    private boolean doPlace(GenRegion level, RandomSource random, double x0, double x1, double z0, double z1, double y0, double y1,
                            int xStart, int yStart, int zStart, int sizeXZ, int sizeY) {
        int placed = 0;
        BitSet tested = new BitSet(sizeXZ * sizeY * sizeXZ);
        double[] data = new double[size * 4];
        for (int i = 0; i < size; ++i) {
            float step = (float) i / (float) size;
            double xx = Mth.lerp((double) step, x0, x1);
            double yy = Mth.lerp((double) step, y0, y1);
            double zz = Mth.lerp((double) step, z0, z1);
            double ss = random.nextDouble() * (double) size / 16.0;
            double r = ((double) (Mth.sin((double) ((float) Math.PI * step)) + 1.0F) * ss + 1.0) / 2.0;
            data[i * 4] = xx;
            data[i * 4 + 1] = yy;
            data[i * 4 + 2] = zz;
            data[i * 4 + 3] = r;
        }
        for (int i1 = 0; i1 < size - 1; ++i1) {
            if (!(data[i1 * 4 + 3] <= 0.0)) {
                for (int i2 = i1 + 1; i2 < size; ++i2) {
                    if (!(data[i2 * 4 + 3] <= 0.0)) {
                        double dx = data[i1 * 4] - data[i2 * 4];
                        double dy = data[i1 * 4 + 1] - data[i2 * 4 + 1];
                        double dz = data[i1 * 4 + 2] - data[i2 * 4 + 2];
                        double dr = data[i1 * 4 + 3] - data[i2 * 4 + 3];
                        if (dr * dr > dx * dx + dy * dy + dz * dz) {
                            if (dr > 0.0) {
                                data[i2 * 4 + 3] = -1.0;
                            } else {
                                data[i1 * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }
        BulkSectionAccess sections = new BulkSectionAccess(level);
        for (int i = 0; i < size; ++i) {
            double r = data[i * 4 + 3];
            if (!(r < 0.0)) {
                double xx = data[i * 4];
                double yy = data[i * 4 + 1];
                double zz = data[i * 4 + 2];
                int xMin = Math.max(Mth.floor(xx - r), xStart);
                int yMin = Math.max(Mth.floor(yy - r), yStart);
                int zMin = Math.max(Mth.floor(zz - r), zStart);
                int xMax = Math.max(Mth.floor(xx + r), xMin);
                int yMax = Math.max(Mth.floor(yy + r), yMin);
                int zMax = Math.max(Mth.floor(zz + r), zMin);
                for (int x = xMin; x <= xMax; ++x) {
                    double xd = ((double) x + 0.5 - xx) / r;
                    if (xd * xd < 1.0) {
                        for (int y = yMin; y <= yMax; ++y) {
                            double yd = ((double) y + 0.5 - yy) / r;
                            if (xd * xd + yd * yd < 1.0) {
                                for (int z = zMin; z <= zMax; ++z) {
                                    double zd = ((double) z + 0.5 - zz) / r;
                                    if (xd * xd + yd * yd + zd * zd < 1.0 && !level.isOutsideBuildHeight(y)) {
                                        int bitSetIndex = x - xStart + (y - yStart) * sizeXZ + (z - zStart) * sizeXZ * sizeY;
                                        if (!tested.get(bitSetIndex)) {
                                            tested.set(bitSetIndex);
                                            if (level.ensureCanWrite(x, y, z) && sections.hasSection(y)) {
                                                StateRef blockState = sections.getBlockState(x, y, z);
                                                for (BlockReplacement targetState : targetStates) {
                                                    if (canPlaceOre(blockState, sections, random, targetState, x, y, z)) {
                                                        sections.setSectionBlockState(x, y, z, targetState.state());
                                                        ++placed;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return placed > 0;
    }

    private boolean canPlaceOre(StateRef state, BulkSectionAccess sections, RandomSource random, BlockReplacement targetState,
                                int x, int y, int z) {
        if (!targetState.target().test(state, new Pos(x, y, z), random)) return false;
        if (shouldSkipAirCheck(random, discardChanceOnAirExposure)) return true;
        return !isAdjacentToAir(sections, x, y, z);
    }

    private static boolean isAdjacentToAir(BulkSectionAccess sections, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            if (BlockProperties.isAir(sections.getBlockState(x + d.stepX(), y + d.stepY(), z + d.stepZ()))) return true;
        }
        return false;
    }

    private static boolean shouldSkipAirCheck(RandomSource random, float discardChanceOnAirExposure) {
        if (discardChanceOnAirExposure <= 0.0F) return true;
        if (discardChanceOnAirExposure >= 1.0F) return false;
        return random.nextFloat() >= discardChanceOnAirExposure;
    }
}
