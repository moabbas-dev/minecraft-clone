package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sun, moon and stars fade with the rain exactly as SkyRenderer does it: their brightness is {@code 1 - rain level}
 * (rain and thunder do not differ for them; the sky itself is what gets darker, see {@link SkyColors}).
 */
class CelestialWeatherTest {

    @Test
    void fullyVisibleWithoutRain() {
        assertEquals(1f, Game.celestialAlpha(0f));
    }

    @Test
    void goneAtFullRainLevel() {
        assertEquals(0f, Game.celestialAlpha(1f));
    }

    @Test
    void fadesLinearlyWithTheRainLevel() {
        assertEquals(0.75f, Game.celestialAlpha(0.25f), 1e-6f);
        assertEquals(0.5f, Game.celestialAlpha(0.5f), 1e-6f);
    }

    @Test
    void theLevelIsClampedToTheValidRange() {
        assertEquals(Game.celestialAlpha(1f), Game.celestialAlpha(1.5f));
        assertEquals(Game.celestialAlpha(0f), Game.celestialAlpha(-0.5f));
    }
}
