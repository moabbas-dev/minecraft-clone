package com.example.engine;

/**
 * The mip levels of one atlas cell the way the 26.3 client builds a sprite's (MipmapGenerator, MipmapStrategy, TextureUtil):
 * <ul>
 *   <li>{@code MEAN}: each level is the mean of 2 x 2 texels of the one above, taken in linear light (ARGB.meanLinear: the alpha is the
 *       plain integer mean).</li>
 *   <li>{@code CUTOUT}: the transparent texels first take the colour of their nearest opaque neighbour (TextureUtil.solidify), the levels are
 *       means, and each level's alpha is then scaled so that as many texels pass the 0.5 alpha test as at level 0 (scaleAlphaToCoverage).</li>
 *   <li>{@code DARK_CUTOUT} (all leaves): the transparent texels take a dark colour (3/4 of the darkest opaque texel,
 *       fillEmptyAreasWithDarkColor); a level averages in linear light but divides by four however many of the four are transparent, so a
 *       leaf edge gets darker, not paler (darkenedAlphaBlend); the alpha is scaled to keep the coverage like CUTOUT.</li>
 * </ul>
 * Together with the shader's 0.5 alpha test for terrain cutouts, a distant leaf block keeps the leaf-to-hole ratio of the texture instead of
 * fading into a pale see-through haze. The coverage count is a 4 x 4 bilinear supersample per texel, recalled from the bytecode: its
 * edge handling is clamped here.
 */
public final class MipmapChain {
    public enum Strategy { MEAN, CUTOUT, DARK_CUTOUT }

    /** The terrain cutout alpha test and the coverage cutoff (RenderPipelines ALPHA_CUTOUT_THRESHOLD_CUTOUT_TERRAIN, MipmapGenerator 0.5). */
    public static final float CUTOFF = 0.5f;

    private MipmapChain() {
    }

    /** The strategy a cell's texture gets: leaves are dark cutouts, glass is a plain mean, anything else with a fully transparent texel a cutout. */
    public static Strategy strategyFor(int[] cell, boolean leaves, boolean forcedMean) {
        if (forcedMean) return Strategy.MEAN;
        if (leaves) return Strategy.DARK_CUTOUT;
        for (int px : cell) if ((px >>> 24) == 0) return Strategy.CUTOUT;
        return Strategy.MEAN;
    }

