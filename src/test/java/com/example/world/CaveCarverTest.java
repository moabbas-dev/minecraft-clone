package com.example.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cave/ravine generation (CaveCarver, wired into TerrainGenerator.generate). The most important
 * properties here are determinism and order-independence (see CaveCarver's class docs for why
 * chunk generation order/neighbor-load-state must never affect the result in this project's
 * architecture) - not the exact tunnel shape, which is inherently randomized.
 */
class CaveCarverTest {

    private Chunk freshChunk(long seed, int cx, int cz) {
        TerrainGenerator gen = new TerrainGenerator(seed);
        Chunk chunk = new Chunk(cx, cz);
        World world = new World(seed, gen);
        gen.generate(chunk, world);
        return chunk;
    }

    @Test
    void sameSeedAndChunkProducesIdenticalBlocks() {
        short[] a = freshChunk(777L, 3, -2).exportBlocks();
        short[] b = freshChunk(777L, 3, -2).exportBlocks();
        assertArrayEquals(a, b, "identical seed+chunk must produce identical terrain including caves");
    }

    @Test
    void differentSeedsProduceDifferentCarvingSomewhereAcrossManyChunks() {
        boolean anyDifference = false;
        for (int i = 0; i < 20; i++) {
            short[] a = freshChunk(100L, i, 0).exportBlocks();
            short[] b = freshChunk(999L, i, 0).exportBlocks();
            if (!java.util.Arrays.equals(a, b)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "different seeds must eventually produce different terrain somewhere");
    }

    @Test
    void generationOrderDoesNotAffectEitherChunksResult() {
        long seed = 4242L;
        TerrainGenerator gen1 = new TerrainGenerator(seed);
        World world1 = new World(seed, gen1);
        Chunk a1 = new Chunk(0, 0);
        Chunk b1 = new Chunk(1, 0);
        gen1.generate(a1, world1);
        gen1.generate(b1, world1);

        TerrainGenerator gen2 = new TerrainGenerator(seed);
        World world2 = new World(seed, gen2);
        Chunk b2 = new Chunk(1, 0);
        Chunk a2 = new Chunk(0, 0);
        gen2.generate(b2, world2); // opposite order from the run above
        gen2.generate(a2, world2);

        assertArrayEquals(a1.exportBlocks(), a2.exportBlocks(), "chunk (0,0) must be identical regardless of generation order");
        assertArrayEquals(b1.exportBlocks(), b2.exportBlocks(), "chunk (1,0) must be identical regardless of generation order");
    }

    @Test
    void aChunkGeneratedAloneMatchesTheSameChunkGeneratedAmongNeighbors() {
        long seed = 8080L;
        Chunk alone = freshChunk(seed, 5, 5);

        TerrainGenerator gen = new TerrainGenerator(seed);
        World world = new World(seed, gen);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = new Chunk(5 + dx, 5 + dz);
                gen.generate(c, world);
                if (dx == 0 && dz == 0) {
                    assertArrayEquals(alone.exportBlocks(), c.exportBlocks(),
                            "a chunk's own carve must not depend on whether its neighbors exist or were generated first");
                }
            }
        }
    }

    @Test
    void bedrockIsNeverCarvedAway() {
        for (int cx = 0; cx < 10; cx++) {
            Chunk chunk = freshChunk(31337L, cx, 0);
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    assertEquals(BlockType.BEDROCK, chunk.getLocal(x, 0, z),
                            "y=0 must always stay bedrock even with caves enabled");
                }
            }
        }
    }

    @Test
    void cavesActuallyCarveSomeAirIntoOtherwiseSolidUnderground() {
        boolean foundCarvedAir = false;
        outer:
        for (int cx = 0; cx < 12 && !foundCarvedAir; cx++) {
            for (int cz = 0; cz < 12; cz++) {
                Chunk chunk = freshChunk(2024L, cx, cz);
                for (int x = 0; x < Chunk.SIZE_X && !foundCarvedAir; x++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        int height = chunk.getColumnHeight(x, z);
                        for (int y = 5; y < height - 5; y++) {
                            if (chunk.getLocal(x, y, z) == BlockType.AIR) {
                                foundCarvedAir = true;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(foundCarvedAir, "expected at least one carved air pocket across a 12x12 chunk region");
    }

    @Test
    void neverThrowsOrWritesOutsideValidWorldHeightAcrossManyChunks() {
        assertDoesNotThrow(() -> {
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    freshChunk(555L, cx, cz);
                }
            }
        });
    }
}
