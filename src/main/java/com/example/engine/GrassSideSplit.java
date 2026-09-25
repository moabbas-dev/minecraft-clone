package com.example.engine;

/**
 * Splits a grass block's side texture into a plain-dirt base and a tintable grass overlay, so
 * the grass fringe on the sides can follow the biome color exactly like the top face already
 * does (vanilla does the same with its separate grass_block_side_overlay texture; this project
 * ships only the combined side image, so the overlay is derived from it).
 *
 * The two outputs never overlap: a "grass" pixel is opaque only in the overlay (as a grayscale
 * value, so multiplying by the biome tint reproduces a natural green) and transparent in the
 * base; every other pixel is the reverse. The renderer draws both and the union is exactly the
 * original tile - with the tint applied to just the grass part. Pure integer/array math, no GL.
 */
final class GrassSideSplit {
    private GrassSideSplit() {
    }

    /** Greenish and non-transparent: the grass fringe (a dirt pixel has red >= green). */
    static boolean isGrassPixel(int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return a > 0 && g > r + 8 && g > b + 8;
    }

    private static float luma(int argb) {
        return (0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
    }

    /**
     * @param argb           the combined side tile, size x size ARGB pixels, row-major
     * @param targetMeanLuma average brightness (0..1) of the grass-top tile; the overlay's gray
     *                       levels are scaled so tint * gray matches the top face's brightness
     * @return {base, overlay}, both size*size ARGB
     */
    static int[][] split(int[] argb, int size, float targetMeanLuma) {
        int[] base = argb.clone();
        int[] overlay = new int[argb.length];

        double sum = 0;
        int grassCount = 0;
        for (int p : argb) {
            if (isGrassPixel(p)) {
                sum += luma(p);
                grassCount++;
            }
        }
        float scale = grassCount == 0 || sum <= 0 ? 1f : (float) (targetMeanLuma / (sum / grassCount));
        int mean = clamp255(Math.round(targetMeanLuma * 255f));

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if (isGrassPixel(argb[i])) {
                    int gray = clamp255(Math.round(luma(argb[i]) * scale * 255f));
                    overlay[i] = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
                    // Transparent in the base, but keep an RGB that matches the dirt below it so
                    // mip-mapping never blends a stray green into the edges.
                    int below = argb[i];
                    for (int yy = y + 1; yy < size; yy++) {
                        if (!isGrassPixel(argb[yy * size + x])) {
                            below = argb[yy * size + x];
                            break;
                        }
                    }
                    base[i] = 0x00000000 | (below & 0x00FFFFFF);
                } else {
                    overlay[i] = (mean << 16) | (mean << 8) | mean; // alpha 0
                }
            }
        }
        return new int[][]{base, overlay};
    }

    /** Average luma (0..1) over a tile's non-transparent pixels. */
    static float meanLuma(int[] argb) {
        double sum = 0;
        int n = 0;
        for (int p : argb) {
            if (((p >>> 24) & 0xFF) > 0) {
                sum += luma(p);
                n++;
            }
        }
        return n == 0 ? 0.5f : (float) (sum / n);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
