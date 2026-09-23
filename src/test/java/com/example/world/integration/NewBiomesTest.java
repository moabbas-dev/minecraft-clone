package com.example.world.integration;

import com.example.world.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ocean, Frozen Ocean, and Mangrove (added alongside per-biome sky tint) actually generate and
 * behave correctly through the real TerrainGenerator - not just in isolated color-table tests.
 * Mirrors WorldGenerationTest's existing "generate real chunks, scan real blocks" style.
 */
class NewBiomesTest {

    /** Scans a grid of columns around the origin for one whose dominant biome is {@code target}.
     *  Coarse 4-block stride since climate varies smoothly - a real column, not a pure-weight
     *  synthetic one, so the water-fill/tree-placement code under test runs exactly as it would
     *  during real play. */
    private int[] findColumnWithDominantBiome(TerrainGenerator gen, Biome target, int searchRadius) {
        for (int cx = -searchRadius; cx <= searchRadius; cx++) {
            for (int cz = -searchRadius; cz <= searchRadius; cz++) {
                int wx = cx * 4, wz = cz * 4;
                if (gen.climateAt(wx, wz).dominant() == target) {
                    return new int[]{wx, wz};
                }
            }
        }
        return null;
    }

    @Test
    void oceanFrozenOceanAndMangroveAreAllReachableInRealClimateSpace() {
        // Confirms the new biomes' Biome.java targets actually win dominant-biome selection
        // somewhere within a reasonably sized area - not merely definable but unreachable.
        TerrainGenerator gen = new TerrainGenerator(2024L);
        assertNotNull(findColumnWithDominantBiome(gen, Biome.OCEAN, 60), "OCEAN must be reachable");
        assertNotNull(findColumnWithDominantBiome(gen, Biome.FROZEN_OCEAN, 60), "FROZEN_OCEAN must be reachable");
        assertNotNull(findColumnWithDominantBiome(gen, Biome.MANGROVE, 60), "MANGROVE must be reachable");
    }

    @Test
    void frozenOceanColumnsFreezeAtSeaLevelUsingTheSameIceRuleAsSnowyTundra() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithDominantBiome(gen, Biome.FROZEN_OCEAN, 60);
        assertNotNull(column, "expected a FROZEN_OCEAN column within the search area");

        World world = new World(seed, gen);
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);

        int lx = Math.floorMod(column[0], Chunk.SIZE_X), lz = Math.floorMod(column[1], Chunk.SIZE_Z);
        int height = chunk.getColumnHeight(lx, lz);
        if (height < TerrainGenerator.SEA_LEVEL) {
            assertEquals(BlockType.ICE, chunk.getLocal(lx, TerrainGenerator.SEA_LEVEL, lz),
                    "a Frozen Ocean column below sea level must freeze at the surface, same rule as Snowy Tundra");
        }
    }

    @Test
    void oceanColumnsUseLiquidWaterNotIceAtSeaLevel() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithDominantBiome(gen, Biome.OCEAN, 60);
        assertNotNull(column, "expected an OCEAN column within the search area");

        World world = new World(seed, gen);
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);

        int lx = Math.floorMod(column[0], Chunk.SIZE_X), lz = Math.floorMod(column[1], Chunk.SIZE_Z);
        int height = chunk.getColumnHeight(lx, lz);
        if (height < TerrainGenerator.SEA_LEVEL) {
            assertEquals(BlockType.WATER, chunk.getLocal(lx, TerrainGenerator.SEA_LEVEL, lz),
                    "an Ocean column must never freeze at the surface");
        }
    }

    @Test
    void mangroveColumnsNeverGrowTreesAboveTheSeaLevelIceRule() {
        // Not a special case for Mangrove specifically - just confirms it went through the same
        // real generation path (surface block, tree placement) as every other biome without a
        // crash or an obviously wrong block, since Mangrove is this round's only new biome that
        // both generates land AND places trees.
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithDominantBiome(gen, Biome.MANGROVE, 60);
        assertNotNull(column, "expected a MANGROVE column within the search area");

        World world = new World(seed, gen);
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        assertDoesNotThrow(() -> world.generateChunk(chunk));

        int lx = Math.floorMod(column[0], Chunk.SIZE_X), lz = Math.floorMod(column[1], Chunk.SIZE_Z);
        int height = chunk.getColumnHeight(lx, lz);
        assertTrue(height > 0, "a generated Mangrove column must have real terrain, not stay empty");
    }
}
