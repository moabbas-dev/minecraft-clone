package com.example.world.unit;

import com.example.world.BiomeAttributes;
import com.example.world.Chunk;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeDefinitionLoader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The biome atmosphere attributes read from the real 26.3 biome files (data/minecraft/worldgen/biome, restored from the client
 * jar) through the probe's Gaussian blend ({@code GaussianSampler}, {@code SpatialAttributeInterpolator}); expected values are the
 * files' own colours and hand-derived blends.
 */
class BiomeAttributesTest {
    private static final BiomeDefinitionLoader LOADER = new BiomeDefinitionLoader(Path.of("data"));
    private static final Map<String, BiomeDefinition> CACHE = new HashMap<>();
    private static final Function<String, BiomeDefinition> DEFS = id -> CACHE.computeIfAbsent(id, k -> {
        try {
            return LOADER.load(k);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    });

    private static BiomeAttributes.Values only(String biome) {
        return BiomeAttributes.sample(12.3, 70.2, -4.6, (x, y, z) -> biome, DEFS);
    }

    private static float[] rgb(int hex) {
        return BiomeAttributes.rgb(hex);
    }

    @Test
    void aBiomeWithoutAtmosphereEntriesKeepsTheOverworldValues() {
        // plains.json sets no fog or water fog: overworld.json's fog #c0d8ff and the attribute defaults #050533, -8, 96
        BiomeAttributes.Values v = only("minecraft:plains");
        assertArrayEquals(rgb(0x78A7FF), v.sky(), 1e-6f);
        assertArrayEquals(rgb(0xC0D8FF), v.fog(), 1e-6f);
        assertArrayEquals(rgb(0x050533), v.waterFog(), 1e-6f);
        assertEquals(-8f, v.waterFogStart(), 0f);
        assertEquals(96f, v.waterFogEnd(), 0f);
    }

    @Test
    void skyColoursComeFromEachBiomeFile() {
        assertArrayEquals(rgb(0x6EB1FF), only("minecraft:desert").sky(), 1e-6f);
        assertArrayEquals(rgb(0x6EB1FF), only("minecraft:badlands").sky(), 1e-6f);
        assertArrayEquals(rgb(0x7FA1FF), only("minecraft:snowy_plains").sky(), 1e-6f);
        assertArrayEquals(rgb(0x7BA4FF), only("minecraft:ocean").sky(), 1e-6f);
        assertArrayEquals(rgb(0x77A8FF), only("minecraft:jungle").sky(), 1e-6f);
        assertArrayEquals(rgb(0xB9B9B9), only("minecraft:pale_garden").sky(), 1e-6f);
        assertArrayEquals(rgb(0x817770), only("minecraft:pale_garden").fog(), 1e-6f);
    }

    @Test
    void swampsHaveTheirWaterFogColourAndAShorterWaterFog() {
        // swamp.json / mangrove_swamp.json: water_fog_end_distance {"modifier": "multiply", "argument": 0.85} on the default 96
        BiomeAttributes.Values swamp = only("minecraft:swamp");
        assertArrayEquals(rgb(0x232317), swamp.waterFog(), 1e-6f);
        assertEquals(96f * 0.85f, swamp.waterFogEnd(), 0f);
        BiomeAttributes.Values mangrove = only("minecraft:mangrove_swamp");
        assertArrayEquals(rgb(0x4D7A60), mangrove.waterFog(), 1e-6f);
        assertEquals(96f * 0.85f, mangrove.waterFogEnd(), 0f);
        assertArrayEquals(rgb(0x5DB7EF), only("minecraft:cherry_grove").waterFog(), 1e-6f);
        assertArrayEquals(rgb(0x041F33), only("minecraft:warm_ocean").waterFog(), 1e-6f);
    }

    @Test
    void theGaussianKernelSumsTo4096AndIsCentredOnTheCamera() {
        double total = 0;
        for (double w : BiomeAttributes.gaussianWeights(3.7, -20.1, 101.9, (x, y, z) -> "a").values()) total += w;
        assertEquals(16.0 * 16.0 * 16.0, total, 1e-9);
        // at x = 2 (quart 0.5 - 0.5 = 0, fraction 0) the six x samples get K[1..6] = 1 4 6 4 1 0 for quarts -2..3
        LinkedHashMap<String, Double> w = BiomeAttributes.gaussianWeights(2.0, 2.0, 2.0, (x, y, z) -> "q" + x);
        assertEquals(1.0 * 256, w.get("q-2"), 1e-9);
        assertEquals(4.0 * 256, w.get("q-1"), 1e-9);
        assertEquals(6.0 * 256, w.get("q0"), 1e-9);
        assertEquals(4.0 * 256, w.get("q1"), 1e-9);
        assertEquals(1.0 * 256, w.get("q2"), 1e-9);
        assertEquals(0.0, w.get("q3"), 1e-9);
    }

    @Test
    void atABorderTheSkyIsTheWeightedBlendOfBothBiomes() {
        // camera at x = 0: quart x - 0.5 = -0.5, fraction 0.5, samples -3..2 weigh 0.5 2.5 5 5 2.5 0.5 -> 8 : 8 between the halves
        BiomeAttributes.Values v = BiomeAttributes.sample(0.0, 64.0, 0.0,
                (x, y, z) -> x < 0 ? "minecraft:plains" : "minecraft:desert", DEFS);
        float[] plains = rgb(0x78A7FF), desert = rgb(0x6EB1FF);
        for (int i = 0; i < 3; i++) assertEquals(plains[i] + (desert[i] - plains[i]) * 0.5f, v.sky()[i], 1e-6f);
        // one quart further into the desert (x = 4): samples -2..3, the plains quarts -2 and -1 weigh 0.5 + 2.5 = 3 of 16
        BiomeAttributes.Values w = BiomeAttributes.sample(4.0, 64.0, 0.0,
                (x, y, z) -> x < 0 ? "minecraft:plains" : "minecraft:desert", DEFS);
        float t = (float) (13.0 / 16.0);
        for (int i = 0; i < 3; i++) assertEquals(plains[i] + (desert[i] - plains[i]) * t, w.sky()[i], 1e-6f);
    }

    @Test
    void precipitationFollowsTheBiomeFileAndTheHeight() {
        BiomeDefinition.Noises noises = BiomeDefinition.Noises.vanilla();
        assertEquals(BiomeDefinition.Precipitation.NONE, DEFS.apply("minecraft:desert").getPrecipitationAt(0, 70, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.NONE, DEFS.apply("minecraft:savanna").getPrecipitationAt(0, 70, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.NONE, DEFS.apply("minecraft:badlands").getPrecipitationAt(0, 70, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.RAIN, DEFS.apply("minecraft:plains").getPrecipitationAt(0, 70, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.RAIN, DEFS.apply("minecraft:jungle").getPrecipitationAt(0, 70, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.SNOW, DEFS.apply("minecraft:snowy_plains").getPrecipitationAt(0, 70, 0, 63, noises));
        // taiga (0.25): below sea level + 17 it rains; 0.25 - (v + y - 80) * 0.05 / 40 < 0.15 once y > 160 + |v| (|v| <= 8)
        BiomeDefinition taiga = DEFS.apply("minecraft:taiga");
        assertEquals(BiomeDefinition.Precipitation.RAIN, taiga.getPrecipitationAt(5, 80, 5, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.SNOW, taiga.getPrecipitationAt(5, 200, 5, 63, noises));
    }

    @Test
    void aFlatWorldIsPlainsWithThePlainsTintAndAtmosphere() throws IOException {
        World world = new World(1L, new FlatWorldGenerator());
        Chunk chunk = world.getOrCreateChunk(0, 0);
        world.generateChunk(chunk);
        assertEquals("minecraft:plains", world.getBiome(3, FlatWorldGenerator.GRASS_HEIGHT, 7));
        assertEquals(BiomeDefinition.Precipitation.RAIN, world.getPrecipitationAt(3, FlatWorldGenerator.GRASS_HEIGHT + 1, 7));
        BiomeAttributes.Values v = world.atmosphereAt(3.5, -59.0, 7.5);
        assertArrayEquals(rgb(0x78A7FF), v.sky(), 1e-6f);
        assertArrayEquals(rgb(0xC0D8FF), v.fog(), 1e-6f);
        // plains: temperature 0.8, downfall 0.4 -> ColorMapColorUtil index x = (int) (0.2 * 255) = 50, y = (int) ((1 - 0.32) * 255)
        BufferedImage grass = ImageIO.read(new File("textures/colormap/grass.png"));
        int expected = grass.getRGB(50, (int) ((1.0 - 0.4 * 0.8) * 255.0));
        float[] tint = chunk.getGrassTint(3, 7);
        assertEquals(((expected >> 16) & 0xFF) / 255f, tint[0], 1e-6f);
        assertEquals(((expected >> 8) & 0xFF) / 255f, tint[1], 1e-6f);
        assertEquals((expected & 0xFF) / 255f, tint[2], 1e-6f);
        assertTrue(tint[1] > tint[0], "plains grass is green");
    }
}
