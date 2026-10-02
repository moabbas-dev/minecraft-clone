package com.example.entity;

import com.example.entity.ai.RangedAttackGoal;
import com.example.entity.mob.Creeper;
import com.example.entity.mob.MobTypes;
import com.example.entity.mob.Skeleton;
import com.example.entity.mob.Spider;
import com.example.survival.DamageSource;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Creeper explosions, skeleton arrows and spider rules (research: docs/research/hostile-mob-models.md). */
class HostileMobTest {
    World world;
    int ground;

    /** A target that records the damage it takes. */
    static final class Dummy implements Targetable {
        final Vector3f pos;
        float taken;
        boolean alive = true;

        Dummy(float x, float y, float z) {
            pos = new Vector3f(x, y, z);
        }

        @Override
        public Vector3f getPosition() {
            return pos;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public boolean damage(float amount, DamageSource source) {
            taken += amount;
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++)
            for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        ground = 1;
        while (world.isSolidCollision(5, ground, 5)) ground++;
    }

    @Test
    void explosionDamageFollowsTheVanillaFormula() {
        // point blank, full exposure, power 3: floor((1 + 1) / 2 * 7 * 6 + 1) = 43
        assertEquals(43f, Explosions.entityDamage(0f, 3f, 1f));
        // halfway out: impact 0.5 -> floor((0.25 + 0.5) / 2 * 42 + 1) = 16
        assertEquals(16f, Explosions.entityDamage(3f, 3f, 1f));
        assertEquals(0f, Explosions.entityDamage(6f, 3f, 1f), "nothing at twice the power");
        assertEquals(0f, Explosions.entityDamage(1f, 3f, 0f), "full cover means no damage");
        assertEquals(11f, Explosions.scaleForDifficulty(20f, Difficulty.EASY), "min(D/2 + 1, D)");
        assertEquals(20f, Explosions.scaleForDifficulty(20f, Difficulty.NORMAL));
        assertEquals(30f, Explosions.scaleForDifficulty(20f, Difficulty.HARD));
        assertEquals(0f, Explosions.scaleForDifficulty(20f, Difficulty.PEACEFUL));
    }

    @Test
    void aBlastDigsCraterButNeverBreaksBedrockOrObsidian() {
        Vector3f center = new Vector3f(5.5f, ground, 5.5f);
        world.setBlock(5, ground + 1, 5, BlockType.OBSIDIAN);
        world.setBlock(5, ground - 1, 5, BlockType.DIRT);
        assertTrue(world.isSolidCollision(5, ground - 1, 5));
        Explosions.detonate(world, center, 3f, List.of(), new ItemEntityManager(64), new Random(4), Difficulty.NORMAL);
        assertEquals(BlockType.AIR, world.getBlock(5, ground - 1, 5), "the dirt under the blast is destroyed");
        assertEquals(BlockType.OBSIDIAN, world.getBlock(5, ground + 1, 5), "blast resistance 1200 survives");
        assertEquals(BlockType.BEDROCK, world.getBlock(5, 0, 5), "bedrock never breaks");
    }

    @Test
    void anExplosionHurtsNearbyTargetsMoreThanFarOnesAndSkipsTheOutOfRange() {
        Dummy near = new Dummy(6.5f, ground + 1f, 5.5f), far = new Dummy(9.5f, ground + 1f, 5.5f), out = new Dummy(30f, ground + 1f, 5.5f);
        Explosions.detonate(world, new Vector3f(5.5f, ground + 1f, 5.5f), 3f, new ArrayList<>(List.of(near, far, out)),
                new ItemEntityManager(64), new Random(1), Difficulty.NORMAL);
        assertTrue(near.taken > far.taken, "closer targets take more");
        assertTrue(far.taken > 0f);
        assertEquals(0f, out.taken);
    }

    @Test
    void aCreeperSwellsForThirtyTicksNearItsTargetThenExplodes() {
        Creeper c = new Creeper(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL);
        Dummy d = new Dummy(6.5f, ground + 1f, 5.5f);
        c.setTarget(d);
        float t = 0f;
        while (c.isAlive() && t < 3f) {
            c.tick(world, List.of(d), 0.05f);
            t += 0.05f;
        }
        assertFalse(c.isAlive(), "it blew itself up");
        assertEquals(1.5f, t, 0.15f, "fuse of 30 ticks = 1.5 s");
        assertTrue(c.isSilentRemoval(), "an exploded creeper leaves no loot");
        assertEquals(3f, c.takeExplosion());
    }

    @Test
    void aCreeperWithNobodyNearbyDoesNotSwell() {
        Creeper c = new Creeper(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL);
        Dummy d = new Dummy(15.5f, ground + 1f, 5.5f);
        c.setTarget(d);
        for (int i = 0; i < 10; i++) c.tick(world, List.of(d), 0.05f); // too far to start the fuse
        assertEquals(0f, c.getSwell());
        assertTrue(c.isAlive());
    }

