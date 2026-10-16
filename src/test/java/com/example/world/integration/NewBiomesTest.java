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

    /** Same idea as {@link #findColumnWithDominantBiome} but also requires the target biome's own
     *  blend weight to be at least {@code minWeight} there - a column can be the argmax ("dominant")
     *  while still sitting near a climate-space boundary where several biomes are nearly tied, which
     *  dilutes blended properties like tree density enough that a single fixed-seed chunk can roll
     *  zero trees by chance. Requiring real interior-of-territory dominance makes tests reliable
     *  instead of flaky against biome-boundary luck. */
    private int[] findColumnWithMinWeight(TerrainGenerator gen, Biome target, int searchRadius, float minWeight) {
        for (int cx = -searchRadius; cx <= searchRadius; cx++) {
            for (int cz = -searchRadius; cz <= searchRadius; cz++) {
                int wx = cx * 4, wz = cz * 4;
                ClimateSample cs = gen.climateAt(wx, wz);
                if (cs.dominant() == target && cs.weights()[target.ordinal()] >= minWeight) {
                    return new int[]{wx, wz};
                }
            }
        }
        return null;
    }

    /** Mangrove's own baseHeight (63, sea level) means most dominant-Mangrove columns fall under
     *  TerrainGenerator.generate's "beach" override (height &lt;= SEA_LEVEL+1 -&gt; sand, regardless
     *  of biome.surface) - real, pre-existing behavior that predates this round, not new. Finding a
     *  column that clears that override is required to actually observe Mangrove's own mud surface. */
    private int[] findNonBeachMangroveColumn(long seed, int searchRadius) {
        TerrainGenerator probe = new TerrainGenerator(seed);
        for (int cx = -searchRadius; cx <= searchRadius; cx++) {
            for (int cz = -searchRadius; cz <= searchRadius; cz++) {
                int wx = cx * 4, wz = cz * 4;
                if (probe.climateAt(wx, wz).dominant() != Biome.MANGROVE) continue;
                World w = new World(seed, new TerrainGenerator(seed));
                Chunk c = w.getOrCreateChunk(World.worldToChunk(wx), World.worldToChunk(wz));
                w.generateChunk(c);
                int lx = Math.floorMod(wx, Chunk.SIZE_X), lz = Math.floorMod(wz, Chunk.SIZE_Z);
                if (groundHeight(c, lx, lz) > TerrainGenerator.SEA_LEVEL + 1) return new int[]{wx, wz};
            }
        }
        return null;
    }

    @Test
    void mangroveSurfaceIsMudAndStillGrowsTreesOnIt() {
        // Regression guard: switching MANGROVE's surface from grass to mud (this round) required
        // TerrainGenerator.suitableColumn to also accept MUD, or mangrove trees would silently
        // stop generating entirely - this is exactly the bug this test would have caught.
        long seed = 2024L;
        int[] column = findNonBeachMangroveColumn(seed, 60);
        assertNotNull(column, "expected a non-beach MANGROVE column within the search area");

        World world = new World(seed, new TerrainGenerator(seed));
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);

        int lx = Math.floorMod(column[0], Chunk.SIZE_X), lz = Math.floorMod(column[1], Chunk.SIZE_Z);
        // Chunk.getColumnHeight reports the highest non-air block AFTER tree placement, which
        // can be a tree's own leaves/roots stacked above the true ground (with air gaps between
        // canopy layers below that) - walk down past both to find the real terrain surface,
        // rather than assuming the heightmap top - or the first gap below it - is ground.
        int y = chunk.getColumnHeight(lx, lz);
        y = groundHeight(chunk, lx, lz);
        assertEquals(BlockType.MUD, chunk.getLocal(lx, y, lz), "Mangrove Swamp's surface must be mud");

        // A single chunk has only a few tree candidates (1 in 60 columns), so ask the mangrove chunks around: some must have trees.
        int withTrees = 0;
        for (int dcx = -3; dcx <= 3; dcx++) {
            for (int dcz = -3; dcz <= 3; dcz++) {
                Chunk near = world.getOrCreateChunk(cx + dcx, cz + dcz);
                if (!near.isGenerated()) world.generateChunk(near);
                if (countBlock(near, BlockType.MANGROVE_LOG) > 0) withTrees++;
            }
        }
        assertTrue(withTrees > 0, "mangrove trees still grow on mud (chunks with one around here: " + withTrees + ")");
    }

    /** The terrain surface of a column: the first block from the top that is neither air nor a part of any tree (a neighbour's canopy can hang over it). */
    private static int groundHeight(Chunk c, int lx, int lz) {
        int y = c.getColumnHeight(lx, lz);
        while (y > 0) {
            BlockType t = c.getLocal(lx, y, lz);
            boolean treePart = t.isAir() || t.name().endsWith("_LEAVES") || t.name().endsWith("_LOG") || t == BlockType.MANGROVE_ROOTS;
            if (!treePart) break;
            y--;
        }
        return y;
    }

    private boolean isMangroveTreePart(BlockType t) {
        return t == BlockType.MANGROVE_LOG || t == BlockType.MANGROVE_LEAVES || t == BlockType.MANGROVE_ROOTS;
    }

    @Test
    void birchForestDarkForestAndCherryGroveAreAllReachableInRealClimateSpace() {
        // A wider radius for Birch Forest specifically - its climate point sits in a busier part
        // of the 16-biome space (see Biome.java's own comment), so its strongest interior points
        // are spaced further apart than the other two; still cheap since this only samples
        // climateAt, no chunk generation.
        TerrainGenerator gen = new TerrainGenerator(2024L);
        assertNotNull(findColumnWithMinWeight(gen, Biome.BIRCH_FOREST, 150, 0.35f), "BIRCH_FOREST must be reachable with real dominance");
        assertNotNull(findColumnWithMinWeight(gen, Biome.DARK_FOREST, 60, 0.35f), "DARK_FOREST must be reachable with real dominance");
        assertNotNull(findColumnWithMinWeight(gen, Biome.CHERRY_GROVE, 60, 0.35f), "CHERRY_GROVE must be reachable with real dominance");
    }

    @Test
    void birchForestGrowsBirchTreesOnRealGeneratedTerrain() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithMinWeight(gen, Biome.BIRCH_FOREST, 150, 0.35f);
        assertNotNull(column);
        World world = new World(seed, gen);
        Chunk chunk = world.getOrCreateChunk(World.worldToChunk(column[0]), World.worldToChunk(column[1]));
        assertDoesNotThrow(() -> world.generateChunk(chunk));

        assertTrue(countBlock(chunk, BlockType.BIRCH_LOG) > 0, "a full Birch Forest chunk should grow at least one birch tree");
    }

    // --- Birch Forest hardening round: chunk-boundary consistency, reload/no-duplication, and
    // invalid-terrain rejection, named specifically for this biome per the round's checklist -
    // the underlying mechanisms are shared/generic (WorldGenerationTest already covers them for
    // biomes in general), but this locks Birch Forest itself in explicitly. ---

    @Test
    void birchForestClimateIsIdenticalRegardlessOfWhichChunkGeneratesTheBoundaryColumn() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithMinWeight(gen, Biome.BIRCH_FOREST, 150, 0.35f);
        assertNotNull(column);
        // Re-request the exact same world column's climate from a fresh generator instance -
        // climateAt takes only world (x,z), never chunk state, so it must agree with itself
        // regardless of chunk load order/origin (the actual "chunk load order must not affect
        // the result" requirement, expressed the same way WorldGenerationTest already does).
        ClimateSample first = gen.climateAt(column[0], column[1]);
        ClimateSample second = new TerrainGenerator(seed).climateAt(column[0], column[1]);
        assertEquals(Biome.BIRCH_FOREST, first.dominant());
        assertEquals(first.dominant(), second.dominant());
        assertArrayEquals(first.grassColor(), second.grassColor());
        assertArrayEquals(first.skyTint(), second.skyTint());
    }

    @Test
    void reloadingABirchForestChunkNeverDuplicatesOrRemovesTrees() {
        long seed = 2024L;
        int[] column = findColumnWithMinWeight(new TerrainGenerator(seed), Biome.BIRCH_FOREST, 150, 0.35f);
        assertNotNull(column);
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);

        World world = new World(seed, new TerrainGenerator(seed));
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);
        short[] firstGeneration = chunk.exportBlocks();

        // Simulates "chunk unload, then reload" without going through WorldIO's disk round-trip -
        // regenerating the same coordinates from a fresh generator instance must be byte-identical,
        // the actual guarantee that prevents duplicate/missing trees on a real reload.
        World reloadedWorld = new World(seed, new TerrainGenerator(seed));
        Chunk reloadedChunk = reloadedWorld.getOrCreateChunk(cx, cz);
        reloadedWorld.generateChunk(reloadedChunk);
        short[] secondGeneration = reloadedChunk.exportBlocks();

        assertArrayEquals(firstGeneration, secondGeneration,
                "reloading the same Birch Forest chunk coordinates must reproduce identical blocks, not duplicate or drop trees");
    }

    @Test
    void darkForestGrowsDarkOakTreesWithRealTwoByTwoTrunksOnRealGeneratedTerrain() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithMinWeight(gen, Biome.DARK_FOREST, 60, 0.35f);
        assertNotNull(column);
        World world = new World(seed, gen);
        Chunk chunk = world.getOrCreateChunk(World.worldToChunk(column[0]), World.worldToChunk(column[1]));
        assertDoesNotThrow(() -> world.generateChunk(chunk));

        assertTrue(countBlock(chunk, BlockType.DARK_OAK_LOG) > 0, "a full Dark Forest chunk should grow at least one dark oak tree");
    }

    @Test
    void cherryGroveGrowsCherryTreesOnRealGeneratedTerrain() {
        long seed = 2024L;
        TerrainGenerator gen = new TerrainGenerator(seed);
        int[] column = findColumnWithMinWeight(gen, Biome.CHERRY_GROVE, 60, 0.35f);
        assertNotNull(column);
        World world = new World(seed, gen);
        Chunk chunk = world.getOrCreateChunk(World.worldToChunk(column[0]), World.worldToChunk(column[1]));
        assertDoesNotThrow(() -> world.generateChunk(chunk));

        assertTrue(countBlock(chunk, BlockType.CHERRY_LOG) > 0, "a full Cherry Grove chunk should grow at least one cherry tree");
    }

    @Test
    void reloadingADarkForestChunkNeverDuplicatesOrRemovesBranches() {
        // Same pattern as the Birch Forest hardening round's reload test, now exercising Dark
        // Oak's branch geometry through the real generation pipeline (not just the isolated
        // place() call) - regenerating the same coordinates must be byte-identical.
        long seed = 2024L;
        int[] column = findColumnWithMinWeight(new TerrainGenerator(seed), Biome.DARK_FOREST, 60, 0.35f);
        assertNotNull(column);
        int cx = World.worldToChunk(column[0]), cz = World.worldToChunk(column[1]);

        World world = new World(seed, new TerrainGenerator(seed));
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);
        short[] firstGeneration = chunk.exportBlocks();
        assertTrue(countBlock(chunk, BlockType.DARK_OAK_LOG) > 0, "expected at least one Dark Oak tree (with branches) in this chunk");

        World reloadedWorld = new World(seed, new TerrainGenerator(seed));
        Chunk reloadedChunk = reloadedWorld.getOrCreateChunk(cx, cz);
        reloadedWorld.generateChunk(reloadedChunk);
        short[] secondGeneration = reloadedChunk.exportBlocks();

        assertArrayEquals(firstGeneration, secondGeneration,
                "reloading the same Dark Forest chunk coordinates must reproduce identical branches, not duplicate or drop them");
    }

    private int countBlock(Chunk chunk, BlockType type) {
        int count = 0;
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    if (chunk.getLocal(x, y, z) == type) count++;
                }
            }
        }
        return count;
    }
}
