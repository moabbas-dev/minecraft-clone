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
        // Drive until we observe at least one lightning strike (flash > 0), which only happens
        // during THUNDER - a long run makes this a near-certainty without hardcoding a seed's
        // exact schedule.
        boolean sawFlash = false;
        for (int i = 0; i < (int) (72000 / DT); i++) {
            w.update(DT);
            if (w.getLightningFlash() > 0f) {
                sawFlash = true;
                break;
            }
        }
        assertTrue(sawFlash, "expected at least one lightning flash over a long simulated run");

        // Now step forward with no further state changes possible within this short window and
        // confirm the flash decays to exactly 0, never negative, never stuck above 0 forever.
        for (int i = 0; i < 20; i++) {
            w.update(DT);
        }
        assertEquals(0f, w.getLightningFlash(), 0f);
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
        }
    }
}
