package com.example.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sun's path and the sky, fog, cloud and sunrise colours, against the numbers in the 26.3 client's day timeline
 * ({@code data/minecraft/timeline/day.json}, {@code WeatherAttributes}). All pure maths, no GL.
 */
class DayNightTest {
    private static final float[] PLAINS_SKY = SkyColors.rgb(0x78A7FF);
    private static final float[] FOG = SkyColors.rgb(SkyColors.OVERWORLD_FOG);

    // ---- the sun ----

    @Test
    void theSunIsStraightUpAtNoonAndStraightDownAtMidnight() {
        assertEquals(1f, Game.sunDirection(0.5f).y, 1e-4f);
        assertEquals(-1f, Game.sunDirection(0f).y, 1e-4f);
        assertEquals(0f, SkyDetails.sunAngleDegrees(6000f), 1e-3f);
        assertEquals(180f, SkyDetails.sunAngleDegrees(18000f), 1e-3f);
    }

    @Test
    void theSunCrossesTheHorizonWhereTheTimelineSwitchesMonstersOff() {
        // sunset: angle 90 near tick 12542 (monsters_burn turns false); sunrise: angle 270 near tick 23460 (turns true)
        assertEquals(90f, SkyDetails.sunAngleDegrees(12542f), 6f);
        assertEquals(270f, SkyDetails.sunAngleDegrees(23460f), 6f);
    }

    @Test
    void theSunRisesInPlusXAndSetsInMinusX() {
        Vector3f dawn = Game.sunDirection(SkyDetailsAccess.dayTimeOfTick(23460f));
        Vector3f dusk = Game.sunDirection(SkyDetailsAccess.dayTimeOfTick(12542f));
        assertTrue(dawn.x > 0.95f && Math.abs(dawn.y) < 0.1f, "sunrise in the east: " + dawn);
        assertTrue(dusk.x < -0.95f && Math.abs(dusk.y) < 0.1f, "sunset in the west: " + dusk);
    }

    @Test
    void theSunAngleNeverRunsBackwards() {
        float last = -1f;
        for (float tick = 6000f; tick <= 30000f; tick += 50f) {
            float a = SkyDetails.sunAngleDegrees(tick);
            float unwrapped = tick >= 30000f ? a + 360f : a;
            assertTrue(unwrapped >= last - 1e-3f || a < 1f, "angle went backwards at tick " + tick);
            last = a < 1f ? -1f : unwrapped;
        }
    }

    @Test
    void sunDirectionIsAlwaysNormalized() {
        for (float dayTime = 0f; dayTime < 1f; dayTime += 0.01f) {
            assertEquals(1.0f, Game.sunDirection(dayTime).length(), 0.001f, "sun direction must be a unit vector at dayTime=" + dayTime);
        }
    }

    // ---- sky and fog colours ----

    @Test
    void theSkyIsTheBiomeColourByDayAndBlackAtNight() {
        Vector3f noon = SkyColors.sky(PLAINS_SKY, 6000f, 0f, 0f, false);
        assertArrayEquals(PLAINS_SKY, new float[]{noon.x, noon.y, noon.z}, 1e-5f);
        Vector3f midnight = SkyColors.sky(PLAINS_SKY, 18000f, 0f, 0f, false);
        assertEquals(0f, midnight.length(), 1e-6f);
        // between the keyframes it fades linearly: half way down 11867 -> 13670
        Vector3f dusk = SkyColors.sky(PLAINS_SKY, (11867f + 13670f) / 2f, 0f, 0f, false);
        assertEquals(PLAINS_SKY[2] * 0.5f, dusk.z, 1e-3f);
    }

    @Test
    void theSkyColourIsPeriodicOverTheDay() {
        Vector3f start = SkyColors.sky(PLAINS_SKY, 0f, 0f, 0f, false);
        Vector3f end = SkyColors.sky(PLAINS_SKY, 23999.9f, 0f, 0f, false);
        assertTrue(start.distance(end) < 0.01f);
    }

    @Test
    void rainBlendsTheSkyTowardGreyAndThunderMoreSo() {
        Vector3f clear = SkyColors.sky(PLAINS_SKY, 6000f, 0f, 0f, false);
        Vector3f rain = SkyColors.sky(PLAINS_SKY, 6000f, 1f, 0f, false);
        Vector3f thunder = SkyColors.sky(PLAINS_SKY, 6000f, 1f, 1f, false);
        // BlendToGray(0.6, 0.75): lerp(0.75, colour, grey * 0.6), grey = 0.3 r + 0.59 g + 0.11 b
        float grey = (clear.x * 0.3f + clear.y * 0.59f + clear.z * 0.11f) * 0.6f;
        assertEquals(clear.x + (grey - clear.x) * 0.75f, rain.x, 1e-4f);
        assertTrue(thunder.length() < rain.length(), "thunder is darker than rain");
        // half the rain level: half way between clear and the full effect
        Vector3f half = SkyColors.sky(PLAINS_SKY, 6000f, 0.5f, 0f, false);
        assertEquals((clear.x + rain.x) / 2f, half.x, 1e-4f);
    }

