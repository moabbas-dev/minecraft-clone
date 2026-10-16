package com.example.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for {@link BiomeDefinitionLoader} / {@link BiomeDefinition}. Data-file tests need the extracted {@code data/}. */
class BiomeDefinitionLoaderTest {
    private static final Path DATA_ROOT = Path.of(System.getProperty("worldgen.dataRoot", "data"));

    private static BiomeDefinitionLoader loaderOrSkip() {
        assumeTrue(Files.isDirectory(DATA_ROOT.resolve("minecraft").resolve("worldgen").resolve("biome")),
            "data/minecraft/worldgen/biome not extracted");
        return new BiomeDefinitionLoader(DATA_ROOT);
    }

    @Test
    void parsesCodecFieldsAndDefaults() {
        BiomeDefinition biome = BiomeDefinitionLoader.parse("test:a", "{\"has_precipitation\":false,"
            + "\"temperature\":0.8,\"downfall\":0.4,\"effects\":{\"water_color\":\"#3f76e4\"},"
            + "\"carvers\":\"minecraft:nether_cave\",\"features\":[[],[\"minecraft:x\",\"minecraft:y\"]]}");
        assertFalse(biome.hasPrecipitation());
        assertEquals(0.8F, biome.temperature());
        assertEquals(0.4F, biome.downfall());
        assertEquals(BiomeDefinition.TemperatureModifier.NONE, biome.temperatureModifier());
        assertEquals(0xFF3F76E4, biome.getWaterColor());
        assertEquals(Optional.empty(), biome.effects().grassColorOverride());
        assertEquals(BiomeDefinition.GrassColorModifier.NONE, biome.effects().grassColorModifier());
        assertEquals(List.of("minecraft:nether_cave"), biome.carvers());
        assertEquals(List.of(List.of(), List.of("minecraft:x", "minecraft:y")), biome.features());
        assertTrue(biome.attributes().isEmpty());
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> BiomeDefinitionLoader.parse("t", "{\"temperature\":1}"));
        assertThrows(IllegalArgumentException.class, () -> BiomeDefinitionLoader.rgbColor("3f76e4", "c"));
        assertThrows(IllegalArgumentException.class, () -> BiomeDefinitionLoader.rgbColor("#3f76e", "c"));
        assertThrows(IllegalArgumentException.class, () -> BiomeJson.parse("{\"a\":1,}"));
        assertThrows(IllegalArgumentException.class, () -> BiomeJson.parse("[01]"));
        assertEquals(123, BiomeDefinitionLoader.rgbColor(BiomeJson.parse("123"), "c"));
    }

    @Test
    void heightAdjustedTemperatureFollowsVanillaFormula() {
        BiomeDefinition biome = BiomeDefinitionLoader.parse("t", "{\"has_precipitation\":true,"
            + "\"temperature\":0.25,\"downfall\":0.5,\"effects\":{\"water_color\":\"#000000\"},"
            + "\"carvers\":[],\"features\":[]}");
        BiomeDefinition.Noise2D half = (x, z) -> 0.5F;
        BiomeDefinition.Noises noises = new BiomeDefinition.Noises(half, half, half);
        assertEquals(0.25F, biome.getHeightAdjustedTemperature(0, 80, 0, 63, noises));
        // y = 100, snow level 80: 0.25 - (0.5*8 + 100 - 80) * 0.05 / 40
        float expected = 0.25F - (4.0F + 100.0F - 80.0F) * 0.05F / 40.0F;
        assertEquals(expected, biome.getHeightAdjustedTemperature(0, 100, 0, 63, noises));
        assertTrue(biome.warmEnoughToRain(0, 100, 0, 63, noises));
        assertEquals(BiomeDefinition.Precipitation.RAIN, biome.getPrecipitationAt(0, 100, 0, 63, noises));
        // y = 200: 0.25 - 124 * 0.00125 = 0.095 -> snow
        assertEquals(BiomeDefinition.Precipitation.SNOW, biome.getPrecipitationAt(0, 200, 0, 63, noises));
    }

    @Test
    void frozenModifierAndGrassModifiers() {
        BiomeDefinition.Noise2D low = (x, z) -> -0.5F;
        BiomeDefinition.Noises lowNoises = new BiomeDefinition.Noises(low, low, low);
        assertEquals(0.2F, BiomeDefinition.TemperatureModifier.FROZEN.modifyTemperature(5, 9, 0.0F, lowNoises));
        BiomeDefinition.Noise2D high = (x, z) -> 0.9F;
        BiomeDefinition.Noises highNoises = new BiomeDefinition.Noises(high, high, high);
        assertEquals(0.0F, BiomeDefinition.TemperatureModifier.FROZEN.modifyTemperature(5, 9, 0.0F, highNoises));
        assertEquals(-11766212, BiomeDefinition.GrassColorModifier.SWAMP.modifyColor(0, 0, 0, lowNoises));
        assertEquals(-9801671, BiomeDefinition.GrassColorModifier.SWAMP.modifyColor(0, 0, 0, highNoises));
        int base = 0xFF91BD59;
        assertEquals(((base & 0xFEFEFE) + 0x28340A >> 1) | 0xFF000000,
            BiomeDefinition.GrassColorModifier.DARK_FOREST.modifyColor(0, 0, base, lowNoises));
    }

    @Test
    void everyBiomeIdHasALoadableFile() throws Exception {
        BiomeDefinitionLoader loader = loaderOrSkip();
        for (String id : Biomes.ALL) {
            assertTrue(Files.isRegularFile(loader.fileFor(id)), id);
            BiomeDefinition biome = loader.load(id);
            assertEquals(id, biome.id());
            assertTrue(biome.attributes().containsKey("minecraft:gameplay/natural_mob_spawns"), id);
        }
    }

    @Test
    void knownValuesFromSwampAndFrozenFiles() throws Exception {
        BiomeDefinitionLoader loader = loaderOrSkip();
        BiomeDefinition swamp = loader.load(Biomes.SWAMP);
        assertEquals(0.8F, swamp.temperature());
        assertEquals(0.9F, swamp.downfall());
        assertEquals(BiomeDefinition.GrassColorModifier.SWAMP, swamp.effects().grassColorModifier());
        assertEquals(Optional.of(0xFF6A7039), swamp.effects().foliageColorOverride());
        assertEquals(0xFF617B64, swamp.getWaterColor());
        assertEquals(Optional.of("#78a7ff"), swamp.attribute("minecraft:visual/sky_color"));
        assertEquals(List.of("minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"),
            swamp.carvers());
        assertEquals(11, swamp.features().size());
        assertEquals(BiomeDefinition.TemperatureModifier.FROZEN, loader.load(Biomes.FROZEN_OCEAN).temperatureModifier());
    }
}
