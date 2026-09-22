package com.example.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link Game}'s pure day/night helpers directly - they take only primitives/
 * {@link Vector3f} and touch no GL/GLFW state, so no window is needed. Package-private (not
 * public) on purpose: this test class lives in the same package specifically so it can reach
 * them without widening their real visibility.
 */
class DayNightTest {

    @Test
    void daylightFactorNeverDropsBelowTheDocumentedNightFloor() {
        // This is the exact regression case from the "world goes black at night" fix earlier
        // in this project: before that fix, daylightFactor could reach 0.0 and every block with
        // no nearby light source went fully black.
        for (float dayTime = 0f; dayTime < 1f; dayTime += 0.001f) {
            Vector3f sunDir = Game.sunDirection(dayTime);
            float factor = Game.computeDaylightFactor(sunDir);
            assertTrue(factor >= Game.MIN_NIGHT_DAYLIGHT - 1e-6f,
                    "daylightFactor fell below the night floor at dayTime=" + dayTime + ": " + factor);
        }
    }

    @Test
    void daylightFactorNeverExceedsFullBrightness() {
        for (float dayTime = 0f; dayTime < 1f; dayTime += 0.001f) {
            float factor = Game.computeDaylightFactor(Game.sunDirection(dayTime));
            assertTrue(factor <= 1.0001f, "daylightFactor exceeded 1.0 at dayTime=" + dayTime + ": " + factor);
        }
    }

    @Test
    void daylightFactorIsHighestAtNoonAndAtItsFloorAtMidnight() {
        float noon = Game.computeDaylightFactor(Game.sunDirection(0.5f));
        float midnight = Game.computeDaylightFactor(Game.sunDirection(0.0f));
        assertTrue(noon > midnight, "noon should be brighter than midnight");
        assertEquals(1.0f, noon, 0.01f, "noon should be at or very near full daylight");
        assertEquals(Game.MIN_NIGHT_DAYLIGHT, midnight, 0.02f, "midnight should sit at the documented night floor");
    }

    @Test
    void skyColorHasNoDiscontinuityAtAnySegmentBoundary() {
        float[] boundaries = {0.22f, 0.30f, 0.68f, 0.78f, 0.85f};
        float eps = 1e-4f;
        for (float b : boundaries) {
            Vector3f just_before = Game.computeSkyColor(b - eps);
            Vector3f just_after = Game.computeSkyColor(b + eps);
            float dist = just_before.distance(just_after);
            assertTrue(dist < 0.01f,
                    "sky color jumps discontinuously at dayTime boundary " + b + ": " + dist);
        }
    }

    @Test
    void skyColorAtDayIsTheConfiguredDaytimeColor() {
        Vector3f noon = Game.computeSkyColor(0.5f);
        assertEquals(0.53f, noon.x, 0.001f);
        assertEquals(0.81f, noon.y, 0.001f);
        assertEquals(0.92f, noon.z, 0.001f);
    }

    @Test
    void skyColorAtMidnightAndWrappedEndOfDayAgree() {
        Vector3f start = Game.computeSkyColor(0.0f);
        Vector3f end = Game.computeSkyColor(0.999999f);
        assertTrue(start.distance(end) < 0.01f,
                "the day/night cycle should be periodic - start and end of the cycle should match");
    }

    @Test
    void sunDirectionIsAlwaysNormalized() {
        for (float dayTime = 0f; dayTime < 1f; dayTime += 0.01f) {
            Vector3f dir = Game.sunDirection(dayTime);
            assertEquals(1.0f, dir.length(), 0.001f, "sun direction must be a unit vector at dayTime=" + dayTime);
        }
    }
}
