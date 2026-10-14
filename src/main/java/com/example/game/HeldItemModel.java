package com.example.game;

import org.joml.Matrix4f;

/**
 * First-person hand geometry and transforms, following vanilla's {@code ItemInHandRenderer} (swing offsets, the
 * 0.56 / -0.52 / -0.72 right-hand anchor, the attack rotations) and the item display transforms
 * (flat items: rotation [0,-90,25], translation [1.13,3.2,1.13]/16, scale 0.68; blocks: rotation [0,45,0], scale 0.4;
 * both from the 26.x item models). Flat items are extruded 1/16 thick like vanilla's generated item models; the bare
 * arm is the 4x12x4 px player arm. Pure maths, no GL.
 */
final class HeldItemModel {
    private HeldItemModel() {
    }

    static final int FLOATS_PER_VERTEX = 11;
    /** Half the thickness of an extruded sprite: 1/16 of a block in total. */
    static final float HALF_THICKNESS = 1f / 32f;

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    // ---- transforms (swing = 0..1 attack progress, 0 when idle) ----

    /** Anchor and attack rotations shared by every held item (vanilla applyItemArmTransform + applyItemArmAttackTransform). */
    private static Matrix4f itemBase(float swing) {
        float sqrt = (float) Math.sqrt(swing);
        float sinSqrt = (float) Math.sin(sqrt * Math.PI);
        float sinSq = (float) Math.sin(swing * swing * Math.PI);
        return new Matrix4f()
                .translate(-0.4f * sinSqrt, 0.2f * (float) Math.sin(sqrt * 2 * Math.PI), -0.2f * (float) Math.sin(swing * Math.PI))
                .translate(0.56f, -0.52f, -0.72f)
                .rotateY(rad(45f + sinSq * -20f))
                .rotateZ(rad(sinSqrt * -20f))
                .rotateX(rad(sinSqrt * -80f))
                .rotateY(rad(-45f));
    }

    /**
     * The item model JSON says rotation Y -90, but with this renderer's plane orientation that sends a tool's tip towards the
     * camera and out of the screen; +90 gives vanilla's look (tip rising toward the centre, handle in the lower-right corner),
     * checked against the in-game screenshots of the sword, pickaxe and apple.
     */
    static final float FLAT_YAW = 90f;

    /** Tools, swords, food, ingots and the other flat sprite items. */
    static Matrix4f flatItem(float swing) {
        return itemBase(swing)
                .translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
                .rotateX(0f).rotateY(rad(FLAT_YAW)).rotateZ(rad(25f))
                .scale(0.68f);
    }

    /** Full-cube blocks held in the hand. */
    static Matrix4f block(float swing) {
        return itemBase(swing).rotateY(rad(45f)).scale(0.4f);
    }

    /** The bare arm shown when the hand is empty (vanilla renderPlayerArm); the matrix expects {@link #armBox} geometry. */
    static Matrix4f emptyArm(float swing) {
        float sqrt = (float) Math.sqrt(swing);
        float sinSqrt = (float) Math.sin(sqrt * Math.PI);
        float sinSq = (float) Math.sin(swing * swing * Math.PI);
        return new Matrix4f()
                .translate(-0.3f * sinSqrt + 0.64000005f, 0.4f * (float) Math.sin(sqrt * 2 * Math.PI) - 0.6f,
                        -0.4f * (float) Math.sin(swing * Math.PI) - 0.71999997f)
                .rotateY(rad(45f))
                .rotateY(rad(sinSqrt * 70f))
                .rotateZ(rad(sinSq * -20f))
                .translate(-1.0f, 3.6f, 3.5f)
                .rotateZ(rad(120f)).rotateX(rad(200f)).rotateY(rad(-135f))
                .translate(5.6f, 0f, 0f);
    }

    // ---- meshes (11 floats per vertex: x y z u v sky blockLight r g b wind) ----

    private static int vertex(float[] a, int i, float x, float y, float z, float u, float v, float shade, float[] tint) {
        a[i++] = x;
        a[i++] = y;
        a[i++] = z;
        a[i++] = u;
        a[i++] = v;
        a[i++] = 0f;     // sky: the held item ignores day/night
        a[i++] = shade;  // baked brightness rides in the block-light channel, which daylight does not scale
        a[i++] = tint[0];
        a[i++] = tint[1];
        a[i++] = tint[2];
        a[i++] = 0f;     // no wind
        return i;
    }

