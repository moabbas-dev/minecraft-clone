package com.example.fx;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ParticleSystemTest {
    private static final float[] UV = {0f, 0f, 1f, 1f};
    private static final float[] TINT = {1f, 1f, 1f};

    private int burst(ParticleSystem p) {
        return p.spawnBurst(5f, 5f, 5f, UV, TINT, 1f, 0f, new Random(1));
    }

    @Test
    void oneBurstSpawnsExactlyTheConfiguredCount() {
        ParticleSystem p = new ParticleSystem();
        assertEquals(ParticleSystem.BURST_COUNT, burst(p));
        assertEquals(ParticleSystem.BURST_COUNT, p.count());
    }

    @Test
    void particlesExpireAndAreRemoved() {
        ParticleSystem p = new ParticleSystem();
        burst(p);
        for (int i = 0; i < 200; i++) p.update(0.02f); // 4s, well past the max lifetime
        assertEquals(0, p.count());
    }

    @Test
    void particlesFallUnderGravityAndAreStillAliveMidLife() {
        ParticleSystem p = new ParticleSystem();
        burst(p);
        p.update(0.1f);
        assertEquals(ParticleSystem.BURST_COUNT, p.count(), "nothing should expire after 0.1s");
        float[] out = new float[ParticleSystem.maxVertexFloats()];
        int written = p.buildVertices(out, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0));
        assertEquals(ParticleSystem.BURST_COUNT * 6 * ParticleSystem.FLOATS_PER_VERTEX, written);
    }

    @Test
    void thePoolNeverExceedsItsCapacityAndTruncatesInsteadOfCrashing() {
        ParticleSystem p = new ParticleSystem();
        for (int i = 0; i < 100; i++) burst(p);
        assertEquals(ParticleSystem.MAX_PARTICLES, p.count());
        int extra = burst(p);
        assertEquals(0, extra, "a full pool spawns nothing rather than evicting or overflowing");
        float[] out = new float[ParticleSystem.maxVertexFloats()];
        assertEquals(ParticleSystem.maxVertexFloats(), p.buildVertices(out, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0)));
    }

    @Test
    void expiredSlotsAreReusableSoTheSystemKeepsWorkingAfterManyBursts() {
        ParticleSystem p = new ParticleSystem();
        for (int round = 0; round < 50; round++) {
            burst(p);
            for (int i = 0; i < 60; i++) p.update(0.02f);
        }
        assertTrue(p.count() <= ParticleSystem.MAX_PARTICLES);
        assertEquals(ParticleSystem.BURST_COUNT, burst(p) , "a drained pool accepts a full burst again");
    }

    @Test
    void fragmentsSampleASubPatchInsideTheSourceTile() {
        ParticleSystem p = new ParticleSystem();
        p.spawnBurst(0, 0, 0, new float[]{0.5f, 0.25f, 0.75f, 0.5f}, TINT, 1f, 0f, new Random(7));
        float[] out = new float[ParticleSystem.maxVertexFloats()];
        int n = p.buildVertices(out, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0));
        for (int i = 0; i < n; i += ParticleSystem.FLOATS_PER_VERTEX) {
            float u = out[i + 3], v = out[i + 4];
            assertTrue(u >= 0.5f - 1e-5f && u <= 0.75f + 1e-5f, "u out of tile: " + u);
            assertTrue(v >= 0.25f - 1e-5f && v <= 0.5f + 1e-5f, "v out of tile: " + v);
        }
    }

    @Test
    void clearRemovesEverything() {
        ParticleSystem p = new ParticleSystem();
        burst(p);
        p.clear();
        assertEquals(0, p.count());
    }
}
