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
    /** A cube plus a tinted overlay quad on each of its 4 sides (grass). */
    static final int CUBE_WITH_OVERLAY_VERTICES = CUBE_VERTICES + 4 * 6;
    /** Worst-case floats one item can write. */
    static final int MAX_FLOATS_PER_ITEM = CUBE_WITH_OVERLAY_VERTICES * FLOATS_PER_VERTEX;

    /** UVs are {u0,v0,u1,v1}; tints are {r,g,b}. {@code sideOverlay}/{@code overlayTint} are
     *  null except for grass, whose side has a separately tinted fringe drawn on top. */
    record Model(float[] top, float[] bottom, float[] side, float[] topTint, float[] sideTint, boolean cross,
                 float[] sideOverlay, float[] overlayTint) {
        Model(float[] top, float[] bottom, float[] side, float[] topTint, float[] sideTint, boolean cross) {
            this(top, bottom, side, topTint, sideTint, cross, null, null);
        }
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
    /** Writes a rotated cube centered on (cx,cy,cz) with half-extent {@code half}; returns the new write index. */
    static int emitCube(float[] out, int n, float cx, float cy, float cz, float half, float cos, float sin,
                        Model m, float sky, float blockLight) {
        for (int f = 0; f < 6; f++) {
            float[] uv = f == 0 ? m.top() : f == 1 ? m.bottom() : m.side();
            float[] tint = f == 0 ? m.topTint() : m.sideTint();
            int[][] c = FACES[f];
            n = quad(out, n, cx, cy, cz, half, cos, sin, c[0], c[1], c[2], c[3], uv, sky, blockLight, tint);
        }
        if (m.sideOverlay() != null) {
            // Slightly larger than the base cube so it wins the depth test on the (disjoint) pixels it covers.
            for (int f = 2; f < 6; f++) {
                int[][] c = FACES[f];
                n = quad(out, n, cx, cy, cz, half * 1.01f, cos, sin, c[0], c[1], c[2], c[3], m.sideOverlay(),
                        sky, blockLight, m.overlayTint());
            }
        }
        return n;
    }

    static final int BOX_VERTICES = 36;

    /**
     * An axis-aligned box centered on the origin with independent half-extents - the first-person
     * arm. The light levels are placeholders (the game writes the light at the player when it draws the hand) and the face
     * shading comes from the shader. Returns the new write index.
     */
    static int emitBox(float[] out, int n, float hx, float hy, float hz, float[] uv, float[] tint) {
        for (int f = 0; f < 6; f++) {
            int[][] c = FACES[f];
            float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
            int[] order = {0, 1, 2, 0, 2, 3};
            float[][] corner = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            for (int k : order) {
                out[n++] = c[k][0] * hx;
                out[n++] = c[k][1] * hy;
                out[n++] = c[k][2] * hz;
                out[n++] = corner[k][0];
                out[n++] = corner[k][1];
                out[n++] = 1f;
                out[n++] = 1f;
                out[n++] = tint[0];
                out[n++] = tint[1];
                out[n++] = tint[2];
                out[n++] = 0f;
            }
        }
        return n;
    }

    /**
     * Writes a flat extruded sprite (the local mesh from {@code HeldItemModel.extrudedSprite}, centred on the origin,
     * spanning -0.5..0.5) scaled by {@code scale}, turned about the
     * vertical axis and centred on (cx,cy,cz) - a dropped tool, food or ingot. Returns the new write index.
     */
    static int emitSprite(float[] out, int n, float[] local, float cx, float cy, float cz, float scale,
                          float cos, float sin, float sky, float blockLight) {
        for (int i = 0; i < local.length; i += FLOATS_PER_VERTEX) {
            float lx = local[i] * scale, ly = local[i + 1] * scale, lz = local[i + 2] * scale;
            out[n++] = cx + lx * cos + lz * sin;
            out[n++] = cy + ly;
            out[n++] = cz - lx * sin + lz * cos;
            out[n++] = local[i + 3];
            out[n++] = local[i + 4];
            out[n++] = sky;
            out[n++] = blockLight;
            out[n++] = local[i + 7];
            out[n++] = local[i + 8];
            out[n++] = local[i + 9];
            out[n++] = 0f;
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
