package com.example.game;

/**
 * Vertex generation for the mob models: a box humanoid (zombie), a four-legged body (pig, cow) and a small
 * bird (chicken), textured with the original Minecraft skins. Pure math, no GL: writes the same 11-float vertex
 * the chunk shader consumes (see {@link ItemModelBuilder}) into a caller-owned array.
 *
 * <p>Every box is laid out exactly as vanilla does it (research: docs/research/mob-skins-and-sounds.md): a box of
 * w x h x d pixels with texture offset (u, v) uses the cross-shaped unwrap - top and bottom above, then the four
 * side strips west / north (front) / east / south in a row. Dimensions below are in model pixels (16 per block);
 * the vanilla model frame is Y-down with the ground at y = 24, so positions convert as
 * {@code mine = (-x, 24 - y, z) / 16}. In the mob's local frame the front faces -Z and the yaw rotation is applied
 * last. Body boxes that vanilla rotates 90 degrees about X (pig/cow/chicken bodies, zombie arms) are handled by
 * a quarter-turn flag that re-orients which strip lands on which face and which way its texture points.
 */
final class MobModelBuilder {
    private MobModelBuilder() {
    }

    static final int BOXES = 14;
    static final int FLOATS_PER_MOB = BOXES * ItemModelBuilder.BOX_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX;

    static final int ZOMBIE_TEX_W = 64, ZOMBIE_TEX_H = 64;
    static final int ANIMAL_TEX_W = 64, ANIMAL_TEX_H = 32;

