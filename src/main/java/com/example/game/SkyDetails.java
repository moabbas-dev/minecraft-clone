package com.example.game;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Stars and clouds as the 26.3 client builds them (constants read from SkyRenderer, CloudRenderer, the cloud shader and the
 * overworld day timeline). Pure maths and vertex generation, no GL: vertices are the chunk shader's 11 floats
 * (x y z u v sky blockLight r g b wind). Drawn unlit (the shader's lit mode 1, no lightmap), with the day-time dimming done here
 * from the real keyframes.
 */
final class SkyDetails {
    private SkyDetails() {
    }

    static final int FLOATS_PER_VERTEX = 11;

    // ---- the overworld day timeline (data/minecraft/timeline/day.json), in ticks of a 24000-tick day, noon = 6000 ----

    /** The vanilla tick of day (0 = sunrise, 6000 = noon) for this game's day fraction (0 = midnight, 0.5 = noon). */
    static float dayTick(float dayTime) {
        float t = dayTime * 24000f - 6000f;
        return ((t % 24000f) + 24000f) % 24000f;
    }

    private static final float[][] STAR_BRIGHTNESS = {
            {92, 0.037f}, {627, 0f}, {11373, 0f}, {11732, 0.016f}, {11959, 0.044f}, {12399, 0.143f}, {12729, 0.258f},
            {13228, 0.5f}, {22772, 0.5f}, {23032, 0.364f}, {23356, 0.225f}, {23758, 0.101f}};

    /** The star brightness at a tick (linear between the keyframes, wrapping around the day): 0 by day, 0.5 at night. */
    static float starBrightness(float tick) {
        return cyclic(STAR_BRIGHTNESS, tick);
    }

    /** The cloud colour multiplier: white by day, a dark blue (#191926) at night, blending between ticks 11867-13670 and 22330-24133. */
    static float[] cloudTint(float tick) {
        float[][] r = {{133, 1f}, {11867, 1f}, {13670, 0x19 / 255f}, {22330, 0x19 / 255f}};
        float[][] g = {{133, 1f}, {11867, 1f}, {13670, 0x19 / 255f}, {22330, 0x19 / 255f}};
        float[][] b = {{133, 1f}, {11867, 1f}, {13670, 0x26 / 255f}, {22330, 0x26 / 255f}};
        return new float[]{cyclic(r, tick), cyclic(g, tick), cyclic(b, tick)};
    }

    static float cyclic(float[][] keys, float tick) {
        float t = ((tick % 24000f) + 24000f) % 24000f;
        int n = keys.length;
        for (int i = 0; i < n; i++) {
            float t0 = keys[i][0], t1 = i + 1 < n ? keys[i + 1][0] : keys[0][0] + 24000f;
            float tt = t;
            if (i + 1 == n && t < keys[0][0]) tt = t + 24000f;
            if (tt >= t0 && tt <= t1) {
                float v0 = keys[i][1], v1 = keys[(i + 1) % n][1];
                return t1 == t0 ? v0 : v0 + (v1 - v0) * (tt - t0) / (t1 - t0);
            }
        }
        return keys[0][1];
    }

    // ---- stars (SkyRenderer.buildStars) ----

    static final int STAR_COUNT = 1500;
    static final float STAR_DISTANCE = 100f;

    /**
     * The star field: random points inside the unit ball (seed 10842, 1500 tries, those with squared length outside (0.01, 1)
     * dropped), pushed out to 100 blocks, each a square of half-size 0.15 + 0.1 * random turned to face the origin and by a
     * random angle. Returns the vertices (six per star).
     */
    static float[] buildStars() {
        Random random = new Random(10842L);
        float[] out = new float[STAR_COUNT * 6 * FLOATS_PER_VERTEX];
        int n = 0;
        for (int i = 0; i < STAR_COUNT; i++) {
            float x = random.nextFloat() * 2f - 1f, y = random.nextFloat() * 2f - 1f, z = random.nextFloat() * 2f - 1f;
            float size = 0.15f + random.nextFloat() * 0.1f;
            float d = x * x + y * y + z * z;
            if (d <= 0.010000001f || d >= 1f) continue;
            Vector3f pos = new Vector3f(x, y, z).normalize(STAR_DISTANCE);
            double angle = random.nextDouble() * Math.PI * 2.0;
            Matrix3f m = new Matrix3f().rotateTowards(new Vector3f(pos).negate(), new Vector3f(0f, 1f, 0f)).rotateZ((float) angle);
            Vector3f[] c = {
                    new Vector3f(size, -size, 0f).mul(m).add(pos), new Vector3f(size, size, 0f).mul(m).add(pos),
                    new Vector3f(-size, size, 0f).mul(m).add(pos), new Vector3f(-size, -size, 0f).mul(m).add(pos)};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) n = vertex(out, n, c[k].x, c[k].y, c[k].z, 1f, 1f, 1f, 1f);
        }
        float[] trimmed = new float[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static int vertex(float[] a, int i, float x, float y, float z, float shade, float r, float g, float b) {
        a[i++] = x;
        a[i++] = y;
        a[i++] = z;
        a[i++] = 0.5f; // u, v: any texel of the plain white texture
        a[i++] = 0.5f;
        a[i++] = 0f;
        a[i++] = 1f;   // light levels, ignored: these are drawn unlit
        a[i++] = r * shade;
        a[i++] = g * shade;
        a[i++] = b * shade;
        a[i++] = 0f;
        return i;
    }

    // ---- clouds (CloudRenderer, clouds.vsh, overworld cloud_height) ----

    static final float CLOUD_HEIGHT = 192.33f;
    static final float CELL = 12f, CELL_HEIGHT = 4f;
    /** The pattern drifts 0.6 blocks per second (0.03 per tick) toward -X and sits 3.96 blocks off in Z. */
    static final float DRIFT_PER_TICK = 0.03f, Z_OFFSET = 3.96f;
    static final float BOTTOM = 0.7f, TOP = 1f, NORTH_SOUTH = 0.8f, WEST_EAST = 0.9f;

    /** Which texels of {@code clouds.png} hold cloud: those that are not fully transparent. */
    static boolean[] cloudMask(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[] mask = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) mask[y * w + x] = (img.getRGB(x, y) >>> 24) > 0;
        return mask;
    }

    /** The cell index above a world coordinate: the cloud pattern has drifted {@code driftBlocks} toward -X by now. */
    static int cellAt(float worldCoord, float drift) {
        return (int) Math.floor((worldCoord + drift) / CELL);
    }

    /**
     * Fancy clouds for the cells within {@code radius} of cell ({@code cellX}, {@code cellZ}): a 12 x 4 x 12 box per cloud texel
     * (the texture repeats every {@code w} cells), with the top and bottom always and a side only where the neighbouring
     * cell is empty, shaded top 1.0, sides 0.9 / 0.8, bottom 0.7. Coordinates are relative to the corner of cell
     * ({@code cellX}, {@code cellZ}) at the cloud base. Returns the number of floats written.
     */
    static int buildClouds(float[] out, boolean[] mask, int w, int h, int cellX, int cellZ, int radius, float[] tint) {
        int n = 0;
        for (int j = cellZ - radius; j <= cellZ + radius; j++) {
            for (int i = cellX - radius; i <= cellX + radius; i++) {
                if (!solid(mask, w, h, i, j)) continue;
                float x0 = (i - cellX) * CELL, x1 = x0 + CELL, z0 = (j - cellZ) * CELL, z1 = z0 + CELL, y0 = 0f, y1 = CELL_HEIGHT;
                if (n + 5 * 6 * FLOATS_PER_VERTEX > out.length) return n;
                n = quad(out, n, new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, TOP, tint);
                n = quad(out, n, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y0, z0}}, BOTTOM, tint);
                if (!solid(mask, w, h, i, j - 1)) n = quad(out, n, new float[][]{{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}}, NORTH_SOUTH, tint);
                if (!solid(mask, w, h, i, j + 1)) n = quad(out, n, new float[][]{{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}}, NORTH_SOUTH, tint);
                if (!solid(mask, w, h, i - 1, j)) n = quad(out, n, new float[][]{{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}}, WEST_EAST, tint);
                if (!solid(mask, w, h, i + 1, j)) n = quad(out, n, new float[][]{{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, WEST_EAST, tint);
            }
        }
        return n;
    }

    private static boolean solid(boolean[] mask, int w, int h, int i, int j) {
        return mask[Math.floorMod(j, h) * w + Math.floorMod(i, w)];
    }

    private static int quad(float[] out, int n, float[][] p, float shade, float[] tint) {
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) n = vertex(out, n, p[k][0], p[k][1], p[k][2], shade, tint[0], tint[1], tint[2]);
        return n;
    }
}
