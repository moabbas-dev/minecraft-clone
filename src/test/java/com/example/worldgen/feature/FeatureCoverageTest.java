package com.example.worldgen.feature;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeDefinitionLoader;
import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.biome.MultiNoiseBiomeSourceParameterList;
import com.example.worldgen.json.Json;
import com.example.worldgen.terrain.BlockTags;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lists exactly which top-level placed features of the 56 overworld biomes run and which are skipped as {@link UnportedFeature}
 * (so nothing is silently missing), and checks the premise of {@link BiomeDecoration#biomeHasFeature}'s identity set: no two
 * distinct biome-listed placed features have the same feature and placement JSON.
 */
class FeatureCoverageTest {

    static final Path DATA = Path.of("data");

    @Test
    void portedAndSkippedOverworldFeatures() throws Exception {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/placed_feature")), "vanilla data not restored");
        FeatureLoader loader = new FeatureLoader(DATA, BlockTags.load(DATA), BiomeDefinition.Noises.vanilla());
        BiomeDefinitionLoader biomeLoader = new BiomeDefinitionLoader(DATA);
        Set<String> top = new TreeSet<>();
        for (String b : MultiNoiseBiomeSource.createFromPreset(
                new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD)).possibleBiomes()) {
            for (List<String> step : biomeLoader.load(b).features()) top.addAll(step);
        }
        assertEquals(171, top.size());
        Map<String, Integer> ported = new TreeMap<>();
        Map<String, Integer> skipped = new TreeMap<>();
        Map<String, String> byJson = new HashMap<>();
        for (String id : top) {
            PlacedFeature p = loader.placedFeature(id);
            String type = p.feature().type();
            (p.feature().isPorted() ? ported : skipped).merge(type, 1, Integer::sum);
            String json = String.valueOf(Json.parse(Files.readString(DATA.resolve("minecraft/worldgen/placed_feature/"
                    + id.substring(10) + ".json"))));
            String other = byJson.put(json, id);
            assertTrue(other == null, "structurally equal placed features " + other + " and " + id);
        }
        System.out.println("ported top-level placed features by type: " + ported);
        System.out.println("skipped (UnportedFeature) by type: " + skipped);
        assertEquals(Map.of("minecraft:disk", 5, "minecraft:ore", 30, "minecraft:spring_feature", 3, "minecraft:underwater_magma", 1), ported);
        int skippedCount = skipped.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(171 - 39, skippedCount);
        for (String type : skipped.keySet()) {
            assertTrue(!FeatureLoader.PORTED_TYPES.contains(type.substring(10)), type);
        }
    }
}
