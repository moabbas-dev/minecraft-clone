package com.example.world.unit;

import com.example.world.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BiomeTest {

    @Test
    void closestPicksExactMatchAtItsOwnClimatePoint() {
        for (Biome b : Biome.values()) {
            Biome found = Biome.closest(b.temperature, b.humidity, b.continentalness, b.erosion, b.weirdness);
            assertEquals(b, found, "a biome's own climate point should resolve to itself");
        }
    }

    @Test
    void weightsSumToOneAndAreNonNegative() {
        double[][] samplePoints = {
                {0.3, 0.0, 0.0, 0.5, 0.0},
                {-0.9, -0.2, 0.0, 0.4, 0.0},
                {0.0, 0.0, 0.0, 0.0, 0.0},
                {5.0, -5.0, 5.0, -5.0, 5.0}, // far outside any biome's target - must still resolve cleanly
        };
        for (double[] p : samplePoints) {
            float[] w = Biome.weights(p[0], p[1], p[2], p[3], p[4]);
            assertEquals(Biome.values().length, w.length);
            double sum = 0;
            for (float wi : w) {
                assertTrue(wi >= 0f, "weight must never be negative: " + wi);
                assertFalse(Float.isNaN(wi), "weight must never be NaN");
                sum += wi;
            }
            assertEquals(1.0, sum, 1e-4, "weights must be a normalized partition of unity");
        }
    }

    @Test
    void extremeClimateStillResolvesToSomeBiome() {
        Biome found = Biome.closest(1000, -1000, 1000, -1000, 1000);
        assertNotNull(found);
    }

    @Test
    void weightsIndexingMatchesBiomeOrdinal() {
        float[] w = Biome.weights(Biome.DESERT.temperature, Biome.DESERT.humidity,
                Biome.DESERT.continentalness, Biome.DESERT.erosion, Biome.DESERT.weirdness);
        int desertIdx = Biome.DESERT.ordinal();
        float maxWeight = 0f;
        int maxIdx = -1;
        for (int i = 0; i < w.length; i++) {
            if (w[i] > maxWeight) {
                maxWeight = w[i];
                maxIdx = i;
            }
        }
        assertEquals(desertIdx, maxIdx, "the dominant weight at a biome's own point must be that same biome");
    }
}
