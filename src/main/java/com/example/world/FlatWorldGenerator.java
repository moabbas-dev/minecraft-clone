package com.example.world;

/**
 * Vanilla's classic flat preset (worldgen/flat_level_generator_preset/classic_flat.json): layers bedrock x1, dirt x2, grass_block x1,
 * stacked from the bottom of the world ({@link Chunk#MIN_Y}) upward - so bedrock is at y -64 and the grass at y -61.
 */
public class FlatWorldGenerator implements WorldGenerator {
    private static final int DIRT_LAYERS = 2;
    /** World Y of the grass layer, the top block of the ground. */
    public static final int GRASS_HEIGHT = Chunk.MIN_Y + 1 + DIRT_LAYERS;

    @Override
    public void generate(Chunk chunk, World world) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setLocal(x, Chunk.MIN_Y, z, BlockType.BEDROCK);
                for (int y = Chunk.MIN_Y + 1; y < GRASS_HEIGHT; y++) {
                    chunk.setLocal(x, y, z, BlockType.DIRT);
                }
                chunk.setLocal(x, GRASS_HEIGHT, z, BlockType.GRASS_BLOCK);
            }
        }
    }

    @Override
    public void computeTint(Chunk chunk, World world) {
        // A flat world is effectively single-biome (plains-like) - a fixed tint is correct
        // here rather than plumbing climate noise into a generator that otherwise has none.
        float[] grass = {0.62f, 0.75f, 0.38f};
        float[] foliage = {0.42f, 0.68f, 0.20f};
        // Default vanilla water color (0x3F76E4) - a flat world has no biome to blend from, so
        // this matches what most real biomes already use anyway rather than inventing a
        // flat-specific value.
        float[] water = {0x3F / 255f, 0x76 / 255f, 0xE4 / 255f};
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setGrassTint(x, z, grass[0], grass[1], grass[2]);
                chunk.setFoliageTint(x, z, foliage[0], foliage[1], foliage[2]);
                chunk.setWaterTint(x, z, water[0], water[1], water[2]);
            }
        }
    }
}
