package com.example.worldgen.density;

import com.example.worldgen.json.Json;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.NoiseGeneratorSettingsLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Decoding of every vanilla density-function file and all seven noise settings, plus codec validations. */
class DensityFunctionLoadingTest {

    private static final Path DATA = Path.of("data");

    @Test
    void typeListHas44Types() {
        assertEquals(44, DensityFunctionParser.TYPES.size());
        assertEquals("constant", DensityFunctionParser.TYPES.get(0));
        assertEquals("old_blended_noise", DensityFunctionParser.TYPES.get(43));
    }

    @Test
    void everyDensityFunctionFileDecodes() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/density_function")), "vanilla data not restored");
        WorldgenRegistries registries = new WorldgenRegistries(DATA);
        List<String> ids = registries.listIds("density_function");
        assertEquals(55, ids.size());
        for (String id : ids) {
            assertNotNull(registries.densityFunction(id).value(), id);
        }
    }

    @Test
    void allNoiseSettingsDecode() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/noise_settings")), "vanilla data not restored");
        WorldgenRegistries registries = new WorldgenRegistries(DATA);
        NoiseGeneratorSettingsLoader loader = new NoiseGeneratorSettingsLoader(registries);
        for (String name : List.of("overworld", "large_biomes", "amplified", "nether", "end", "caves", "floating_islands")) {
            NoiseGeneratorSettings s = loader.load(name);
            assertNotNull(s.noiseRouter().finalDensity(), name);
            assertNotNull(s.materialRule(), name);
        }
        NoiseGeneratorSettings overworld = loader.load("minecraft:overworld");
        assertEquals(-64, overworld.noiseSettings().minY());
        assertEquals(384, overworld.noiseSettings().height());
        assertEquals(63, overworld.seaLevel());
        assertFalse(overworld.useLegacyRandomSource());
        assertTrue(overworld.aquifers().isPresent());
        assertEquals(2, overworld.spawnTarget().size());
        assertEquals(9, overworld.debugFunctions().size());
        assertEquals("minecraft:overworld", overworld.materialRuleId().orElseThrow());
        NoiseGeneratorSettings nether = loader.load("nether");
        assertTrue(nether.useLegacyRandomSource());
        assertTrue(nether.aquifers().isEmpty());
    }

    @Test
    void codecValidationsRejectBadInput() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/density_function")), "vanilla data not restored");
        DensityFunctionParser parser = new WorldgenRegistries(DATA).parser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"minecraft:clamp\",\"input\":0.5,\"min\":1,\"max\":0}")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"minecraft:gradient\",\"axis\":\"y\",\"from_coordinate\":3,\"to_coordinate\":3,\"from_value\":0,\"to_value\":1}")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"minecraft:interval_select\",\"input\":0,\"thresholds\":[0.5,0.1,0.7],\"functions\":[0,1,2,3]}")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"minecraft:interpolated\",\"input\":0,\"cell_size_xz\":0,\"cell_size_y\":4}")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals("2000000.0")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(Json.parseWithNumberLiterals("{\"type\":\"minecraft:weird_scaled_sampler\"}")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("minecraft:does/not/exist"));
    }
}
