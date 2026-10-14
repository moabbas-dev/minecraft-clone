package com.example.fx;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Vanilla's {@code CritParticle} (used for the critical-hit stars and, with {@link Kind#DAMAGE_INDICATOR}, for the dark
 * hearts a strong melee hit leaves behind) plus the {@code TrackingEmitter} that spawns the critical-hit burst around the
 * victim. Everything runs on vanilla's 20 ticks per second and positions are interpolated between ticks when drawn.
 * Headless: it produces vertex data only and never touches GL. The sprite itself (critical_hit.png / damage.png) is bound
 * by the caller; the quad uses the whole texture.
 *
 * <p>Numbers (CritParticle / TrackingEmitter / ParticleEngine in 1.21): friction 0.7, gravity 0.5, no collisions, start
 * velocity = 0.1 x the base particle's random velocity + 0.4 x the emitted velocity, size 0.1 x (0.5..1) x 2 x 0.75 blocks
 * growing in over the first 1/32 of its life, lifetime max(1, (int)(6 / (0.6..1.4))) ticks, grey 0.6..0.9 whose green fades
 * x0.96 and blue x0.9 per tick; the emitter lives 3 ticks and tries 16 times per tick to place a particle inside a
 * 0.5-block ball around the victim's middle, giving it velocity (x, y + 0.2, z); a damage indicator adds 1.0 to the upward
 * velocity and lives 20 ticks.
 */
public final class SpriteParticles {
    public enum Kind { CRIT, DAMAGE_INDICATOR }

    /** What a tracking emitter follows: a bounding box standing on (x, y, z). */
    public interface Tracked {
        float x();

        float y();

        float z();

        float width();

        float height();
    }

    public static final int CAPACITY = 512;
    public static final float TICK_SECONDS = 0.05f;
    public static final int EMITTER_LIFETIME_TICKS = 3;
    public static final int FLOATS_PER_VERTEX = 11;

    private static final float FRICTION = 0.7f;
    private static final float GRAVITY = 0.5f;

    private final Kind kind;
    private final Random random;

