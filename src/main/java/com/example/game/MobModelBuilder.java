package com.example.game;

/**
 * Vertex generation for the zombie's simple box-humanoid model (head, torso, two forward-held
 * arms, two legs). Pure math, no GL: writes the same 11-float vertex the chunk shader consumes
 * (see {@link ItemModelBuilder}) into a caller-owned array, so mobs draw through the existing
 * shader/atlas with no new GL state. Boxes are placed in the mob's local frame (feet at the
 * origin, facing -Z) and rotated by yaw; the walking animation is a simple opposite-phase leg
 * raise (no per-limb rotation rig exists - an honest, minimal "basic walking animation").
 */
final class MobModelBuilder {
    private MobModelBuilder() {
    }

    static final int BOXES = 6;
    static final int FLOATS_PER_MOB = BOXES * ItemModelBuilder.BOX_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX;

    // Corner signs per face (bottom-left, bottom-right, top-right, top-left), as in ItemModelBuilder.
    private static final int[][][] FACES = {
            {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}},
            {{-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}},
            {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},
            {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},
            {{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}},
            {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
    };
    private static final float[] FACE_SHADE = {1.0f, 0.6f, 0.85f, 0.85f, 0.7f, 0.7f};

    /** Writes one zombie at world feet position (px,py,pz) facing {@code yaw}; returns new index. */
    static int emitZombie(float[] out, int n, float px, float py, float pz, float yaw, float walkPhase,
                          float walkAmount, float[] uv, float sky, float blockLight, float[] tint) {
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float lift = (float) Math.sin(walkPhase) * 0.12f * walkAmount;
        // head
        n = box(out, n, px, py, pz, cos, sin, 0f, 1.725f, 0f, 0.225f, 0.225f, 0.225f, uv, sky, blockLight, tint);
        // torso
        n = box(out, n, px, py, pz, cos, sin, 0f, 1.125f, 0f, 0.25f, 0.375f, 0.125f, uv, sky, blockLight, tint);
        // arms held forward
        n = box(out, n, px, py, pz, cos, sin, -0.375f, 1.3f, -0.3f, 0.125f, 0.125f, 0.35f, uv, sky, blockLight, tint);
        n = box(out, n, px, py, pz, cos, sin, 0.375f, 1.3f, -0.3f, 0.125f, 0.125f, 0.35f, uv, sky, blockLight, tint);
        // legs (opposite phase)
        n = box(out, n, px, py, pz, cos, sin, -0.125f, 0.375f + Math.max(0f, lift), 0f, 0.125f, 0.375f, 0.125f, uv, sky, blockLight, tint);
        n = box(out, n, px, py, pz, cos, sin, 0.125f, 0.375f + Math.max(0f, -lift), 0f, 0.125f, 0.375f, 0.125f, uv, sky, blockLight, tint);
        return n;
    }

    private static int box(float[] out, int n, float px, float py, float pz, float cos, float sin,
                           float cx, float cy, float cz, float hx, float hy, float hz,
                           float[] uv, float sky, float blockLight, float[] tint) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] corner = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int f = 0; f < 6; f++) {
            for (int k : order) {
                int[] c = FACES[f][k];
                float lx = cx + c[0] * hx, ly = cy + c[1] * hy, lz = cz + c[2] * hz;
                out[n++] = px + lx * cos - lz * sin;
                out[n++] = py + ly;
                out[n++] = pz + lx * sin + lz * cos;
                out[n++] = corner[k][0];
                out[n++] = corner[k][1];
                out[n++] = sky * FACE_SHADE[f];
                out[n++] = blockLight;
                out[n++] = tint[0];
                out[n++] = tint[1];
                out[n++] = tint[2];
                out[n++] = 0f;
            }
        }
        return n;
    }
}
