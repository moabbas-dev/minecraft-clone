package com.example.entity;

import com.example.entity.mob.Zombie;
import com.example.survival.DamageSource;
import com.example.survival.HealthComponent;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MobSystemTest {
    static final int GROUND = FlatWorldGenerator.GRASS_HEIGHT + 1; // standing Y
    static final float DT = 0.05f;

    /** A stationary, health-bearing stand-in for the player. */
    static final class Dummy implements Targetable {
        final Vector3f pos;
        final HealthComponent health = new HealthComponent();
        int hits;
        float lastAmount;

        Dummy(float x, float y, float z) {
            pos = new Vector3f(x, y, z);
        }

        public Vector3f getPosition() {
            return new Vector3f(pos.x, pos.y + 0.9f, pos.z);
        }

        public boolean isAlive() {
            return health.isAlive();
        }

        public boolean damage(float amount, DamageSource source) {
            assertEquals(DamageSource.MOB, source);
            boolean ok = health.damage(amount, source);
            if (ok) {
                hits++;
                lastAmount = amount;
            }
            return ok;
        }
    }

    World world;
    Difficulty difficulty = Difficulty.NORMAL;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++)
            for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
    }

    Zombie zombie(float x, float y, float z) {
        return new Zombie(new Vector3f(x, y, z), 0f, () -> difficulty);
    }

    void run(Mob m, List<Targetable> targets, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            m.tick(world, targets, DT);
            if (targets.get(0) instanceof Dummy d) {
                d.health.tick(DT);
                d.health.heal(0.5f);
            }
        }
    }

    @Test
    void fallsWithGravityAndLandsOnGround() {
        Zombie z = zombie(4.5f, GROUND + 10, 4.5f);
        run(z, List.of(new Dummy(100, GROUND, 100)), 3f);
        assertTrue(z.isOnGround());
        assertEquals(GROUND, z.position.y, 0.01f);
    }

    @Test
    void cannotWalkThroughAWall() {
        for (int y = GROUND; y < GROUND + 4; y++)
            for (int z = -30; z <= 40; z++) world.setBlock(8, y, z, BlockType.STONE);
        Zombie zom = zombie(4.5f, GROUND, 4.5f);
        run(zom, List.of(new Dummy(12.5f, GROUND, 4.5f)), 6f);
        assertTrue(zom.position.x < 8f - 0.3f + 0.01f, "stopped at the wall: x=" + zom.position.x);
    }

    @Test
    void navigatesAroundASimpleWall() {
        for (int y = GROUND; y < GROUND + 4; y++)
            for (int z = 1; z <= 8; z++) world.setBlock(8, y, z, BlockType.STONE);
        Zombie zom = zombie(4.5f, GROUND, 4.5f);
        run(zom, List.of(new Dummy(12.5f, GROUND, 4.5f)), 20f);
        assertTrue(zom.position.x > 9f, "got past the wall: x=" + zom.position.x);
    }

    @Test
    void stepsUpOneBlockCurb() {
        for (int z = -4; z <= 12; z++) world.setBlock(8, GROUND, z, BlockType.STONE);
        Zombie zom = zombie(4.5f, GROUND, 4.5f);
        run(zom, List.of(new Dummy(12.5f, GROUND + 1, 4.5f)), 8f);
        assertTrue(zom.position.x > 9f, "climbed the curb: x=" + zom.position.x);
    }

    @Test
    void detectsFollowsStopsAtRangeAndAttacksOnCooldownWithCorrectDamage() {
        Dummy d = new Dummy(10.5f, GROUND, 4.5f);
        Zombie zom = zombie(4.5f, GROUND, 4.5f);
        run(zom, List.of(d), 0.6f);
        assertSame(d, zom.getTarget());
        assertTrue(zom.position.x > 4.6f, "moving toward target");
        run(zom, List.of(d), 8f);
        assertTrue(zom.distanceToTarget() <= Zombie.ATTACK_RANGE + 0.05f, "dist=" + zom.distanceToTarget() + " x=" + zom.position.x + " z=" + zom.position.z);
        assertTrue(d.hits >= 1);
        assertEquals(3.0f, d.lastAmount, 1e-4f);
        int before = d.hits;
        run(zom, List.of(d), 5f);
        assertTrue(d.hits - before <= 6 && d.hits - before >= 3, "swings=" + (d.hits - before));
    }

    @Test
    void attackDamageFollowsDifficulty() {
        assertEquals(2.5f, Zombie.attackDamageFor(Difficulty.EASY));
        assertEquals(3.0f, Zombie.attackDamageFor(Difficulty.NORMAL));
        assertEquals(4.0f, Zombie.attackDamageFor(Difficulty.HARD));
        difficulty = Difficulty.HARD;
        Dummy d = new Dummy(6f, GROUND, 4.5f);
        run(zombie(4.5f, GROUND, 4.5f), List.of(d), 2f);
        assertEquals(4.0f, d.lastAmount, 1e-4f);
    }

    @Test
    void cannotAttackThroughAWall() {
        for (int y = GROUND; y < GROUND + 3; y++)
            for (int z = -6; z <= 14; z++) world.setBlock(5, y, z, BlockType.STONE);
        Dummy d = new Dummy(6.4f, GROUND, 4.5f);
        Zombie zom = zombie(4.4f, GROUND, 4.5f);
        run(zom, List.of(d), 5f);
        assertEquals(0, d.hits);
    }

    @Test
    void takesDamageDiesStopsAndDropsOnce() {
        MobManager mm = new MobManager();
        ItemEntityManager items = new ItemEntityManager(64);
        Dummy d = new Dummy(6f, GROUND, 4.5f);
        Zombie zom = zombie(4.5f, GROUND, 4.5f);
        mm.spawn(zom);
        Vector3f p = new Vector3f(6, GROUND, 4.5f);
        Random rnd = new Random(1);
        for (int i = 0; i < 20; i++) mm.tick(world, List.of(d), p, items, rnd, DT);
        assertTrue(zom.damage(8f, DamageSource.PLAYER));
        assertEquals(20f - com.example.combat.ArmorRules.reduce(8f, 2f, 0f), zom.getHealth().getCurrentHealth(), 1e-4f, "zombies carry 2 natural armor points");
        assertFalse(zom.damage(8f, DamageSource.PLAYER), "hurt cooldown applies like any living entity");
        zom.getHealth().tick(1f);
        zom.damage(100f, DamageSource.PLAYER);
        int hitsAtDeath = d.hits;
        for (int i = 0; i < 10; i++) mm.tick(world, List.of(d), p, items, rnd, DT);
        assertFalse(zom.isAlive());
        assertEquals(1, mm.size(), "corpse lingers briefly");
        assertEquals(hitsAtDeath, d.hits, "no attacks after death");
        int dropsFirst = items.size();
        for (int i = 0; i < 40; i++) mm.tick(world, List.of(d), p, items, rnd, DT);
        assertEquals(0, mm.size(), "removed after the corpse timer");
        assertEquals(dropsFirst, items.size(), "drops spawned exactly once");
    }

    @Test
    void multipleZombiesCoexistAndFarOnesDespawn() {
        MobManager mm = new MobManager();
        ItemEntityManager items = new ItemEntityManager(64);
        for (int i = 0; i < 10; i++) mm.spawn(zombie(2 + i, GROUND, 4.5f));
        Dummy d = new Dummy(20, GROUND, 20);
        for (int i = 0; i < 100; i++) mm.tick(world, List.of(d), new Vector3f(20, GROUND, 20), items, new Random(1), DT);
        assertEquals(10, mm.size());
        mm.tick(world, List.of(d), new Vector3f(500, GROUND, 500), items, new Random(1), DT);
        assertEquals(0, mm.size(), "beyond despawn distance");
    }

    @Test
    void populationCapIsEnforced() {
        MobManager mm = new MobManager();
        for (int i = 0; i < MobManager.MAX_MOBS; i++) assertTrue(mm.spawn(zombie(1, GROUND, 1)));
        assertFalse(mm.spawn(zombie(1, GROUND, 1)));
    }

    @Test
    void rayHitsAMobBoxAndMissesOthers() {
        MobManager mm = new MobManager();
        Zombie z = zombie(8.5f, GROUND, 4.5f);
        mm.spawn(z);
        float[] dist = new float[1];
        assertSame(z, mm.raycast(new Vector3f(4.5f, GROUND + 1f, 4.5f), new Vector3f(1, 0, 0), 5f, dist));
        assertEquals(3.7f, dist[0], 0.05f);
        assertNull(mm.raycast(new Vector3f(4.5f, GROUND + 1f, 4.5f), new Vector3f(0, 0, 1), 5f, dist));
        assertNull(mm.raycast(new Vector3f(4.5f, GROUND + 1f, 4.5f), new Vector3f(1, 0, 0), 2f, dist), "out of reach");
    }

    @Test
    void spawnerRejectsBrightSolidAndTooCloseSpots() {
        Vector3f player = new Vector3f(8, GROUND, 8);
        assertFalse(MobSpawner.isValidSpawn(world, 30, GROUND, 8, player, 1f), "bright daylight");
        assertTrue(MobSpawner.isValidSpawn(world, 30, GROUND, 8, player, 0f), "night is dark enough");
        assertFalse(MobSpawner.isValidSpawn(world, 10, GROUND, 8, player, 0f), "too close to player");
        world.setBlock(30, GROUND, 8, BlockType.STONE);
        assertFalse(MobSpawner.isValidSpawn(world, 30, GROUND, 8, player, 0f), "inside a block");
    }

    @Test
    void spawnerSpawnsAtNightRespectingTheCap() {
        MobManager mm = new MobManager();
        MobSpawner sp = new MobSpawner();
        Vector3f player = new Vector3f(8, GROUND, 8);
        Random rnd = new Random(7);
        for (int i = 0; i < 2000; i++) sp.tick(world, mm, player, 0f, () -> difficulty, rnd, 0.5f);
        assertTrue(mm.size() > 0);
        assertTrue(mm.size() <= MobSpawner.MAX_ZOMBIES);
        for (Mob m : mm.mobs()) assertTrue(m.position.distance(player) >= MobSpawner.MIN_DISTANCE - 1f);
    }

    @Test
    void manyZombiesTickCheaply() {
        MobManager mm = new MobManager();
        for (int i = 0; i < 24; i++) mm.spawn(zombie(2 + (i % 12), GROUND, 2 + (i / 12) * 3));
        Dummy d = new Dummy(30, GROUND, 20);
        ItemEntityManager items = new ItemEntityManager(64);
        long t0 = System.nanoTime();
        for (int i = 0; i < 400; i++) mm.tick(world, List.of(d), new Vector3f(30, GROUND, 20), items, new Random(1), DT);
        double msPerTick = (System.nanoTime() - t0) / 1e6 / 400;
        assertTrue(msPerTick < 20, "24 zombies per tick ms=" + msPerTick);
    }
}
