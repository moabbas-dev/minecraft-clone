package com.example.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LeafEdgeDilateTest {
    private static final int GREEN = 0xFF3C7822;
    private static final int OTHER_GREEN = 0xFF285A16;
    private static final int TRANSPARENT_BLACK = 0x00000000;

    @Test
    void everyPixelKeepsItsOriginalAlpha() {
        int[] tile = {GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, GREEN};
        int[] out = LeafEdgeDilate.dilate(tile, 2);
        for (int i = 0; i < tile.length; i++) {
            assertEquals(tile[i] >>> 24, out[i] >>> 24, "pixel " + i + " alpha must be unchanged");
        }
    }

    @Test
    void opaquePixelsAreCompletelyUnchanged() {
        int[] tile = {GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK,
                       GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK,
                       TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK,
                       TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK};
        int[] out = LeafEdgeDilate.dilate(tile, 4);
        assertEquals(GREEN, out[0]);
        assertEquals(GREEN, out[4]);
    }

    @Test
    void aTransparentPixelAdjacentToOneOpaquePixelBorrowsItsExactRgb() {
        // 2x2: opaque green top-left, transparent everywhere else - the two pixels adjacent to
        // it (right, below) must each borrow its exact RGB; the diagonal one is 2 steps away.
        int[] tile = {GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK};
        int[] out = LeafEdgeDilate.dilate(tile, 2);
        assertEquals(0, out[1] >>> 24, "still fully transparent");
        assertEquals(GREEN & 0x00FFFFFF, out[1] & 0x00FFFFFF, "right neighbor borrows the opaque pixel's RGB");
        assertEquals(0, out[2] >>> 24, "still fully transparent");
        assertEquals(GREEN & 0x00FFFFFF, out[2] & 0x00FFFFFF, "neighbor below borrows the opaque pixel's RGB");
    }

    @Test
    void everyTransparentPixelEndsUpWithSomeOpaqueTilesRgbNeverBlack() {
        // A ring of opaque pixels around one transparent center pixel in a 3x3 tile.
        int[] tile = new int[9];
        for (int i = 0; i < 9; i++) tile[i] = i == 4 ? TRANSPARENT_BLACK : GREEN;
        int[] out = LeafEdgeDilate.dilate(tile, 3);
        assertEquals(0, out[4] >>> 24);
        assertEquals(GREEN & 0x00FFFFFF, out[4] & 0x00FFFFFF);
    }

    @Test
    void distanceTiesArePickedDeterministically() {
        int[] tile = {GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, OTHER_GREEN};
        int[] a = LeafEdgeDilate.dilate(tile, 2);
        int[] b = LeafEdgeDilate.dilate(tile, 2);
        assertArrayEquals(a, b, "must be deterministic for the same input");
    }

    @Test
    void aTileWithNoTransparentPixelsIsUnchanged() {
        int[] tile = {GREEN, GREEN, OTHER_GREEN, GREEN};
        assertArrayEquals(tile, LeafEdgeDilate.dilate(tile, 2));
    }

    @Test
    void aFullyTransparentTileIsReturnedUnchanged() {
        int[] tile = {TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK};
        assertArrayEquals(tile, LeafEdgeDilate.dilate(tile, 2));
    }

    @Test
    void doesNotMutateTheInputArray() {
        int[] tile = {GREEN, TRANSPARENT_BLACK, TRANSPARENT_BLACK, TRANSPARENT_BLACK};
        int[] copy = tile.clone();
        LeafEdgeDilate.dilate(tile, 2);
        assertArrayEquals(copy, tile);
    }
}
