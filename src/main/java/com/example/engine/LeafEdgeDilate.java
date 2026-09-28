package com.example.engine;

import java.util.ArrayDeque;

/**
 * Fixes the pale halo that appears around leaf (and sapling) block edges at a distance: every
 * real leaf/sapling texture this project loads is a binary cutout (opaque leaf pixels, fully
 * transparent holes whose RGB is simply whatever the blank canvas started as - black, alpha 0).
 * Mipmapping (see TextureAtlas.upload) averages nearby texels, including that transparent black,
 * into the opaque green ones near an edge; since rendering always has GL_BLEND on, the resulting
 * partially-transparent, darkened fragment blends with the sky behind it instead of just fading
 * out cleanly, showing up as a thin dark/blue-tinted line right where the cutout meets the sky.
 *
 * The fix mirrors real Minecraft/graphics practice for cutout textures with mipmaps: give every
 * fully-transparent pixel the color of its nearest opaque pixel (multi-source BFS - a standard
 * "dilate"/color-bleed step) before it reaches the GPU, while leaving alpha completely untouched.
 * Mip levels then blend green-with-green at the edges instead of green-with-black, so a distant
 * leaf face fades toward transparent without ever going dark or picking up a stray tint.
 */
final class LeafEdgeDilate {
    private static final int[] NEIGHBORS_XY = {1, 0, -1, 0, 0, 1, 0, -1};

    private LeafEdgeDilate() {
    }

    /**
     * @param argb size x size ARGB pixels, row-major; not mutated
     * @return a new array, same alpha per pixel, but every alpha==0 pixel's RGB replaced by its
     *         nearest (BFS distance) opaque pixel's RGB. A tile with no opaque pixels, or none
     *         transparent, is returned unchanged (as a copy).
     */
    static int[] dilate(int[] argb, int size) {
        int[] out = argb.clone();
        boolean[] settled = new boolean[argb.length]; // opaque already, or transparent already given a color
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < argb.length; i++) {
            if (((argb[i] >>> 24) & 0xFF) != 0) {
                settled[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % size, y = i / size;
            int rgb = out[i] & 0x00FFFFFF;
            for (int n = 0; n < NEIGHBORS_XY.length; n += 2) {
                int nx = x + NEIGHBORS_XY[n], ny = y + NEIGHBORS_XY[n + 1];
                if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                int ni = ny * size + nx;
                if (settled[ni]) continue;
                settled[ni] = true;
                // The neighbor is transparent (alpha==0 by construction - settled opaque pixels
                // were seeded already) - it keeps its own (zero) alpha, only its RGB is borrowed.
                out[ni] = (out[ni] & 0xFF000000) | rgb;
                queue.add(ni);
            }
        }
        return out;
    }
}
