package com.example.world.integration;

import com.example.world.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every test here uses {@code @TempDir} - a fresh throwaway directory per test - so nothing
 * ever touches the real {@code saves/} folder a player's world lives in.
 */
class WorldIoRoundTripTest {

    @Test
    void metaRoundTripsExactly(@TempDir File tempDir) {
        WorldSettings settings = new WorldSettings("My Test World", WorldSettings.WorldType.SURVIVAL);
        WorldIO.saveMeta(tempDir, settings, 12345L, 1.5f, 64.25f, -3.75f, 0.42f, 3);

        WorldIO.LoadedMeta loaded = WorldIO.loadMeta(tempDir);
        assertNotNull(loaded);
        assertEquals(12345L, loaded.seed());
        assertEquals(WorldSettings.WorldType.SURVIVAL, loaded.settings().type());
        assertEquals("My Test World", loaded.settings().name());
        assertEquals(new Vector3f(1.5f, 64.25f, -3.75f), loaded.playerPos());
        assertEquals(0.42f, loaded.dayTime(), 0.0001f);
        assertEquals(3, loaded.selectedIndex());
        assertEquals(Difficulty.NORMAL, loaded.settings().difficulty(), "default when nothing overrides it");
        assertNull(loaded.survival(), "no survival section was ever written");
    }

    @Test
    void survivalStateAndDifficultyRoundTripThroughWorldDat(@TempDir File tempDir) {
        WorldSettings settings = new WorldSettings("Hardcore", WorldSettings.WorldType.SURVIVAL, Difficulty.HARD);
        WorldIO.SurvivalSnapshot snapshot = new WorldIO.SurvivalSnapshot(13.5f, 8f, 2f, 1.25f);
        WorldIO.saveMeta(tempDir, settings, 1L, 0f, 64f, 0f, 0f, 0, new com.example.inventory.Inventory(), snapshot);

        WorldIO.LoadedMeta loaded = WorldIO.loadMeta(tempDir);
        assertNotNull(loaded);
        assertEquals(Difficulty.HARD, loaded.settings().difficulty());
        assertNotNull(loaded.survival());
        assertEquals(13.5f, loaded.survival().health());
        assertEquals(8f, loaded.survival().foodLevel());
        assertEquals(2f, loaded.survival().saturation());
        assertEquals(1.25f, loaded.survival().exhaustion());
    }

    @Test
    void savesWrittenBeforeHealthHungerExistedStillLoadWithNoSurvivalSnapshot(@TempDir File tempDir) {
        // The pre-survival 9-argument saveMeta (with an inventory but no snapshot) writes
        // exactly the on-disk format from before this milestone.
        WorldIO.saveMeta(tempDir, new WorldSettings("PreHealth", WorldSettings.WorldType.SURVIVAL), 5L,
                0f, 64f, 0f, 0f, 0, new com.example.inventory.Inventory());
        WorldIO.LoadedMeta loaded = WorldIO.loadMeta(tempDir);
        assertNotNull(loaded, "a save with no survival section must not be treated as corrupt");
        assertNull(loaded.survival());
        assertEquals(Difficulty.NORMAL, loaded.settings().difficulty(), "falls back to the default");
        assertNotNull(loaded.inventory(), "the inventory section it does have must still load");
    }