    /** One quad as two triangles; corners are p0..p3 in order with per-corner texture coordinates. */
    private static int quad(float[] a, int i, float[][] p, float[][] uv, float shade, float[] tint) {
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) i = vertex(a, i, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1], shade, tint);
        return i;
    }

    /**
     * A flat sprite extruded 1/16 of a block, centred on the origin and spanning -0.5..0.5 in x and y: a front and a back
     * face carrying the whole tile, plus a thin side quad (sampling that pixel's colour) wherever an opaque pixel borders
     * a transparent one, so tools show the same stepped edge as vanilla.
     *
     * @param opaque 16x16 row-major (top row first): true where the sprite has a pixel
     * @param uv     the tile's texture rectangle {u0, v0, u1, v1}
     */
    static float[] extrudedSprite(boolean[] opaque, float[] uv) {
        float[] tint = {1f, 1f, 1f};
        float t = HALF_THICKNESS;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int sides = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!opaque[y * 16 + x]) continue;
                if (!at(opaque, x - 1, y)) sides++;
                if (!at(opaque, x + 1, y)) sides++;
                if (!at(opaque, x, y - 1)) sides++;
                if (!at(opaque, x, y + 1)) sides++;
            }
        }
        float[] out = new float[(2 + sides) * 6 * FLOATS_PER_VERTEX];
        int i = 0;
        i = quad(out, i, new float[][]{{-.5f, -.5f, t}, {.5f, -.5f, t}, {.5f, .5f, t}, {-.5f, .5f, t}},
                new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, 1f, tint);
        i = quad(out, i, new float[][]{{.5f, -.5f, -t}, {-.5f, -.5f, -t}, {-.5f, .5f, -t}, {.5f, .5f, -t}},
                new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, 1f, tint);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!opaque[y * 16 + x]) continue;
                float xl = -0.5f + x / 16f, xr = xl + 1f / 16f, yt = 0.5f - y / 16f, yb = yt - 1f / 16f;
                float uc = u0 + (x + 0.5f) / 16f * (u1 - u0), vc = v0 + (y + 0.5f) / 16f * (v1 - v0);
                float[][] same = {{uc, vc}, {uc, vc}, {uc, vc}, {uc, vc}};
                if (!at(opaque, x - 1, y)) i = quad(out, i, new float[][]{{xl, yb, -t}, {xl, yb, t}, {xl, yt, t}, {xl, yt, -t}}, same, 0.75f, tint);
                if (!at(opaque, x + 1, y)) i = quad(out, i, new float[][]{{xr, yb, t}, {xr, yb, -t}, {xr, yt, -t}, {xr, yt, t}}, same, 0.75f, tint);
                if (!at(opaque, x, y - 1)) i = quad(out, i, new float[][]{{xl, yt, t}, {xr, yt, t}, {xr, yt, -t}, {xl, yt, -t}}, same, 1f, tint);
                if (!at(opaque, x, y + 1)) i = quad(out, i, new float[][]{{xl, yb, -t}, {xr, yb, -t}, {xr, yb, t}, {xl, yb, t}}, same, 0.6f, tint);
            }
        }
        return out;
    }

    private static boolean at(boolean[] m, int x, int y) {
        return x >= 0 && y >= 0 && x < 16 && y < 16 && m[y * 16 + x];
    }

    /**
     * The right player arm in the model space {@link #emptyArm} expects: the 4x12x4 px cube with the arm's part offset
     * (-5,2,0) applied, in blocks, painted with one tile (skin tone) and face-shaded.
     */
    static float[] armBox(float[] uv) {
        float x0 = -8f / 16f, x1 = -4f / 16f, y0 = 0f, y1 = 12f / 16f, z0 = -2f / 16f, z1 = 2f / 16f;
        float[] tint = {1f, 1f, 1f};
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[][] c = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        float[] out = new float[6 * 6 * FLOATS_PER_VERTEX];
        int i = 0;
        i = quad(out, i, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, c, 0.85f, tint);
        i = quad(out, i, new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, c, 0.85f, tint);
        i = quad(out, i, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, c, 0.7f, tint);
        i = quad(out, i, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, c, 0.7f, tint);
        i = quad(out, i, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, c, 1f, tint);
        i = quad(out, i, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, c, 0.6f, tint);
        return out;
    }
}
