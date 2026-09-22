package com.example.world.unit;

import com.example.world.SimplexNoise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimplexNoiseTest {

    @Test
    void sameSeedProducesIdenticalOutput() {
        SimplexNoise a = new SimplexNoise(42L);
        SimplexNoise b = new SimplexNoise(42L);
        for (int x = -50; x <= 50; x += 7) {
            for (int y = -50; y <= 50; y += 11) {
                assertEquals(a.noise(x * 0.05, y * 0.05), b.noise(x * 0.05, y * 0.05), 0.0,
                        "identical seed must produce bit-identical noise at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void differentSeedsDiverge() {
        SimplexNoise a = new SimplexNoise(1L);
        SimplexNoise b = new SimplexNoise(2L);
        boolean anyDifferent = false;
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                if (a.noise(x * 0.1, y * 0.1) != b.noise(x * 0.1, y * 0.1)) {
                    anyDifferent = true;
                    break;
                }
            }
        }
        assertTrue(anyDifferent, "two different seeds should not produce identical noise fields");
    }

    @Test
    void noiseStaysWithinDocumentedRange() {
        SimplexNoise noise = new SimplexNoise(7L);
        for (int x = -200; x <= 200; x += 3) {
            for (int y = -200; y <= 200; y += 5) {
                double v = noise.noise(x * 0.01, y * 0.01);
                assertTrue(v >= -1.05 && v <= 1.05, "raw noise sample out of expected range: " + v);
            }
        }
    }

    @Test
    void fbmStaysNormalized() {
        SimplexNoise noise = new SimplexNoise(99L);
        for (int x = -300; x <= 300; x += 13) {
            for (int y = -300; y <= 300; y += 17) {
                double v = noise.fbm(x, y, 4, 0.5, 2.0, 1.0 / 200.0);
                assertTrue(v >= -1.2 && v <= 1.2, "fbm sample out of expected normalized range: " + v);
            }
        }
    }

    @Test
    void fbmIsDeterministicAtTheSamePoint() {
        SimplexNoise noise = new SimplexNoise(123L);
        double first = noise.fbm(37, -41, 3, 0.5, 2.0, 1.0 / 64.0);
        double second = noise.fbm(37, -41, 3, 0.5, 2.0, 1.0 / 64.0);
        assertEquals(first, second, 0.0);
    }
}