    /**
     * @param cell   size x size ARGB texels (not changed)
     * @param levels the number of levels below level 0
     * @return levels + 1 arrays; level n is (size >> n) square; level 0 has its transparent texels recoloured
     */
    public static int[][] generate(int[] cell, int size, int levels, Strategy strategy) {
        int[][] out = new int[levels + 1][];
        int[] base = cell.clone();
        if (strategy == Strategy.DARK_CUTOUT) fillEmptyAreasWithDarkColor(base);
        else if (strategy == Strategy.CUTOUT) base = LeafEdgeDilate.dilate(base, size);
        out[0] = base;
        boolean cutout = strategy != Strategy.MEAN;
        float coverage = cutout ? alphaTestCoverage(base, size, CUTOFF, 1f) : 0f;
        int s = size;
        for (int level = 1; level <= levels; level++) {
            int[] prev = out[level - 1];
            int n = s >> 1;
            int[] cur = new int[n * n];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int a = prev[(2 * y) * s + 2 * x], b = prev[(2 * y) * s + 2 * x + 1];
                    int c = prev[(2 * y + 1) * s + 2 * x], d = prev[(2 * y + 1) * s + 2 * x + 1];
                    cur[y * n + x] = strategy == Strategy.DARK_CUTOUT ? darkenedAlphaBlend(a, b, c, d) : meanLinear(a, b, c, d);
                }
            }
            if (cutout) scaleAlphaToCoverage(cur, n, coverage, CUTOFF, 0f);
            out[level] = cur;
            s = n;
        }
        return out;
    }

    // ---- TextureUtil ----

    /** fillEmptyAreasWithDarkColor: every transparent texel gets 3/4 of the darkest opaque texel's colour (alpha stays 0). */
    static void fillEmptyAreasWithDarkColor(int[] px) {
        int darkest = -1, darkestSum = Integer.MAX_VALUE;
        for (int p : px) {
            if ((p >>> 24) == 0) continue;
            int sum = ((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF);
            if (sum < darkestSum) {
                darkestSum = sum;
                darkest = p;
            }
        }
        if (darkest == -1) return;
        int dark = (3 * ((darkest >> 16) & 0xFF) / 4 << 16) | (3 * ((darkest >> 8) & 0xFF) / 4 << 8) | (3 * (darkest & 0xFF) / 4);
        for (int i = 0; i < px.length; i++) if ((px[i] >>> 24) == 0) px[i] = dark;
    }

    // ---- colour maths ----

    private static final float[] SRGB_TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
    }

    static int linearToSrgb(float lin) {
        double l = Math.max(0.0, Math.min(1.0, lin));
        double c = l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
        return (int) Math.max(0, Math.min(255, Math.round(c * 255.0)));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** ARGB.meanLinear: mean alpha, and the colour channels averaged in linear light. */
    static int meanLinear(int a, int b, int c, int d) {
        int alpha = ((a >>> 24) + (b >>> 24) + (c >>> 24) + (d >>> 24)) / 4;
        return argb(alpha, channelMean(a, b, c, d, 16), channelMean(a, b, c, d, 8), channelMean(a, b, c, d, 0));
    }

    private static int channelMean(int a, int b, int c, int d, int shift) {
        float sum = SRGB_TO_LINEAR[(a >> shift) & 0xFF] + SRGB_TO_LINEAR[(b >> shift) & 0xFF]
                + SRGB_TO_LINEAR[(c >> shift) & 0xFF] + SRGB_TO_LINEAR[(d >> shift) & 0xFF];
        return linearToSrgb(sum / 4f);
    }

    /** darkenedAlphaBlend: only the texels with some alpha count, but the sum is divided by four anyway, so transparent texels darken the colour. */
    static int darkenedAlphaBlend(int a, int b, int c, int d) {
        float al = 0f, r = 0f, g = 0f, bl = 0f;
        for (int p : new int[]{a, b, c, d}) {
            if ((p >>> 24) == 0) continue;
            al += SRGB_TO_LINEAR[p >>> 24];
            r += SRGB_TO_LINEAR[(p >> 16) & 0xFF];
            g += SRGB_TO_LINEAR[(p >> 8) & 0xFF];
            bl += SRGB_TO_LINEAR[p & 0xFF];
        }
        return argb(linearToSrgb(al / 4f), linearToSrgb(r / 4f), linearToSrgb(g / 4f), linearToSrgb(bl / 4f));
    }

    // ---- coverage ----

    /** The share of the image that passes an alpha test at {@code cutoff} once alpha is multiplied by {@code scale}, supersampled 4 x 4 per texel. */
    static float alphaTestCoverage(int[] px, int size, float cutoff, float scale) {
        float total = 0f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int x1 = Math.min(x + 1, size - 1), y1 = Math.min(y + 1, size - 1);
                float a00 = alpha(px[y * size + x], scale), a10 = alpha(px[y * size + x1], scale);
                float a01 = alpha(px[y1 * size + x], scale), a11 = alpha(px[y1 * size + x1], scale);
                int count = 0;
                for (int j = 0; j < 4; j++) {
                    float v = (j + 0.5f) / 4f;
                    for (int i = 0; i < 4; i++) {
                        float u = (i + 0.5f) / 4f;
                        float a = (a00 * (1 - u) + a10 * u) * (1 - v) + (a01 * (1 - u) + a11 * u) * v;
                        if (a > cutoff) count++;
                    }
                }
                total += count / 16f;
            }
        }
        return total / (size * size);
    }

    private static float alpha(int px, float scale) {
        return Math.max(0f, Math.min(1f, (px >>> 24) / 255f * scale));
    }

    /** scaleAlphaToCoverage: five bisection steps on the alpha scale (0 to 4) toward the level-0 coverage, then alpha = alpha x scale + bias + 0.025. */
    static void scaleAlphaToCoverage(int[] px, int size, float desired, float cutoff, float bias) {
        float lo = 0f, hi = 4f, scale = 1f, best = 1f, bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            float cov = alphaTestCoverage(px, size, cutoff, scale);
            float diff = Math.abs(cov - desired);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = scale;
            }
            if (cov < desired) lo = scale;
            else if (cov > desired) hi = scale;
            else break;
            scale = (lo + hi) * 0.5f;
        }
        for (int i = 0; i < px.length; i++) {
            float a = Math.max(0f, Math.min(1f, (px[i] >>> 24) / 255f * best + bias + 0.025f));
            px[i] = (Math.round(a * 255f) << 24) | (px[i] & 0x00FFFFFF);
        }
    }
}