    @Test
    void aLightningFlashLiftsTheSkyTwentyTwoPercentTowardBlueWhite() {
        Vector3f plain = SkyColors.sky(PLAINS_SKY, 18000f, 0f, 0f, false);
        Vector3f flash = SkyColors.sky(PLAINS_SKY, 18000f, 0f, 0f, true);
        assertEquals(0.22f * 0.8f, flash.x, 1e-5f);
        assertEquals(0.22f * 1f, flash.z, 1e-5f);
        assertEquals(0f, plain.length(), 1e-6f);
    }

    @Test
    void theFogIsTheDimensionColourByDayAndDarkBlueAtNight() {
        Vector3f noon = SkyColors.fog(FOG, 6000f, 0f, 0f);
        assertArrayEquals(FOG, new float[]{noon.x, noon.y, noon.z}, 1e-5f);
        Vector3f night = SkyColors.fog(FOG, 18000f, 0f, 0f);
        assertEquals(FOG[0] * 0x0F / 255f, night.x, 1e-5f);
        assertEquals(FOG[2] * 0x16 / 255f, night.z, 1e-5f);
        Vector3f rain = SkyColors.fog(FOG, 6000f, 1f, 0f);
        assertEquals(FOG[0] * 0.5f, rain.x, 1e-5f);
        assertEquals(FOG[2] * 0.6f, rain.z, 1e-5f);
    }

    @Test
    void theFogTakesOnTheSunriseColourWhenLookingTowardTheSun() {
        Vector3f fog = SkyColors.fog(FOG, 71f, 0f, 0f);
        Vector3f sky = SkyColors.sky(PLAINS_SKY, 71f, 0f, 0f, false);
        float[] glow = SkyColors.sunrise(71f, 0f, 0f);
        assertEquals(0x5F / 255f, glow[3], 1e-4f, "alpha at tick 71");
        float angle = SkyDetails.sunAngleDegrees(71f);
        Vector3f east = SkyColors.fogForView(fog, sky, glow, angle, 1f, 12, 0f, 0f);
        Vector3f west = SkyColors.fogForView(fog, sky, glow, angle, -1f, 12, 0f, 0f);
        assertTrue(east.x > west.x, "facing the rising sun (+x) is redder than facing away: " + east + " vs " + west);
    }

    @Test
    void theSunriseGlowIsStrongestAtTheHorizonAndGoneAtNoon() {
        assertEquals(0f, SkyColors.sunrise(6000f, 0f, 0f)[3], 1e-6f);
        assertEquals(0f, SkyColors.sunrise(18000f, 0f, 0f)[3], 1e-6f);
        assertEquals(0xB1 / 255f, SkyColors.sunrise(12243f, 0f, 0f)[3], 1e-4f);
        assertEquals(0xFE / 255f, SkyColors.sunrise(12732f, 0f, 0f)[3], 1e-4f, "the strongest dusk keyframe");
        float[] rain = SkyColors.sunrise(12732f, 1f, 0f);
        assertEquals(0xDA / 255f * 0.5f, rain[0], 1e-4f, "rain multiplies the colour by (0.5, 0.5, 0.6)");
    }

    @Test
    void theCloudColourIsWhiteByDayAndDarkBlueAtNightAndGreyInRain() {
        assertEquals(1f, SkyColors.cloud(6000f, 0f, 0f).x, 1e-6f);
        Vector3f night = SkyColors.cloud(18000f, 0f, 0f);
        assertEquals(0x19 / 255f, night.x, 1e-5f);
        assertEquals(0x26 / 255f, night.z, 1e-5f);
        Vector3f rain = SkyColors.cloud(6000f, 1f, 0f);
        assertEquals(0.5f + 0.5f * 0.24f, rain.x, 1e-4f, "BlendToGray(0.24, 0.5) of white: lerp(0.5, 1, 0.24)");
    }

    @Test
    void theFogMixesTowardTheSkyByTheRenderDistanceFactor() {
        Vector3f fog = new Vector3f(1f, 1f, 1f);
        Vector3f sky = new Vector3f(0f, 0f, 0f);
        float[] none = {0f, 0f, 0f, 0f};
        // 12 chunks: f = 0.25 + 0.75 * 12 / 32 = 0.53125, mix = 1 - f^0.25
        float expected = 1f - (float) Math.pow(0.53125, 0.25);
        assertEquals(1f - expected, SkyColors.fogForView(fog, sky, none, 0f, 0f, 12, 0f, 0f).x, 1e-4f);
        // closer than 4 chunks the sunrise tint is skipped but the sky mix still applies
        assertTrue(SkyColors.fogForView(fog, sky, none, 0f, 0f, 2, 0f, 0f).x < 1f);
    }

    // ---- fog under water ----

    @Test
    void waterVisionGrowsWithTheTimeSpentUnderwater() {
        assertEquals(0f, Game.waterVision(0f, false));
        assertEquals(0f, Game.waterVision(0f, true), 1e-6f);
        assertEquals(0.6f, Game.waterVision(100f, true), 1e-5f);
        assertEquals(1f, Game.waterVision(600f, true), 1e-6f);
        assertEquals(1f, Game.waterVision(5000f, true), 1e-6f);
        Vector3f c = Game.brightenByVision(new Vector3f(0.02f, 0.02f, 0.2f), 1f);
        assertEquals(1f, c.z, 1e-5f, "full vision brightens the colour to its own maximum");
        assertEquals(0.1f, c.x, 1e-5f);
    }
}
