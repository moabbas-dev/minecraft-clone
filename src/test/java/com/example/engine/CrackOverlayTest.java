package com.example.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Only the pure crack-pixel conversion - the overlay's GL parts need a real context. */
class CrackOverlayTest {

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
}