    @Test
    void arrowsFlyInAnArcStickInBlocksAndHurtWhatTheyHit() {
        ArrowManager am = new ArrowManager();
        Dummy target = new Dummy(5.5f, ground + 1f, 12.5f);
        am.fire(new Vector3f(5.5f, ground + 1.2f, 5.5f), new Vector3f(0, 2, 32), 4f);
        List<Vector3f> sounds = new ArrayList<>();
        for (int i = 0; i < 100 && !am.arrows().isEmpty(); i++) am.update(world, List.of(target), 0.02f, sounds::add);
        assertEquals(4f, target.taken, "the hit deals the arrow's damage");
        assertTrue(am.arrows().isEmpty());
        assertFalse(sounds.isEmpty(), "and makes the hit sound");

        am.fire(new Vector3f(5.5f, ground + 1.2f, 5.5f), new Vector3f(0, -10, 20), 4f);
        for (int i = 0; i < 100; i++) am.update(world, List.of(), 0.02f, sounds::add);
        assertEquals(1, am.arrows().size());
        assertTrue(am.arrows().get(0).stuck, "lodged in the ground");
        for (int i = 0; i < 4000; i++) am.update(world, List.of(), 0.02f, sounds::add);
        assertTrue(am.arrows().isEmpty(), "despawns after a minute");
    }

    @Test
    void skeletonNumbersMatchTheResearch() {
        assertEquals(2f, RangedAttackGoal.intervalFor(Difficulty.NORMAL));
        assertEquals(1f, RangedAttackGoal.intervalFor(Difficulty.HARD));
        assertEquals(10f, RangedAttackGoal.inaccuracyFor(Difficulty.EASY));
        assertEquals(6f, RangedAttackGoal.inaccuracyFor(Difficulty.NORMAL));
        assertEquals(2f, RangedAttackGoal.inaccuracyFor(Difficulty.HARD));
        assertEquals(32f, RangedAttackGoal.ARROW_SPEED, "1.6 blocks per tick");
    }

    @Test
    void aSkeletonShootsAnArrowAtATargetInRangeAndLineOfSight() {
        Skeleton s = new Skeleton(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL);
        Dummy d = new Dummy(5.5f, ground + 1f, 14.5f);
        s.setTarget(d);
        List<Mob.ProjectileRequest> shots = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            s.tick(world, List.of(d), 0.05f);
            shots.addAll(s.drainProjectiles());
        }
        assertFalse(shots.isEmpty(), "it fires");
        assertTrue(shots.size() <= 3, "one shot per 2 seconds on Normal");
        assertTrue(shots.get(0).velocity().z > 20f, "toward the target");
        assertEquals(4f, shots.get(0).damage());
    }

    @Test
    void aSpiderIsNeutralInBrightLightUnlessHurtAndClimbsWalls() {
        Spider sp = new Spider(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL);
        Dummy d = new Dummy(8.5f, ground + 1f, 5.5f);
        sp.setTarget(d);
        sp.setLightLevel(15);
        for (int i = 0; i < 12; i++) sp.tick(world, List.of(d), 0.05f);
        assertFalse(sp.hasTarget(), "neutral at light level 12+");
        sp.setLightLevel(5);
        for (int i = 0; i < 12; i++) sp.tick(world, List.of(d), 0.05f);
        assertTrue(sp.hasTarget(), "hostile in the dark");
        assertEquals(2f, Spider.attackDamageFor(Difficulty.NORMAL));
        assertEquals(3f, Spider.attackDamageFor(Difficulty.HARD));
        assertEquals(16f, sp.getHealth().getMaxHealth());
    }

    @Test
    void dropsAndTypeIdsMatchTheResearch() {
        Random r = new Random(11);
        for (int i = 0; i < 300; i++) {
            for (var d : new Creeper(new Vector3f(), 0, () -> Difficulty.NORMAL).getDrops(r)) {
                assertEquals(BlockType.GUNPOWDER, d.item());
                assertTrue(d.count() >= 1 && d.count() <= 2);
            }
            for (var d : new Skeleton(new Vector3f(), 0, () -> Difficulty.NORMAL).getDrops(r)) {
                assertTrue(d.item() == BlockType.BONE || d.item() == BlockType.ARROW);
            }
            for (var d : new Spider(new Vector3f(), 0, () -> Difficulty.NORMAL).getDrops(r)) {
                assertTrue(d.item() == BlockType.STRING || d.item() == BlockType.SPIDER_EYE);
            }
        }
        for (String id : new String[]{"creeper", "skeleton", "spider"}) {
            Mob m = MobTypes.create(id, new Vector3f(), 0f, () -> Difficulty.NORMAL);
            assertEquals(id, m.typeId());
            assertTrue(m.isHostile());
        }
    }
}
