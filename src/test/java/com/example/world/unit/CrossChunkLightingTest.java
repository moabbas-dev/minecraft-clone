package com.example.world.unit;

import com.example.world.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for light crossing chunk borders (a real bug once: one face of a placed light block stayed dark
 * depending on where it was placed). The engine now relights across loaded neighbours both when a chunk is lit and when a
 * block changes, see {@link LightEngineTest} for the rules themselves.
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
        world.setBlock(13, 5, 8, BlockType.GLOWSTONE); // placed before either chunk is lit
        LightEngine.compute(west, world);
        LightEngine.compute(east, world);
        assertEquals(12, east.getBlockLight(0, 5, 8), "block light should cross the chunk boundary from a source 3 blocks away");
        assertEquals(13, west.getBlockLight(15, 5, 8));
    }

    @Test
    void aSourceInTheNeighbourIsPickedUpWhenALaterChunkIsLit() {
        world.setBlock(13, 5, 8, BlockType.GLOWSTONE);
        LightEngine.compute(west, world); // the east chunk is not lit yet
        LightEngine.compute(east, world);
        assertEquals(12, east.getBlockLight(0, 5, 8));
        // lighting the east chunk again changes nothing and loses nothing
        LightEngine.compute(east, world);
        assertEquals(12, east.getBlockLight(0, 5, 8));
        assertEquals(13, west.getBlockLight(15, 5, 8));
    }

    @Test
    void anEditNearTheBorderMarksTheNeighbourChunkDirty() {
        LightEngine.compute(west, world);
        LightEngine.compute(east, world);
        east.clearMeshDirty();
        assertFalse(east.isMeshDirty());
        world.setBlock(13, 5, 8, BlockType.GLOWSTONE);
        assertTrue(east.isMeshDirty(), "its light reached the east chunk, so that mesh must be rebuilt");
    }

    @Test
    void noNeighborLoadedLightsTheChunkOnItsOwn() {
        World lonely = new World(2L, new FlatWorldGenerator());
        Chunk chunk = lonely.getOrCreateChunk(5, 5);
        lonely.generateChunk(chunk);
        chunk.setLocal(8, 5, 8, BlockType.GLOWSTONE);
        assertDoesNotThrow(() -> LightEngine.compute(chunk, lonely));
        assertEquals(15, chunk.getBlockLight(8, 5, 8));
        assertEquals(15, chunk.getSkyLight(8, 200, 8));
    }
}
