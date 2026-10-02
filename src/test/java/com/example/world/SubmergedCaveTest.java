package com.example.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SubmergedCaveTest {
    @Test
    void floodFillsOnlyAirBelowSeaLevelInSubmergedColumns() {
        Chunk c = new Chunk(0, 0);
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int x = 0; x < Chunk.SIZE_X; x++)
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                boolean ocean = x < 8;
                heights[x][z] = ocean ? 40 : 80;
                for (int y = 1; y <= heights[x][z]; y++) c.setLocal(x, y, z, BlockType.STONE);
                if (ocean) for (int y = 41; y <= TerrainGenerator.SEA_LEVEL; y++) c.setLocal(x, y, z, BlockType.WATER);
                c.setLocal(x, 20, z, BlockType.AIR); // a carved cave cell
                c.setLocal(x, 21, z, BlockType.AIR);
            }
        TerrainGenerator.floodSubmergedCaves(c, heights);
        assertEquals(BlockType.WATER, c.getLocal(2, 20, 2), "cave under the sea is flooded");
        assertEquals(BlockType.WATER, c.getLocal(2, 21, 2));
        assertEquals(BlockType.AIR, c.getLocal(12, 20, 2), "cave under land stays dry");
        assertEquals(BlockType.STONE, c.getLocal(2, 10, 2), "solid blocks untouched");
    }

    @Test
    void generatedTerrainNeverHasAirDirectlyBeneathWater() {
        int checkedOceanColumns = 0;
        for (long seed : new long[]{7L, 42L}) {
            TerrainGenerator gen = new TerrainGenerator(seed);
            World world = new World(seed, gen);
            for (int cx = -5; cx <= 5; cx++) {
                for (int cz = -5; cz <= 5; cz++) {
                    Chunk c = world.getOrCreateChunk(cx, cz);
                    world.generateChunk(c);
                    for (int x = 0; x < Chunk.SIZE_X; x++)
                        for (int z = 0; z < Chunk.SIZE_Z; z++)
                            for (int y = 1; y <= TerrainGenerator.SEA_LEVEL; y++) {
                                if (c.getLocal(x, y, z) == BlockType.WATER) {
                                    checkedOceanColumns++;
                                    assertNotEquals(BlockType.AIR, c.getLocal(x, y - 1, z),
                                            "air pocket under water at chunk " + cx + "," + cz + " local " + x + "," + (y - 1) + "," + z);
                                }
                            }
                }
            }
        }
        assumeTrue(checkedOceanColumns > 0, "no water in the sampled area");
    }
}
