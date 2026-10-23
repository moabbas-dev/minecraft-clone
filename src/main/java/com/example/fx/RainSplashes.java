package com.example.fx;

import org.joml.Vector3f;

import java.util.Random;

/**
 * The drops that hop off the ground in the rain: vanilla's {@code ClientLevel.tickWeatherEffects} spawning and
 * {@code WaterDropParticle} (rules read from the 26.3 client). Every tick, (int)(0.225 * (2R+1)^2 * rain^2) tries pick a random column
 * within R = 10 blocks of the camera whose surface is within 10 blocks of the camera's block height and where the biome's precipitation
 * there is rain (not snow, not none), and put a drop on that surface at a
 * random spot of the block. A drop starts moving up at 0.1-0.3 blocks per tick, falls with gravity 0.06 and 2% drag, lives
 * 8 / (0.2..1.0) ticks, and once it is on the ground has a 50% chance of vanishing each tick (it also slides, x0.7, but it has no
 * sideways speed). It is drawn as a camera-facing quad of half-size 0.1-0.2 blocks with one of the four splash sprites.
 * Headless: vertex data only.
 */
public final class RainSplashes {
    /** The ground the drops land on. */
    public interface Surface {
        /** The y of the top of the highest block that stops rain in column (x, z). */
        int topY(int x, int z);

        /** {@code getPrecipitationAt(heightmapPos) == RAIN}: whether it rains (not snows, not dry) at that surface position. */
        default boolean rainsAt(int x, int y, int z) {
            return true;
        }
    }

    public static final int CAPACITY = 2048;
    public static final int SPRITES = 4;
    public static final float TICK_SECONDS = 0.05f;
    public static final int RADIUS = 10;
    public static final int FLOATS_PER_VERTEX = 11;

