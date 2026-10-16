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

    /**
     * The sun angle in degrees (0 at noon, 90 at the sunset horizon, 180 at midnight, 270 at the sunrise horizon): the timeline's
     * {@code visual/sun_angle} track, one 0 -> 360 degree turn per day eased by cubic_bezier(0.362, 0.241, 0.638, 0.759), so the sun
     * is slowest around noon and midnight. With it the sun crosses the horizon at ticks ~12542 and ~23460, where the other tracks
     * (monsters_burn, the dusk and dawn colours) switch.
     */
    static float sunAngleDegrees(float tick) {
        float u = ((tick - 6000f) % 24000f + 24000f) % 24000f / 24000f;
        return 360f * easeSun(u);
    }

    private static float easeSun(float x) {
        // solve x(t) = x on the bezier, then return y(t)
        float lo = 0f, hi = 1f;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) * 0.5f;
            if (bezier(mid, 0.362f, 0.638f) < x) lo = mid;
            else hi = mid;
        }
        float t = (lo + hi) * 0.5f;
        return bezier(t, 0.241f, 0.759f);
    }

    private static float bezier(float t, float p1, float p2) {
        float s = 1f - t;
        return 3f * s * s * t * p1 + 3f * s * t * t * p2 + t * t * t;
    }

    /** The sky disc of SkyRenderer.buildSkyDisc as triangles: centre (0, 16, 0), a rim of 8 segments at radius 512, coloured {@code rgb}. */
    static float[] skyDisc(float[] rgb) {
        return skyDisc(rgb, 16f);
    }

    /** The same fan at height {@code y} (SkyRenderer.buildSkyDisc: +16 for the sky disc, -16 for the dark disc below the horizon). */
    static float[] skyDisc(float[] rgb, float y) {
        float[] out = new float[8 * 3 * FLOATS_PER_VERTEX];
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a0 = Math.toRadians(-180 + i * 45), a1 = Math.toRadians(-180 + (i + 1) * 45);
            n = vertex(out, n, 0f, y, 0f, 1f, rgb[0], rgb[1], rgb[2]);
            n = vertex(out, n, (float) (512 * Math.cos(a1)), y, (float) (512 * Math.sin(a1)), 1f, rgb[0], rgb[1], rgb[2]);
            n = vertex(out, n, (float) (512 * Math.cos(a0)), y, (float) (512 * Math.sin(a0)), 1f, rgb[0], rgb[1], rgb[2]);
        }
        return out;
    }

    /**
     * The sunrise / sunset glow as triangles (the fan of {@link SkyColors#sunriseFan}), coloured {@code rgb}; the alpha 1 in the middle
     * and 0 on the rim travels in the sky-light slot of each vertex (the shader's fan mode multiplies it into the alpha).
     */
    static float[] sunriseFanTriangles(float[] rgb) {
        float[] fan = SkyColors.sunriseFan();
        float[] out = new float[16 * 3 * FLOATS_PER_VERTEX];
        int n = 0;
        for (int i = 1; i <= 16; i++) {
            int[] idx = {0, i, i + 1};
            for (int k : idx) n = fanVertex(out, n, fan, k, rgb);
        }
        return out;
    }

    private static int fanVertex(float[] a, int i, float[] fan, int k, float[] rgb) {
        a[i++] = fan[k * 4];
        a[i++] = fan[k * 4 + 1];
        a[i++] = fan[k * 4 + 2];
        a[i++] = 0.5f;
        a[i++] = 0.5f;
        a[i++] = fan[k * 4 + 3]; // the fan alpha rides in the sky-light slot
        a[i++] = 0f;
        a[i++] = rgb[0];
        a[i++] = rgb[1];
        a[i++] = rgb[2];
        a[i++] = 0f;
        return i;
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

    private static final float[][] SKY_LIGHT_LEVEL_FACTOR = {{133, 1f}, {11867, 1f}, {13670, 0.26666668f}, {22330, 0.26666668f}};
    /** The level rain and thunder pull the gameplay sky light toward (WeatherAttributes: alpha 0.3125 rain, 0.527 thunder). */
    static final float STORM_SKY_LIGHT_LEVEL = 4f;

    /**
     * The overworld's {@code gameplay/sky_light_level}: 15 times the day timeline factor, pulled toward 4 by rain (alpha 0.3125)
     * and thunder (alpha 0.52734375), each scaled by how strong the weather is. Mob spawning and burning read it.
     */
    static float skyLightLevel(float tick, float rainLevel, float thunderLevel) {
        float level = 15f * cyclic(SKY_LIGHT_LEVEL_FACTOR, tick);
        level += (STORM_SKY_LIGHT_LEVEL - level) * 0.3125f * Math.max(0f, Math.min(1f, rainLevel));
        level += (STORM_SKY_LIGHT_LEVEL - level) * 0.52734375f * Math.max(0f, Math.min(1f, thunderLevel));
        return level;
    }

    /** {@code Level.updateSkyBrightness}: {@code (int) (15 - sky_light_level)}, how much the sky light is dimmed for gameplay. */
    static int skyDarken(float tick, float rainLevel, float thunderLevel) {
        return (int) (15f - skyLightLevel(tick, rainLevel, thunderLevel));
    }

    /** gameplay/monsters_burn: false from tick 12542, true from tick 23460 (a boolean track that switches at its keyframes). */
    static boolean monstersBurn(float tick) {
        float t = ((tick % 24000f) + 24000f) % 24000f;
        return t >= 23460f || t < 12542f;
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
