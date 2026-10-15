package com.example.world.integration;

import com.example.world.Weather;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fixed-seed, fixed-dt stepping only - no wall-clock time, so this is fully deterministic. */
class WeatherTest {

    private static final float DT = 0.25f;

    @Test
    void startsClearAndNotRaining() {
        Weather w = new Weather(1L);
        assertEquals(Weather.State.CLEAR, w.getState());
        assertFalse(w.isRaining());
        assertFalse(w.isThundering());
    }

    @Test
    void eventuallyReachesEveryState() {
        Weather w = new Weather(2L);
        boolean sawRain = false, sawThunder = false, sawClearAfterStart = false;
        Weather.State last = w.getState();
        // Long enough simulated duration (10 in-game hours) to statistically guarantee at least
        // one full clear->rain/thunder->clear cycle given the documented min/max durations.
        for (int i = 0; i < (int) (36000 / DT); i++) {
            w.update(DT);
            Weather.State s = w.getState();
            if (s == Weather.State.RAIN) sawRain = true;
            if (s == Weather.State.THUNDER) sawThunder = true;
            if (s == Weather.State.CLEAR && last != Weather.State.CLEAR) sawClearAfterStart = true;
            last = s;
        }
        assertTrue(sawRain || sawThunder, "weather should leave CLEAR at least once over a long simulated run");
        assertTrue(sawClearAfterStart, "weather should return to CLEAR after a rain/thunder period");
    }

    @Test
    void isRainingAndIsThunderingAlwaysAgreeWithGetState() {
        Weather w = new Weather(3L);
        for (int i = 0; i < 10000; i++) {
            w.update(DT);
            switch (w.getState()) {
                case CLEAR -> {
                    assertFalse(w.isRaining());
                    assertFalse(w.isThundering());
                }
                case RAIN -> {
                    assertTrue(w.isRaining());
                    assertFalse(w.isThundering());
                }
                case THUNDER -> {
                    assertTrue(w.isRaining());
                    assertTrue(w.isThundering());
                }
            }
        }
    }

    @Test
    void lightningFlashDecaysBackToZero() {
        Weather w = new Weather(4L);
        assertEquals(0f, w.getLightningFlash(), 0f, "no strike happens by itself: bolts are spawned by the game");
        w.strike();
        assertEquals(1f, w.getLightningFlash(), 0f);
        for (int i = 0; i < 20; i++) {
            w.update(DT);
        }
        assertEquals(0f, w.getLightningFlash(), 0f);
    }

    @Test
    void stormIntensityAlwaysStaysInZeroOneRange() {
        Weather w = new Weather(5L);
        for (int i = 0; i < 20000; i++) {
            w.update(DT);
            float intensity = w.getStormIntensity();
            assertTrue(intensity >= 0f && intensity <= 1f, "stormIntensity out of [0,1] at tick " + i + ": " + intensity);
        }
    }

    /**
     * Regression test for the actual "abrupt transition" bug renderWorld's storm blend used to
     * have: isRaining() is an instant flag flip the moment a state timer expires, and the old
     * code gated the storm blend directly on that flag. stormIntensity must ease toward its
     * target instead of jumping there in one tick.
     */
    @Test
    void stormIntensityRisesGraduallyNotInstantlyWhenRainStarts() {
        Weather w = new Weather(6L);
        boolean wasClear = true;
        for (int i = 0; i < 20000; i++) {
            boolean clearBefore = w.getState() == Weather.State.CLEAR;
            w.update(DT);
            boolean justStartedRaining = clearBefore && w.getState() != Weather.State.CLEAR;
            if (justStartedRaining) {
                assertTrue(w.getStormIntensity() < 0.5f,
                        "stormIntensity should still be low immediately after rain starts, not snapped to its target: " + w.getStormIntensity());
                return; // found and checked the transition - done
            }
        }
        fail("rain never started within the simulated window - cannot verify the transition is gradual");
    }

    @Test
    void stormIntensityApproachesOneDuringASustainedRainPeriod() {
        Weather w = new Weather(7L);
        int consecutiveWetTicks = 0;
        for (int i = 0; i < 40000; i++) {
            w.update(DT);
            consecutiveWetTicks = w.isRaining() ? consecutiveWetTicks + 1 : 0;
            // 5 simulated seconds into a rain period is well past this project's easing rate
            // reaching near-convergence, and well under the documented minimum rain duration
            // (60s), so any rain period gives enough room to observe this.
            if (consecutiveWetTicks * DT > 5f) {
                assertTrue(w.getStormIntensity() > 0.7f,
                        "stormIntensity should have mostly converged after 5s of sustained rain: " + w.getStormIntensity());
                return;
            }
        }
        fail("never observed a sustained rain period within the simulated window");
    }

    @Test
    void sameSeedProducesIdenticalStateSequence() {
        Weather a = new Weather(99L);
        Weather b = new Weather(99L);
        for (int i = 0; i < 5000; i++) {
            a.update(DT);
            b.update(DT);
            assertEquals(a.getState(), b.getState(), "tick " + i);
            assertEquals(a.getLightningFlash(), b.getLightningFlash(), 0f, "tick " + i);
            assertEquals(a.getStormIntensity(), b.getStormIntensity(), 0f, "tick " + i);
        }
    }
}
