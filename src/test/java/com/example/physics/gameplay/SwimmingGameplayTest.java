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
 * Real Player.update driven swimming scenarios, same fixed-timestep/FakeInputState approach as
 * {@link PlayerMovementGameplayTest}. A deep pool (x/z 20-29, water from y=4 to y=11, open air
 * above y=12) covers most cases; a separate shallow pocket with a low stone ceiling (x/z 40-41)
 * covers the "can't stand up" headroom case specifically.
 */
class SwimmingGameplayTest {

    private static final float DT = 1f / 60f;
    private static final float SPAWN_Y = FlatWorldGenerator.GRASS_HEIGHT + 1;
    private static final int POOL_MIN = 20, POOL_MAX = 29;
    private static final int WATER_BOTTOM = 4, WATER_TOP = 11; // last water cell; y=12 is open air

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 3; cx++) {
            for (int cz = -1; cz <= 3; cz++) {
                world.generateChunk(world.getOrCreateChunk(cx, cz));
            }
        }
        for (int x = POOL_MIN; x <= POOL_MAX; x++) {
            for (int z = POOL_MIN; z <= POOL_MAX; z++) {
                for (int y = WATER_BOTTOM; y <= WATER_TOP; y++) {
                    world.setBlock(x, y, z, BlockType.WATER);
                }
            }
        }
        // Shallow pocket (single water layer) capped by a low stone ceiling - not enough
        // headroom to stand at full HEIGHT after swimming here.
        for (int x = 40; x <= 41; x++) {
            for (int z = 40; z <= 41; z++) {
                world.setBlock(x, WATER_BOTTOM, z, BlockType.WATER);
                world.setBlock(x, WATER_BOTTOM + 1, z, BlockType.STONE);
            }
        }
    }

    private void tick(Player player, FakeInputState input, int ticks, double startTime) {
        for (int i = 0; i < ticks; i++) {
            player.update(DT, input, world, startTime + i * DT);
        }
    }

    private Vector3f poolCenter(float y) {
        return new Vector3f((POOL_MIN + POOL_MAX) / 2f + 0.5f, y, (POOL_MIN + POOL_MAX) / 2f + 0.5f);
    }

    @Test
    void fallingIntoWaterSetsSwimmingState() {
        Player player = new Player(poolCenter(WATER_TOP + 10f)); // well above the pool, in open air
        FakeInputState input = new FakeInputState();
        assertFalse(player.isSwimming());

        boolean becameSwimming = false;
        for (int i = 0; i < 300 && !becameSwimming; i++) {
            player.update(DT, input, world, i * DT);
            becameSwimming = player.isSwimming();
        }
        assertTrue(becameSwimming, "falling into the pool should set swimming true");
    }

    @Test
    void swimmingUpOutOfWaterEventuallyClearsSwimmingState() {
        Player player = new Player(poolCenter(WATER_BOTTOM + 1f)); // submerged near the bottom
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_SPACE, true); // swim up

        boolean leftWater = false;
        for (int i = 0; i < 600 && !leftWater; i++) {
            player.update(DT, input, world, i * DT);
            leftWater = !player.isSwimming();
        }
        assertTrue(leftWater, "swimming straight up for 10s should clear the water and leave swimming state");
        assertTrue(player.position.y > WATER_TOP, "should have actually risen above the pool: y=" + player.position.y);
    }

    @Test
    void partialSubmersionVsFullSubmersionAreDistinguished() {
        // Feet in water, eye (at full standing eye height, before any easing) still above the
        // surface - the posture hasn't eased down yet on this very first tick, so head isn't
        // submerged even though feet already are.
        Player player = new Player(poolCenter(WATER_TOP)); // feet in the topmost water cell
        FakeInputState input = new FakeInputState();
        player.update(DT, input, world, 0.0);
        assertTrue(player.isSwimming(), "feet in water should count as swimming");

        // Deep in the pool, eye position is also underwater - fully submerged.
        Player deepPlayer = new Player(poolCenter(WATER_BOTTOM + 2f));
        deepPlayer.update(DT, input, world, 0.0);
        assertTrue(deepPlayer.isSwimming());
        assertTrue(deepPlayer.isHeadSubmerged(), "well below the surface, the eye position should also be underwater");
    }

    @Test
    void postureEasesTowardSwimmingHeightThenBackToStandingWithClearHeadroom() {
        Player player = new Player(poolCenter(WATER_BOTTOM + 2f));
        FakeInputState input = new FakeInputState();
        float standingHeight = player.currentHitboxHeight();

        tick(player, input, 90, 0.0); // 1.5s submerged - plenty for the hitbox ease to converge
        float swimmingHitbox = player.currentHitboxHeight();
        assertTrue(swimmingHitbox < standingHeight, "hitbox should shrink while swimming: " + swimmingHitbox);

        // Relocate directly onto dry land with clear headroom (not just "above the pool," which
        // has nothing to stand on and would just fall straight back into the same water) - this
        // test is specifically about the posture easing back once conditions allow it, not
        // about the swim-out journey itself (covered separately).
        player.position.set(0.5f, SPAWN_Y, 0.5f);
        tick(player, input, 60, 1.5); // 1s - plenty for the hitbox ease to reconverge

        assertFalse(player.isSwimming(), "should not be swimming on dry land");
        assertEquals(standingHeight, player.currentHitboxHeight(), 0.01f,
                "hitbox should ease back to full standing height once clear of the water with headroom");
    }

    @Test
    void standingIsNotRestoredUnderALowCeiling() {
        Player player = new Player(new Vector3f(40.5f, WATER_BOTTOM, 40.5f));
        FakeInputState input = new FakeInputState();
        float standingHeight = player.currentHitboxHeight();

        tick(player, input, 120, 0.0); // 2s - long enough for the hitbox ease to fully converge

        assertTrue(player.currentHitboxHeight() < standingHeight,
                "under a 1-block-tall pocket with a stone ceiling, the hitbox must stay reduced: " + player.currentHitboxHeight());
    }

    @Test
    void idleBuoyancyRisesTowardTheSurfaceWhenFullySubmerged() {
        Player player = new Player(poolCenter(WATER_BOTTOM + 1f));
        FakeInputState input = new FakeInputState(); // no input at all - buoyancy only
        float startY = player.position.y;

        tick(player, input, 60, 0.0); // 1s
        assertTrue(player.position.y > startY, "idle buoyancy should drift the player upward: " + player.position.y);
    }

    @Test
    void swimmingForwardMovesHorizontallyWithDragRatherThanInstantly() {
        Player player = new Player(poolCenter(WATER_BOTTOM + 3f));
        Vector3f start = new Vector3f(player.position);
        Vector3f forwardFlat = new Vector3f(player.frontVector().x, 0, player.frontVector().z).normalize();

        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);

        player.update(DT, input, world, 0.0); // one tick
        Vector3f afterOneTick = new Vector3f(player.position).sub(start);
        float distAfterOneTick = new Vector3f(afterOneTick.x, 0, afterOneTick.z).length();

        tick(player, input, 119, DT); // out to 2s total
        Vector3f afterTwoSeconds = new Vector3f(player.position).sub(start);
        float distAfterTwoSeconds = new Vector3f(afterTwoSeconds.x, 0, afterTwoSeconds.z).length();

        assertTrue(distAfterTwoSeconds > distAfterOneTick * 5,
                "drag means the first tick covers proportionally much less ground than it would at full eased speed");
        Vector3f movedFlatNormalized = new Vector3f(afterTwoSeconds.x, 0, afterTwoSeconds.z).normalize();
        assertTrue(movedFlatNormalized.dot(forwardFlat) > 0.99f, "should still move in the direction faced");
    }

    @Test
    void sprintSwimmingCoversMoreGroundThanPlainSwimming() {
        Player swimmer = new Player(poolCenter(WATER_BOTTOM + 3f));
        FakeInputState swimInput = new FakeInputState();
        swimInput.setKeyDown(GLFW_KEY_W, true);
        Vector3f swimStart = new Vector3f(swimmer.position);
        tick(swimmer, swimInput, 120, 0.0);
        float swimDist = new Vector3f(swimmer.position).sub(swimStart).length();

        Player sprinter = new Player(poolCenter(WATER_BOTTOM + 3f));
        FakeInputState sprintInput = new FakeInputState();
        sprintInput.setKeyDown(GLFW_KEY_W, true);
        sprintInput.setKeyDown(GLFW_KEY_LEFT_CONTROL, true);
        Vector3f sprintStart = new Vector3f(sprinter.position);
        tick(sprinter, sprintInput, 120, 0.0);
        float sprintDist = new Vector3f(sprinter.position).sub(sprintStart).length();

        assertTrue(sprintDist > swimDist * 1.1f,
                "sprint-swimming should cover more ground than plain swimming: swim=" + swimDist + " sprintSwim=" + sprintDist);
    }

    @Test
    void verticalSwimUpIsBoundedAndSurfacingDoesNotLaunchThePlayer() {
        Player player = new Player(poolCenter(WATER_BOTTOM + 1f));
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_SPACE, true);

        float maxYReachedWhileSubmerged = player.position.y;
        for (int i = 0; i < 180; i++) { // 3s
            player.update(DT, input, world, i * DT);
            if (player.isSwimming()) {
                maxYReachedWhileSubmerged = Math.max(maxYReachedWhileSubmerged, player.position.y);
            }
        }
        // Continue past the surface with space still held and confirm no launch above a sane
        // ceiling - the surface-exit speed cap (proven correct for land->water already) applies
        // here the same way.
        float peakAfterSurfacing = player.position.y;
        for (int i = 180; i < 240; i++) {
            player.update(DT, input, world, i * DT);
            peakAfterSurfacing = Math.max(peakAfterSurfacing, player.position.y);
        }
        assertTrue(peakAfterSurfacing < WATER_TOP + 5f,
                "surfacing must not launch the player like a jump: peak=" + peakAfterSurfacing);
    }

    @Test
    void collisionAppliesWhileSwimmingTowardAWall() {
        int wallX = POOL_MAX; // east edge of the pool, at full water depth
        for (int y = WATER_BOTTOM; y <= WATER_TOP + 1; y++) {
            for (int z = POOL_MIN; z <= POOL_MAX; z++) {
                world.setBlock(wallX + 1, y, z, BlockType.STONE);
            }
        }

        Player player = new Player(new Vector3f(POOL_MIN + 0.5f, WATER_BOTTOM + 2f, POOL_MIN + 5f));
        double deltaYawRadians = Math.PI; // face +X, same technique as the land wall-collision test
        player.look(deltaYawRadians / 0.0025, 0);

        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);
        tick(player, input, 300, 0.0); // 5s - far more than enough to cross the pool unobstructed

        assertTrue(player.position.x < wallX + 1, "must stop before entering the wall: x=" + player.position.x);
    }
}