    // Corner signs per face (bottom-left, bottom-right, top-right, top-left), as in ItemModelBuilder.
    // Face order: +Y, -Y, +Z, -Z, +X, -X.
    private static final int[][][] FACES = {
            {{-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}},
            {{-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}},
            {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},
            {{1, -1, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}},
            {{1, -1, 1}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}},
            {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},
    };
    private static final int[][] FACE_NORMAL = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
    private static final float[] FACE_SHADE = {1.0f, 0.6f, 0.85f, 0.85f, 0.7f, 0.7f};
    private static final int[] ORDER = {0, 1, 2, 0, 2, 3};

    // Strip ids: 0 U, 1 D, 2 W, 3 N, 4 E, 5 S.
    private static final int U = 0, D = 1, W = 2, N = 3, E = 4, S = 5;

    /** Rotates an integer axis vector by a quarter turn about X: -1 is the body roll, +1 the zombie arm raise. */
    private static int[] rot(int[] v, int q) {
        if (q == 0) return v;
        if (q < 0) return new int[]{v[0], v[2], -v[1]};
        return new int[]{v[0], -v[2], v[1]};
    }

    /** Shared per-mob pose and output cursor. */
    private static final class Writer {
        final float[] out;
        int n;
        final float px, py, pz, cos, sin, tiltCos, tiltSin, sky, blockLight, texW, texH;
        final float[] tint;

        Writer(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float sky, float blockLight,
               float[] tint, int texW, int texH) {
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
            this.texW = texW;
            this.texH = texH;
        }

        /**
         * One textured box. Centre in vanilla model pixels (x, y-down, z), size w x h x d pixels, texture offset
         * (u, v), {@code quarter} = 0 upright, -1 rolled onto its belly axis (animal bodies), +1 raised forward
         * (zombie arms), {@code mirror} for left-hand limbs.
         */
        void box(float mx, float my, float mz, int w, int h, int d, int u, int v, int quarter, boolean mirror) {
            float cx = -mx / 16f, cy = (24f - my) / 16f, cz = mz / 16f;
            float lhx = w / 32f, lhy = h / 32f, lhz = d / 32f;
            // World half extents: a quarter turn swaps the local Y and Z extents.
            float hx = lhx, hy = quarter == 0 ? lhy : lhz, hz = quarter == 0 ? lhz : lhy;
            for (int f = 0; f < 6; f++) {
                int[] worldNormal = FACE_NORMAL[f];
                int[] localNormal = rot(worldNormal, -quarter);
                int strip = stripFor(localNormal);
                int[] localUp = strip == U ? new int[]{0, 0, 1} : strip == D ? new int[]{0, 0, -1} : new int[]{0, 1, 0};
                int[] up = rot(localUp, quarter);
                int[] fwd = {-worldNormal[0], -worldNormal[1], -worldNormal[2]};
                int[] right = {fwd[1] * up[2] - fwd[2] * up[1], fwd[2] * up[0] - fwd[0] * up[2], fwd[0] * up[1] - fwd[1] * up[0]};
                if (mirror && (strip == W || strip == E)) strip = strip == W ? E : W;
                float[] rect = stripRect(strip, u, v, w, h, d);
                for (int k : ORDER) {
                    int[] c = FACES[f][k];
                    float uu = (c[0] * right[0] + c[1] * right[1] + c[2] * right[2] + 1) / 2f;
                    float vv = 1f - (c[0] * up[0] + c[1] * up[1] + c[2] * up[2] + 1) / 2f;
                    if (mirror) uu = 1f - uu;
                    float tu = (rect[0] + uu * (rect[2] - rect[0])) / texW;
                    float tv = (rect[1] + vv * (rect[3] - rect[1])) / texH;
                    float lx = cx + c[0] * hx, ly = cy + c[1] * hy, lz = cz + c[2] * hz;
                    float rx = lx * tiltCos - ly * tiltSin;  // roll about the forward (Z) axis for the death topple
                    float ry = lx * tiltSin + ly * tiltCos;
                    out[n++] = px + rx * cos - lz * sin;
                    out[n++] = py + ry;
                    out[n++] = pz + rx * sin + lz * cos;
                    out[n++] = tu;
                    out[n++] = tv;
                    out[n++] = sky * FACE_SHADE[f];
                    out[n++] = blockLight;
                    out[n++] = tint[0];
                    out[n++] = tint[1];
                    out[n++] = tint[2];
                    out[n++] = 0f;
                }
            }
        }
    }

    /** Which vanilla strip a local-frame face normal reads from (the model frame's X is flipped into ours). */
    private static int stripFor(int[] n) {
        if (n[1] == 1) return U;
        if (n[1] == -1) return D;
        if (n[2] == -1) return N;
        if (n[2] == 1) return S;
        return n[0] == 1 ? W : E;
    }

    static float[] stripRect(int strip, int u, int v, int w, int h, int d) {
        return switch (strip) {
            case U -> new float[]{u + d, v, u + d + w, v + d};
            case D -> new float[]{u + d + w, v, u + d + 2 * w, v + d};
            case W -> new float[]{u, v + d, u + d, v + d + h};
            case N -> new float[]{u + d, v + d, u + d + w, v + d + h};
            case E -> new float[]{u + d + w, v + d, u + 2 * d + w, v + d + h};
            default -> new float[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h};
        };
    }

    /** Writes one zombie at world feet position (px,py,pz) facing {@code yaw}; returns the new index. */
    static int emitZombie(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                          float walkAmount, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ZOMBIE_TEX_W, ZOMBIE_TEX_H);
        float lift = (float) Math.sin(walkPhase) * 2f * walkAmount; // model pixels
        w.box(0, -4, 0, 8, 8, 8, 0, 0, 0, false);                     // head
        w.box(0, 6, 0, 8, 12, 4, 16, 16, 0, false);                   // torso
        // arms held straight forward: shoulder height 1.375 blocks = model y 2
        w.box(-6, 2, -6, 4, 12, 4, 40, 16, 1, false);                 // right arm
        w.box(6, 2, -6, 4, 12, 4, 32, 48, 1, true);                   // left arm
        w.box(-2, 18 - Math.max(0f, lift), 0, 4, 12, 4, 0, 16, 0, false);   // right leg
        w.box(2, 18 - Math.max(0f, -lift), 0, 4, 12, 4, 16, 48, 0, true);    // left leg
        return w.n;
    }

    static int emitPig(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                       float walkAmount, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        float swing = (float) Math.sin(walkPhase) * 3f * walkAmount;
        float lift = Math.max(0f, (float) Math.sin(walkPhase)) * 1.5f * walkAmount, liftB = Math.max(0f, -(float) Math.sin(walkPhase)) * 1.5f * walkAmount;
        w.box(0, 12, -10, 8, 8, 8, 0, 0, 0, false);                    // head
        w.box(0, 13.5f, -14.5f, 4, 3, 1, 16, 16, 0, false);            // snout
        w.box(0, 14, 0, 10, 16, 8, 28, 8, -1, false);                  // body
        w.box(-3, 21 - lift, -5 + swing, 4, 6, 4, 0, 16, 0, false);    // front right
        w.box(3, 21 - liftB, -5 - swing, 4, 6, 4, 0, 16, 0, true);     // front left
        w.box(-3, 21 - liftB, 7 - swing, 4, 6, 4, 0, 16, 0, false);    // hind right
        w.box(3, 21 - lift, 7 + swing, 4, 6, 4, 0, 16, 0, true);       // hind left
        return w.n;
    }

    static int emitCow(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                       float walkAmount, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        float swing = (float) Math.sin(walkPhase) * 3f * walkAmount;
        float lift = Math.max(0f, (float) Math.sin(walkPhase)) * 1.5f * walkAmount, liftB = Math.max(0f, -(float) Math.sin(walkPhase)) * 1.5f * walkAmount;
        w.box(0, 4, -11, 8, 8, 6, 0, 0, 0, false);                     // head
        w.box(-4.5f, 0.5f, -11.5f, 1, 3, 1, 22, 0, 0, false);          // horns
        w.box(4.5f, 0.5f, -11.5f, 1, 3, 1, 22, 0, 0, true);
        w.box(0, 7, 1, 12, 18, 10, 18, 4, -1, false);                  // body
        w.box(0, 12.5f, 7, 4, 6, 1, 52, 0, -1, false);                 // udder
        w.box(-4, 18 - lift, -6 + swing, 4, 12, 4, 0, 16, 0, false);   // front right
        w.box(4, 18 - liftB, -6 - swing, 4, 12, 4, 0, 16, 0, true);    // front left
        w.box(-4, 18 - liftB, 7 - swing, 4, 12, 4, 0, 16, 0, false);   // hind right
        w.box(4, 18 - lift, 7 + swing, 4, 12, 4, 0, 16, 0, true);      // hind left
        return w.n;
    }

    static int emitChicken(float[] out, int n, float px, float py, float pz, float yaw, float tilt, float walkPhase,
                           float walkAmount, boolean airborne, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        float swing = (float) Math.sin(walkPhase) * 2f * walkAmount;
        w.box(0, 12, -4.5f, 4, 6, 3, 0, 0, 0, false);                  // head
        w.box(0, 12, -7, 4, 2, 2, 14, 0, 0, false);                    // beak
        w.box(0, 14, -6, 2, 2, 2, 14, 4, 0, false);                    // wattle
        w.box(0, 16, 0, 6, 8, 6, 0, 9, -1, false);                     // body
        float flap = airborne ? -2.5f : 0f;                            // wings lift while falling
        w.box(-3.5f, 15 + flap, 0, 1, 4, 6, 24, 13, 0, false);         // right wing
        w.box(3.5f, 15 + flap, 0, 1, 4, 6, 24, 13, 0, true);           // left wing
        w.box(-1.5f, 21.5f - Math.max(0f, swing), -0.5f + swing, 3, 5, 3, 26, 0, 0, false);   // right leg
        w.box(1.5f, 21.5f - Math.max(0f, -swing), -0.5f - swing, 3, 5, 3, 26, 0, 0, true);    // left leg
        return w.n;
    }
}
