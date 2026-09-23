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

        @Override
        public String captureScreenshot(String name) throws IOException {
            if (throwOnScreenshot) throw new IOException("disk full");
            screenshotName = name;
            return "test-output/screenshots/" + name + ".png";
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
    void newTestWorldWithoutSeedStillDispatches() {
        Commands.Result r = commands.execute("newTestWorld", params());
        assertEquals(200, r.status());
        assertNotNull(fake.seed, "must fall back to some seed rather than skip the call");
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
}
