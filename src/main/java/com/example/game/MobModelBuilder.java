package com.example.game;

/**
 * Vertex generation for the mob models: a box humanoid (zombie), a four-legged body (pig, cow) and a
 * small bird (chicken). Pure math, no GL: writes the same 11-float vertex the chunk shader consumes
 * (see {@link ItemModelBuilder}) into a caller-owned array, so mobs draw through the existing
 * shader/atlas with no new GL state. Boxes are placed in the mob's local frame (feet at the origin,
 * facing -Z), optionally rolled onto their side (the death topple), then rotated by yaw. Walking is
 * an opposite-phase leg raise and swing - no per-limb rotation rig exists, an honest minimal animation.
 */
final class MobModelBuilder {
    private MobModelBuilder() {
    }

    static final int BOXES = 8;
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
    private static final int[] ORDER = {0, 1, 2, 0, 2, 3};

    /** Shared per-mob pose and output cursor. */
    private static final class Writer {
        final float[] out;
        int n;
        final float px, py, pz, cos, sin, tiltCos, tiltSin, sky, blockLight;
        final float[] tint;

        Writer(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float sky, float blockLight, float[] tint) {
            this.out = out;
            this.n = n;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.cos = (float) Math.cos(yaw);
            this.sin = (float) Math.sin(yaw);
            this.tiltCos = (float) Math.cos(tilt);
            this.tiltSin = (float) Math.sin(tilt);
            this.sky = sky;
            this.blockLight = blockLight;
            this.tint = tint;
        }

        void box(float cx, float cy, float cz, float hx, float hy, float hz, float[] uv) {
            box(cx, cy, cz, hx, hy, hz, uv, tint);
        }

        void box(float cx, float cy, float cz, float hx, float hy, float hz, float[] uv, float[] boxTint) {
            float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
            float[][] corner = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            for (int f = 0; f < 6; f++) {
                for (int k : ORDER) {
                    int[] c = FACES[f][k];
                    float lx = cx + c[0] * hx, ly = cy + c[1] * hy, lz = cz + c[2] * hz;
                    float rx = lx * tiltCos - ly * tiltSin;  // roll about the forward (Z) axis
                    float ry = lx * tiltSin + ly * tiltCos;
                    out[n++] = px + rx * cos - lz * sin;
                    out[n++] = py + ry;
                    out[n++] = pz + rx * sin + lz * cos;
                    out[n++] = corner[k][0];
                    out[n++] = corner[k][1];
                    out[n++] = sky * FACE_SHADE[f];
                    out[n++] = blockLight;
                    out[n++] = boxTint[0];
                    out[n++] = boxTint[1];
                    out[n++] = boxTint[2];
                    out[n++] = 0f;
                }
            }
        }
    }

