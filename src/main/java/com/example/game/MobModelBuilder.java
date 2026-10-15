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

    private static final java.util.Map<String, int[]> SKIN_SIZES = new java.util.HashMap<>();

    /**
     * Records the real pixel size of a loaded skin so the box UVs are normalised by it. Minecraft 1.21.5 made the pig and
     * cow skins larger than the old 64x32; a box UV is a pixel position, so dividing by the real size keeps it pointing at
     * the same pixel (it assumes the old layout still anchors at the top-left).
     */
    static void setSkinSize(String species, int w, int h) {
        SKIN_SIZES.put(species, new int[]{w, h});
    }

    static int skinW(String species) {
        int[] s = SKIN_SIZES.get(species);
        return s == null ? ANIMAL_TEX_W : s[0];
    }

    static int skinH(String species) {
        int[] s = SKIN_SIZES.get(species);
        return s == null ? ANIMAL_TEX_H : s[1];
    }

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
    static final class Writer {
        final float[] out;
        int n;
        final float px, py, pz, cos, sin, tiltCos, tiltSin, sky, blockLight, texW, texH;
        final float[] tint;
        float scale = 1f;
        float scaleY = -1f; // vertical scale; negative = same as {@link #scale}
        /** Grows every box by this many model pixels on each side without changing its texture layout (armor, overlays). */
        float inflate;
        /** Optional transform of a posed part (the head turning), in the mob's local frame in blocks; null = none. */
        org.joml.Matrix4f part;

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
            float sv = scaleY < 0f ? scale : scaleY;
            cx *= scale;
            cy *= sv;
            cz *= scale;
            float lhx = (w / 32f + inflate / 16f) * scale, lhy = (h / 32f + inflate / 16f) * sv, lhz = (d / 32f + inflate / 16f) * scale;
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
                    vertex(cx + c[0] * hx, cy + c[1] * hy, cz + c[2] * hz, tu, tv, f);
                }
            }
        }

        /**
         * The transform of a joint: a rotation about a pivot given in vanilla model pixels (x, y-down, z), with the
         * ModelPart angles in radians (vanilla applies z, then y, then x; here the model frame is mirrored into ours, so x and y
         * turn the other way). Assign it to {@link #part} before the boxes that belong to the part.
         */
        org.joml.Matrix4f joint(float pmx, float pmy, float pmz, float xRot, float yRot, float zRot) {
            float sv = scaleY < 0f ? scale : scaleY;
            float x = -pmx / 16f * scale, y = (24f - pmy) / 16f * sv, z = pmz / 16f * scale;
            return new org.joml.Matrix4f().translate(x, y, z).rotateZ(zRot).rotateY(-yRot).rotateX(-xRot).translate(-x, -y, -z);
        }

        /** Writes one vertex given a position in the mob's local frame (feet origin, front = -Z). */
        void vertex(float lx, float ly, float lz, float tu, float tv, int face) {
            if (part != null) {
                org.joml.Vector3f v = part.transformPosition(new org.joml.Vector3f(lx, ly, lz));
                lx = v.x;
                ly = v.y;
                lz = v.z;
            }
            float rx = lx * tiltCos - ly * tiltSin;  // roll about the forward (Z) axis for the death topple
            float ry = lx * tiltSin + ly * tiltCos;
            out[n++] = px + rx * cos - lz * sin;
            out[n++] = py + ry;
            out[n++] = pz + rx * sin + lz * cos;
            out[n++] = tu;
            out[n++] = tv;
            out[n++] = sky; // the face shading comes from the shader (entity lighting from the face normal)
            out[n++] = blockLight;
            out[n++] = tint[0];
            out[n++] = tint[1];
            out[n++] = tint[2];
            out[n++] = 0f;
        }

        /**
         * A spider leg: a {@code lengthPx} x 2 x 2 box starting at a pivot (mob-local blocks), pointing out along the
         * side {@code s} (+1 = +X) and rolled down by {@code roll} and swung in the horizontal plane by {@code yaw}
         * (radians, positive = toward the front for the +X side). Its faces all sample the leg strip of the skin.
         */
        void leg(float pivotX, float pivotY, float pivotZ, float s, float roll, float yaw, float lengthPx) {
            float half = lengthPx / 32f, t = 1f / 16f;
            float phi = -s * roll, cphi = (float) Math.cos(phi), sphi = (float) Math.sin(phi);
            float cth = (float) Math.cos(yaw), sth = (float) Math.sin(yaw);
            float[] side = {20, 2, 36, 4}, top = {20, 0, 36, 2}, cap = {18, 2, 20, 4};
            for (int f = 0; f < 6; f++) {
                float[] rect = f < 2 ? top : f < 4 ? side : cap;
                for (int k : ORDER) {
                    int[] c = FACES[f][k];
                    float lx = s * half + c[0] * half, ly = c[1] * t, lz = c[2] * t;
                    float u01, v01;
                    if (f < 2) {
                        u01 = (c[0] * s + 1) / 2f;
                        v01 = (c[2] + 1) / 2f;
                    } else if (f < 4) {
                        u01 = (c[0] * s + 1) / 2f;
                        v01 = 1f - (c[1] + 1) / 2f;
                    } else {
                        u01 = (c[2] + 1) / 2f;
                        v01 = 1f - (c[1] + 1) / 2f;
                    }
                    float rx = lx * cphi - ly * sphi, ry = lx * sphi + ly * cphi, rz = lz; // roll about Z
                    float yx = rx * cth + rz * sth, yz = -rx * sth + rz * cth;               // swing about Y
                    vertex((pivotX + yx) * scale, pivotY + ry, (pivotZ + yz) * scale,
                            (rect[0] + u01 * (rect[2] - rect[0])) / texW, (rect[1] + v01 * (rect[3] - rect[1])) / texH, f);
                }
            }
        }
    }

    /** A writer at the origin for the player model: {@code yaw} turns the whole body (0 = facing -Z). */
    static Writer writer(float[] out, int n, float yaw, float sky, float[] tint, int texW, int texH) {
        return new Writer(out, n, 0f, 0f, 0f, yaw, 0f, sky, 0f, tint, texW, texH);
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

    /**
     * What a model needs to pose itself, all as vanilla's render state gives it: the walk animation position and speed
     * (legs swing by cos(position * 0.6662) * 1.4 * speed), the head's turn against the body and its pitch (radians), the age
     * in ticks for the idle arm sway, whether the mob is attacking, and the chicken's wing flap.
     */
    record Pose(float limbPos, float limbSpeed, float headYaw, float headPitch, float ageTicks, boolean aggressive,
                float flap, float flapSpeed) {
        static final Pose REST = new Pose(0f, 0f, 0f, 0f, 0f, false, 0f, 0f);
    }

    /** QuadrupedModel / CreeperModel leg angles: {rightHind, leftHind, rightFront, leftFront}. */
    static float[] quadLegAngles(float pos, float speed) {
        float a = pos * 0.6662f, s = 1.4f * speed, pi = (float) Math.PI;
        return new float[]{(float) Math.cos(a) * s, (float) Math.cos(a + pi) * s, (float) Math.cos(a + pi) * s, (float) Math.cos(a) * s};
    }

    /** HumanoidModel walking angles: {rightArm, leftArm, rightLeg, leftLeg}. */
    static float[] humanoidSwing(float pos, float speed) {
        float a = pos * 0.6662f, pi = (float) Math.PI;
        return new float[]{(float) Math.cos(a + pi) * speed, (float) Math.cos(a) * speed,
                (float) Math.cos(a) * 1.4f * speed, (float) Math.cos(a + pi) * 1.4f * speed};
    }

    /** AnimationUtils.bobArms: the idle drift of a raised or hanging arm; {@code sign} is +1 for the right arm, -1 for the left. */
    static float armBobX(float ageTicks, float sign) {
        return sign * (float) Math.sin(ageTicks * 0.067f) * 0.05f;
    }

    static float armBobZ(float ageTicks, float sign) {
        return sign * ((float) Math.cos(ageTicks * 0.09f) * 0.05f + 0.05f);
    }

    /** The zombie's arms (AnimationUtils.animateZombieArms): raised -80 degrees, or -120 when attacking, turned 0.1 inwards. */
    static float zombieArmRaise(boolean aggressive) {
        return -(float) Math.PI / (aggressive ? 1.5f : 2.25f);
    }

    /** Writes one zombie at world feet position (px,py,pz) facing {@code yaw}; returns the new index. */
    static int emitZombie(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                          float sky, float blockLight, float[] tint) {
        return emitZombie(out, n, px, py, pz, yaw, tilt, pose, sky, blockLight, tint, 1f);
    }

    /** Same with a uniform {@code scale} (0.5 for baby zombies). */
    static int emitZombie(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                          float sky, float blockLight, float[] tint, float scale) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ZOMBIE_TEX_W, ZOMBIE_TEX_H);
        w.scale = scale;
        float[] swing = humanoidSwing(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 0, 0, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, -4, 0, 8, 8, 8, 0, 0, 0, false);                                    // head
        w.part = null;
        w.box(0, 6, 0, 8, 12, 4, 16, 16, 0, false);                                  // torso
        float raise = zombieArmRaise(pose.aggressive());
        w.part = w.joint(-5, 2, 0, raise + armBobX(pose.ageTicks(), 1f), -0.1f, armBobZ(pose.ageTicks(), 1f));
        w.box(-6, 6, 0, 4, 12, 4, 40, 16, 0, false);                                 // right arm
        w.part = w.joint(5, 2, 0, raise + armBobX(pose.ageTicks(), -1f), 0.1f, armBobZ(pose.ageTicks(), -1f));
        w.box(6, 6, 0, 4, 12, 4, 32, 48, 0, true);                                   // left arm
        w.part = w.joint(-1.9f, 12, 0, swing[2], 0, 0);
        w.box(-1.9f, 18, 0, 4, 12, 4, 0, 16, 0, false);                              // right leg
        w.part = w.joint(1.9f, 12, 0, swing[3], 0, 0);
        w.box(1.9f, 18, 0, 4, 12, 4, 16, 48, 0, true);                               // left leg
        w.part = null;
        return w.n;
    }

    /** Creeper with the vanilla swell pulse: horizontal scale (1 + 0.4 q) w, vertical (1 + 0.1 q) / w, q = s^4. */
    static int emitCreeper(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                           float swell, float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        float wobble = 1f + (float) Math.sin(100f * swell) * swell * 0.01f;
        float q = swell * swell * swell * swell;
        w.scale = (1f + 0.4f * q) * wobble;
        w.scaleY = (1f + 0.1f * q) / wobble;
        float[] leg = quadLegAngles(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 6, 0, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 2, 0, 8, 8, 8, 0, 0, 0, false);                                     // head
        w.part = null;
        w.box(0, 12, 0, 8, 12, 4, 16, 16, 0, false);                                 // body
        w.part = w.joint(-2, 18, -4, leg[2], 0, 0);
        w.box(-2, 21, -4, 4, 6, 4, 0, 16, 0, false);                                 // front right
        w.part = w.joint(2, 18, -4, leg[3], 0, 0);
        w.box(2, 21, -4, 4, 6, 4, 0, 16, 0, true);                                   // front left
        w.part = w.joint(-2, 18, 4, leg[0], 0, 0);
        w.box(-2, 21, 4, 4, 6, 4, 0, 16, 0, false);                                  // hind right
        w.part = w.joint(2, 18, 4, leg[1], 0, 0);
        w.box(2, 21, 4, 4, 6, 4, 0, 16, 0, true);                                    // hind left
        w.part = null;
        return w.n;
    }

    /**
     * The skeleton: legs and arms swing as a humanoid's; while attacking both arms are raised to hold the bow (right arm
     * yaw -0.1 + head yaw, left 0.1 + head yaw + 0.4, both -90 degrees + head pitch), otherwise the right arm rests
     * at half its swing minus 18 degrees (the held-item pose).
     */
    static int emitSkeleton(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                            float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        float[] swing = humanoidSwing(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 0, 0, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, -4, 0, 8, 8, 8, 0, 0, 0, false);                                    // head
        w.part = null;
        w.box(0, 6, 0, 8, 12, 4, 16, 16, 0, false);                                  // ribcage
        float rx, ry, lx, ly;
        if (pose.aggressive()) {
            rx = lx = -(float) Math.PI / 2f + pose.headPitch();
            ry = -0.1f + pose.headYaw();
            ly = 0.1f + pose.headYaw() + 0.4f;
        } else {
            rx = swing[0] * 0.5f - (float) Math.PI / 10f;
            lx = swing[1];
            ry = 0f;
            ly = 0f;
        }
        w.part = w.joint(-5, 2, 0, rx + armBobX(pose.ageTicks(), 1f), ry, armBobZ(pose.ageTicks(), 1f));
        w.box(-5, 6, 0, 2, 12, 2, 40, 16, 0, false);                                 // right arm
        w.part = w.joint(5, 2, 0, lx + armBobX(pose.ageTicks(), -1f), ly, armBobZ(pose.ageTicks(), -1f));
        w.box(5, 6, 0, 2, 12, 2, 40, 16, 0, true);                                   // left arm
        w.part = w.joint(-2, 12, 0, swing[2], 0, 0);
        w.box(-2, 18, 0, 2, 12, 2, 0, 16, 0, false);
        w.part = w.joint(2, 12, 0, swing[3], 0, 0);
        w.box(2, 18, 0, 2, 12, 2, 0, 16, 0, true);
        w.part = null;
        return w.n;
    }

    /**
     * SpiderModel.setupAnim: every leg pair starts from its resting yaw and roll and the walk adds a swing,
     * {@code -cos(2 a + phase) * 0.4 * speed} to the yaw and a lift of {@code |sin(a + phase) * 0.4| * speed} to the roll,
     * with a = position * 0.6662 and the four pairs a quarter of a cycle apart (0, 180, 90, 270 degrees); the head follows
     * the look direction.
     */
    static int emitSpider(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                          float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ANIMAL_TEX_W, ANIMAL_TEX_H);
        w.part = w.joint(0, 15, -3, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 15, -7, 8, 8, 8, 32, 4, 0, false);                    // head
        w.part = null;
        w.box(0, 15, 0, 6, 6, 6, 0, 0, 0, false);                      // neck
        w.box(0, 15, 9, 10, 8, 12, 0, 12, 0, false);                   // abdomen
        float[] yawBase = {-45f, -22.5f, 22.5f, 45f};
        float[] roll = {45f, 33.3f, 33.3f, 45f};
        float[] phases = {0f, (float) Math.PI, (float) Math.PI / 2f, (float) Math.PI * 1.5f};
        float a = pose.limbPos() * 0.6662f;
        for (int i = 0; i < 4; i++) {
            float z = (2 - i) / 16f;
            float swing = (float) Math.cos(a * 2f + phases[i]) * 0.4f * pose.limbSpeed();
            float lift = Math.abs((float) Math.sin(a + phases[i]) * 0.4f) * pose.limbSpeed();
            for (int side = -1; side <= 1; side += 2) {
                w.leg(side * 0.25f, 0.5625f, z, side, (float) Math.toRadians(roll[i]) - lift,
                        side * ((float) Math.toRadians(yawBase[i]) + swing), 16f);
            }
        }
        return w.n;
    }

    /**
     * BabyZombieModel: its own small boxes (head 6 cubed with an 0.25 outer layer, a 4x5x2 body, 2x5x2 arms, 2x4x2 legs), posed
     * like the adult - head turn, arms raised, legs swinging as a humanoid's.
     */
    static int emitBabyZombie(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                              float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, ZOMBIE_TEX_W, ZOMBIE_TEX_H);
        float[] swing = humanoidSwing(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 15.25f, 0, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 12f, 0, 6, 6, 6, 3, 3, 0, false);                                   // head
        w.inflate = 0.25f;
        w.box(0, 12.1f, 0, 6, 6, 6, 35, 3, 0, false);                                // outer head layer
        w.inflate = 0f;
        w.part = null;
        w.box(0, 17.5f, 0, 4, 5, 2, 16, 16, 0, false);                               // body
        float raise = zombieArmRaise(pose.aggressive());
        w.part = w.joint(-3, 15.5f, 0, raise + armBobX(pose.ageTicks(), 1f), -0.1f, armBobZ(pose.ageTicks(), 1f));
        w.box(-3, 18f, 0, 2, 5, 2, 36, 16, 0, false);                                // right arm
        w.part = w.joint(3, 15.5f, 0, raise + armBobX(pose.ageTicks(), -1f), 0.1f, armBobZ(pose.ageTicks(), -1f));
        w.box(3, 18f, 0, 2, 5, 2, 28, 16, 0, false);                                 // left arm
        w.part = w.joint(-1, 20, 0, swing[2], 0, 0);
        w.box(-1, 22, 0, 2, 4, 2, 8, 16, 0, false);                                  // right leg
        w.part = w.joint(1, 20, 0, swing[3], 0, 0);
        w.box(1, 22, 0, 2, 4, 2, 0, 16, 0, false);                                   // left leg
        w.part = null;
        return w.n;
    }

    /** One arrow as two crossed quads along {@code dir}, sampling the shaft strip of the 32x32 arrow texture. */
    static int emitArrow(float[] out, int n, float x, float y, float z, float dx, float dy, float dz, float sky, float blockLight) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return n;
        dx /= len;
        dy /= len;
        dz /= len;
        float ux = Math.abs(dy) > 0.95f ? 1f : 0f, uy = Math.abs(dy) > 0.95f ? 0f : 1f;
        float ax = dy * 0f - dz * uy, ay = dz * ux - dx * 0f, az = dx * uy - dy * ux; // d x up
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al;
        ay /= al;
        az /= al;
        float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax; // d x a
        float half = 0.45f, wd = 0.07f;
        float[][] planes = {{ax, ay, az}, {bx, by, bz}};
        float[][] uvc = {{0, 5}, {16, 5}, {16, 0}, {0, 0}};
        int[] order = {0, 1, 2, 0, 2, 3};
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] p : planes) {
            for (int k : order) {
                float along = corner[k][0] * half, across = corner[k][1] * wd;
                out[n++] = x + dx * along + p[0] * across;
                out[n++] = y + dy * along + p[1] * across;
                out[n++] = z + dz * along + p[2] * across;
                out[n++] = uvc[k][0] / 32f;
                out[n++] = uvc[k][1] / 32f;
                out[n++] = sky;
                out[n++] = blockLight;
                out[n++] = 1f;
                out[n++] = 1f;
                out[n++] = 1f;
                out[n++] = 0f;
            }
        }
        return n;
    }

    /**
     * The baby pig, cow and chicken are dedicated models in 26.x (BabyPigModel, BabyCowModel, BabyChickenModel), not the
     * adult scaled down. Every box below is read from those classes in the client jar: the part's offset plus the box's
     * corner give the centre, the box size and texture offset are used as they are, and the part offsets are the joints the
     * head and legs turn about. Texture sizes: pig 32x32, cow 64x64, chicken 16x16.
     */
    static int emitBabyPig(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                           float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("pig_baby"), skinH("pig_baby"));
        float[] leg = quadLegAngles(pose.limbPos(), pose.limbSpeed());
        w.box(0, 19, 0.5f, 7, 6, 9, 0, 0, 0, false);                                 // body
        w.part = w.joint(0, 19, -2, pose.headPitch(), pose.headYaw(), 0);
        w.inflate = 0.025f;
        w.box(0, 17, -4, 7, 6, 6, 0, 15, 0, false);                                  // head
        w.inflate = 0.015f;
        w.box(0, 18.025f, -7.5f, 3, 2, 1, 6, 27, 0, false);                          // snout
        w.inflate = 0f;
        w.part = w.joint(2.5f, 22, -3, leg[3], 0, 0);
        w.box(2.5f, 23, -3, 2, 2, 2, 0, 0, 0, false);                                // left front leg
        w.part = w.joint(-2.5f, 22, -3, leg[2], 0, 0);
        w.box(-2.5f, 23, -3, 2, 2, 2, 23, 0, 0, false);                              // right front leg
        w.part = w.joint(2.5f, 22, 4, leg[1], 0, 0);
        w.box(2.5f, 23, 4, 2, 2, 2, 0, 4, 0, false);                                 // left hind leg
        w.part = w.joint(-2.5f, 22, 4, leg[0], 0, 0);
        w.box(-2.5f, 23, 4, 2, 2, 2, 23, 4, 0, false);                               // right hind leg
        w.part = null;
        return w.n;
    }

    static int emitBabyCow(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                           float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("cow_baby"), skinH("cow_baby"));
        float[] leg = quadLegAngles(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 13.569f, -5.1667f, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 12, -7.5f, 6, 6, 5, 0, 18, 0, false);                               // head
        w.box(3.5f, 9, -8.5f, 1, 2, 1, 8, 29, 0, false);                             // horn
        w.box(-3.5f, 9, -8.5f, 1, 2, 1, 4, 29, 0, true);                             // horn (mirrored)
        w.box(0, 13.5f, -10.5f, 4, 3, 1, 12, 29, 0, false);                          // muzzle
        w.part = null;
        w.box(0, 15, 0, 8, 6, 12, 0, 0, 0, false);                                   // body
        w.part = w.joint(-2.5f, 18, -3.5f, leg[2], 0, 0);
        w.box(-2.5f, 21, -3.5f, 3, 6, 3, 22, 18, 0, false);                          // right front leg
        w.part = w.joint(2.5f, 18, -3.5f, leg[3], 0, 0);
        w.box(2.5f, 21, -3.5f, 3, 6, 3, 34, 18, 0, false);                           // left front leg
        w.part = w.joint(-2.5f, 18, 3.5f, leg[0], 0, 0);
        w.box(-2.5f, 21, 3.5f, 3, 6, 3, 22, 27, 0, false);                           // right hind leg
        w.part = w.joint(2.5f, 18, 3.5f, leg[1], 0, 0);
        w.box(2.5f, 21, 3.5f, 3, 6, 3, 34, 27, 0, false);                            // left hind leg
        w.part = null;
        return w.n;
    }

    /** The chicken's wings open by (sin(flap) + 1) * flapSpeed; the legs swing as a chicken's (cos, 1.4 * speed). */
    static float wingAngle(Pose pose) {
        return ((float) Math.sin(pose.flap()) + 1f) * pose.flapSpeed();
    }

    static float[] chickenLegAngles(Pose pose) {
        float a = pose.limbPos() * 0.6662f;
        return new float[]{(float) Math.cos(a) * 1.4f * pose.limbSpeed(), (float) Math.cos(a + Math.PI) * 1.4f * pose.limbSpeed()};
    }

    static int emitBabyChicken(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                               float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("chicken_baby"), skinH("chicken_baby"));
        float[] leg = chickenLegAngles(pose);
        float wing = wingAngle(pose);
        w.box(0, 20, 0, 4, 4, 4, 0, 0, 0, false);                                    // body (the chick has no separate head)
        w.box(0, 20.5f, -2.5f, 2, 1, 1, 10, 8, 0, false);                            // beak
        w.part = w.joint(1, 22, 0.5f, leg[1], 0, 0);
        w.box(1, 23, 0.5f, 1, 2, 0, 2, 2, 0, false);                                 // left leg
        w.box(1, 24, 0, 1, 0, 1, 0, 1, 0, false);                                    // left foot
        w.part = w.joint(-1, 22, 0.5f, leg[0], 0, 0);
        w.box(-1, 23, 0.5f, 1, 2, 0, 0, 2, 0, false);                                // right leg
        w.box(-1, 24, 0, 1, 0, 1, 0, 0, 0, false);                                   // right foot
        w.part = w.joint(2, 20, 0, 0, 0, wing);
        w.box(2.5f, 20, 0, 1, 0, 2, 6, 8, 0, false);                                 // right wing
        w.part = w.joint(-2, 20, 0, 0, 0, -wing);
        w.box(-2.5f, 20, 0, 1, 0, 2, 4, 8, 0, false);                                // left wing
        w.part = null;
        return w.n;
    }

    static int emitPig(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                       float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("pig"), skinH("pig"));
        float[] leg = quadLegAngles(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 12, -6, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 12, -10, 8, 8, 8, 0, 0, 0, false);                                  // head
        w.box(0, 13.5f, -14.5f, 4, 3, 1, 16, 16, 0, false);                          // snout
        w.part = null;
        w.box(0, 14, 0, 10, 16, 8, 28, 8, -1, false);                                // body
        w.part = w.joint(-3, 18, -5, leg[2], 0, 0);
        w.box(-3, 21, -5, 4, 6, 4, 0, 16, 0, false);                                 // front right
        w.part = w.joint(3, 18, -5, leg[3], 0, 0);
        w.box(3, 21, -5, 4, 6, 4, 0, 16, 0, true);                                   // front left
        w.part = w.joint(-3, 18, 7, leg[0], 0, 0);
        w.box(-3, 21, 7, 4, 6, 4, 0, 16, 0, false);                                  // hind right
        w.part = w.joint(3, 18, 7, leg[1], 0, 0);
        w.box(3, 21, 7, 4, 6, 4, 0, 16, 0, true);                                    // hind left
        w.part = null;
        return w.n;
    }

    static int emitCow(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                       float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("cow"), skinH("cow"));
        float[] leg = quadLegAngles(pose.limbPos(), pose.limbSpeed());
        w.part = w.joint(0, 4, -8, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 4, -11, 8, 8, 6, 0, 0, 0, false);                                   // head
        w.box(-4.5f, 0.5f, -11.5f, 1, 3, 1, 22, 0, 0, false);                        // horns
        w.box(4.5f, 0.5f, -11.5f, 1, 3, 1, 22, 0, 0, true);
        w.part = null;
        w.box(0, 7, 1, 12, 18, 10, 18, 4, -1, false);                                // body
        w.box(0, 12.5f, 7, 4, 6, 1, 52, 0, -1, false);                               // udder
        w.part = w.joint(-4, 12, -5, leg[2], 0, 0);
        w.box(-4, 18, -5, 4, 12, 4, 0, 16, 0, false);                                // front right
        w.part = w.joint(4, 12, -5, leg[3], 0, 0);
        w.box(4, 18, -5, 4, 12, 4, 0, 16, 0, true);                                  // front left
        w.part = w.joint(-4, 12, 7, leg[0], 0, 0);
        w.box(-4, 18, 7, 4, 12, 4, 0, 16, 0, false);                                 // hind right
        w.part = w.joint(4, 12, 7, leg[1], 0, 0);
        w.box(4, 18, 7, 4, 12, 4, 0, 16, 0, true);                                   // hind left
        w.part = null;
        return w.n;
    }

    static int emitChicken(float[] out, int n, float px, float py, float pz, float yaw, float tilt, Pose pose,
                           float sky, float blockLight, float[] tint) {
        Writer w = new Writer(out, n, px, py, pz, yaw, tilt, sky, blockLight, tint, skinW("chicken"), skinH("chicken"));
        float[] leg = chickenLegAngles(pose);
        float wing = wingAngle(pose);
        w.part = w.joint(0, 15, -4, pose.headPitch(), pose.headYaw(), 0);
        w.box(0, 12, -4.5f, 4, 6, 3, 0, 0, 0, false);                                // head
        w.box(0, 12, -7, 4, 2, 2, 14, 0, 0, false);                                  // beak
        w.box(0, 14, -6, 2, 2, 2, 14, 4, 0, false);                                  // wattle
        w.part = null;
        w.box(0, 16, 0, 6, 8, 6, 0, 9, -1, false);                                   // body
        w.part = w.joint(-4, 13, 0, 0, 0, wing);
        w.box(-3.5f, 15, 0, 1, 4, 6, 24, 13, 0, false);                              // right wing
        w.part = w.joint(4, 13, 0, 0, 0, -wing);
        w.box(3.5f, 15, 0, 1, 4, 6, 24, 13, 0, true);                                // left wing
        w.part = w.joint(-2, 19, 1, leg[0], 0, 0);
        w.box(-1.5f, 21.5f, -0.5f, 3, 5, 3, 26, 0, 0, false);                        // right leg
        w.part = w.joint(1, 19, 1, leg[1], 0, 0);
        w.box(1.5f, 21.5f, -0.5f, 3, 5, 3, 26, 0, 0, true);                          // left leg
        w.part = null;
        return w.n;
    }
}
