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
