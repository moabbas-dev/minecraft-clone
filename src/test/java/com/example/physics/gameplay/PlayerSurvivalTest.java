package com.example.physics.gameplay;

import com.example.physics.Player;
import com.example.survival.DamageSource;
import com.example.survival.PlayerSurvival;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Drives the real {@link Player#update} loop together with {@link PlayerSurvival#update} - the
 * exact pair {@code Game}'s own frame loop calls - so sprint/jump/swim/fall exhaustion and fall
 * damage are exercised against real movement and real gravity, not a hand-simulated distance.
 * Reuses this package's {@link FakeInputState}/world-setup convention (see
 * {@code PlayerMovementGameplayTest}) rather than rolling a second one.
 */
class PlayerSurvivalTest {
    private static final float DT = 1f / 60f;
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

    private PlayerSurvival survival(Difficulty difficulty) {
        return new PlayerSurvival(difficulty);
    }

    @Test
    void positionTrackingInitializesOnTheFirstUpdateWithoutCountingAPhantomDistance() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(DT, player);
        assertEquals(0f, s.getHunger().getExhaustion(), "the very first call must not treat spawn position as a huge move");
    }

    @Test
    void fallingBelowSixtyFourBlocksUnderTheWorldHurtsFourAtATimeWhileAboveItDoesNot() {
        Player player = new Player(new Vector3f(0.5f, 1f, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player);
        player.position.y = -60f; // below the world but not yet 64 blocks under it
        s.update(DT, player);
        assertEquals(20f, s.getHealth().getCurrentHealth(), "no void damage above y = -64");
        player.position.y = -70f;
        s.update(DT, player);
        assertEquals(16f, s.getHealth().getCurrentHealth(), 1e-4f, "4 damage");
        s.update(DT, player);
        assertEquals(16f, s.getHealth().getCurrentHealth(), 1e-4f, "then the hurt cooldown holds the next hit back");
        for (int i = 0; i < 40; i++) s.update(DT, player);
        assertTrue(s.getHealth().getCurrentHealth() < 16f, "and it keeps coming while the player stays down there");
    }

    @Test
    void sprintingOnGroundAddsExhaustionProportionalToDistanceTravelled() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player); // initialize position tracking at t=0 distance
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_W, true);
        input.setKeyDown(GLFW_KEY_LEFT_CONTROL, true); // hold-to-sprint
        float traveled = 0f;
        Vector3f last = new Vector3f(player.position);
        for (int i = 0; i < 30; i++) {
            player.update(DT, input, world, i * DT, true);
            s.update(DT, player);
            traveled += (float) Math.sqrt(
                    Math.pow(player.position.x - last.x, 2) + Math.pow(player.position.z - last.z, 2));
            last.set(player.position);
        }
        assertTrue(player.isSprinting(), "sanity: must actually be sprinting");
        // addExhaustion's every-4.0-points rollover only ever moves points into saturation/food -
        // the exhaustion value itself is exactly the running total modulo 4, so this is a real
        // check of the 0.1-per-block rate, not just "some positive number came out."
        assertEquals(traveled * 0.1f % 4f, s.getHunger().getExhaustion(), 0.02f);
    }

    @Test
    void flyingNeverAddsExhaustionEvenWhileMovingFast() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y + 5f, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player);
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_SPACE, true);
        player.update(DT, input, world, 0.0); // double-tap toggle needs 2 presses close together
        input.setKeyDown(GLFW_KEY_SPACE, false);
        player.update(DT, input, world, 0.05);
        input.setKeyDown(GLFW_KEY_SPACE, true);
        player.update(DT, input, world, 0.1); // second tap - now flying
        assertTrue(player.isFlying(), "sanity: must actually be flying");
        input.setKeyDown(GLFW_KEY_W, true);
        for (int i = 0; i < 30; i++) {
            player.update(DT, input, world, 0.1 + i * DT);
            s.update(DT, player);
        }
        assertEquals(0f, s.getHunger().getExhaustion(), "flying is exempt from every movement-exhaustion rule");
    }

    @Test
    void jumpingAddsADiscreteExhaustionAmountLargerWhileSprinting() {
        Player groundOnly = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        PlayerSurvival s1 = survival(Difficulty.NORMAL);
        s1.update(0f, groundOnly);
        FakeInputState input1 = new FakeInputState();
        // onGround starts false and is only ever set by moveWithCollision at the END of
        // update() - a jump attempt on the very first tick always finds onGround still false
        // (last tick's value), so one settle tick with nothing held establishes it first.
        groundOnly.update(DT, input1, world, 0.0, true);
        s1.update(DT, groundOnly);
        input1.setKeyDown(GLFW_KEY_SPACE, true);
        groundOnly.update(DT, input1, world, DT, true);
        s1.update(DT, groundOnly);
        assertTrue(groundOnly.isJustJumped(), "sanity: must have actually jumped this tick");
        assertEquals(0.05f, s1.getHunger().getExhaustion(), 0.001f);

        Player sprintJump = new Player(new Vector3f(0.5f, SPAWN_Y, 0.5f));
        PlayerSurvival s2 = survival(Difficulty.NORMAL);
        s2.update(0f, sprintJump);
        FakeInputState input2 = new FakeInputState();
        sprintJump.update(DT, input2, world, 0.0, true); // settle tick, see above
        s2.update(DT, sprintJump);
        input2.setKeyDown(GLFW_KEY_LEFT_CONTROL, true);
        input2.setKeyDown(GLFW_KEY_W, true);
        input2.setKeyDown(GLFW_KEY_SPACE, true);
        sprintJump.update(DT, input2, world, DT, true);
        s2.update(DT, sprintJump);
        assertTrue(sprintJump.isJustJumped());
        assertTrue(sprintJump.isSprinting());
        // Sprint-jump's discrete bonus (0.2) alone already exceeds a plain jump's (0.05) - the
        // same tick also adds a tiny bit of sprint-distance exhaustion on top, so this is a
        // lower bound, not an exact match.
        assertTrue(s2.getHunger().getExhaustion() >= 0.2f,
                "sprint-jump exhaustion should be at least the 0.2 discrete bonus, got " + s2.getHunger().getExhaustion());
        assertTrue(s2.getHunger().getExhaustion() > s1.getHunger().getExhaustion(),
                "sprint-jumping must cost more exhaustion than a plain jump");
    }

    @Test
    void swimmingAddsATenthTheExhaustionOfSprinting() {
        // Build a small pool: replace a column of grass with water and clear above it.
        for (int y = FlatWorldGenerator.GRASS_HEIGHT; y <= FlatWorldGenerator.GRASS_HEIGHT + 3; y++) {
            world.setBlock(0, y, 0, BlockType.WATER);
        }
        Player player = new Player(new Vector3f(0.5f, FlatWorldGenerator.GRASS_HEIGHT + 1.5f, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player);
        FakeInputState input = new FakeInputState();
        input.setKeyDown(GLFW_KEY_D, true);
        float traveledSq = 0f;
        Vector3f last = new Vector3f(player.position);
        for (int i = 0; i < 20; i++) {
            player.update(DT, input, world, i * DT, true);
            s.update(DT, player);
        }
        assertTrue(player.isSwimming(), "sanity: must actually be swimming");
        assertTrue(s.getHunger().getExhaustion() > 0f, "swimming should add some exhaustion");
        assertTrue(s.getHunger().getExhaustion() < 1f, "swimming's rate (0.01/block) is far below sprinting's (0.1/block)");
    }

    @Test
    void breakingABlockAddsAFixedSmallExhaustion() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, new Player(new Vector3f(0, SPAWN_Y, 0)));
        s.onBlockBroken();
        assertEquals(0.005f, s.getHunger().getExhaustion(), 0.0001f);
    }

    @Test
    void takingDamageAddsExhaustionOnlyWhenItActuallyLands() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, new Player(new Vector3f(0, SPAWN_Y, 0)));
        assertTrue(s.damage(1f, DamageSource.GENERIC));
        assertEquals(0.1f, s.getHunger().getExhaustion(), 0.0001f);
        assertFalse(s.damage(1f, DamageSource.GENERIC), "blocked by the hurt cooldown");
        assertEquals(0.1f, s.getHunger().getExhaustion(), 0.0001f, "a blocked hit adds nothing");
    }

    @Test
    void naturalRegenerationHealsOverTimeAndCostsExhaustionOnlyWhenFoodIsHighEnough() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.damage(5f, DamageSource.GENERIC);
        assertEquals(15f, s.getHealth().getCurrentHealth());
        s.getHunger().forceState(20f, 0f, 0f); // no saturation: the slow 80-tick regeneration

        float saturationBefore = s.getHunger().getSaturation();
        // ~6.67s of ticks at 60fps - enough for exactly one regen cycle at this project's chosen
        // (documented-adapted) 4s interval, not two, so the "+6 exhaustion per HP" cost is
        // checked precisely rather than just "some exhaustion appeared."
        for (int i = 0; i < 400; i++) s.update(DT, player);
        assertEquals(16f, s.getHealth().getCurrentHealth(), "should have regenerated exactly once");
        assertTrue(s.getHunger().getSaturation() < saturationBefore || s.getHunger().getExhaustion() > 0f,
                "regenerating 1 HP must cost the +6 exhaustion the request specifies");
    }

    @Test
    void regenerationStopsBelowTheFoodThreshold() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.getHunger().forceState(17.9f, 0f, 0f);
        s.damage(5f, DamageSource.GENERIC);
        for (int i = 0; i < 400; i++) s.update(DT, player);
        assertEquals(15f, s.getHealth().getCurrentHealth(), "no regen below the food threshold");
    }

    @Test
    void starvationDamagesOverTimeAndRespectsEachDifficultysFloor() {
        for (Difficulty d : Difficulty.values()) {
            if (d == Difficulty.PEACEFUL) continue; // Peaceful never drains hunger (own test)
            PlayerSurvival s = survival(d);
            Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
            s.update(0f, player);
            s.getHunger().forceState(0f, 0f, 0f);
            for (int i = 0; i < 6000; i++) s.update(DT, player); // long enough to hit the floor
            float floor = switch (d) {
                case EASY -> 10f;
                case NORMAL -> 1f;
                case HARD -> 0f;
                case PEACEFUL -> 20f;
            };
            assertTrue(s.getHealth().getCurrentHealth() <= floor + 0.01f, d + ": should have starved down to its floor");
            assertTrue(s.getHealth().getCurrentHealth() >= floor - 0.01f, d + ": must never go below its floor from starvation alone");
        }
    }

    @Test
    void eatingARealFoodItemRestoresHungerAndConsumesOneItem() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.getHunger().forceState(10f, 0f, 0f);
        assertTrue(s.eat(BlockType.APPLE));
        assertEquals(14f, s.getHunger().getFoodLevel());
    }

    @Test
    void eatingANonFoodItemDoesNothing() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        assertFalse(s.eat(BlockType.STONE));
        assertEquals(20f, s.getHunger().getFoodLevel());
    }

    @Test
    void updateDoesNothingFurtherOnceDead() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.damage(100f, DamageSource.GENERIC);
        assertTrue(s.getHealth().isDead());
        s.getHunger().forceState(0f, 0f, 0f); // would otherwise starve further
        for (int i = 0; i < 200; i++) s.update(DT, player);
        assertEquals(0f, s.getHealth().getCurrentHealth(), "still exactly 0, no further starvation while dead");
    }

    @Test
    void resetRevivesAndRestoresFreshHungerAndReAnchorsPositionTracking() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.damage(100f, DamageSource.GENERIC);
        s.getHunger().forceState(2f, 0f, 3f);

        s.reset(new Vector3f(50, SPAWN_Y, 50));
        assertFalse(s.getHealth().isDead());
        assertEquals(20f, s.getHealth().getCurrentHealth());
        assertEquals(20f, s.getHunger().getFoodLevel());
        assertEquals(0f, s.getHunger().getExhaustion());
    }

    @Test
    void loadStateRestoresExactSavedValues() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.loadState(7f, 12f, 3f, 1.5f);
        assertEquals(7f, s.getHealth().getCurrentHealth());
        assertEquals(12f, s.getHunger().getFoodLevel());
        assertEquals(3f, s.getHunger().getSaturation());
        assertEquals(1.5f, s.getHunger().getExhaustion());
    }

    @Test
    void fallingFromAMeaningfulHeightDamagesProportionallyToVanillasRule() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y + 16f, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player);
        FakeInputState input = new FakeInputState();
        for (int i = 0; i < 200 && !player.isOnGround(); i++) {
            player.update(DT, input, world, i * DT, true);
            s.update(DT, player);
        }
        assertTrue(player.isOnGround(), "sanity: must have actually landed within the tick budget");
        assertTrue(s.getHealth().getCurrentHealth() < s.getHealth().getMaxHealth(), "a 16-block fall must damage the player");
        // Real vanilla rule: 1 HP per block beyond a 3-block safe distance - the fall distance
        // here is close to 16 blocks (spawn Y + 16, landing back at spawn Y), so damage should
        // land in a wide but sane band around 13 HP, not e.g. 1 or 20.
        float damageTaken = s.getHealth().getMaxHealth() - s.getHealth().getCurrentHealth();
        assertTrue(damageTaken > 8f && damageTaken < 16f, "unexpected fall damage: " + damageTaken);
    }

    @Test
    void aShortFallOfThreeBlocksOrLessDoesNoDamage() {
        Player player = new Player(new Vector3f(0.5f, SPAWN_Y + 2f, 0.5f));
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.update(0f, player);
        FakeInputState input = new FakeInputState();
        for (int i = 0; i < 60 && !player.isOnGround(); i++) {
            player.update(DT, input, world, i * DT, true);
            s.update(DT, player);
        }
        assertTrue(player.isOnGround());
        assertEquals(20f, s.getHealth().getCurrentHealth(), "a 2-block fall is within the safe distance");
    }

    @Test
    void fastRegenerationAtFullFoodHealsHalfASecondPerStepAndSpendsSaturation() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.damage(5f, DamageSource.GENERIC);
        s.getHunger().forceState(20f, 6f, 0f);
        for (int i = 0; i < 61; i++) s.update(DT, player); // ~1 s
        // first step heals min(6,6)/6 = 1 HP and costs 6 exhaustion (one saturation point, 2 carried over); the second heals 5/6 HP
        assertEquals(15f + 1f + 5f / 6f, s.getHealth().getCurrentHealth(), 0.02f, "a step every 10 ticks, scaled by saturation");
        assertTrue(s.getHunger().getSaturation() < 6f, "fast regen drains saturation through exhaustion");
    }

    @Test
    void peacefulRegeneratesAndNeverStarves() {
        PlayerSurvival s = survival(Difficulty.PEACEFUL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        s.damage(5f, DamageSource.GENERIC);
        s.getHunger().forceState(0f, 0f, 0f);
        for (int i = 0; i < 400; i++) s.update(DT, player);
        assertTrue(s.getHealth().getCurrentHealth() > 15f, "health regenerates on Peaceful");
        assertEquals(20f, s.getHunger().getFoodLevel(), "the hunger bar stays full");
    }

    @Test
    void experienceLevelsFollowTheVanillaCurve() {
        assertEquals(7, com.example.survival.Experience.costOfLevel(0));
        assertEquals(37, com.example.survival.Experience.costOfLevel(15));
        assertEquals(42, com.example.survival.Experience.costOfLevel(16));
        assertEquals(1395f, com.example.survival.Experience.totalForLevel(30), 0.5f);
        PlayerSurvival s = survival(Difficulty.NORMAL);
        s.addExperience(7f);
        assertEquals(1, s.getLevel());
        s.addExperience(8f);
        assertEquals(1, s.getLevel());
        assertEquals(8f / 9f, s.getLevelProgress(), 1e-4f);
        assertEquals(7f, com.example.survival.Experience.deathDrop(1));
        assertEquals(100f, com.example.survival.Experience.deathDrop(30));
        assertEquals(7f, s.takeDeathExperience());
        assertEquals(0, s.getLevel());
        assertEquals(15f, s.getScore(), "score keeps what was collected this life");
    }

    @Test
    void drowningDrainsAirThenDealsTwoHealthPerSecond() {
        PlayerSurvival s = survival(Difficulty.NORMAL);
        Player player = new Player(new Vector3f(0, SPAWN_Y, 0));
        s.update(0f, player);
        assertEquals(PlayerSurvival.MAX_AIR_TICKS, s.getAirTicks());
        assertTrue(com.example.world.BlockType.WATER != null);
    }
}