    private final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY];
    private final float[] ox = new float[CAPACITY], oy = new float[CAPACITY], oz = new float[CAPACITY];
    private final float[] xd = new float[CAPACITY], yd = new float[CAPACITY], zd = new float[CAPACITY];
    private final float[] quad = new float[CAPACITY], g = new float[CAPACITY], b = new float[CAPACITY], r = new float[CAPACITY];
    private final int[] age = new int[CAPACITY], life = new int[CAPACITY];
    private int count;

    private static final class Emitter {
        final Tracked target;
        int life;

        Emitter(Tracked target) {
            this.target = target;
        }
    }

    private final List<Emitter> emitters = new ArrayList<>();
    private float accumulator;

    public SpriteParticles(Kind kind, Random random) {
        this.kind = kind;
        this.random = random;
    }

    public int count() {
        return count;
    }

    public int emitterCount() {
        return emitters.size();
    }

    public void clear() {
        count = 0;
        emitters.clear();
        accumulator = 0f;
    }

    /** Vanilla {@code createTrackingEmitter(entity, CRIT)}: bursts around {@code target} on this tick and the next two. */
    public void track(Tracked target) {
        Emitter e = new Emitter(target);
        emitterTick(e);
        if (e.life < EMITTER_LIFETIME_TICKS) emitters.add(e);
    }

    /**
     * The server's "N particles around a point" packet: each particle is offset by a Gaussian scaled by
     * {@code spread} and given a Gaussian velocity scaled by {@code speed}.
     */
    public void spawnGaussian(float cx, float cy, float cz, float spreadX, float spreadY, float spreadZ, float speed, int n) {
        for (int i = 0; i < n; i++) {
            spawn(cx + (float) random.nextGaussian() * spreadX, cy + (float) random.nextGaussian() * spreadY,
                    cz + (float) random.nextGaussian() * spreadZ,
                    (float) random.nextGaussian() * speed, (float) random.nextGaussian() * speed, (float) random.nextGaussian() * speed);
        }
    }

    /** One particle, as its constructor builds it (including the single {@code tick()} the constructor runs). Returns false when full. */
    boolean spawn(float px, float py, float pz, float xa, float ya, float za) {
        if (count >= CAPACITY) return false;
        int i = count++;
        // The base Particle gives a random direction of length (0.15..0.45) x 0.4 when started from rest.
        float bx = (float) (random.nextDouble() * 2 - 1) * 0.4f, by = (float) (random.nextDouble() * 2 - 1) * 0.4f,
                bz = (float) (random.nextDouble() * 2 - 1) * 0.4f;
        float speed = (float) (random.nextDouble() + random.nextDouble() + 1) * 0.15f;
        float len = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (len < 1e-6f) len = 1f;
        bx = bx / len * speed * 0.4f;
        by = by / len * speed * 0.4f;
        bz = bz / len * speed * 0.4f;
        if (kind == Kind.DAMAGE_INDICATOR) ya += 1.0f;
        x[i] = ox[i] = px;
        y[i] = oy[i] = py;
        z[i] = oz[i] = pz;
        xd[i] = bx * 0.1f + xa * 0.4f;
        yd[i] = by * 0.1f + ya * 0.4f;
        zd[i] = bz * 0.1f + za * 0.4f;
        float grey = random.nextFloat() * 0.3f + 0.6f;
        r[i] = grey;
        g[i] = grey;
        b[i] = grey;
        quad[i] = 0.1f * (random.nextFloat() * 0.5f + 0.5f) * 2f * 0.75f;
        life[i] = kind == Kind.DAMAGE_INDICATOR ? 20 : Math.max((int) (6.0 / (random.nextFloat() * 0.8 + 0.6)), 1);
        age[i] = 0;
        tickParticle(i);
        return true;
    }

    private void emitterTick(Emitter e) {
        Tracked t = e.target;
        for (int k = 0; k < 16; k++) {
            float xa = random.nextFloat() * 2f - 1f, ya = random.nextFloat() * 2f - 1f, za = random.nextFloat() * 2f - 1f;
            if (xa * xa + ya * ya + za * za > 1f) continue;
            spawn(t.x() + t.width() * xa / 4f, t.y() + t.height() * (0.5f + ya / 4f), t.z() + t.width() * za / 4f,
                    xa, ya + 0.2f, za);
        }
        e.life++;
    }

    /** Returns false once the particle has expired (the caller removes it). */
    private boolean tickParticle(int i) {
        ox[i] = x[i];
        oy[i] = y[i];
        oz[i] = z[i];
        if (age[i]++ >= life[i]) return false;
        yd[i] -= 0.04f * GRAVITY;
        x[i] += xd[i];
        y[i] += yd[i];
        z[i] += zd[i];
        xd[i] *= FRICTION;
        yd[i] *= FRICTION;
        zd[i] *= FRICTION;
        g[i] *= 0.96f;
        b[i] *= 0.9f;
        return true;
    }

    public void update(float dt) {
        accumulator += dt;
        int guard = 0;
        while (accumulator >= TICK_SECONDS && guard++ < 6) {
            accumulator -= TICK_SECONDS;
            tick();
        }
        if (accumulator > TICK_SECONDS) accumulator = TICK_SECONDS; // a long stall must not replay seconds of ticks
    }

    private void tick() {
        for (int i = count - 1; i >= 0; i--) {
            if (!tickParticle(i)) removeAt(i);
        }
        for (int i = emitters.size() - 1; i >= 0; i--) {
            Emitter e = emitters.get(i);
            emitterTick(e);
            if (e.life >= EMITTER_LIFETIME_TICKS) emitters.remove(i);
        }
    }

    private void removeAt(int i) {
        int last = --count;
        if (i == last) return;
        x[i] = x[last]; y[i] = y[last]; z[i] = z[last];
        ox[i] = ox[last]; oy[i] = oy[last]; oz[i] = oz[last];
        xd[i] = xd[last]; yd[i] = yd[last]; zd[i] = zd[last];
        quad[i] = quad[last]; r[i] = r[last]; g[i] = g[last]; b[i] = b[last];
        age[i] = age[last]; life[i] = life[last];
    }

    public static int maxVertexFloats() {
        return CAPACITY * 6 * FLOATS_PER_VERTEX;
    }

    /** Half-size of particle {@code i} {@code partial} of a tick after its last update (vanilla getQuadSize: it grows in over 1/32 of its life). */
    float sizeOf(int i, float partial) {
        float grow = Math.max(0f, Math.min(1f, (age[i] + partial) / life[i] * 32f));
        return quad[i] * grow;
    }

    /** Six vertices per live particle (two camera-facing triangles) over the whole sprite; returns the floats written. */
    public int buildVertices(float[] out, Vector3f camRight, Vector3f camUp, float sky, float blockLight) {
        float partial = Math.min(1f, accumulator / TICK_SECONDS);
        int n = 0;
        for (int i = 0; i < count; i++) {
            float px = ox[i] + (x[i] - ox[i]) * partial, py = oy[i] + (y[i] - oy[i]) * partial, pz = oz[i] + (z[i] - oz[i]) * partial;
            float s = sizeOf(i, partial);
            float rx = camRight.x * s, ry = camRight.y * s, rz = camRight.z * s;
            float ux = camUp.x * s, uy = camUp.y * s, uz = camUp.z * s;
            n = vert(out, n, px - rx - ux, py - ry - uy, pz - rz - uz, 0f, 1f, i, sky, blockLight);
            n = vert(out, n, px + rx - ux, py + ry - uy, pz + rz - uz, 1f, 1f, i, sky, blockLight);
            n = vert(out, n, px + rx + ux, py + ry + uy, pz + rz + uz, 1f, 0f, i, sky, blockLight);
            n = vert(out, n, px - rx - ux, py - ry - uy, pz - rz - uz, 0f, 1f, i, sky, blockLight);
            n = vert(out, n, px + rx + ux, py + ry + uy, pz + rz + uz, 1f, 0f, i, sky, blockLight);
            n = vert(out, n, px - rx + ux, py - ry + uy, pz - rz + uz, 0f, 0f, i, sky, blockLight);
        }
        return n;
    }

    private int vert(float[] out, int n, float px, float py, float pz, float u, float v, int i, float sky, float blockLight) {
        out[n++] = px;
        out[n++] = py;
        out[n++] = pz;
        out[n++] = u;
        out[n++] = v;
        out[n++] = sky;
        out[n++] = blockLight;
        out[n++] = r[i];
        out[n++] = g[i];
        out[n++] = b[i];
        out[n++] = 0f; // no wind
        return n;
    }
}
