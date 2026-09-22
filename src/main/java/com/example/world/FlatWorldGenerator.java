package com.example.world;

/** Vanilla's default superflat preset: bedrock, then 2 layers of dirt, then grass on top. */
public class FlatWorldGenerator implements WorldGenerator {
    private static final int DIRT_LAYERS = 2;
    public static final int GRASS_HEIGHT = DIRT_LAYERS + 1;

    @Override
    public void generate(Chunk chunk, World world) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setLocal(x, 0, z, BlockType.BEDROCK);
                for (int y = 1; y <= DIRT_LAYERS; y++) {
                    chunk.setLocal(x, y, z, BlockType.DIRT);
                }
                chunk.setLocal(x, GRASS_HEIGHT, z, BlockType.GRASS_BLOCK);
            }
        }
    }

    @Override
    public void computeTint(Chunk chunk) {
        // A flat world is effectively single-biome (plains-like) - a fixed tint is correct
        // here rather than plumbing climate noise into a generator that otherwise has none.
        float[] grass = {0.62f, 0.75f, 0.38f};
        float[] foliage = {0.42f, 0.68f, 0.20f};
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setGrassTint(x, z, grass[0], grass[1], grass[2]);
                chunk.setFoliageTint(x, z, foliage[0], foliage[1], foliage[2]);
            }
        }
    }
}
