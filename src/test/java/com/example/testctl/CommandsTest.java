package com.example.testctl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises Commands' parsing/validation/dispatch logic against a fake GameAccess - no real
 * HttpServer, GameCommandQueue, or GL context involved, matching this project's established
 * "test the pure logic, not the thing that needs a window" boundary (see TESTING.md).
 */
class CommandsTest {

    /** Records every call it receives instead of doing anything real. */
    private static final class FakeGame implements GameAccess {
        Float tpX, tpY, tpZ, tpYaw, tpPitch;
        Float time;
        String weatherState;
        Long seed;
        String screenshotName;
        boolean shutdownCalled;
        boolean throwOnScreenshot;
        Integer setBlockX, setBlockY, setBlockZ;
        String setBlockType;
        boolean throwOnSetBlock;
        Integer selectedSlot;
        boolean throwOnSelectSlot;
        String givenType;
        Integer givenCount;
        boolean throwOnGive;
        Boolean attackHeld;
        int useCalls;
        Double lookX, lookY, lookZ;
        boolean throwOnLook;
        Integer openedGrid;
        boolean throwOnOpen;
        Integer clickedSlot;
        Boolean clickedRight;
        boolean throwOnClick;
        int closeCalls;
        boolean throwOnClose;

        @Override
        public void teleport(float x, float y, float z, Float yawDegrees, Float pitchDegrees) {
            tpX = x;
            tpY = y;
            tpZ = z;
            tpYaw = yawDegrees;
            tpPitch = pitchDegrees;
        }

        @Override
        public void setTime(float dayTimeFraction) {
            time = dayTimeFraction;
        }

        @Override
        public void setWeather(String state) {
            weatherState = state;
        }

        @Override
        public void newTestWorld(long seed) {
            this.seed = seed;
        }

        boolean flatWorld;
        String spawnedSpecies;
        long spawnedSeed;

        @Override
        public void newFlatTestWorld() {
            flatWorld = true;
        }

        String loadedFolder;
        boolean throwOnLoad;

        @Override
        public void loadTestWorld(String folderName) {
            if (throwOnLoad) throw new IllegalStateException("no saved world folder named " + folderName);
            loadedFolder = folderName;
        }

        @Override
        public void spawnTree(String species, int x, int y, int z, long seed) {
            if (species.equals("NOPE")) throw new IllegalArgumentException("No enum constant");
            spawnedSpecies = species;
            spawnedSeed = seed;
        }

        @Override
        public void setBlock(int x, int y, int z, String blockTypeName) {
            if (throwOnSetBlock) throw new IllegalArgumentException("unknown block type: " + blockTypeName);
            setBlockX = x;
            setBlockY = y;
            setBlockZ = z;
            setBlockType = blockTypeName;
        }

        @Override
        public void selectSlot(int index) {
            if (throwOnSelectSlot) throw new IllegalArgumentException("slot index out of range: " + index);
            selectedSlot = index;
        }

        @Override
        public int giveItem(String blockTypeName, int count) {
            if (throwOnGive) throw new IllegalArgumentException("unknown block type: " + blockTypeName);
            givenType = blockTypeName;
            givenCount = count;
            return count;
        }

        @Override
        public void holdAttack(boolean held) {
            attackHeld = held;
        }

        @Override
        public void useItem() {
            useCalls++;
        }

        @Override
        public void lookAt(double x, double y, double z) {
            if (throwOnLook) throw new IllegalArgumentException("target is at the eye position");
            lookX = x;
            lookY = y;
            lookZ = z;
        }

        @Override
        public void openInventoryScreen(int gridSize) {
            if (throwOnOpen) throw new IllegalStateException("not in game");
            openedGrid = gridSize;
        }

        @Override
        public void inventoryClick(int slot, boolean rightButton) {
            if (throwOnClick) throw new IllegalArgumentException("no such slot: " + slot);
            clickedSlot = slot;
            clickedRight = rightButton;
        }

        @Override
        public void closeInventoryScreen() {
            if (throwOnClose) throw new IllegalStateException("inventory is not open");
            closeCalls++;
        }

