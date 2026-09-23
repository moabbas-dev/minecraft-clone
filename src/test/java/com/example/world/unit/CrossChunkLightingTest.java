package com.example.world.unit;

import com.example.world.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for a real bug reported after playing: one face of a placed light block
 * (e.g. glowstone) stayed dark specifically depending on where it was placed - confirmed
 * location-dependent, matching the root cause: lighting used to be computed strictly per
 * chunk, so a source near (but not touching) a chunk edge never reached the next chunk over.
 * LightEngine.compute now imports light across borders from already-loaded neighbors.
 */
class CrossChunkLightingTest {

    private World world;
    private Chunk west; // chunk (0,0): world x 0-15
    private Chunk east; // chunk (1,0): world x 16-31

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        west = world.getOrCreateChunk(0, 0);
        east = world.getOrCreateChunk(1, 0);
        world.generateChunk(west);
        world.generateChunk(east);
    }

    @Test
    void lightSourceNearButNotAtTheBorderStillReachesTheNextChunk() {
        // 2 blocks in from the east edge of the west chunk (world x=13, chunk-local x=13) -
        // deliberately not exactly on the border, so World.setBlock's own border-only neighbor
        // marking can't be what makes this pass; only LightEngine's own cross-chunk import can.
        world.setBlock(13, 5, 8, BlockType.GLOWSTONE);

        LightEngine.compute(west, world);
        LightEngine.compute(east, world);

        // Distance from the source (world x=13) to the east chunk's nearest column (world
        // x=16) is 3 steps: 15 - 3 = 12.
        assertEquals(12, east.getBlockLight(0, 5, 8),
                "block light should cross the chunk boundary from a source 3 blocks away, not stop dead at the edge");
        // And the source's own chunk should show the expected in-between value at its own edge.
        assertEquals(13, west.getBlockLight(15, 5, 8));
    }

    @Test
    void aBorderChangeMarksTheNeighborChunkDirtyForResettling() {
        // Settle both chunks first with no light source near the border at all.
        LightEngine.compute(west, world);
        LightEngine.compute(east, world);
        east.clearMeshDirty();
        assertFalse(east.isMeshDirty());

        // Not on the exact border, so World.setBlock's own lx==0/15 check does NOT fire -
        // isolates this test to LightEngine's own change-detection specifically.
        world.setBlock(13, 5, 8, BlockType.GLOWSTONE);
        assertFalse(east.isMeshDirty(), "placing a block 3 blocks from the border must not directly touch the neighbor");

        LightEngine.compute(west, world);
        assertTrue(east.isMeshDirty(),
                "recomputing a chunk whose border values changed must mark its neighbor dirty so it re-imports");
    }

    @Test
    void noNeighborLoadedBehavesExactlyAsBefore() {
        // A chunk with no generated neighbors at all must not throw and must light itself
        // exactly as the old chunk-local-only implementation did.
        World lonely = new World(2L, new FlatWorldGenerator());
        Chunk chunk = lonely.getOrCreateChunk(5, 5);
        lonely.generateChunk(chunk);
        chunk.setLocal(8, 5, 8, BlockType.GLOWSTONE);
        assertDoesNotThrow(() -> LightEngine.compute(chunk, lonely));
        assertEquals(15, chunk.getBlockLight(8, 5, 8));
    }

    @Test
    void reComputingWithoutAnyChangeDoesNotMarkNeighborsDirtyForever() {
        LightEngine.compute(west, world);
        LightEngine.compute(east, world);
        east.clearMeshDirty();

        // Recomputing the west chunk again with nothing changed must settle immediately -
        // its border checksum is identical, so it must not keep re-marking the neighbor dirty.
        LightEngine.compute(west, world);
        assertFalse(east.isMeshDirty(), "an unchanged recompute must not perpetually re-dirty neighbors");
    }
}
