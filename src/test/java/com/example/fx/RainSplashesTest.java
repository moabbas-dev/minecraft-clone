package com.example.fx;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RainSplashesTest {
    private static final RainSplashes.Surface FLAT = (x, z) -> 4;

    @Test
    void theNumberOfTriesFollowsVanillasFormula() {
        assertEquals(99, RainSplashes.triesPerTick(1f), "0.225 * 21^2 = 99.2");
        assertEquals(24, RainSplashes.triesPerTick(0.5f));
        assertEquals(0, RainSplashes.triesPerTick(0.05f));
    }

    @Test
    void dropsAppearOnTheSurfaceWithinTenBlocksOfTheCamera() {
        RainSplashes rain = new RainSplashes(new Random(1));
        rain.spawnTick(1f, new Vector3f(0.5f, 6f, 0.5f), FLAT);
        assertTrue(rain.count() > 60, "most of the 99 tries land: " + rain.count());
        float[] out = new float[RainSplashes.maxVertexFloats()];
        int total = 0;
        for (int s = 0; s < RainSplashes.SPRITES; s++) total += rain.buildVertices(out, s, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 1f, 0f);
        assertEquals(rain.count() * 6 * RainSplashes.FLOATS_PER_VERTEX, total);
    }

    @Test
    void aSurfaceFarAboveOrBelowTheCameraGetsNoDrops() {
        RainSplashes rain = new RainSplashes(new Random(1));
        rain.spawnTick(1f, new Vector3f(0.5f, 40f, 0.5f), FLAT); // the surface is 36 blocks below
        assertEquals(0, rain.count());
    }

    @Test
    void aDropHopsUpThenLandsAndDiesWithinFortyTicks() {
        RainSplashes rain = new RainSplashes(new Random(3));
        rain.spawn(0.5f, 4f, 0.5f);
        float max = 4f;
        float[] out = new float[RainSplashes.maxVertexFloats()];
        for (int tick = 0; tick < 40 && rain.count() > 0; tick++) {
            rain.update(0.05f, 0f, new Vector3f(), FLAT);
            for (int s = 0; s < RainSplashes.SPRITES; s++) {
                if (rain.buildVertices(out, s, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 1f, 0f) > 0) max = Math.max(max, out[1]);
            }
        }
        assertTrue(max > 4.02f, "it rose above the ground: " + max);
        assertEquals(0, rain.count(), "and is gone after at most 8 / 0.2 = 40 ticks");
    }

    @Test
    void noRainNoNewDrops() {
        RainSplashes rain = new RainSplashes(new Random(2));
        rain.update(1f, 0f, new Vector3f(0.5f, 6f, 0.5f), FLAT);
        assertEquals(0, rain.count());
        rain.update(0.2f, 1f, new Vector3f(0.5f, 6f, 0.5f), FLAT);
        assertTrue(rain.count() > 0);
        rain.clear();
        assertEquals(0, rain.count());
    }

    @Test
    void theFourSplashSpritesAllGetUsed() {
        RainSplashes rain = new RainSplashes(new Random(5));
        rain.spawnTick(1f, new Vector3f(0.5f, 6f, 0.5f), FLAT);
        for (int s = 0; s < RainSplashes.SPRITES; s++) assertTrue(rain.countOf(s) > 0, "sprite " + s);
    }
}