        @Override
        public String captureScreenshot(String name) throws IOException {
            if (throwOnScreenshot) throw new IOException("disk full");
            screenshotName = name;
            return "test-output/screenshots/" + name + ".png";
        }

        Float damagedAmount;
        String damagedSource;
        boolean throwOnDamage;
        Float healedAmount;
        boolean throwOnHeal;
        int respawnCalls;
        boolean throwOnRespawn;
        String difficultyLevel;
        boolean throwOnDifficulty;
        Float hungerFood, hungerSaturation, hungerExhaustion;
        boolean throwOnHunger;

        @Override
        public void damage(float amount, String source) {
            if (throwOnDamage) throw new IllegalArgumentException("unknown damage source: " + source);
            damagedAmount = amount;
            damagedSource = source;
        }

        @Override
        public void heal(float amount) {
            if (throwOnHeal) throw new IllegalStateException("no active world");
            healedAmount = amount;
        }

        @Override
        public void respawn() {
            if (throwOnRespawn) throw new IllegalStateException("no active world");
            respawnCalls++;
        }

        @Override
        public void setDifficulty(String level) {
            if (throwOnDifficulty) throw new IllegalArgumentException("unknown difficulty: " + level);
            difficultyLevel = level;
        }

        @Override
        public void setHunger(float foodLevel, float saturation, float exhaustion) {
            if (throwOnHunger) throw new IllegalStateException("no active world");
            hungerFood = foodLevel;
            hungerSaturation = saturation;
            hungerExhaustion = exhaustion;
        }

        @Override
        public String stateSnapshot() {
            return "player.position=1.00,2.00,3.00\n";
        }

        @Override
        public void shutdown() {
            shutdownCalled = true;
        }
    }

    private FakeGame fake;
    private Commands commands;

    @BeforeEach
    void setUp() {
        fake = new FakeGame();
        commands = new Commands(fake);
    }

