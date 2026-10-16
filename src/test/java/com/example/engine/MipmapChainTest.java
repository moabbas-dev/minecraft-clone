package com.example.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The mip chain of the 26.3 client: linear means, the dark fill of leaf holes, and alpha scaled to keep the leaf coverage. */
class MipmapChainTest {
    private static final int GREEN = 0xFF2A7A1E;

    /** A 16 x 16 leaf-like tile: about half of the texels opaque green in a fixed random pattern, the rest fully transparent. */
    private static int[] checker() {
        int[] px = new int[16 * 16];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) px[i] = r.nextBoolean() ? GREEN : 0;
        return px;
    }

    @Test
    void emptyAreasTakeThreeQuartersOfTheDarkestOpaqueColour() {
        int[] px = {0xFF404040, 0xFF808080, 0, 0};
        MipmapChain.fillEmptyAreasWithDarkColor(px);
        assertEquals(0x00303030, px[2]);
        assertEquals(0x00303030, px[3]);
        assertEquals(0xFF404040, px[0]);
    }

    @Test
    void theMeanIsTakenInLinearLightNotInSrgb() {
        int mean = MipmapChain.meanLinear(0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF, 0xFF000000);
        int channel = (mean >> 8) & 0xFF;
        assertTrue(channel > 180 && channel < 195, "half white and half black in linear light is about 188, not 128: " + channel);
        assertEquals(0xFF, mean >>> 24);
    }

    @Test
    void aTransparentTexelDarkensTheDarkBlendInsteadOfBeingIgnored() {
        int one = MipmapChain.darkenedAlphaBlend(GREEN, 0, 0, 0);
        int four = MipmapChain.darkenedAlphaBlend(GREEN, GREEN, GREEN, GREEN);
        assertEquals(GREEN, four, "four opaque texels leave the colour alone");
        assertTrue(((one >> 8) & 0xFF) < ((GREEN >> 8) & 0xFF) / 2 + 40, "one in four: a much darker green");
        assertTrue((one >>> 24) < 160 && (one >>> 24) > 100, "and about a quarter of the light of the alpha: " + (one >>> 24));
    }

    @Test
    void everyLevelOfALeafKeepsTheCoverageOfLevelZero() {
        int[][] chain = MipmapChain.generate(checker(), 16, 3, MipmapChain.Strategy.DARK_CUTOUT);
        float level0 = MipmapChain.alphaTestCoverage(chain[0], 16, MipmapChain.CUTOFF, 1f);
        assertEquals(0.5f, level0, 0.15f);
        int size = 16;
        for (int l = 1; l <= 3; l++) {
            size >>= 1;
            float coverage = MipmapChain.alphaTestCoverage(chain[l], size, MipmapChain.CUTOFF, 1f);
            assertEquals(level0, coverage, 0.25f, "level " + l);
        }
    }

    @Test
    void withoutTheScalingAPlainMeanLosesMostOfTheCoverageAtADistance() {
        // the old behaviour: a 50% leaf averaged to alpha 0.5 and nothing passes a 0.5 test any more
        int[][] mean = MipmapChain.generate(checker(), 16, 4, MipmapChain.Strategy.MEAN);
        float plain = MipmapChain.alphaTestCoverage(mean[4], 1, MipmapChain.CUTOFF, 1f);
        int[][] dark = MipmapChain.generate(checker(), 16, 4, MipmapChain.Strategy.DARK_CUTOUT);
        float scaled = MipmapChain.alphaTestCoverage(dark[4], 1, MipmapChain.CUTOFF, 1f);
        assertTrue(scaled >= plain, "scaled " + scaled + " plain " + plain);
        assertTrue(dark[4][0] >>> 24 > 0x70, "the last level still has a solid alpha");
    }

    @Test
    void strategiesFollowTheTextureMetadata() {
        assertEquals(MipmapChain.Strategy.DARK_CUTOUT, MipmapChain.strategyFor(checker(), true, false));
        assertEquals(MipmapChain.Strategy.MEAN, MipmapChain.strategyFor(checker(), false, true), "glass says mean");
        assertEquals(MipmapChain.Strategy.CUTOUT, MipmapChain.strategyFor(checker(), false, false), "auto with transparent texels");
        int[] opaque = new int[16];
        java.util.Arrays.fill(opaque, 0xFF123456);
        assertEquals(MipmapChain.Strategy.MEAN, MipmapChain.strategyFor(opaque, false, false));
    }
}
