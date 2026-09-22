package com.example.world.unit;

import com.example.world.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raycast/DDA correctness against a small, deterministic flat-world fixture (bedrock/dirt/
 * grass, ground surface at y=3 - see {@link FlatWorldGenerator}) rather than the full noise
 * terrain generator, so hit coordinates are known in advance and the test never depends on
 * biome/height noise.
 */
class RaycastTest {

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        // Covers world x/z in [-16, 15] so both positive and negative-coordinate cases fit
        // inside already-generated chunks.
        world.generateChunk(world.getOrCreateChunk(0, 0));
        world.generateChunk(world.getOrCreateChunk(-1, 0));
        world.generateChunk(world.getOrCreateChunk(0, -1));
        world.generateChunk(world.getOrCreateChunk(-1, -1));
    }

    @Test
    void straightDownHitsGroundAtExpectedCoordinateAndFace() {
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(8.5f, 10f, 8.5f), new Vector3f(0, -1, 0), 20f);
        assertNotNull(hit);
        assertEquals(8, hit.x());
        assertEquals(FlatWorldGenerator.GRASS_HEIGHT, hit.y());
        assertEquals(8, hit.z());
        assertEquals(BlockType.GRASS_BLOCK, hit.type());
        // The "place" cell must be the one immediately above the hit (where the ray came from).
        assertEquals(8, hit.placeX());
        assertEquals(FlatWorldGenerator.GRASS_HEIGHT + 1, hit.placeY());
        assertEquals(8, hit.placeZ());
    }

    @Test
    void straightUpFromOpenAirMisses() {
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(8.5f, 10f, 8.5f), new Vector3f(0, 1, 0), 50f);
        assertNull(hit, "casting up through open sky should never hit anything");
    }

    @Test
    void hitBeyondMaxDistanceReturnsNull() {
        // Ground is ~6 blocks below y=10 (surface at y=3+1=4 top face); cap the ray well short of it.
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(8.5f, 10f, 8.5f), new Vector3f(0, -1, 0), 2f);
        assertNull(hit, "a ray capped shorter than the distance to the block must not report a hit");
    }

    @Test
    void diagonalRayHitsExpectedBlock() {
        // From well above and to the side, aimed down-and-across at 45 degrees in the XZ plane too.
        Vector3f origin = new Vector3f(0.5f, 10f, 0.5f);
        Vector3f dir = new Vector3f(1f, -1f, 1f);
        Raycast.Hit hit = Raycast.cast(world, origin, dir, 30f);
        assertNotNull(hit);
        assertEquals(BlockType.GRASS_BLOCK, hit.type());
        assertEquals(FlatWorldGenerator.GRASS_HEIGHT, hit.y());
    }

    @Test
    void negativeCoordinatesResolveCorrectly() {
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(-8.5f, 10f, -8.5f), new Vector3f(0, -1, 0), 20f);
        assertNotNull(hit);
        assertEquals(-9, hit.x());
        assertEquals(FlatWorldGenerator.GRASS_HEIGHT, hit.y());
        assertEquals(-9, hit.z());
    }

    @Test
    void raycastFindsAManuallyPlacedBlockAboveTheSurface() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(5.5f, 20f, 5.5f), new Vector3f(0, -1, 0), 30f);
        assertNotNull(hit);
        assertEquals(5, hit.x());
        assertEquals(10, hit.y());
        assertEquals(5, hit.z());
        assertEquals(BlockType.STONE, hit.type());
    }

    @Test
    void rayPassesThroughWaterWithoutStopping() {
        world.setBlock(3, FlatWorldGenerator.GRASS_HEIGHT + 1, 3, BlockType.WATER);
        Raycast.Hit hit = Raycast.cast(world, new Vector3f(3.5f, 10f, 3.5f), new Vector3f(0, -1, 0), 20f);
        assertNotNull(hit);
        assertEquals(BlockType.GRASS_BLOCK, hit.type(), "water must not itself count as a hit - raycast should pass through to the solid ground beneath it");
    }
}