    @Test
    void inventoryRoundTripsThroughWorldDat(@TempDir File tempDir) {
        com.example.inventory.Inventory inv = new com.example.inventory.Inventory();
        inv.add(BlockType.COBBLESTONE, 70);
        inv.add(BlockType.OAK_SAPLING_STAGE_0, 2);
        inv.selectSlot(3);
        WorldIO.saveMeta(tempDir, new WorldSettings("Inv", WorldSettings.WorldType.SURVIVAL), 1L, 0f, 64f, 0f, 0f, 3, inv);

        WorldIO.LoadedMeta loaded = WorldIO.loadMeta(tempDir);
        assertNotNull(loaded);
        assertNotNull(loaded.inventory());
        assertEquals(70, loaded.inventory().count(BlockType.COBBLESTONE));
        assertEquals(2, loaded.inventory().count(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(3, loaded.inventory().getSelectedSlot());
    }

    @Test
    void savesWrittenBeforeInventoriesExistedStillLoadWithAnEmptyInventory(@TempDir File tempDir) {
        // The legacy 8-argument saveMeta writes exactly the old on-disk format (no inventory section).
        WorldIO.saveMeta(tempDir, new WorldSettings("Old", WorldSettings.WorldType.SURVIVAL), 9L, 1f, 2f, 3f, 0.5f, 7);
        WorldIO.LoadedMeta loaded = WorldIO.loadMeta(tempDir);
        assertNotNull(loaded, "an old-format save must not be treated as corrupt");
        assertNull(loaded.inventory());
        assertEquals(9L, loaded.seed());
        assertEquals(7, loaded.selectedIndex());
    }

    @Test
    void loadMetaOnMissingFileReturnsNullInsteadOfThrowing(@TempDir File tempDir) {
        assertNull(WorldIO.loadMeta(new File(tempDir, "does-not-exist")));
    }

    @Test
    void loadMetaOnCorruptFileReturnsNullInsteadOfThrowing(@TempDir File tempDir) throws Exception {
        File worldDat = new File(tempDir, "world.dat");
        try (var out = new java.io.FileOutputStream(worldDat)) {
            out.write(new byte[]{1, 2, 3}); // far too short to be a valid meta file
        }
        assertNull(WorldIO.loadMeta(tempDir));
    }

    @Test
    void cookingStationsRoundTripThroughTheirOwnFile(@TempDir File tempDir) {
        var input = new com.example.inventory.ItemStack(BlockType.IRON_ORE, 3);
        var fuel = new com.example.inventory.ItemStack(BlockType.COAL, 2);
        var output = new com.example.inventory.ItemStack(BlockType.IRON_INGOT, 1);
        WorldIO.CookingStationSnapshot snap = new WorldIO.CookingStationSnapshot(
                5, 64, -3, "BLAST_FURNACE", input, fuel, output, 150, 400, 37, 0.7f);
        WorldIO.saveCookingStations(tempDir, java.util.List.of(snap));

        var loaded = WorldIO.loadCookingStations(tempDir);
        assertEquals(1, loaded.size());
        var back = loaded.get(0);
        assertEquals(5, back.x());
        assertEquals(64, back.y());
        assertEquals(-3, back.z());
        assertEquals("BLAST_FURNACE", back.stationTypeName());
        assertEquals(input, back.input());
        assertEquals(fuel, back.fuel());
        assertEquals(output, back.output());
        assertEquals(150, back.burnTimeRemainingTicks());
        assertEquals(400, back.totalBurnTimeTicks());
        assertEquals(37, back.cookProgressTicks());
        assertEquals(0.7f, back.storedExperience());
    }

    @Test
    void aWorldWithNoCookingStationsFileLoadsAnEmptyListNotNullOrAnError(@TempDir File tempDir) {
        assertTrue(WorldIO.loadCookingStations(tempDir).isEmpty());
    }

    @Test
    void emptyStacksInACookingStationRoundTripAsEmptyNotAPhantomItem(@TempDir File tempDir) {
        WorldIO.CookingStationSnapshot snap = new WorldIO.CookingStationSnapshot(
                0, 0, 0, "FURNACE", com.example.inventory.ItemStack.EMPTY, com.example.inventory.ItemStack.EMPTY,
                com.example.inventory.ItemStack.EMPTY, 0, 0, 0, 0f);
        WorldIO.saveCookingStations(tempDir, java.util.List.of(snap));
        var back = WorldIO.loadCookingStations(tempDir).get(0);
        assertTrue(back.input().isEmpty());
        assertTrue(back.fuel().isEmpty());
        assertTrue(back.output().isEmpty());
    }

    @Test
    void chunkRoundTripsExactBytes(@TempDir File tempDir) {
        Chunk chunk = new Chunk(2, -3);
        chunk.setLocal(0, 0, 0, BlockType.BEDROCK);
        chunk.setLocal(5, 10, 7, BlockType.DIAMOND_ORE);
        chunk.setLocal(15, 255, 15, BlockType.GLOWSTONE);
        byte[] original = chunk.exportBlocks();

        WorldIO.saveChunk(tempDir, chunk);
        assertTrue(WorldIO.chunkExists(tempDir, 2, -3));
        assertFalse(WorldIO.chunkExists(tempDir, 2, -4));

        byte[] loaded = WorldIO.loadChunk(tempDir, 2, -3);
        assertArrayEquals(original, loaded);
    }

    @Test
    void loadChunkOnMissingFileReturnsNull(@TempDir File tempDir) {
        assertNull(WorldIO.loadChunk(tempDir, 99, 99));
    }

    /**
     * {@link WorldIO#deleteWorld} always resolves against the real {@code WorldIO.SAVES_ROOT}
     * ("saves/"), a hardcoded relative path - it can't be redirected to a {@code @TempDir}. To
     * actually exercise this real method (rather than re-implementing its recursive delete in
     * test code, which would test nothing) this uses a uniquely-named, clearly-marked folder
     * under the real saves/ root and removes it with the method under test itself, wrapped in
     * try/finally so a mid-test assertion failure can never leave the folder behind.
     */
    @Test
    void deleteWorldRemovesEverythingUnderIt() {
        String folderName = "__WorldIoRoundTripTest_deleteWorld__";
        File worldDir = WorldIO.worldDir(folderName);
        try {
            WorldIO.saveMeta(worldDir, new WorldSettings("ToDelete", WorldSettings.WorldType.FLAT), 1L, 0, 0, 0, 0, 0);
            WorldIO.saveChunk(worldDir, new Chunk(0, 0));
            assertTrue(worldDir.exists());

            WorldIO.deleteWorld(folderName);
            assertFalse(worldDir.exists());
        } finally {
            WorldIO.deleteWorld(folderName); // no-op if the assertion above already succeeded
        }
    }
}
