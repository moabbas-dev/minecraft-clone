package com.example.entity;

import com.example.inventory.Inventory;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ItemEntityManagerTest {
    private static final float DT = 1f / 60f;
    /** Feet rest on top of FlatWorldGenerator's grass layer. */
    private static final float FLOOR_Y = FlatWorldGenerator.GRASS_HEIGHT + 1;

    private World world;
    private ItemEntityManager manager;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        }
        manager = new ItemEntityManager(Inventory.DEFAULT_MAX_STACK);
    }

    private void run(float seconds) {
        for (int i = 0; i < (int) (seconds / DT); i++) manager.update(world, DT);
    }

    private Vector3f above(ItemEntity e) {
        return new Vector3f(e.position.x, FLOOR_Y, e.position.z);
    }

    @Test
    void aDroppedItemFallsAndSettlesOnTheGroundWithoutSinking() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 10.5f, 5.5f), new Random(1));
        ItemEntity e = manager.entities().get(0);
        for (int i = 0; i < 240; i++) {
            manager.update(world, DT);
            assertTrue(e.position.y >= FLOOR_Y - 0.01f, "fell through the floor at tick " + i + ": y=" + e.position.y);
        }
        assertEquals(FLOOR_Y, e.position.y, 0.01f);
        assertTrue(e.isOnGround());
        assertTrue(e.isAlive(), "a settled item must not disappear");
    }

    @Test
    void aVeryFastFallStillNeverTunnelsThroughTheFloor() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 200f, 5.5f), new Random(1));
        ItemEntity e = manager.entities().get(0);
        for (int i = 0; i < 600; i++) {
            manager.update(world, 0.05f); // the game's maximum dt
            assertTrue(e.position.y >= FLOOR_Y - 0.01f, "tunneled at tick " + i + ": y=" + e.position.y);
        }
        assertEquals(FLOOR_Y, e.position.y, 0.01f);
    }

    @Test
    void itemsSpawnedInsideTerrainArePushedOutInsteadOfStayingEmbedded() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 2.5f, 5.5f), new Random(1)); // inside the grass layer
        run(2f);
        assertEquals(FLOOR_Y, manager.entities().get(0).position.y, 0.05f);
    }

    @Test
    void pickupIsBlockedDuringTheDelayAndWorksAfterIt() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        Inventory inv = new Inventory();
        ItemEntity e = manager.entities().get(0);
        run(0.2f);
        assertEquals(0, manager.collect(above(e), inv), "pickup delay must block early collection");
        assertEquals(0, inv.count(BlockType.DIRT));
        run(0.6f);
        assertEquals(1, manager.collect(above(manager.entities().get(0)), inv));
        assertEquals(1, inv.count(BlockType.DIRT));
    }

    @Test
    void fullPickupAddsToInventoryAndRemovesTheEntityAtZero() {
        manager.spawn(BlockType.DIRT, 5, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        Inventory inv = new Inventory();
        assertEquals(1, manager.collect(above(manager.entities().get(0)), inv));
        assertEquals(5, inv.count(BlockType.DIRT));
        assertEquals(0, manager.size(), "an emptied entity must be removed");
    }

    @Test
    void aFullInventoryLeavesTheItemInTheWorldUntouched() {
        manager.spawn(BlockType.DIRT, 3, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        Inventory inv = new Inventory(1, 1, 4);
        inv.add(BlockType.STONE, 4);
        assertEquals(0, manager.collect(above(manager.entities().get(0)), inv));
        assertEquals(1, manager.size());
        assertEquals(3, manager.entities().get(0).getCount(), "no item may be destroyed by a failed pickup");
        assertEquals(4, inv.count(BlockType.STONE));
    }

    @Test
    void partialPickupTakesOnlyWhatFitsAndLeavesTheRestInTheWorld() {
        manager.spawn(BlockType.DIRT, 10, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        Inventory inv = new Inventory(2, 2, 4);
        inv.add(BlockType.DIRT, 3); // room: 1 in the stack + 4 in the empty slot = 5
        assertEquals(1, manager.collect(above(manager.entities().get(0)), inv));
        assertEquals(8, inv.count(BlockType.DIRT));
        assertEquals(1, manager.size());
        assertEquals(5, manager.entities().get(0).getCount());
    }

    @Test
    void anItemCanNeverBePickedUpTwice() {
        manager.spawn(BlockType.DIRT, 2, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        Inventory inv = new Inventory();
        Vector3f feet = above(manager.entities().get(0));
        assertEquals(1, manager.collect(feet, inv));
        assertEquals(0, manager.collect(feet, inv));
        assertEquals(2, inv.count(BlockType.DIRT), "repeated collect calls must not duplicate items");
    }

    @Test
    void severalNearbyDifferentItemsAreAllCollected() {
        Vector3f spot = new Vector3f(5.5f, 4.3f, 5.5f);
        manager.spawn(BlockType.DIRT, 1, spot, new Random(1));
        manager.spawn(BlockType.STONE, 1, spot, new Random(2));
        manager.spawn(BlockType.SAND, 1, spot, new Random(3));
        run(1f);
        Inventory inv = new Inventory();
        Vector3f feet = above(manager.entities().get(0));
        assertEquals(3, manager.collect(feet, inv));
        assertEquals(1, inv.count(BlockType.DIRT));
        assertEquals(1, inv.count(BlockType.STONE));
        assertEquals(1, inv.count(BlockType.SAND));
    }

    @Test
    void farAwayItemsAreNotCollected() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        Inventory inv = new Inventory();
        assertEquals(0, manager.collect(new Vector3f(40f, FLOOR_Y, 40f), inv));
        assertEquals(1, manager.size());
    }

    @Test
    void sameTypeDropsAtTheSameSpotMergeIntoOneEntity() {
        Vector3f spot = new Vector3f(5.5f, 6.5f, 5.5f);
        manager.spawn(BlockType.DIRT, 1, spot, new Random(1));
        manager.spawn(BlockType.DIRT, 1, spot, new Random(2));
        assertEquals(1, manager.size());
        assertEquals(2, manager.entities().get(0).getCount());
    }

    @Test
    void mergingNeverExceedsTheStackLimit() {
        Vector3f spot = new Vector3f(5.5f, 6.5f, 5.5f);
        manager.spawn(BlockType.DIRT, 60, spot, new Random(1));
        manager.spawn(BlockType.DIRT, 10, spot, new Random(2));
        int total = 0;
        for (ItemEntity e : manager.entities()) {
            assertTrue(e.getCount() <= Inventory.DEFAULT_MAX_STACK);
            total += e.getCount();
        }
        assertEquals(70, total, "no items lost or duplicated by merging");
    }

    @Test
    void itemsInAnUnloadedChunkAreFrozenNotDeletedOrDropped() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        run(1f);
        ItemEntity e = manager.entities().get(0);
        Vector3f frozenAt = new Vector3f(e.position);
        float ageBefore = e.getAge();

        world.removeChunk(0, 0);
        run(2f);
        assertTrue(e.isAlive(), "unloading its chunk must not delete the item");
        assertEquals(frozenAt.y, e.position.y, 0.0001f);
        assertEquals(ageBefore, e.getAge(), 0.0001f, "a frozen item must not age toward despawn");

        world.generateChunk(world.getOrCreateChunk(0, 0));
        run(0.5f);
        assertTrue(e.getAge() > ageBefore, "the item resumes when its chunk returns");
    }

    @Test
    void itemsDespawnAfterTheirLifetime() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 4.3f, 5.5f), new Random(1));
        for (int i = 0; i < 305; i++) manager.update(world, 1f);
        assertEquals(0, manager.size());
    }

    @Test
    void theEntityCountIsCappedAndEvictsTheOldest() {
        for (int i = 0; i < ItemEntityManager.MAX_ENTITIES + 40; i++) {
            manager.spawn(BlockType.DIRT, 1, new Vector3f(i * 3f, 6.5f, 0.5f), new Random(i)); // far apart: no merging
        }
        assertEquals(ItemEntityManager.MAX_ENTITIES, manager.size());
    }

    @Test
    void invalidSpawnsAreIgnored() {
        manager.spawn(BlockType.AIR, 1, new Vector3f(5.5f, 6.5f, 5.5f), new Random(1));
        manager.spawn(null, 1, new Vector3f(5.5f, 6.5f, 5.5f), new Random(1));
        manager.spawn(BlockType.DIRT, 0, new Vector3f(5.5f, 6.5f, 5.5f), new Random(1));
        manager.spawn(BlockType.DIRT, -3, new Vector3f(5.5f, 6.5f, 5.5f), new Random(1));
        assertEquals(0, manager.size());
    }

    @Test
    void clearRemovesEverything() {
        manager.spawn(BlockType.DIRT, 1, new Vector3f(5.5f, 6.5f, 5.5f), new Random(1));
        manager.clear();
        assertEquals(0, manager.size());
    }
}
