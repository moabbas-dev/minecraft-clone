package com.example.world;

import java.util.Random;

/**
 * Vanilla's random ticks (randomTickSpeed 3): every tick, in every 16 x 16 x 16 section of every loaded chunk near the player,
 * three random blocks get a random tick, so a given block is ticked about once in 68 seconds. Only farmland uses them here
 * (FarmBlock.randomTick): it becomes moist when water is within 4 blocks horizontally (up to 1 above) or rain falls on it, dries
 * one step when neither, and turns back into dirt once dry for good.
 */
public final class RandomTicks {
    private RandomTicks() {
    }

    /** Whether rain falls at a position (the block above the farmland). */
    @FunctionalInterface
    public interface Rain {
        boolean at(int x, int y, int z);
    }

    public static final int PER_SECTION = 3;
    private static final int SECTION = 16;
    /** A dry farmland has moisture 6..0 and then turns to dirt: seven dry ticks on average, approximated by a 1 in 7 chance each tick. */
    static final int DRY_FARMLAND_ODDS = 7;

    /**
     * One game tick of random ticks for the chunks within {@code radiusChunks} of chunk ({@code cx}, {@code cz}).
     *
     * @param rainingAt whether rain falls at a position
     */
    public static void tick(World world, Random random, int cx, int cz, int radiusChunks, Rain rainingAt) {
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                if (dx * dx + dz * dz > radiusChunks * radiusChunks) continue;
                Chunk chunk = world.getChunk(cx + dx, cz + dz);
                if (chunk == null || !chunk.isGenerated()) continue;
                for (int section = 0; section < Chunk.SIZE_Y / SECTION; section++) {
                    for (int i = 0; i < PER_SECTION; i++) {
                        int lx = random.nextInt(SECTION), lz = random.nextInt(SECTION), y = section * SECTION + random.nextInt(SECTION);
                        BlockType t = chunk.getLocal(lx, y, lz);
                        if (BlockSupport.isFarmland(t)) {
                            farmlandTick(world, random, (cx + dx) * Chunk.SIZE_X + lx, y, (cz + dz) * Chunk.SIZE_Z + lz, t, rainingAt);
                        }
                    }
                }
            }
        }
    }

    public static void farmlandTick(World world, Random random, int x, int y, int z, BlockType t, Rain rainingAt) {
        boolean wet = isNearWater(world, x, y, z) || rainingAt.at(x, y + 1, z);
        if (wet) {
            if (t != BlockType.FARMLAND_MOIST) world.setBlock(x, y, z, BlockType.FARMLAND_MOIST);
        } else if (t == BlockType.FARMLAND_MOIST) {
            world.setBlock(x, y, z, BlockType.FARMLAND); // moisture 7 -> 6
        } else if (random.nextInt(DRY_FARMLAND_ODDS) == 0) {
            world.setBlock(x, y, z, BlockType.DIRT); // moisture 0 and no crop: dirt again (nothing here keeps farmland alive)
        }
    }

    /** FarmBlock.isNearWater: water within 4 blocks in x and z, from the farmland's own level to one above. */
    public static boolean isNearWater(World world, int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (world.getBlock(x + dx, y + dy, z + dz).isWater()) return true;
                }
            }
        }
        return false;
    }
}
