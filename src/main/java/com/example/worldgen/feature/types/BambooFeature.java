package com.example.worldgen.feature.types;

import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BlockSurvival;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.BambooFeature} (26.3 record {@code (probability)}, {@code place}
 * 369 bytes): if the origin is empty ({@code isEmptyBlock}) and {@code BAMBOO.defaultBlockState().canSurvive}:
 * {@code height = nextInt(12) + 5}; when {@code nextFloat() < probability}: {@code r = nextInt(4) + 1} and every column
 * {@code (x, z)} of the square {@code origin +- r} (x outer, z inner) with {@code dx^2 + dz^2 <= r^2} whose block at
 * {@code getHeight(WORLD_SURFACE, x, z) - 1} is in {@code #beneath_bamboo_podzol_replaceable} becomes {@code PODZOL} (default
 * state); then up to {@code height} {@code BAMBOO_TRUNK} blocks while the position is empty; if at least 3 were placed above
 * the origin ({@code y - origin.y >= 3}) the top three become {@code BAMBOO_FINAL_LARGE}, {@code BAMBOO_TOP_LARGE},
 * {@code BAMBOO_TOP_SMALL} (writing at the stopping position and the two below it). Returns whether the origin step ran.
 * States ({@code <clinit>}): trunk {@code age=1, leaves=none, stage=0}; final large {@code leaves=large, stage=1}; top large
 * {@code leaves=large}; top small {@code leaves=small}.
 */
public final class BambooFeature implements Feature {

    static final StateRef BAMBOO_TRUNK = StateRef.of("minecraft:bamboo", "age", "1", "leaves", "none", "stage", "0");
    static final StateRef BAMBOO_FINAL_LARGE = StateRef.of("minecraft:bamboo", "age", "1", "leaves", "large", "stage", "1");
    static final StateRef BAMBOO_TOP_LARGE = StateRef.of("minecraft:bamboo", "age", "1", "leaves", "large", "stage", "0");
    static final StateRef BAMBOO_TOP_SMALL = StateRef.of("minecraft:bamboo", "age", "1", "leaves", "small", "stage", "0");
    static final String BENEATH_BAMBOO_PODZOL_REPLACEABLE = "minecraft:beneath_bamboo_podzol_replaceable";

    private final float probability;
    private final BlockSurvival survival;

    public BambooFeature(float probability, BlockSurvival survival) {
        this.probability = probability;
        this.survival = survival;
    }

    @Override
    public String type() {
        return "minecraft:bamboo";
    }

    @Override
    public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
        int placed = 0;
        Pos pos = origin;
        if (level.isEmptyBlock(pos) && survival.canSurvive(BlockProperties.BAMBOO, level, pos)) {
            int height = random.nextInt(12) + 5;
            if (random.nextFloat() < probability) {
                int r = random.nextInt(4) + 1;
                for (int x = origin.x() - r; x <= origin.x() + r; ++x) {
                    for (int z = origin.z() - r; z <= origin.z() + r; ++z) {
                        int dx = x - origin.x();
                        int dz = z - origin.z();
                        if (dx * dx + dz * dz <= r * r) {
                            Pos ground = new Pos(x, level.getHeight(HeightmapType.WORLD_SURFACE, x, z) - 1, z);
                            if (survival.tags().is(level.getBlockState(ground), BENEATH_BAMBOO_PODZOL_REPLACEABLE)) {
                                level.setBlock(ground, BlockProperties.PODZOL, 2);
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < height && level.isEmptyBlock(pos); ++i) {
                level.setBlock(pos, BAMBOO_TRUNK, 2);
                pos = pos.above();
            }
            if (pos.y() - origin.y() >= 3) {
                level.setBlock(pos, BAMBOO_FINAL_LARGE, 2);
                pos = pos.below();
                level.setBlock(pos, BAMBOO_TOP_LARGE, 2);
                pos = pos.below();
                level.setBlock(pos, BAMBOO_TOP_SMALL, 2);
            }
            ++placed;
        }
        return placed > 0;
    }
}
