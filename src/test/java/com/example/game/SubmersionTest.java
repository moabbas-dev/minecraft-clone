package com.example.game;

import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Game.isSubmerged - the camera-underwater check that drives underwater fog in renderWorld.
 * Package-private static, exercised directly (same pattern as DayNightTest/CelestialWeatherTest)
 * rather than through a full Game instance, which would need a real GL context to construct.
 */
class SubmersionTest {

    @Test
    void notSubmergedInOpenAir() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        assertFalse(Game.isSubmerged(world, new Vector3f(8.5f, 20f, 8.5f)));
    }

    @Test
    void submergedInsideAWaterBlock() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        world.setBlock(8, 10, 8, BlockType.WATER);
        assertTrue(Game.isSubmerged(world, new Vector3f(8.5f, 10.5f, 8.5f)));
    }

    @Test
    void notSubmergedJustAboveTheWaterSurface() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        world.setBlock(8, 10, 8, BlockType.WATER);
        // Eye position one full block above the water cell, not inside it.
        assertFalse(Game.isSubmerged(world, new Vector3f(8.5f, 11.5f, 8.5f)));
    }

    @Test
    void notSubmergedInsideASolidBlock() {
        // Solid, not water - isSubmerged should be specifically about water, not "any non-air".
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        assertFalse(Game.isSubmerged(world, new Vector3f(8.5f, 1.5f, 8.5f)),
                "y=1 is dirt in FlatWorldGenerator - solid but not water");
    }
}