    private final Random random;
    private final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY], oy = new float[CAPACITY];
    private final float[] yd = new float[CAPACITY], ground = new float[CAPACITY], size = new float[CAPACITY];
    private final int[] life = new int[CAPACITY], sprite = new int[CAPACITY];
    private final boolean[] onGround = new boolean[CAPACITY];
    private int count;
    private float accumulator;
    /** Whether the last tick found a rain position ({@code rainParticlePosition != null}, the condition for any rain sound). */
    private boolean rainPositionFound;

    public RainSplashes(Random random) {
        this.random = random;
    }

    public int count() {
        return count;
    }

    public void clear() {
        count = 0;
        accumulator = 0f;
    }

    /** How many placement tries one tick makes at this rain level (vanilla: (int)(0.225 * (2R+1)^2 * rain^2)). */
    public static int triesPerTick(float rainLevel) {
        int side = 2 * RADIUS + 1;
        return (int) (0.225f * side * side * rainLevel * rainLevel);
    }

    /** Advances {@code dt} seconds in 20 Hz ticks: moves the drops, then spawns this tick's. */
    public void update(float dt, float rainLevel, Vector3f camera, Surface surface) {
        accumulator += dt;
        int guard = 0;
        while (accumulator >= TICK_SECONDS && guard++ < 4) {
            accumulator -= TICK_SECONDS;
            tickParticles();
            if (rainLevel > 0f) spawnTick(rainLevel, camera, surface);
            else rainPositionFound = false;
        }
        if (accumulator > TICK_SECONDS) accumulator = TICK_SECONDS;
    }

    /** Whether the last spawn tick found a column where it rains near the camera (vanilla only plays rain sounds then). */
    public boolean rainPositionFound() {
        return rainPositionFound;
    }

    void spawnTick(float rainLevel, Vector3f camera, Surface surface) {
        rainPositionFound = false;
        int cx = (int) Math.floor(camera.x), cy = (int) Math.floor(camera.y), cz = (int) Math.floor(camera.z);
        int tries = triesPerTick(rainLevel);
        for (int i = 0; i < tries; i++) {
            int bx = cx + random.nextInt(2 * RADIUS + 1) - RADIUS, bz = cz + random.nextInt(2 * RADIUS + 1) - RADIUS;
            int top = surface.topY(bx, bz);
            if (top > cy + 10 || top < cy - 10 || !surface.rainsAt(bx, top, bz)) continue;
            rainPositionFound = true;
            spawn(bx + random.nextFloat(), top, bz + random.nextFloat());
        }
    }

    /** One drop, as WaterDropParticle's constructor builds it. */
    void spawn(float px, float py, float pz) {
        if (count >= CAPACITY) return;
        int i = count++;
        x[i] = px;
        y[i] = oy[i] = py;
        z[i] = pz;
        ground[i] = py;
        yd[i] = random.nextFloat() * 0.2f + 0.1f;
        life[i] = (int) (8.0 / (random.nextFloat() * 0.8 + 0.2));
        size[i] = 0.1f * (random.nextFloat() * 0.5f + 0.5f) * 2f;
        sprite[i] = random.nextInt(SPRITES);
        onGround[i] = false;
    }

    private void tickParticles() {
        for (int i = count - 1; i >= 0; i--) {
            oy[i] = y[i];
            if (--life[i] <= 0) {
                remove(i);
                continue;
            }
            yd[i] -= 0.06f;
            y[i] += yd[i];
            onGround[i] = false;
            if (y[i] <= ground[i] && yd[i] < 0f) { // landed: the move is stopped by the block
                y[i] = ground[i];
                yd[i] = 0f;
                onGround[i] = true;
            }
            yd[i] *= 0.98f;
            if (onGround[i] && random.nextFloat() < 0.5f) remove(i);
        }
    }

    private void remove(int i) {
        int last = --count;
        if (i == last) return;
        x[i] = x[last]; y[i] = y[last]; z[i] = z[last]; oy[i] = oy[last];
        yd[i] = yd[last]; ground[i] = ground[last]; size[i] = size[last];
        life[i] = life[last]; sprite[i] = sprite[last]; onGround[i] = onGround[last];
    }

    /** How many live drops use sprite {@code s}. */
    public int countOf(int s) {
        int n = 0;
        for (int i = 0; i < count; i++) if (sprite[i] == s) n++;
        return n;
    }

    public static int maxVertexFloats() {
        return CAPACITY * 6 * FLOATS_PER_VERTEX;
    }

    /** Six vertices per live drop of sprite {@code s}, facing the camera; returns the floats written. */
    public int buildVertices(float[] out, int s, Vector3f camRight, Vector3f camUp, float sky, float blockLight) {
        float partial = Math.min(1f, accumulator / TICK_SECONDS);
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (sprite[i] != s) continue;
            float px = x[i], py = oy[i] + (y[i] - oy[i]) * partial, pz = z[i], h = size[i];
            float rx = camRight.x * h, ry = camRight.y * h, rz = camRight.z * h;
            float ux = camUp.x * h, uy = camUp.y * h, uz = camUp.z * h;
            n = vert(out, n, px - rx - ux, py - ry - uy, pz - rz - uz, 0f, 1f, sky, blockLight);
            n = vert(out, n, px + rx - ux, py + ry - uy, pz + rz - uz, 1f, 1f, sky, blockLight);
            n = vert(out, n, px + rx + ux, py + ry + uy, pz + rz + uz, 1f, 0f, sky, blockLight);
            n = vert(out, n, px - rx - ux, py - ry - uy, pz - rz - uz, 0f, 1f, sky, blockLight);
            n = vert(out, n, px + rx + ux, py + ry + uy, pz + rz + uz, 1f, 0f, sky, blockLight);
            n = vert(out, n, px - rx + ux, py - ry + uy, pz - rz + uz, 0f, 0f, sky, blockLight);
        }
        return n;
    }

    private static int vert(float[] out, int n, float px, float py, float pz, float u, float v, float sky, float blockLight) {
        out[n++] = px;
        out[n++] = py;
        out[n++] = pz;
        out[n++] = u;
        out[n++] = v;
        out[n++] = sky;
        out[n++] = blockLight;
        out[n++] = 1f;
        out[n++] = 1f;
        out[n++] = 1f;
        out[n++] = 0f;
        return n;
    }
}
