package com.example.world.unit;

import com.example.world.Chunk;
import com.example.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * World-to-chunk coordinate conversion must use floor division/modulo, not truncation - the
 * classic bug class where negative coordinates near zero resolve to the wrong chunk (e.g.
 * naive integer division makes both -1 and 15 map to chunk 0, instead of -1 correctly mapping
 * to chunk -1).
 */
class ChunkCoordTest {

    @Test
    void positiveCoordinatesMapToExpectedChunk() {
        assertEquals(0, World.worldToChunk(0));
        assertEquals(0, World.worldToChunk(15));
        assertEquals(1, World.worldToChunk(16));
        assertEquals(1, World.worldToChunk(31));
        assertEquals(2, World.worldToChunk(32));
    }

    @Test
    void negativeCoordinatesUseFloorDivisionNotTruncation() {
        assertEquals(-1, World.worldToChunk(-1));
        assertEquals(-1, World.worldToChunk(-16));
        assertEquals(-2, World.worldToChunk(-17));
        assertEquals(-2, World.worldToChunk(-32)); // -32 is exactly chunk -2's origin
    }

    @Test
    void localCoordinateIsAlwaysInRangeIncludingForNegativeWorldCoords() {
        for (int x = -50; x <= 50; x++) {
            int local = World.worldToLocal(x);
            assertTrue(local >= 0 && local < Chunk.SIZE_X,
                    "local coordinate out of [0, " + Chunk.SIZE_X + ") for world x=" + x + ": " + local);
        }
    }

    @Test
    void chunkOriginPlusLocalReconstructsOriginalWorldCoordinate() {
        for (int x = -80; x <= 80; x++) {
            int chunk = World.worldToChunk(x);
            int local = World.worldToLocal(x);
            assertEquals(x, chunk * Chunk.SIZE_X + local,
                    "chunk*SIZE_X + local must reconstruct the original world coordinate");
        }
    }

    @Test
    void negativeOneAndFifteenAreDifferentChunks() {
        // The specific case a truncating (non-floor) division gets wrong.
        assertNotEquals(World.worldToChunk(-1), World.worldToChunk(15));
    }
}