    /** Writes one zombie at world feet position (px,py,pz) facing {@code yaw}; returns the new index. */
    static int emitZombie(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                          float walkAmount, float[] uv, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint);
        float lift = (float) Math.sin(walkPhase) * 0.12f * walkAmount;
        w.box(0f, 1.725f, 0f, 0.225f, 0.225f, 0.225f, uv);                 // head
        w.box(0f, 1.125f, 0f, 0.25f, 0.375f, 0.125f, uv);                  // torso
        w.box(-0.375f, 1.3f, -0.3f, 0.125f, 0.125f, 0.35f, uv);            // arms held forward
        w.box(0.375f, 1.3f, -0.3f, 0.125f, 0.125f, 0.35f, uv);
        w.box(-0.125f, 0.375f + Math.max(0f, lift), 0f, 0.125f, 0.375f, 0.125f, uv); // legs, opposite phase
        w.box(0.125f, 0.375f + Math.max(0f, -lift), 0f, 0.125f, 0.375f, 0.125f, uv);
        return w.n;
    }

    /**
     * Four-legged body used by pig and cow: legs on a diagonal-pair gait, a body box, a head box, and an
     * optional snout. Dimensions are half-extents in blocks.
     */
    static int emitQuadruped(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                             float walkAmount, float[] uv, float[] snoutUv, float[] snoutTint, float sky, float blockLight,
                             float[] tint, float legHalfW, float legHalfH, float legX, float legZ,
                             float bodyHalfW, float bodyHalfH, float bodyHalfL, float bodyZ,
                             float headHalf, float headY, float headZ) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint);
        float swing = (float) Math.sin(walkPhase) * 0.12f * walkAmount;
        float lift = Math.max(0f, swing) * 0.6f, liftOther = Math.max(0f, -swing) * 0.6f;
        // diagonal pairs: front-left + back-right move together, front-right + back-left the other way
        w.box(-legX, legHalfH + lift, -legZ + swing, legHalfW, legHalfH, legHalfW, uv);
        w.box(legX, legHalfH + liftOther, -legZ - swing, legHalfW, legHalfH, legHalfW, uv);
        w.box(-legX, legHalfH + liftOther, legZ - swing, legHalfW, legHalfH, legHalfW, uv);
        w.box(legX, legHalfH + lift, legZ + swing, legHalfW, legHalfH, legHalfW, uv);
        w.box(0f, 2f * legHalfH + bodyHalfH, bodyZ, bodyHalfW, bodyHalfH, bodyHalfL, uv);
        w.box(0f, headY, headZ, headHalf, headHalf, headHalf, uv);
        if (snoutUv != null) {
            w.box(0f, headY - headHalf * 0.4f, headZ - headHalf - 0.04f, headHalf * 0.5f, headHalf * 0.38f, 0.04f, snoutUv, snoutTint);
        }
        return w.n;
    }

    static int emitPig(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                       float walkAmount, float[] uv, float sky, float blockLight, float[] tint) {
        float[] snout = {1.12f * tint[0], 0.82f * tint[1], 0.82f * tint[2]};
        return emitQuadruped(out, n, px, py, pz, yaw, tilt, walkPhase, walkAmount, uv, uv, snout, sky, blockLight, tint,
                0.125f, 0.1875f, 0.25f, 0.32f, 0.3f, 0.28f, 0.5f, 0.05f, 0.25f, 0.72f, -0.62f);
    }

    static int emitCow(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                       float walkAmount, float[] uv, float sky, float blockLight, float[] tint) {
        return emitQuadruped(out, n, px, py, pz, yaw, tilt, walkPhase, walkAmount, uv, null, tint, sky, blockLight, tint,
                0.1f, 0.3f, 0.26f, 0.5f, 0.35f, 0.35f, 0.6f, 0.1f, 0.25f, 1.1f, -0.78f);
    }

    static int emitChicken(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                           float walkAmount, boolean airborne, float[] uv, float[] beakUv, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint);
        float swing = (float) Math.sin(walkPhase) * 0.06f * walkAmount;
        w.box(-0.1f, 0.15f + Math.max(0f, swing), swing, 0.03f, 0.15f, 0.03f, beakUv);   // legs
        w.box(0.1f, 0.15f + Math.max(0f, -swing), -swing, 0.03f, 0.15f, 0.03f, beakUv);
        w.box(0f, 0.4f, 0.04f, 0.17f, 0.15f, 0.24f, uv);                                   // body
        w.box(0f, 0.62f, -0.17f, 0.09f, 0.11f, 0.09f, uv);                                 // head
        w.box(0f, 0.6f, -0.3f, 0.05f, 0.03f, 0.05f, beakUv);                               // beak
        float flap = airborne ? 0.18f : 0f;
        w.box(-0.2f, 0.4f + flap, 0.04f, 0.03f, 0.11f, 0.16f, uv);                         // wings
        w.box(0.2f, 0.4f + flap, 0.04f, 0.03f, 0.11f, 0.16f, uv);
        return w.n;
    }
}
