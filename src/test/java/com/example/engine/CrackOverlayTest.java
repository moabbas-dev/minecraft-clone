package com.example.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Only the pure stage-image generation - the overlay's GL parts need a real context. */
class CrackOverlayTest {

    private static int coverage(boolean[] stage) {
        int n = 0;
        for (boolean b : stage) if (b) n++;
        return n;
    }

    @Test
    void suppliedCrackTexturesBecomeBlackNotWhite() {
        assertEquals(0, CrackOverlay.darkenCrackPixel(0x01808080), "background pixels (alpha 1) stay invisible");
        assertEquals(0, CrackOverlay.darkenCrackPixel(0xFFFFFFFF), "white highlights vanish instead of drawing white scratches");
        int dark = CrackOverlay.darkenCrackPixel(0xFF3D3D3D); // the darkest shade in the real files
        assertTrue((dark >>> 24) >= 200, "dark crack pixels are strongly opaque");
        assertEquals(0x14, dark & 0xFF, "and always black-ish, never grey or white");
        assertEquals(0x14, (dark >> 16) & 0xFF);
        int mid = CrackOverlay.darkenCrackPixel(0xFF808080);
        assertTrue((mid >>> 24) > 0 && (mid >>> 24) < (dark >>> 24), "lighter source pixels are fainter");
    }

    @Test
    void everyStageHasSomeCrackAndCoverageStrictlyGrows() {
        int previous = 0;
        for (int s = 0; s < CrackOverlay.STAGES; s++) {
            int c = coverage(CrackOverlay.generateStage(s));
            assertTrue(c > previous, "stage " + s + " must crack more than stage " + (s - 1) + " (" + c + " vs " + previous + ")");
            previous = c;
        }
    }

    @Test
    void damageIsCumulativeNoPixelEverHealsBetweenStages() {
        boolean[] prev = CrackOverlay.generateStage(0);
        for (int s = 1; s < CrackOverlay.STAGES; s++) {
            boolean[] cur = CrackOverlay.generateStage(s);
            for (int i = 0; i < cur.length; i++) {
                if (prev[i]) assertTrue(cur[i], "pixel " + i + " healed going to stage " + s);
            }
            prev = cur;
        }
    }

    @Test
    void theFinalStageDoesNotCoverTheWholeTileSoTheBlockStaysVisibleUnderneath() {
        int c = coverage(CrackOverlay.generateStage(CrackOverlay.STAGES - 1));
        assertTrue(c < 16 * 16 / 2, "final stage covers too much: " + c);
    }

    @Test
    void generationIsDeterministic() {
        assertArrayEquals(CrackOverlay.generateStage(5), CrackOverlay.generateStage(5));
    }
}
