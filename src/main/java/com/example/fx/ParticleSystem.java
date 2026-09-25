package com.example.fx;

import org.joml.Vector3f;

import java.util.Random;

/**
 * Block-breaking fragments: a fixed-capacity pool of small camera-facing quads. Struct-of-arrays
 * with swap-remove, so spawning, updating and expiring never allocate. Headless - it produces
 * vertex data ({@link #buildVertices}) but never touches GL, so a rendering failure can't affect
 * the break transaction and the whole thing is unit-testable.
 *
 * Every tunable is a named constant here: capacity, burst size, lifetime, gravity, spread, size.
 */
public class ParticleSystem {
    public static final int MAX_PARTICLES = 512;
    public static final int BURST_COUNT = 14;
    static final float LIFETIME_MIN = 0.35f;
    static final float LIFETIME_MAX = 0.8f;
    static final float GRAVITY = 16f;
    static final float SPEED = 1.8f;
    static final float UP_BOOST = 1.2f;
    static final float SIZE_MIN = 0.05f;
    static final float SIZE_MAX = 0.11f;
    /** Floats per vertex - matches Game's CHUNK_VERTEX_LAYOUT (3+2+1+1+3+1). */
    public static final int FLOATS_PER_VERTEX = 11;

    private final float[] px = new float[MAX_PARTICLES], py = new float[MAX_PARTICLES], pz = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES], vy = new float[MAX_PARTICLES], vz = new float[MAX_PARTICLES];
    private final float[] life = new float[MAX_PARTICLES], size = new float[MAX_PARTICLES];
    private final float[] u0 = new float[MAX_PARTICLES], v0 = new float[MAX_PARTICLES];
    private final float[] u1 = new float[MAX_PARTICLES], v1 = new float[MAX_PARTICLES];
    private final float[] tr = new float[MAX_PARTICLES], tg = new float[MAX_PARTICLES], tb = new float[MAX_PARTICLES];
    private final float[] sky = new float[MAX_PARTICLES], blk = new float[MAX_PARTICLES];
    private int count = 0;

    public int count() {
        return count;
    }

    public void clear() {
        count = 0;
    }

    /**
     * One burst of {@link #BURST_COUNT} fragments from a broken block. {@code tileUv} is the
     * source texture's {u0,v0,u1,v1}; each fragment samples a random quarter-size patch of it so
     * a burst looks like chips of that block. Returns how many were actually spawned (fewer when
     * the pool is full - the oldest particles are never evicted, the burst is just truncated).
     */
    public int spawnBurst(float cx, float cy, float cz, float[] tileUv, float[] tint,
                          float skyShade, float blockLight, Random random) {
        int spawned = 0;
        for (int n = 0; n < BURST_COUNT && count < MAX_PARTICLES; n++, spawned++) {
            int i = count++;
            px[i] = cx + (random.nextFloat() - 0.5f) * 0.8f;
            py[i] = cy + (random.nextFloat() - 0.5f) * 0.8f;
            pz[i] = cz + (random.nextFloat() - 0.5f) * 0.8f;
            vx[i] = (random.nextFloat() - 0.5f) * 2f * SPEED;
            vy[i] = random.nextFloat() * SPEED + UP_BOOST;
            vz[i] = (random.nextFloat() - 0.5f) * 2f * SPEED;
            life[i] = LIFETIME_MIN + random.nextFloat() * (LIFETIME_MAX - LIFETIME_MIN);
            size[i] = SIZE_MIN + random.nextFloat() * (SIZE_MAX - SIZE_MIN);

            float w = tileUv[2] - tileUv[0], h = tileUv[3] - tileUv[1];
            float ou = random.nextInt(4) * 0.25f, ov = random.nextInt(4) * 0.25f;
            u0[i] = tileUv[0] + w * ou;
            v0[i] = tileUv[1] + h * ov;
            u1[i] = u0[i] + w * 0.25f;
            v1[i] = v0[i] + h * 0.25f;

            tr[i] = tint[0];
            tg[i] = tint[1];
            tb[i] = tint[2];
            sky[i] = skyShade;
            blk[i] = blockLight;
        }
        return spawned;
    }

    public void update(float dt) {
        for (int i = count - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0f) {
                removeAt(i);
                continue;
            }
            vy[i] -= GRAVITY * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
        }
    }

    private void removeAt(int i) {
        int last = --count;
        if (i == last) return;
        px[i] = px[last]; py[i] = py[last]; pz[i] = pz[last];
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        life[i] = life[last]; size[i] = size[last];
        u0[i] = u0[last]; v0[i] = v0[last]; u1[i] = u1[last]; v1[i] = v1[last];
        tr[i] = tr[last]; tg[i] = tg[last]; tb[i] = tb[last];
        sky[i] = sky[last]; blk[i] = blk[last];
    }

    /** Capacity needed by {@link #buildVertices}'s output array. */
    public static int maxVertexFloats() {
        return MAX_PARTICLES * 6 * FLOATS_PER_VERTEX;
    }

    /**
     * Writes 6 vertices per live particle (two camera-facing triangles) into {@code out} and
     * returns the number of floats written. {@code out} must be at least
     * {@link #maxVertexFloats()} long and is meant to be reused across frames.
     */
    public int buildVertices(float[] out, Vector3f camRight, Vector3f camUp) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            float s = size[i];
            float rx = camRight.x * s, ry = camRight.y * s, rz = camRight.z * s;
            float ux = camUp.x * s, uy = camUp.y * s, uz = camUp.z * s;
            // corners: bottom-left, bottom-right, top-right, top-left
            n = vert(out, n, px[i] - rx - ux, py[i] - ry - uy, pz[i] - rz - uz, u0[i], v1[i], i);
            n = vert(out, n, px[i] + rx - ux, py[i] + ry - uy, pz[i] + rz - uz, u1[i], v1[i], i);
            n = vert(out, n, px[i] + rx + ux, py[i] + ry + uy, pz[i] + rz + uz, u1[i], v0[i], i);
            n = vert(out, n, px[i] - rx - ux, py[i] - ry - uy, pz[i] - rz - uz, u0[i], v1[i], i);
            n = vert(out, n, px[i] + rx + ux, py[i] + ry + uy, pz[i] + rz + uz, u1[i], v0[i], i);
            n = vert(out, n, px[i] - rx + ux, py[i] - ry + uy, pz[i] - rz + uz, u0[i], v0[i], i);
        }
        return n;
    }

    private int vert(float[] out, int n, float x, float y, float z, float u, float v, int i) {
        out[n++] = x;
        out[n++] = y;
        out[n++] = z;
        out[n++] = u;
        out[n++] = v;
        out[n++] = sky[i];
        out[n++] = blk[i];
        out[n++] = tr[i];
        out[n++] = tg[i];
        out[n++] = tb[i];
        out[n++] = 0f; // wind weight: particles never sway
        return n;
    }
}
