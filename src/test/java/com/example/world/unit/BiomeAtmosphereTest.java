package com.example.world.unit;

import com.example.world.Biome;
import com.example.world.BiomeColors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fog/water/underwater-fog blending in BiomeColors - the same IDW weights Biome.weights()
 * already produces for grass/foliage, applied to the new atmosphere color tables.
 */
class BiomeAtmosphereTest {

    private BiomeColors colors;

    @BeforeEach
    void setUp() {
        // Missing colormap files fall back to a flat color (already-established Colormap
        // behavior) - fine here, since fog/water/underwaterFog don't touch the colormap at all.
        colors = new BiomeColors(1L, new File("does-not-exist-grass.png"), new File("does-not-exist-foliage.png"));
    }

    @Test
    void singleBiomeWeightReturnsExactlyThatBiomesOwnWaterColor() {
        float[] weights = new float[Biome.values().length];
        weights[Biome.SWAMP.ordinal()] = 1f;
        float[] water = colors.blendWater(weights);
        // 0x617B64 - the verified vanilla swamp water color.
        assertEquals(0x61 / 255f, water[0], 1e-5f);
        assertEquals(0x7B / 255f, water[1], 1e-5f);
        assertEquals(0x64 / 255f, water[2], 1e-5f);
    }

    @Test
    void everyRealClimatePointProducesValidInRangeColors() {
        double[][] points = {
                {0.3, 0.0, 0.0, 0.5, 0.0}, {-0.9, -0.2, 0.0, 0.4, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0},
                {0.9, -0.8, 0.2, 0.4, -0.2}, {0.95, -0.9, 0.3, 0.0, 0.1},
        };
        for (double[] p : points) {
            float[] weights = Biome.weights(p[0], p[1], p[2], p[3], p[4]);
            for (float[] c : new float[][]{colors.blendWater(weights), colors.blendFog(weights), colors.blendUnderwaterFog(weights)}) {
                for (float channel : c) {
                    assertFalse(Float.isNaN(channel), "color channel must never be NaN");
                    assertFalse(Float.isInfinite(channel), "color channel must never be infinite");
                    assertTrue(channel >= 0f && channel <= 1f, "color channel out of [0,1]: " + channel);
                }
            }
        }
    }

    @Test
    void blendingIsDeterministic() {
        float[] weights = Biome.weights(0.2, 0.1, 0.0, 0.0, 0.0);
        assertArrayEquals(colors.blendWater(weights), colors.blendWater(weights));
        assertArrayEquals(colors.blendFog(weights), colors.blendFog(weights));
        assertArrayEquals(colors.blendUnderwaterFog(weights), colors.blendUnderwaterFog(weights));
    }

    @Test
    void blendedColorLiesBetweenTheTwoNearestBiomesOnAStraightPathBetweenThem() {
        // A point exactly between PLAINS and SWAMP's own climate targets should blend toward
        // swamp's distinctly greener water, not equal plains' plain blue.
        Biome plains = Biome.PLAINS, swamp = Biome.SWAMP;
        double midTemp = (plains.temperature + swamp.temperature) / 2;
        double midHumidity = (plains.humidity + swamp.humidity) / 2;
        double midCont = (plains.continentalness + swamp.continentalness) / 2;
        double midEros = (plains.erosion + swamp.erosion) / 2;
        double midWeird = (plains.weirdness + swamp.weirdness) / 2;
        float[] weights = Biome.weights(midTemp, midHumidity, midCont, midEros, midWeird);
        float[] midWater = colors.blendWater(weights);

        float[] plainsOnly = new float[Biome.values().length];
        plainsOnly[plains.ordinal()] = 1f;
        float[] swampOnly = new float[Biome.values().length];
        swampOnly[swamp.ordinal()] = 1f;
        float[] plainsWater = colors.blendWater(plainsOnly);
        float[] swampWater = colors.blendWater(swampOnly);

        for (int i = 0; i < 3; i++) {
            float lo = Math.min(plainsWater[i], swampWater[i]);
            float hi = Math.max(plainsWater[i], swampWater[i]);
            assertTrue(midWater[i] >= lo - 1e-4f && midWater[i] <= hi + 1e-4f,
                    "midpoint blend channel " + i + " should lie between the two pure-biome colors");
        }
    }

    @Test
    void extremeOutOfRangeClimateStillProducesSafeWeights() {
        // Far outside every biome's target - Biome.weights()'s own EPSILON guards div-by-zero;
        // this just confirms the atmosphere blends built on top of it inherit that safety.
        float[] weights = Biome.weights(1000, -1000, 1000, -1000, 1000);
        assertDoesNotThrow(() -> colors.blendWater(weights));
        assertDoesNotThrow(() -> colors.blendFog(weights));
        assertDoesNotThrow(() -> colors.blendUnderwaterFog(weights));
    }
}
