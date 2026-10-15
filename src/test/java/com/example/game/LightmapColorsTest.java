package com.example.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LightmapColorsTest {
    private static LightmapColors.Params at(float tick, float rain, float thunder, float gamma) {
        return LightmapColors.params(tick, rain, thunder, 0f, gamma);
    }

    @Test
    void daytimeSkyLightIsFullBrightWhite() {
        float[] c = LightmapColors.texel(at(6000f, 0f, 0f, 0.5f), 0, 15);
        assertArrayEquals(new float[]{1f, 1f, 1f}, c, 1e-5f, "ambient 0.039 + 1.0 sky, clamped to 1");
    }

    @Test
    void nightSkyLightIsADimBlue() {
        float[] c = LightmapColors.texel(at(18000f, 0f, 0f, 0f), 0, 15);
        // ambient + 0.24 * #7a7aff before the brightness option (0 here)
        assertEquals(0x0a / 255f + 0.24f * 0x7a / 255f, c[0], 1e-4f);
        assertEquals(0x0a / 255f + 0.24f, c[2], 1e-4f);
        assertTrue(c[2] > c[0], "bluish moonlight");
    }

    @Test
    void noLightAtAllIsTheAmbientColourLiftedByTheBrightnessOption() {
        float[] moody = LightmapColors.texel(at(18000f, 0f, 0f, 0f), 0, 0);
        assertEquals(LightmapColors.AMBIENT, moody[0], 1e-6f);
        float[] bright = LightmapColors.texel(at(18000f, 0f, 0f, 1f), 0, 0);
        float inverted = 1f - LightmapColors.AMBIENT;
        float expected = LightmapColors.AMBIENT * ((1f - inverted * inverted * inverted * inverted) / LightmapColors.AMBIENT);
        assertEquals(expected, bright[0], 1e-5f, "brightness 1 applies the whole notGamma curve");
        assertTrue(bright[0] > moody[0] * 2f);
        float[] mid = LightmapColors.texel(at(18000f, 0f, 0f, 0.5f), 0, 0);
        assertEquals((moody[0] + bright[0]) / 2f, mid[0], 1e-5f, "and the option mixes between them");
    }

    @Test
    void blockLightIsWarmAtLowLevelsAndNeutralNearFullBrightness() {
        LightmapColors.Params p = at(18000f, 0f, 0f, 0f);
        float[] dim = LightmapColors.texel(p, 4, 0);
        assertTrue(dim[0] > dim[1] && dim[1] > dim[2], "warm: more red than green than blue");
        float[] full = LightmapColors.texel(p, 15, 0);
        assertEquals(1f, full[0], 1e-4f);
        assertTrue(full[2] > 0.9f, "level 15 mixes almost all the way to white: " + full[2]);
    }

    @Test
    void brightnessCurveMatchesTheShader() {
        assertEquals(0f, LightmapColors.brightness(0f), 0f);
        assertEquals(1f, LightmapColors.brightness(1f), 1e-6f);
        assertEquals(0.5f / (4f - 1.5f), LightmapColors.brightness(0.5f), 1e-6f);
    }

    @Test
    void rainAndThunderPullTheSkyTowardNight() {
        float day = LightmapColors.params(6000f, 0f, 0f, 0f, 0.5f).skyFactor();
        float rain = LightmapColors.params(6000f, 1f, 0f, 0f, 0.5f).skyFactor();
        float thunder = LightmapColors.params(6000f, 1f, 1f, 0f, 0.5f).skyFactor();
        assertEquals(1f, day, 1e-6f);
        assertEquals(1f + (0.24f - 1f) * 0.3125f, rain, 1e-5f);
        assertTrue(thunder < rain);
        assertEquals(rain + (0.24f - rain) * 0.52734375f, thunder, 1e-5f);
        // half the rain level, half the effect
        assertEquals(1f + (0.24f - 1f) * 0.3125f * 0.5f, LightmapColors.params(6000f, 0.5f, 0f, 0f, 0.5f).skyFactor(), 1e-5f);
    }

    @Test
    void theSkyFactorFollowsTheTimelineKeyframes() {
        assertEquals(1f, LightmapColors.params(730f, 0f, 0f, 0f, 0.5f).skyFactor(), 1e-6f);
        assertEquals(1f, LightmapColors.params(11270f, 0f, 0f, 0f, 0.5f).skyFactor(), 1e-6f);
        assertEquals(0.24f, LightmapColors.params(13140f, 0f, 0f, 0f, 0.5f).skyFactor(), 1e-6f);
        assertEquals(0.24f, LightmapColors.params(22860f, 0f, 0f, 0f, 0.5f).skyFactor(), 1e-6f);
        float dusk = LightmapColors.params(12205f, 0f, 0f, 0f, 0.5f).skyFactor();
        assertEquals(0.62f, dusk, 1e-3f, "half way down the dusk ramp");
        float dawn = LightmapColors.params(23795f, 0f, 0f, 0f, 0.5f).skyFactor();
        assertTrue(dawn > 0.5f && dawn < 0.7f, "the ramp wraps around the day: " + dawn);
    }

    @Test
    void theFlickerIsASmallDecayingRandomWalk() {
        Random r = new Random(5);
        float f = 0f, max = 0f;
        for (int i = 0; i < 2000; i++) {
            f = LightmapColors.nextFlicker(f, r);
            max = Math.max(max, Math.abs(f));
        }
        assertTrue(max < 0.12f, "it stays small: " + max);
        assertEquals(1.4f + f, LightmapColors.params(0f, 0f, 0f, f, 0.5f).blockFactor(), 1e-6f);
    }

    @Test
    void theTextureIsSixteenBySixteenRgba() {
        byte[] t = LightmapColors.build(at(6000f, 0f, 0f, 0.5f));
        assertEquals(16 * 16 * 4, t.length);
        int topRight = (15 * 16 + 15) * 4;
        assertEquals((byte) 255, t[topRight]);
        assertEquals((byte) 255, t[topRight + 3]);
    }
}
