package com.example.combat;

import com.example.entity.ArrowManager;
import com.example.entity.Targetable;
import com.example.inventory.Recipes;
import com.example.survival.DamageSource;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The bow of the 26.3 client: draw power, arrow speed and damage, the pull animation, the FOV squeeze, recipes and pickup. */
class BowTest {
    /** The flat world starts at the bottom of the world: {@code BOTTOM + n} is the n-th layer above its bedrock (grass at 3, air from 4). */
    private static final int BOTTOM = com.example.world.Chunk.MIN_Y;

    @Test
    void powerRisesFasterThanLinearlyAndCapsAtOneAfterTwentyTicks() {
        assertEquals(0f, BowMath.power(0f));
        assertEquals((0.25f * 0.25f + 0.5f) / 3f, BowMath.power(5f), 1e-6f);
        assertEquals(1f, BowMath.power(20f), 1e-6f);
        assertEquals(1f, BowMath.power(100f));
        assertTrue(BowMath.power(2f) < BowMath.MIN_POWER, "a very short draw shoots nothing");
        assertTrue(BowMath.power(4f) >= BowMath.MIN_POWER);
        assertTrue(BowMath.critical(BowMath.power(20f)));
        assertFalse(BowMath.critical(BowMath.power(15f)));
    }

    @Test
    void pullAnimationAndFieldOfViewFollowTheDraw() {
        assertEquals(0, BowMath.pullingStage(1f));
        assertEquals(0, BowMath.pullingStage(12f));
        assertEquals(1, BowMath.pullingStage(13f));
        assertEquals(1, BowMath.pullingStage(17f));
        assertEquals(2, BowMath.pullingStage(18f));
        assertEquals(1f, BowMath.fovFactor(0f));
        assertEquals(0.85f, BowMath.fovFactor(20f), 1e-6f);
        assertEquals(0.85f, BowMath.fovFactor(99f), 1e-6f);
    }

    @Test
    void aFullyDrawnArrowDealsSixAndACriticalOneUpToThreeMore() {
        Random r = new Random(1);
        assertEquals(6f, BowMath.hitDamage(3f, false, r));
        float max = 0f, min = 99f;
        for (int i = 0; i < 2000; i++) {
            float d = BowMath.hitDamage(3f, true, r);
            max = Math.max(max, d);
            min = Math.min(min, d);
        }
        assertEquals(6f, min);
        assertEquals(6f + 6 / 2 + 1, max, "nextInt(damage / 2 + 2) + damage");
        assertEquals(2f, BowMath.hitDamage(1f, false, r), "a slow arrow: ceil(speed x 2)");
    }

    private static final class Dummy implements Targetable {
        final Vector3f pos;
        float taken;
        DamageSource source;

        Dummy(float x, float y, float z) {
            pos = new Vector3f(x, y, z);
        }

        public Vector3f getPosition() {
            return pos;
        }

        public boolean isAlive() {
            return true;
        }

        public boolean damage(float amount, DamageSource s) {
            taken += amount;
            source = s;
            return true;
        }
    }

    @Test
    void aPlayerArrowHurtsMobsNotThePlayerAndItsDamageComesFromItsSpeed() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        ArrowManager arrows = new ArrowManager();
        Dummy mob = new Dummy(8.5f, BOTTOM + 5f, 8.5f), player = new Dummy(2.5f, BOTTOM + 5f, 8.5f);
        arrows.firePlayer(new Vector3f(2f, BOTTOM + 5f, 8.5f), new Vector3f(60f, 0f, 0f), false);
        for (int i = 0; i < 40 && arrows.arrows().size() == 1; i++) {
            arrows.update(world, List.of(player), List.of(mob), 0.05f, p -> {
            }, new Random(1));
        }
        assertEquals(0f, player.taken, "never the shooter");
        assertTrue(mob.taken >= 5f && mob.taken <= 6f, "speed 3 blocks per tick minus drag: " + mob.taken);
        assertEquals(DamageSource.PLAYER, mob.source);
        assertTrue(arrows.arrows().isEmpty(), "the arrow is used up");
    }

    @Test
    void aLodgedPlayerArrowCanBePickedUpButAMobsCannot() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        ArrowManager arrows = new ArrowManager();
        arrows.firePlayer(new Vector3f(5.5f, BOTTOM + 3.9f, 5.5f), new Vector3f(0f, -20f, 0f), false); // into the ground
        arrows.fire(new Vector3f(5.7f, BOTTOM + 3.9f, 5.7f), new Vector3f(0f, -20f, 0f), 2f);
        List<Targetable> none = new ArrayList<>();
        for (int i = 0; i < 40; i++) arrows.update(world, none, List.of(), 0.05f, p -> {
        }, new Random(1));
        assertEquals(2, arrows.arrows().size());
        assertEquals(1, arrows.pickUp(new Vector3f(5.5f, BOTTOM + 4.5f, 5.5f)), "only the player's arrow");
        assertEquals(1, arrows.arrows().size());
        assertEquals(0, arrows.pickUp(new Vector3f(50f, BOTTOM + 4.5f, 5.5f)), "and only when close");
    }

    @Test
    void arrowAndBowRecipes() {
        BlockType[] arrow = new BlockType[9];
        arrow[1] = BlockType.FLINT;
        arrow[4] = BlockType.STICK;
        arrow[7] = BlockType.FEATHER;
        assertEquals(BlockType.ARROW, Recipes.craft(arrow, 3, 3).type());
        assertEquals(4, Recipes.craft(arrow, 3, 3).count());
        BlockType[] bow = new BlockType[9];
        bow[1] = BlockType.STICK;
        bow[2] = BlockType.STRING;
        bow[3] = BlockType.STICK;
        bow[5] = BlockType.STRING;
        bow[7] = BlockType.STICK;
        bow[8] = BlockType.STRING;
        assertEquals(BlockType.BOW, Recipes.craft(bow, 3, 3).type());
        BlockType[] mirrored = new BlockType[9];
        mirrored[0] = BlockType.STRING;
        mirrored[1] = BlockType.STICK;
        mirrored[3] = BlockType.STRING;
        mirrored[5] = BlockType.STICK;
        mirrored[6] = BlockType.STRING;
        mirrored[7] = BlockType.STICK;
        assertEquals(BlockType.BOW, Recipes.craft(mirrored, 3, 3).type());
        assertEquals(384, Durability.max(BlockType.BOW));
    }
}
