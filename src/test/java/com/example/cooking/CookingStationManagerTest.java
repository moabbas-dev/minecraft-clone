package com.example.cooking;

import com.example.inventory.ItemStack;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookingStationManagerTest {

    @Test
    void createGetAndRemoveWorkByExactPosition() {
        CookingStationManager m = new CookingStationManager();
        assertNull(m.get(5, 10, 5));
        CookingStationState created = m.create(5, 10, 5, CookingStationType.FURNACE);
        assertSame(created, m.get(5, 10, 5));
        assertNull(m.get(6, 10, 5), "a different position must be unaffected");
        assertEquals(1, m.count());

        CookingStationState removed = m.remove(5, 10, 5);
        assertSame(created, removed);
        assertNull(m.get(5, 10, 5));
        assertEquals(0, m.count());
    }

    @Test
    void removingAPositionThatWasNeverCreatedReturnsNull() {
        assertNull(new CookingStationManager().remove(1, 2, 3));
    }

    @Test
    void tickAllOnlyAdvancesStationsInLoadedGeneratedChunks() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0)); // loaded+generated
        // Chunk (5,5) deliberately never generated - stays absent from the world.

        CookingStationManager m = new CookingStationManager();
        CookingStationState loaded = m.create(8, 4, 8, CookingStationType.FURNACE); // inside chunk (0,0)
        loaded.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        loaded.setFuelStack(new ItemStack(BlockType.COAL, 1));

        CookingStationState unloaded = m.create(8 + 16 * 5, 4, 8 + 16 * 5, CookingStationType.FURNACE); // inside chunk (5,5)
        unloaded.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        unloaded.setFuelStack(new ItemStack(BlockType.COAL, 1));

        for (int i = 0; i < 200; i++) m.tickAll(world);

        assertFalse(loaded.getOutputStack().isEmpty(), "the loaded station's chunk is generated, so it must have ticked");
        assertTrue(unloaded.getOutputStack().isEmpty(), "the unloaded station's chunk was never generated, so it must not have ticked");
        assertEquals(0, unloaded.getCookProgressTicks());
    }

    @Test
    void snapshotAllAndRestoreAllRoundTripExactly() {
        CookingStationManager m = new CookingStationManager();
        CookingStationState s = m.create(3, 4, 5, CookingStationType.BLAST_FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 7));
        s.setFuelStack(new ItemStack(BlockType.COAL, 2));
        for (int i = 0; i < 100; i++) s.tick(); // partial progress, still mid-cook

        List<CookingStationManager.Snapshot> snaps = m.snapshotAll();
        assertEquals(1, snaps.size());

        CookingStationManager restored = new CookingStationManager();
        restored.restoreAll(snaps);
        CookingStationState back = restored.get(3, 4, 5);
        assertNotNull(back);
        assertEquals(CookingStationType.BLAST_FURNACE, back.getType());
        assertEquals(s.getInputStack(), back.getInputStack());
        assertEquals(s.getFuelStack(), back.getFuelStack());
        assertEquals(s.getOutputStack(), back.getOutputStack());
        assertEquals(s.getBurnTimeRemainingTicks(), back.getBurnTimeRemainingTicks());
        assertEquals(s.getTotalBurnTimeTicks(), back.getTotalBurnTimeTicks());
        assertEquals(s.getCookProgressTicks(), back.getCookProgressTicks());
        assertEquals(s.getStoredExperience(), back.getStoredExperience());
    }

    @Test
    void restoreAllSkipsAnUnknownStationTypeInsteadOfThrowing() {
        CookingStationManager.Snapshot bad = new CookingStationManager.Snapshot(
                1, 1, 1, "NOT_A_REAL_STATION", ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 0, 0f);
        CookingStationManager m = new CookingStationManager();
        assertDoesNotThrow(() -> m.restoreAll(List.of(bad)));
        assertEquals(0, m.count());
    }
}
