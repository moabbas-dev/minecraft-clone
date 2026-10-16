package com.example.world;

import java.util.Random;

/**
 * Vanilla's random ticks (randomTickSpeed 3): every tick, in every 16 x 16 x 16 section of every loaded chunk near the player,
 * three random blocks get a random tick, so a given block is ticked about once in 68 seconds. Only farmland uses them here
 * (FarmBlock.randomTick): its moisture jumps to 7 when water is within 4 blocks horizontally (from its own level to 1 above) or rain falls on it,
 * drops one level per tick when neither, and at 0 it turns back into dirt.
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
                        int lx = random.nextInt(SECTION), lz = random.nextInt(SECTION), y = Chunk.MIN_Y + section * SECTION + random.nextInt(SECTION);
                        BlockType t = chunk.getLocal(lx, y, lz);
                        if (BlockSupport.isFarmland(t)) {
                            farmlandTick(world, random, (cx + dx) * Chunk.SIZE_X + lx, y, (cz + dz) * Chunk.SIZE_Z + lz, t, rainingAt);
                        }
                    }
                }
            }
        }
    }

    /**
     * FarmlandBlock.randomTick: near water or under rain it becomes moisture 7; otherwise it loses one level, and at 0 it turns to
     * dirt (nothing here keeps farmland alive the way a crop does).
     */
    public static void farmlandTick(World world, Random random, int x, int y, int z, BlockType t, Rain rainingAt) {
        int moisture = BlockSupport.moisture(t);
        if (isNearWater(world, x, y, z) || rainingAt.at(x, y + 1, z)) {
            if (moisture < 7) world.setBlock(x, y, z, BlockType.FARMLAND_MOIST);
        } else if (moisture > 0) {
            world.setBlock(x, y, z, BlockSupport.farmland(moisture - 1));
        } else {
            world.setBlock(x, y, z, BlockType.DIRT);
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
