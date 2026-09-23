package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test: the sun/moon used to render at a hardcoded full alpha regardless of
 * weather, while the sky color and terrain lighting both darkened for a storm - a bright,
 * fully-lit disc sitting on top of a dark stormy sky. Vanilla doesn't literally hide the sun
 * during rain, but it does dim along with everything else; this is what Game.celestialAlpha
 * now provides.
 */
class CelestialWeatherTest {

    @Test
    void fullyVisibleInClearWeather() {
        assertEquals(1f, Game.celestialAlpha(false, false));
    }

    @Test
    void dimmedDuringRain() {
        float alpha = Game.celestialAlpha(true, false);
        assertTrue(alpha < 1f, "should be dimmer than clear weather");
        assertTrue(alpha > 0f, "should still be present, not literally erased - vanilla doesn't hide it either");
    }

    @Test
    void dimmerDuringThunderThanPlainRain() {
        float rain = Game.celestialAlpha(true, false);
        float thunder = Game.celestialAlpha(true, true);
        assertTrue(thunder < rain, "a thunderstorm should obscure it more than light rain");
    }

    @Test
    void thunderingImpliesTheRainDimAppliesRegardlessOfTheRainingFlag() {
        // isThundering() always implies isRaining() in Weather, but the formula shouldn't
        // depend on that invariant holding - thundering alone must still dim it.
        assertEquals(Game.celestialAlpha(true, true), Game.celestialAlpha(false, true));
    }
}