    private Map<String, String> params(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void unknownCommandIsRejected() {
        Commands.Result r = commands.execute("doTheThing", params());
        assertEquals(400, r.status());
        assertTrue(r.body().contains("Unknown command"));
    }

    @Test
    void teleportDispatchesWithAllFields() {
        Commands.Result r = commands.execute("teleport", params("x", "1.5", "y", "70", "z", "-3", "yaw", "90", "pitch", "-10"));
        assertEquals(200, r.status());
        assertEquals(1.5f, fake.tpX);
        assertEquals(70f, fake.tpY);
        assertEquals(-3f, fake.tpZ);
        assertEquals(90f, fake.tpYaw);
        assertEquals(-10f, fake.tpPitch);
    }

    @Test
    void teleportWithoutYawOrPitchLeavesThemNull() {
        commands.execute("teleport", params("x", "1", "y", "2", "z", "3"));
        assertNull(fake.tpYaw);
        assertNull(fake.tpPitch);
    }

    @Test
    void teleportMissingCoordinateIsRejectedAsBadRequest() {
        Commands.Result r = commands.execute("teleport", params("x", "1", "y", "2"));
        assertEquals(400, r.status());
        assertNull(fake.tpX, "game must not be touched when validation fails");
    }

    @Test
    void teleportWithNonNumericCoordinateIsRejected() {
        Commands.Result r = commands.execute("teleport", params("x", "not-a-number", "y", "2", "z", "3"));
        assertEquals(400, r.status());
        assertNull(fake.tpX);
    }

    @Test
    void setTimeAcceptsValueInRange() {
        Commands.Result r = commands.execute("setTime", params("time", "0.25"));
        assertEquals(200, r.status());
        assertEquals(0.25f, fake.time);
    }

    @Test
    void setTimeRejectsOutOfRangeValue() {
        Commands.Result r = commands.execute("setTime", params("time", "1.5"));
        assertEquals(400, r.status());
        assertNull(fake.time);
    }

    @Test
    void setTimeRejectsMissingParam() {
        Commands.Result r = commands.execute("setTime", params());
        assertEquals(400, r.status());
    }

    @Test
    void setWeatherAcceptsAndNormalizesValidState() {
        Commands.Result r = commands.execute("setWeather", params("state", "thunder"));
        assertEquals(200, r.status());
        assertEquals("THUNDER", fake.weatherState);
    }

    @Test
    void setWeatherRejectsInvalidState() {
        Commands.Result r = commands.execute("setWeather", params("state", "hurricane"));
        assertEquals(400, r.status());
        assertNull(fake.weatherState);
    }

    @Test
    void newTestWorldUsesProvidedSeed() {
        commands.execute("newTestWorld", params("seed", "42"));
        assertEquals(42L, fake.seed);
    }

    @Test
    void newTestWorldFlatTypeStartsAFlatWorld() {
        assertEquals(200, commands.execute("newTestWorld", params("type", "flat")).status());
        assertTrue(fake.flatWorld);
    }

    @Test
    void spawnTreeDispatchesAndValidates() {
        assertEquals(200, commands.execute("spawnTree", params("species", "BIRCH", "x", "8", "y", "4", "z", "8", "seed", "5")).status());
        assertEquals("BIRCH", fake.spawnedSpecies);
        assertEquals(5L, fake.spawnedSeed);
        assertEquals(400, commands.execute("spawnTree", params("species", "BIRCH", "x", "8")).status());
        assertEquals(400, commands.execute("spawnTree", params("species", "NOPE", "x", "8", "y", "4", "z", "8")).status());
    }

    @Test
    void newTestWorldWithoutSeedStillDispatches() {
        Commands.Result r = commands.execute("newTestWorld", params());
        assertEquals(200, r.status());
        assertNotNull(fake.seed, "must fall back to some seed rather than skip the call");
    }

    @Test
    void setBlockDispatchesWithAllFields() {
        Commands.Result r = commands.execute("setBlock", params("x", "8", "y", "70", "z", "8", "type", "oak_sapling_stage_0"));
        assertEquals(200, r.status());
        assertEquals(8, fake.setBlockX);
        assertEquals(70, fake.setBlockY);
        assertEquals(8, fake.setBlockZ);
        assertEquals("oak_sapling_stage_0", fake.setBlockType);
    }

    @Test
    void setBlockRejectsMissingCoordinate() {
        Commands.Result r = commands.execute("setBlock", params("x", "8", "y", "70", "type", "STONE"));
        assertEquals(400, r.status());
        assertNull(fake.setBlockX);
    }

    @Test
    void setBlockRejectsMissingType() {
        Commands.Result r = commands.execute("setBlock", params("x", "8", "y", "70", "z", "8"));
        assertEquals(400, r.status());
        assertNull(fake.setBlockType);
    }

    @Test
    void setBlockRejectsNonIntegerCoordinate() {
        Commands.Result r = commands.execute("setBlock", params("x", "not-a-number", "y", "70", "z", "8", "type", "STONE"));
        assertEquals(400, r.status());
        assertNull(fake.setBlockX);
    }

    @Test
    void setBlockUnknownTypeBecomesA400NotACrash() {
        fake.throwOnSetBlock = true;
        Commands.Result r = commands.execute("setBlock", params("x", "8", "y", "70", "z", "8", "type", "not_a_real_block"));
        assertEquals(400, r.status());
    }

    @Test
    void selectSlotDispatchesWithIndex() {
        Commands.Result r = commands.execute("selectSlot", params("index", "3"));
        assertEquals(200, r.status());
        assertEquals(3, fake.selectedSlot);
    }

    @Test
    void selectSlotRejectsMissingIndex() {
        Commands.Result r = commands.execute("selectSlot", params());
        assertEquals(400, r.status());
        assertNull(fake.selectedSlot);
    }

    @Test
    void selectSlotRejectsNonIntegerIndex() {
        Commands.Result r = commands.execute("selectSlot", params("index", "not-a-number"));
        assertEquals(400, r.status());
        assertNull(fake.selectedSlot);
    }

    @Test
    void selectSlotOutOfRangeBecomesA400NotACrash() {
        fake.throwOnSelectSlot = true;
        Commands.Result r = commands.execute("selectSlot", params("index", "999"));
        assertEquals(400, r.status());
    }

    @Test
    void giveItemDispatchesAndReportsHowManyFit() {
        Commands.Result r = commands.execute("giveItem", params("type", "stone", "count", "5"));
        assertEquals(200, r.status());
        assertEquals("stone", fake.givenType);
        assertEquals(5, fake.givenCount);
    }

    @Test
    void giveItemRejectsMissingOrInvalidParamsAndGameErrors() {
        assertEquals(400, commands.execute("giveItem", params("type", "stone")).status());
        assertEquals(400, commands.execute("giveItem", params("count", "5")).status());
        assertEquals(400, commands.execute("giveItem", params("type", "stone", "count", "many")).status());
        assertNull(fake.givenType);
        fake.throwOnGive = true;
        assertEquals(400, commands.execute("giveItem", params("type", "nope", "count", "1")).status());
    }

    @Test
    void holdAttackAcceptsOnlyTrueOrFalse() {
        assertEquals(200, commands.execute("holdAttack", params("held", "true")).status());
        assertEquals(true, fake.attackHeld);
        assertEquals(200, commands.execute("holdAttack", params("held", "FALSE")).status());
        assertEquals(false, fake.attackHeld);
        assertEquals(400, commands.execute("holdAttack", params("held", "maybe")).status());
        assertEquals(400, commands.execute("holdAttack", params()).status());
    }

    @Test
    void useItemQueuesOneClickPerCall() {
        commands.execute("useItem", params());
        commands.execute("useItem", params());
        assertEquals(2, fake.useCalls);
    }

    @Test
    void lookAtDispatchesAndRejectsBadInput() {
        assertEquals(200, commands.execute("lookAt", params("x", "1", "y", "2", "z", "3")).status());
        assertEquals(1.0, fake.lookX);
        assertEquals(400, commands.execute("lookAt", params("x", "1", "y", "2")).status());
        fake.throwOnLook = true;
        assertEquals(400, commands.execute("lookAt", params("x", "1", "y", "2", "z", "3")).status());
    }

    @Test
    void openInventoryDefaultsToTwoByTwoAcceptsThreeAndRejectsOtherSizes() {
        assertEquals(200, commands.execute("openInventory", params()).status());
        assertEquals(2, fake.openedGrid);
        assertEquals(200, commands.execute("openInventory", params("grid", "3")).status());
        assertEquals(3, fake.openedGrid);
        assertEquals(400, commands.execute("openInventory", params("grid", "4")).status());
        assertEquals(400, commands.execute("openInventory", params("grid", "x")).status());
        fake.throwOnOpen = true;
        assertEquals(400, commands.execute("openInventory", params()).status(), "not in game must be a clean 400");
    }

    @Test
    void inventoryClickParsesSlotAndButtonAndReportsBadSlots() {
        assertEquals(200, commands.execute("inventoryClick", params("slot", "5", "button", "right")).status());
        assertEquals(5, fake.clickedSlot);
        assertEquals(true, fake.clickedRight);
        assertEquals(200, commands.execute("inventoryClick", params("slot", "1000")).status());
        assertEquals(false, fake.clickedRight, "button defaults to left");
        assertEquals(400, commands.execute("inventoryClick", params("button", "left")).status());
        assertEquals(400, commands.execute("inventoryClick", params("slot", "2", "button", "middle")).status());
        fake.throwOnClick = true;
        assertEquals(400, commands.execute("inventoryClick", params("slot", "999")).status());
    }

    @Test
    void closeInventoryDispatchesAndReportsWhenNothingIsOpen() {
        assertEquals(200, commands.execute("closeInventory", params()).status());
        assertEquals(1, fake.closeCalls);
        fake.throwOnClose = true;
        assertEquals(400, commands.execute("closeInventory", params()).status());
    }

    @Test
    void screenshotRejectsMissingName() {
        Commands.Result r = commands.execute("screenshot", params());
        assertEquals(400, r.status());
        assertNull(fake.screenshotName);
    }

    @Test
    void screenshotRejectsNameWithPathTraversalCharacters() {
        Commands.Result r = commands.execute("screenshot", params("name", "../../etc/passwd"));
        assertEquals(400, r.status());
        assertNull(fake.screenshotName, "must never reach the filesystem with an unsafe name");
    }

    @Test
    void screenshotAcceptsSafeNameAndDispatches() {
        Commands.Result r = commands.execute("screenshot", params("name", "biome_ocean_01"));
        assertEquals(200, r.status());
        assertEquals("biome_ocean_01", fake.screenshotName);
    }

    @Test
    void screenshotIoFailureBecomesA500NotACrash() {
        fake.throwOnScreenshot = true;
        Commands.Result r = commands.execute("screenshot", params("name", "will_fail"));
        assertEquals(500, r.status());
    }

    @Test
    void stateReturnsTheSnapshotVerbatim() {
        Commands.Result r = commands.execute("state", params());
        assertEquals(200, r.status());
        assertEquals(fake.stateSnapshot(), r.body());
    }

    @Test
    void shutdownDispatches() {
        Commands.Result r = commands.execute("shutdown", params());
        assertEquals(200, r.status());
        assertTrue(fake.shutdownCalled);
    }

    @Test
    void damageDispatchesAndValidates() {
        assertEquals(200, commands.execute("damage", params("amount", "5", "source", "FALL")).status());
        assertEquals(5f, fake.damagedAmount);
        assertEquals("FALL", fake.damagedSource);
        assertEquals(400, commands.execute("damage", params("source", "FALL")).status(), "missing amount");
        assertEquals(400, commands.execute("damage", params("amount", "5")).status(), "missing source");
        fake.throwOnDamage = true;
        assertEquals(400, commands.execute("damage", params("amount", "5", "source", "NOPE")).status());
    }

    @Test
    void healDispatchesAndValidates() {
        assertEquals(200, commands.execute("heal", params("amount", "3")).status());
        assertEquals(3f, fake.healedAmount);
        assertEquals(400, commands.execute("heal", params()).status());
        fake.throwOnHeal = true;
        assertEquals(400, commands.execute("heal", params("amount", "1")).status());
    }

    @Test
    void respawnDispatches() {
        assertEquals(200, commands.execute("respawn", params()).status());
        assertEquals(1, fake.respawnCalls);
        fake.throwOnRespawn = true;
        assertEquals(400, commands.execute("respawn", params()).status());
    }

    @Test
    void setDifficultyDispatchesAndValidates() {
        assertEquals(200, commands.execute("setDifficulty", params("level", "HARD")).status());
        assertEquals("HARD", fake.difficultyLevel);
        assertEquals(400, commands.execute("setDifficulty", params()).status());
        fake.throwOnDifficulty = true;
        assertEquals(400, commands.execute("setDifficulty", params("level", "NOPE")).status());
    }

    @Test
    void loadTestWorldDispatchesAndValidates() {
        assertEquals(200, commands.execute("loadTestWorld", params("folder", "My World")).status());
        assertEquals("My World", fake.loadedFolder);
        assertEquals(400, commands.execute("loadTestWorld", params()).status());
        fake.throwOnLoad = true;
        assertEquals(400, commands.execute("loadTestWorld", params("folder", "Nope")).status());
    }

    @Test
    void setHungerDispatchesAndValidates() {
        assertEquals(200, commands.execute("setHunger", params("food", "0", "saturation", "0", "exhaustion", "3.9")).status());
        assertEquals(0f, fake.hungerFood);
        assertEquals(0f, fake.hungerSaturation);
        assertEquals(3.9f, fake.hungerExhaustion);
        assertEquals(400, commands.execute("setHunger", params("food", "0", "saturation", "0")).status(), "missing exhaustion");
        fake.throwOnHunger = true;
        assertEquals(400, commands.execute("setHunger", params("food", "0", "saturation", "0", "exhaustion", "0")).status());
    }
}
