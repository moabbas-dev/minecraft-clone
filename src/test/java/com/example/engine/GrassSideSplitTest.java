package com.example.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrassSideSplitTest {
    private static final int GREEN = 0xFF66A63C;
    private static final int DIRT = 0xFF8C6446;

    /** 4x4 tile: top row grass, the rest dirt. */
    private static int[] tile() {
        int[] t = new int[16];
        for (int i = 0; i < 16; i++) t[i] = i < 4 ? GREEN : DIRT;
        return t;
    }

    @Test
    void greenPixelsAreGrassAndDirtIsNot() {
        assertTrue(GrassSideSplit.isGrassPixel(GREEN));
        assertFalse(GrassSideSplit.isGrassPixel(DIRT));
        assertFalse(GrassSideSplit.isGrassPixel(0x0066A63C), "a fully transparent pixel is nothing");
        assertFalse(GrassSideSplit.isGrassPixel(0xFF808080), "gray is not green");
    }

    @Test
    void theBaseIsFullyOpaqueSoNothingIsSeeThroughAndTheOverlayCoversOnlyGrass() {
        int[][] out = GrassSideSplit.split(tile(), 4, 0.5f);
        for (int i = 0; i < 16; i++) {
            assertEquals(0xFF, out[0][i] >>> 24, "base pixel " + i + " must be opaque (no see-through slivers)");
            assertEquals(i < 4, (out[1][i] >>> 24) != 0, "overlay is opaque exactly on grass pixels");
        }
    }

    @Test
    void theOverlayHoldsExactlyTheGrassPixelsAsPureGrayAndTheBaseKeepsTheDirtUntouched() {
        int[] src = tile();
        int[][] out = GrassSideSplit.split(src, 4, 0.5f);
        for (int i = 0; i < 16; i++) {
            if (i < 4) {
                int px = out[1][i];
                assertEquals(0xFF, px >>> 24);
                assertEquals((px >> 16) & 0xFF, (px >> 8) & 0xFF, "overlay must be gray so the tint sets the hue");
                assertEquals((px >> 8) & 0xFF, px & 0xFF);
                assertEquals(DIRT, out[0][i], "grass pixels are backed with the dirt below in the base");
            } else {
                assertEquals(src[i], out[0][i], "dirt pixels are unchanged in the base");
            }
        }
    }

    @Test
    void theOverlayBrightnessIsScaledToMatchTheTopTile() {
        float target = 0.6f;
        int[][] out = GrassSideSplit.split(tile(), 4, target);
        double sum = 0;
        for (int i = 0; i < 4; i++) sum += (out[1][i] & 0xFF) / 255.0;
        assertEquals(target, sum / 4, 0.02, "mean overlay gray should equal the top tile's mean brightness");
    }

    @Test
    void aTileWithNoGrassYieldsAnEmptyOverlayAndAnUnchangedBase() {
        int[] dirtOnly = new int[16];
        java.util.Arrays.fill(dirtOnly, DIRT);
        int[][] out = GrassSideSplit.split(dirtOnly, 4, 0.5f);
        assertArrayEquals(dirtOnly, out[0]);
        for (int p : out[1]) assertEquals(0, p >>> 24);
    }

    @Test
    void meanLumaIgnoresTransparentPixels() {
        int[] px = {0xFFFFFFFF, 0x00000000, 0xFF000000, 0x00FFFFFF};
        assertEquals(0.5f, GrassSideSplit.meanLuma(px), 0.001f);
    }
}
