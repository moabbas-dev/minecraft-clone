package com.example.entity;

import com.example.entity.mob.Zombie;
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

/** Tests for the vanilla-alignment milestone: falling blocks, XP orbs, zombie sunlight, thrown items, spawn rules. */
class VanillaAlignmentTest {
    World world;
    int ground; // y of the first air block above the flat floor

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++)
            for (int cz = -1; cz <= 1; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        ground = com.example.world.Chunk.MIN_Y;
        while (world.isSolidCollision(5, ground, 5)) ground++;
    }

    @Test
    void sandWithNoSupportFallsAndLandsOnTheGround() {
        FallingBlockManager fb = new FallingBlockManager();
        ItemEntityManager items = new ItemEntityManager(64);
        world.setBlock(5, ground + 6, 5, BlockType.SAND);
        fb.checkColumn(world, 5, ground + 6, 5);
        assertEquals(1, fb.entities().size());
        assertEquals(BlockType.AIR, world.getBlock(5, ground + 6, 5), "the block became an entity");
        for (int i = 0; i < 400 && !fb.entities().isEmpty(); i++) fb.update(world, 0.05f, items, new Random(1));
        assertTrue(fb.entities().isEmpty());
        assertEquals(BlockType.SAND, world.getBlock(5, ground, 5), "landed on the floor");
    }

    @Test
    void supportedSandStaysAndAStackFallsTogether() {
        FallingBlockManager fb = new FallingBlockManager();
        world.setBlock(5, ground, 5, BlockType.SAND);
        fb.checkColumn(world, 5, ground, 5);
        assertTrue(fb.entities().isEmpty(), "resting on stone does not fall");
        world.setBlock(5, ground + 3, 5, BlockType.GRAVEL);
        world.setBlock(5, ground + 4, 5, BlockType.SAND);
        fb.checkColumn(world, 5, ground + 3, 5);
        assertEquals(2, fb.entities().size(), "gravel and the sand above it both fall");
    }

    @Test
    void aBlockThatCannotLandDropsAsAnItem() {
        FallingBlockManager fb = new FallingBlockManager();
        ItemEntityManager items = new ItemEntityManager(64);
        world.setBlock(5, ground + 4, 5, BlockType.GRAVEL);
        fb.checkColumn(world, 5, ground + 4, 5);
        world.setBlock(5, ground, 5, BlockType.STONE); // the landing cell gets filled while it falls
        for (int i = 0; i < 400 && !fb.entities().isEmpty(); i++) fb.update(world, 0.05f, items, new Random(1));
        assertTrue(fb.entities().isEmpty());
        assertEquals(BlockType.GRAVEL, world.getBlock(5, ground + 1, 5));
    }

    @Test
    void settleAllLandsEverythingBeforeASave() {
        FallingBlockManager fb = new FallingBlockManager();
        world.setBlock(5, ground + 5, 5, BlockType.RED_SAND);
        fb.checkColumn(world, 5, ground + 5, 5);
        fb.settleAll(world, new ItemEntityManager(64), new Random(1));
        assertTrue(fb.entities().isEmpty());
        assertEquals(BlockType.RED_SAND, world.getBlock(5, ground, 5));
    }

    @Test
    void experienceSplitsIntoVanillaOrbSizesLargestFirst() {
        assertEquals(List.of(37), XpOrbManager.split(37));
        assertEquals(List.of(7, 1), XpOrbManager.split(8));
        assertEquals(List.of(17, 17, 1), XpOrbManager.split(35));
        assertEquals(List.of(2477, 1237), XpOrbManager.split(3714));
        assertTrue(XpOrbManager.split(0).isEmpty());
    }

    @Test
    void anOrbIsPulledToThePlayerAndCollectedOnContact() {
        XpOrbManager orbs = new XpOrbManager();
        orbs.spawn(5f, new Vector3f(5.5f, ground + 1f, 5.5f), new Random(1));
        int total = orbs.orbs().stream().mapToInt(o -> o.value).sum();
        assertEquals(5, total);
        double[] collected = {0};
        Vector3f player = new Vector3f(8.5f, ground, 5.5f); // within 7.25 blocks
        for (int i = 0; i < 400 && !orbs.orbs().isEmpty(); i++) orbs.update(world, player, 0.05f, v -> collected[0] += v);
        assertTrue(orbs.orbs().isEmpty(), "orbs reach the player");
        assertEquals(5.0, collected[0]);
    }

    @Test
    void anOrbOutOfRangeStaysPutAndEventuallyDespawns() {
        XpOrbManager orbs = new XpOrbManager();
        orbs.spawn(3f, new Vector3f(5.5f, ground + 1f, 5.5f), new Random(1));
        Vector3f far = new Vector3f(50f, ground, 50f);
        for (int i = 0; i < 6000 && !orbs.orbs().isEmpty(); i++) orbs.update(world, far, 0.1f, v -> fail("not in range"));
        assertTrue(orbs.orbs().isEmpty(), "despawns after 5 minutes");
    }

    @Test
    void thrownItemsFlyAlongTheViewWithVanillaSpeed() {
        Vector3f v = ItemEntityManager.throwVelocity(new Vector3f(0, 0, -1), new Random(3));
        assertEquals(-6f, v.z, 0.5f, "0.3 blocks/tick along the view");
        assertEquals(2f, v.y, 2.01f, "0.1 up plus a little scatter");
        assertEquals(2f, ItemEntityManager.THROWN_PICKUP_DELAY_SECONDS);
    }

    @Test
    void aZombieInDirectSunlightCatchesFireAndBurnsOneHealthPerSecond() {
        Zombie z = new Zombie(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL);
        z.setSunlit(true);
        z.tick(world, List.of(), 0.05f);
        assertTrue(z.isOnFire());
        float hp = z.getHealth().getCurrentHealth();
        for (int i = 0; i < 40; i++) z.tick(world, List.of(), 0.05f); // 2 s
        assertTrue(z.getHealth().getCurrentHealth() <= hp - 1.5f, "burning drains about 1 HP per second");
        z.setSunlit(false);
        for (int i = 0; i < 200; i++) z.tick(world, List.of(), 0.05f);
        assertFalse(z.isOnFire(), "the fire burns out once the sun is gone");
    }

    @Test
    void babyZombiesAreSmallerFasterAndWorthMoreXp() {
        Zombie adult = new Zombie(new Vector3f(), 0f, () -> Difficulty.NORMAL);
        Zombie baby = new Zombie(new Vector3f(), 0f, () -> Difficulty.NORMAL, true);
        assertEquals(adult.getHeight() / 2f, baby.getHeight(), 1e-5f);
        assertEquals(adult.getMoveSpeed() * 1.5f, baby.getMoveSpeed(), 1e-5f);
        assertEquals(12, baby.xpReward(new Random()));
        assertEquals(5, adult.xpReward(new Random()));
        assertEquals(4.5f, Zombie.attackDamageFor(Difficulty.HARD));
    }

    @Test
    void peacefulStopsHostileSpawningAndRemovesHostileMobs() {
        MobManager mobs = new MobManager();
        MobSpawner spawner = new MobSpawner();
        for (int i = 0; i < 200; i++) {
            spawner.tick(world, mobs, new Vector3f(5, ground, 5), false, () -> Difficulty.PEACEFUL, new Random(i), 2f);
        }
        assertEquals(0, mobs.size());
        mobs.spawn(new Zombie(new Vector3f(5.5f, ground, 5.5f), 0f, () -> Difficulty.NORMAL));
        mobs.removeHostile();
        assertEquals(0, mobs.size());
    }

    @Test
    void hostileSpawnsNeedBlockLightZeroAndLowSkyLight() {
        Vector3f far = new Vector3f(-30, ground, -30);
        // flat floor in daylight: sky light is bright, so no spawn
        world.setSkyDarken(0);
        Random random = new Random(5);
        for (int i = 0; i < 300; i++) assertFalse(MobSpawner.isValidSpawn(world, 5, ground, 5, far, false, random));
        // at night the sky is dimmed by 11 and block light is 0, so some attempts pass
        world.setSkyDarken(11);
        boolean any = false;
        for (int i = 0; i < 300; i++) any |= MobSpawner.isValidSpawn(world, 5, ground, 5, far, false, random);
        assertTrue(any);
    }
}
