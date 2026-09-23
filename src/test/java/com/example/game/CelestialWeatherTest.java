package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test: the sun/moon used to render at a hardcoded full alpha regardless of
 * weather, while the sky color and terrain lighting both darkened for a storm - a bright,
 * fully-lit disc sitting on top of a dark stormy sky. Vanilla doesn't literally hide the sun
 * during rain, but it does dim along with everything else; this is what Game.celestialAlpha
 * now provides. Driven by the eased storm intensity (not an instant raining/not-raining flag)
 * so it fades along with the rest of the atmosphere instead of snapping.
 */
class CelestialWeatherTest {

    @Test
    void fullyVisibleAtZeroStormIntensity() {
        assertEquals(1f, Game.celestialAlpha(0f, false));
        assertEquals(1f, Game.celestialAlpha(0f, true), "zero intensity means fully visible regardless of which state is 'current'");
    }

    @Test
    void dimmedAtFullRainIntensity() {
        float alpha = Game.celestialAlpha(1f, false);
        assertTrue(alpha < 1f, "should be dimmer than clear weather");
        assertTrue(alpha > 0f, "should still be present, not literally erased - vanilla doesn't hide it either");
    }

    @Test
    void dimmerAtFullThunderIntensityThanFullRainIntensity() {
        float rain = Game.celestialAlpha(1f, false);
        float thunder = Game.celestialAlpha(1f, true);
        assertTrue(thunder < rain, "a thunderstorm should obscure it more than light rain");
    }

    @Test
    void easesLinearlyWithIntensityRatherThanSnapping() {
        float quarter = Game.celestialAlpha(0.25f, false);
        float half = Game.celestialAlpha(0.5f, false);
        float full = Game.celestialAlpha(1f, false);
        assertTrue(quarter > half && half > full, "alpha should decrease monotonically as intensity rises");
        // Strictly between the endpoints, not already at the full-intensity value partway through.
        assertTrue(half < 1f && half > full);
    }

    @Test
    void intensityIsClampedToValidRange() {
        assertEquals(Game.celestialAlpha(1f, false), Game.celestialAlpha(1.5f, false),
                "intensity above 1 must not overshoot past the full-storm alpha");
        assertEquals(Game.celestialAlpha(0f, false), Game.celestialAlpha(-0.5f, false),
                "intensity below 0 must not undershoot past fully visible");
    }
}
