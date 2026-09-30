package com.example.physics.gameplay;

import com.example.physics.Player;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Drives the real {@link Player#update} loop with a fixed timestep and a hand-written
 * {@link FakeInputState} - no GLFW window, no wall-clock time. This is the headless equivalent
 * of the spec's "Scenario A/B" gameplay tests: real player simulation, real collision, real
 * {@link World}, just without a renderer.
 */
class PlayerMovementGameplayTest {

    private static final float DT = 1f / 60f;
    /** Feet rest exactly on top of FlatWorldGenerator's grass layer. */
    private static final float SPAWN_Y = FlatWorldGenerator.GRASS_HEIGHT + 1;

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.generateChunk(world.getOrCreateChunk(cx, cz));
            }
        }
    }

    private void tick(Player player, FakeInputState input, int ticks, double startTime) {
        for (int i = 0; i < ticks; i++) {
            player.update(DT, input, world, startTime + i * DT);
        }
    }

    @Test
    void walkingForwardMovesInFrontVectorDirectionWithoutVerticalDrift() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        Vector3f startPos = new Vector3f(player.position);
        Vector3f forwardFlat = new Vector3f(player.frontVector().x, 0, player.frontVector().z).normalize();

        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);
        tick(player, input, 30, 0.0); // 0.5s

        Vector3f moved = new Vector3f(player.position).sub(startPos);
        float horizontalDist = new Vector3f(moved.x, 0, moved.z).length();
        assertTrue(horizontalDist > 0.1f, "player should have moved forward: " + horizontalDist);

        Vector3f movedFlatNormalized = new Vector3f(moved.x, 0, moved.z).normalize();
        assertTrue(movedFlatNormalized.dot(forwardFlat) > 0.99f,
                "movement direction should match the front vector's flat projection");

        assertEquals(SPAWN_Y, player.position.y, 0.01f, "walking on flat ground must not drift vertically");
    }

    @Test
    void sprintingCoversMoreGroundThanWalkingOverTheSameDuration() {
        Player walker = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        FakeInputState walkInput = new FakeInputState();
        walkInput.setKeyDown(GLFW_KEY_W, true);
        Vector3f walkStart = new Vector3f(walker.position);
        tick(walker, walkInput, 120, 0.0); // 2s - long enough for the speed lerp to converge
        float walkDist = new Vector3f(walker.position).sub(walkStart).length();

        Player sprinter = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        FakeInputState sprintInput = new FakeInputState();
        sprintInput.setKeyDown(GLFW_KEY_W, true);
        sprintInput.setKeyDown(GLFW_KEY_LEFT_CONTROL, true);
        Vector3f sprintStart = new Vector3f(sprinter.position);
        tick(sprinter, sprintInput, 120, 0.0);
        float sprintDist = new Vector3f(sprinter.position).sub(sprintStart).length();

        assertTrue(sprinter.isSprinting(), "holding sprint key while moving forward should engage sprinting");
        assertFalse(walker.isSprinting(), "walking without the sprint key must not engage sprinting");
        assertTrue(sprintDist > walkDist * 1.2f,
                "sprint should cover meaningfully more ground than walking: walk=" + walkDist + " sprint=" + sprintDist);
    }

    @Test
    void canSprintFalseForciblyKeepsSprintingOffRegardlessOfInput() {
        // The hunger-driven "sprint disabled at food<=6" rule (see PlayerSurvivalTest/
        // HungerComponentTest) - Player itself just honors whatever the caller passes.
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);
        input.setKeyDown(GLFW_KEY_LEFT_CONTROL, true);
        for (int i = 0; i < 30; i++) {
            player.update(DT, input, world, i * DT, false);
            assertFalse(player.isSprinting(), "canSprint=false must override the sprint key every tick");
        }
        for (int i = 30; i < 60; i++) {
            player.update(DT, input, world, i * DT, true);
        }
        assertTrue(player.isSprinting(), "sprinting must resume once canSprint is true again");
    }

    @Test
    void playerLandsOnGroundAndNeverTunnelsBelowTheFloor() {
        float startHeight = SPAWN_Y + 20f;
        Player player = new Player(new Vector3f(0.5f, startHeight, 0.5f));
        FakeInputState input = new FakeInputState();

        for (int i = 0; i < 300; i++) { // up to 5s of falling - generous margin
            player.update(DT, input, world, i * DT);
            assertTrue(player.position.y >= SPAWN_Y - 0.01f,
                    "player must never fall below the floor surface, tick " + i + ": y=" + player.position.y);
        }

        assertEquals(SPAWN_Y, player.position.y, 0.01f, "player should have landed exactly on the floor");
    }

    @Test
    void jumpingFromGroundGainsHeightThenReturnsToTheSameFloor() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        FakeInputState input = new FakeInputState();

        // Settle one tick to confirm onGround, then jump.
        player.update(DT, input, world, 0.0);
        input.setKeyDown(GLFW_KEY_SPACE, true);
        player.update(DT, input, world, DT);
        input.setKeyDown(GLFW_KEY_SPACE, false);

        float peak = player.position.y;
        for (int i = 2; i < 120; i++) {
            player.update(DT, input, world, i * DT);
            peak = Math.max(peak, player.position.y);
        }

        assertTrue(peak > SPAWN_Y + 0.5f, "jump should gain meaningful height above the floor: peak=" + peak);
        assertEquals(SPAWN_Y, player.position.y, 0.01f, "player should settle back on the same floor after the jump");
    }

    @Test
    void walkingIntoASolidWallStopsAtTheWallInsteadOfPassingThroughIt() {
        int wallX = 10;
        for (int y = (int) SPAWN_Y; y <= SPAWN_Y + 2; y++) {
            for (int z = 6; z <= 10; z++) {
                world.setBlock(wallX, y, z, BlockType.STONE);
            }
        }

        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 8.5f));
        // Rotate yaw by exactly +pi radians (via the real look() API, in mouse-sensitivity
        // units) so the player faces +X - deterministic without needing a new setter.
        double deltaYawRadians = Math.PI;
        player.look(deltaYawRadians / 0.0025, 0);
        assertEquals(1.0, player.frontVector().x, 0.01, "sanity check: player should now face +X");

        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);
        tick(player, input, 180, 0.0); // 3s - far more than enough to reach the wall unobstructed

        assertTrue(player.position.x < wallX, "player must stop before entering the wall: x=" + player.position.x);

        float xBeforeExtraTicks = player.position.x;
        tick(player, input, 30, 3.0);
        assertEquals(xBeforeExtraTicks, player.position.x, 0.001f,
                "once blocked, continuing to hold forward must not make progress through the wall");
    }
}
