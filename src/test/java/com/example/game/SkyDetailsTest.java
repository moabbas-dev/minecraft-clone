package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyDetailsTest {
    @Test
    void theGameDayMapsOntoVanillaTicks() {
        assertEquals(6000f, SkyDetails.dayTick(0.5f), 0.01f, "noon");
        assertEquals(0f, SkyDetails.dayTick(0.25f), 0.01f, "sunrise");
        assertEquals(12000f, SkyDetails.dayTick(0.75f), 0.01f, "sunset");
        assertEquals(18000f, SkyDetails.dayTick(0f), 0.01f, "midnight");
    }

    @Test
    void starsAreInvisibleByDayAndHalfBrightAtNight() {
        assertEquals(0f, SkyDetails.starBrightness(6000f), 1e-6f);
        assertEquals(0.5f, SkyDetails.starBrightness(18000f), 1e-6f);
        assertEquals(0.5f, SkyDetails.starBrightness(13228f), 1e-6f, "full brightness from tick 13228");
        float dusk = SkyDetails.starBrightness(12000f);
        assertTrue(dusk > 0.04f && dusk < 0.15f, "fading in at dusk: " + dusk);
        assertEquals(0.037f, SkyDetails.starBrightness(92f), 1e-6f, "the keyframes wrap around the day");
        assertTrue(SkyDetails.starBrightness(23900f) < SkyDetails.starBrightness(23358f));
    }

    @Test
    void cloudsAreWhiteByDayAndDarkBlueAtNight() {
        float[] noon = SkyDetails.cloudTint(6000f);
        assertArrayEquals(new float[]{1f, 1f, 1f}, noon, 1e-6f);
        float[] midnight = SkyDetails.cloudTint(18000f);
        assertEquals(0x19 / 255f, midnight[0], 1e-6f);
        assertEquals(0x26 / 255f, midnight[2], 1e-6f);
        float[] dusk = SkyDetails.cloudTint(12768f); // half way through 11867..13670
        assertTrue(dusk[0] > midnight[0] && dusk[0] < 1f);
    }

    @Test
    void theStarFieldIsTheSameEveryTimeAndStaysOnTheHundredBlockSphere() {
        float[] a = SkyDetails.buildStars(), b = SkyDetails.buildStars();
        assertArrayEquals(a, b);
        int stars = a.length / (6 * SkyDetails.FLOATS_PER_VERTEX);
        assertTrue(stars > 600 && stars <= SkyDetails.STAR_COUNT, "about half of the tries land in the ball: " + stars);
        for (int v = 0; v < a.length; v += SkyDetails.FLOATS_PER_VERTEX) {
            double r = Math.sqrt(a[v] * a[v] + a[v + 1] * a[v + 1] + a[v + 2] * a[v + 2]);
            assertEquals(100.0, r, 0.6, "every corner is within a star's size of the 100 block sphere");
        }
    }

    @Test
    void aCloudCellHasATopAndBottomAndSidesOnlyNextToGaps() {
        boolean[] mask = new boolean[4 * 4];
        mask[1 * 4 + 1] = true; // one lone cell
        float[] out = new float[100_000];
        int n = SkyDetails.buildClouds(out, mask, 4, 4, 1, 1, 0, new float[]{1f, 1f, 1f});
        assertEquals(6 * 6 * SkyDetails.FLOATS_PER_VERTEX, n, "top, bottom and four sides");
        mask[1 * 4 + 2] = true; // a neighbour to the east hides the shared side on both cells
        n = SkyDetails.buildClouds(out, mask, 4, 4, 1, 1, 1, new float[]{1f, 1f, 1f});
        assertEquals((2 * 2 + 2 * 3) * 6 * SkyDetails.FLOATS_PER_VERTEX, n);
    }

    @Test
    void theCloudPatternRepeatsWithTheTexture() {
        boolean[] mask = new boolean[4 * 4];
        mask[0] = true;
        float[] out = new float[100_000];
        int a = SkyDetails.buildClouds(out, mask, 4, 4, 0, 0, 0, new float[]{1f, 1f, 1f});
        int b = SkyDetails.buildClouds(out, mask, 4, 4, 4, 8, 0, new float[]{1f, 1f, 1f});
        assertEquals(a, b, "cell (4, 8) is texel (0, 0) again");
    }

    @Test
    void cloudShadesFollowTheShader() {
        boolean[] mask = new boolean[]{true};
        float[] out = new float[10_000];
        int n = SkyDetails.buildClouds(out, mask, 1, 1, 0, 0, 0, new float[]{1f, 1f, 1f});
        assertEquals(0, n % SkyDetails.FLOATS_PER_VERTEX);
        assertEquals(1f, out[7], 1e-6f, "the first face is the top at 1.0");
        assertEquals(0.7f, out[6 * SkyDetails.FLOATS_PER_VERTEX + 7], 1e-6f, "then the bottom at 0.7");
    }

    @Test
    void theCloudCellIsFoundUnderTheDrift() {
        assertEquals(0, SkyDetails.cellAt(5f, 0f));
        assertEquals(1, SkyDetails.cellAt(5f, 8f));
        assertEquals(-1, SkyDetails.cellAt(-1f, 0f));
    }
}
