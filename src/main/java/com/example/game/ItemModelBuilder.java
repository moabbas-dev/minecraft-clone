package com.example.game;

/**
 * Vertex generation for dropped-item models: a small spinning cube for ordinary blocks, two
 * crossing flat planes for cross-shaped plants (saplings). Pure math with no GL/atlas
 * dependency - the caller supplies each face's UV rect - so it's directly unit-testable, and it
 * writes into a caller-owned reusable array (no per-item allocation).
 *
 * Emits the same 11-float vertex the chunk shader consumes (pos, uv, sky, blockLight, tint,
 * wind), so dropped items render through the existing chunk shader/atlas with no shader change.
 */
final class ItemModelBuilder {
    private ItemModelBuilder() {
    }

    static final int FLOATS_PER_VERTEX = 11;
    static final int CUBE_VERTICES = 36;
    static final int CROSS_VERTICES = 12;
    /** Worst-case floats one item can write. */
    static final int MAX_FLOATS_PER_ITEM = CUBE_VERTICES * FLOATS_PER_VERTEX;

    /** UVs are {u0,v0,u1,v1}; tints are {r,g,b}. */
    record Model(float[] top, float[] bottom, float[] side, float[] topTint, float[] sideTint, boolean cross) {
    }

    // Face corner signs (x,y,z each -1 or +1) in bottom-left, bottom-right, top-right, top-left
    // texture order - the same convention Game.emitCubeFace uses for the held item.
    private static final int[][][] FACES = {
            {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}},      // top
            {{-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}},  // bottom
            {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},      // south
            {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},  // north
            {{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}},      // east
            {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}}, // west
    };
    private static final float[] FACE_SHADE = {1.0f, 0.6f, 0.85f, 0.85f, 0.7f, 0.7f};

    /** Writes a rotated cube centered on (cx,cy,cz) with half-extent {@code half}; returns the new write index. */
    static int emitCube(float[] out, int n, float cx, float cy, float cz, float half, float cos, float sin,
                        Model m, float sky, float blockLight) {
        for (int f = 0; f < 6; f++) {
            float[] uv = f == 0 ? m.top() : f == 1 ? m.bottom() : m.side();
            float[] tint = f == 0 ? m.topTint() : m.sideTint();
            int[][] c = FACES[f];
            float shadedSky = sky * FACE_SHADE[f];
            n = quad(out, n, cx, cy, cz, half, cos, sin, c[0], c[1], c[2], c[3], uv, shadedSky, blockLight, tint);
        }
        return n;
    }

    /** Two crossing diagonal planes (the in-world sapling shape), same placement rules as {@link #emitCube}. */
    static int emitCross(float[] out, int n, float cx, float cy, float cz, float half, float cos, float sin,
                         Model m, float sky, float blockLight) {
        int[][] planeA = {{-1, -1, -1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, -1}};
        int[][] planeB = {{1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {1, 1, -1}};
        n = quad(out, n, cx, cy, cz, half, cos, sin, planeA[0], planeA[1], planeA[2], planeA[3], m.side(), sky, blockLight, m.sideTint());
        n = quad(out, n, cx, cy, cz, half, cos, sin, planeB[0], planeB[1], planeB[2], planeB[3], m.side(), sky, blockLight, m.sideTint());
        return n;
    }

    private static int quad(float[] out, int n, float cx, float cy, float cz, float half, float cos, float sin,
                            int[] p0, int[] p1, int[] p2, int[] p3, float[] uv, float sky, float blockLight, float[] tint) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        n = vert(out, n, cx, cy, cz, half, cos, sin, p0, u0, v1, sky, blockLight, tint);
        n = vert(out, n, cx, cy, cz, half, cos, sin, p1, u1, v1, sky, blockLight, tint);
        n = vert(out, n, cx, cy, cz, half, cos, sin, p2, u1, v0, sky, blockLight, tint);
        n = vert(out, n, cx, cy, cz, half, cos, sin, p0, u0, v1, sky, blockLight, tint);
        n = vert(out, n, cx, cy, cz, half, cos, sin, p2, u1, v0, sky, blockLight, tint);
        n = vert(out, n, cx, cy, cz, half, cos, sin, p3, u0, v0, sky, blockLight, tint);
        return n;
    }

    private static int vert(float[] out, int n, float cx, float cy, float cz, float half, float cos, float sin,
                            int[] p, float u, float v, float sky, float blockLight, float[] tint) {
        float lx = p[0] * half, ly = p[1] * half, lz = p[2] * half;
        out[n++] = cx + lx * cos + lz * sin;
        out[n++] = cy + ly;
        out[n++] = cz - lx * sin + lz * cos;
        out[n++] = u;
        out[n++] = v;
        out[n++] = sky;
        out[n++] = blockLight;
        out[n++] = tint[0];
        out[n++] = tint[1];
        out[n++] = tint[2];
        out[n++] = 0f; // wind weight: dropped items never sway
        return n;
    }
}
