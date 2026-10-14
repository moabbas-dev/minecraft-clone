package com.example.world.integration;

import com.example.world.WorldIO;
import com.example.world.WorldSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The day counter that drives the moon phase is saved after the older optional sections and tolerated when missing. */
class WorldIoDayCountTest {
    @TempDir
    File tempDir;

    @Test
    void theDayCountRoundTripsEvenWithoutInventoryOrSurvivalSections() {
        WorldSettings settings = new WorldSettings("Days", WorldSettings.WorldType.SURVIVAL);
        WorldIO.saveMeta(tempDir, settings, 7L, 0f, 64f, 0f, 0.3f, 2, null, null, 13L);
        assertEquals(13L, WorldIO.loadMeta(tempDir).dayCount());
    }

    @Test
    void theDayCountRoundTripsAfterTheOtherSections() {
        WorldSettings settings = new WorldSettings("Days", WorldSettings.WorldType.SURVIVAL);
        WorldIO.SurvivalSnapshot snapshot = new WorldIO.SurvivalSnapshot(20f, 20f, 5f, 0f);
        WorldIO.saveMeta(tempDir, settings, 7L, 0f, 64f, 0f, 0.3f, 2, new com.example.inventory.Inventory(), snapshot, 21L);
        WorldIO.LoadedMeta meta = WorldIO.loadMeta(tempDir);
        assertEquals(21L, meta.dayCount());
        assertEquals(20f, meta.survival().health());
    }

    @Test
    void savesWithoutADayCountLoadWithZero() {
        WorldSettings settings = new WorldSettings("Old", WorldSettings.WorldType.SURVIVAL);
        WorldIO.saveMeta(tempDir, settings, 7L, 0f, 64f, 0f, 0.3f, 2);
        assertEquals(0L, WorldIO.loadMeta(tempDir).dayCount());
    }
}
