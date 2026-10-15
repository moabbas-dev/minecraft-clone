package com.example.game;

import java.util.Random;

/**
 * The jagged bolt of LightningBoltRenderer: eight 16-block segments from the cloud down to the strike, each flash random-walking
 * its offset by -5..+5 per segment, drawn as four nested square prisms (widths 0.1 to 0.7) and, for the lower segments, side
 * branches (offset steps -15..+15). Colour (0.45, 0.45, 0.5), alpha 0.3, added to the scene. Vertices are the chunk shader's
 * 11 floats, in blocks relative to the strike position.
 */
final class LightningGeometry {
    private LightningGeometry() {
    }

    static final int FLOATS = SkyDetails.FLOATS_PER_VERTEX;
    private static final int MAX_QUADS = 4 * 3 * 8 * 4;

    /** The triangles of one bolt shape; returns the number of floats written into {@code out}. */
    static int build(long seed, float[] out) {
        float[] ox = new float[8], oz = new float[8];
        Random random = new Random(seed);
        float x = 0f, z = 0f;
        for (int i = 7; i >= 0; i--) {
            ox[i] = x;
            oz[i] = z;
            x += random.nextInt(11) - 5;
            z += random.nextInt(11) - 5;
        }
        int n = 0;
        for (int layer = 0; layer < 4; layer++) {
            Random r = new Random(seed);
            for (int branch = 0; branch < 3; branch++) {
                int top = 7, bottom = 0;
                if (branch > 0) top = 7 - branch;
                if (branch > 0) bottom = top - 2;
                float cx = ox[top] - x, cz = oz[top] - z;
                for (int seg = top; seg >= bottom; seg--) {
                    float px = cx, pz = cz;
                    if (branch == 0) {
                        cx += r.nextInt(11) - 5;
                        cz += r.nextInt(11) - 5;
                    } else {
                        cx += r.nextInt(31) - 15;
                        cz += r.nextInt(31) - 15;
                    }
                    float wUpper = 0.1f + layer * 0.2f, wLower = 0.1f + layer * 0.2f;
                    if (branch == 0) {
                        wUpper *= seg * 0.1f + 1f;
                        wLower *= (seg - 1) * 0.1f + 1f;
                    }
                    float yUpper = (seg + 1) * 16f, yLower = seg * 16f;
                    n = prism(out, n, px, pz, yUpper, wUpper, cx, cz, yLower, wLower);
                }
            }
        }
        return n;
    }

    /** The four side faces of a square prism between an upper and a lower ring. */
    private static int prism(float[] out, int n, float ux, float uz, float uy, float uw, float lx, float lz, float ly, float lw) {
        int[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int s = 0; s < 4; s++) {
            int[] a = corner[s], b = corner[(s + 1) % 4];
            float[][] q = {
                    {ux + a[0] * uw, uy, uz + a[1] * uw}, {ux + b[0] * uw, uy, uz + b[1] * uw},
                    {lx + b[0] * lw, ly, lz + b[1] * lw}, {lx + a[0] * lw, ly, lz + a[1] * lw}};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) {
                out[n++] = q[k][0];
                out[n++] = q[k][1];
                out[n++] = q[k][2];
                out[n++] = 0.5f;
                out[n++] = 0.5f;
                out[n++] = 1f;
                out[n++] = 1f;
                out[n++] = 0.45f;
                out[n++] = 0.45f;
                out[n++] = 0.5f;
                out[n++] = 0f;
            }
        }
        return n;
    }

    static int capacityFloats() {
        return MAX_QUADS * 6 * FLOATS;
    }
}
