package com.example.fx;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpriteParticlesTest {
    private static final SpriteParticles.Tracked ZOMBIE = new SpriteParticles.Tracked() {
        public float x() { return 10f; }
        public float y() { return 64f; }
        public float z() { return 10f; }
        public float width() { return 0.6f; }
        public float height() { return 1.95f; }
    };

    @Test
    void trackingEmitterBurstsImmediatelyAndLivesThreeTicks() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.CRIT, new Random(1));
        p.track(ZOMBIE);
        assertTrue(p.count() > 0, "the emitter's constructor tick already spawns particles");
        assertEquals(1, p.emitterCount());
        int afterFirst = p.count();
        p.update(SpriteParticles.TICK_SECONDS);
        assertTrue(p.count() > afterFirst || p.count() > 0);
        p.update(SpriteParticles.TICK_SECONDS);
        assertEquals(0, p.emitterCount(), "3 bursts in total: creation + 2 ticks");
    }

    @Test
    void aBurstIsAtMostSixteenAttemptsPerTickAndStaysInsideAHalfBlockBallAroundTheVictimsMiddle() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.CRIT, new Random(7));
        p.track(ZOMBIE);
        assertTrue(p.count() <= 16);
        float[] out = new float[SpriteParticles.maxVertexFloats()];
        int n = p.buildVertices(out, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 1f, 0f);
        assertEquals(p.count() * 6 * SpriteParticles.FLOATS_PER_VERTEX, n);
        for (int i = 0; i < n; i += SpriteParticles.FLOATS_PER_VERTEX) {
            assertEquals(10f, out[i], 1.0f);
            assertEquals(64f + 1.95f * 0.5f, out[i + 1], 1.5f);
        }
    }

    @Test
    void particlesLiveFourToTenTicksThenDisappear() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.CRIT, new Random(3));
        p.spawnGaussian(0, 0, 0, 0, 0, 0, 0.1f, 40);
        assertEquals(40, p.count());
        for (int t = 0; t < 3; t++) p.update(SpriteParticles.TICK_SECONDS);
        assertTrue(p.count() > 0, "none of them dies within 3 ticks");
        for (int t = 0; t < 12; t++) p.update(SpriteParticles.TICK_SECONDS);
        assertEquals(0, p.count(), "all gone after the longest lifetime (10 ticks)");
    }

    @Test
    void damageIndicatorsRiseAndLiveTwentyTicks() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.DAMAGE_INDICATOR, new Random(5));
        p.spawnGaussian(0, 0, 0, 0.1f, 0f, 0.1f, 0.2f, 3);
        assertEquals(3, p.count());
        for (int t = 0; t < 19; t++) p.update(SpriteParticles.TICK_SECONDS);
        assertEquals(3, p.count());
        for (int t = 0; t < 3; t++) p.update(SpriteParticles.TICK_SECONDS);
        assertEquals(0, p.count());
    }

    @Test
    void sizeGrowsInOverTheFirstThirtySecondOfLife() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.CRIT, new Random(9));
        p.spawnGaussian(0, 0, 0, 0, 0, 0, 0f, 1);
        float full = p.sizeOf(0, 100f);
        assertTrue(full >= 0.1f * 0.5f * 2f * 0.75f - 1e-6f && full <= 0.1f * 2f * 0.75f + 1e-6f, "size " + full);
        assertTrue(p.sizeOf(0, 0f) <= full);
    }

    @Test
    void aFullPoolRefusesNewParticlesInsteadOfOverflowing() {
        SpriteParticles p = new SpriteParticles(SpriteParticles.Kind.CRIT, new Random(2));
        p.spawnGaussian(0, 0, 0, 0, 0, 0, 0.1f, SpriteParticles.CAPACITY + 50);
        assertEquals(SpriteParticles.CAPACITY, p.count());
    }
}
