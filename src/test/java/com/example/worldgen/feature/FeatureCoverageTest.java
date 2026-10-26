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
 * (so nothing is silently missing), which ported ones can stop at a nested unported feature ({@link UnportedFeature.Reached}),
 * and checks the premise of {@link BiomeDecoration#biomeHasFeature}'s identity set: no two
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
        Map<String, Integer> partial = new TreeMap<>();
        Map<String, Integer> reachedUnported = new TreeMap<>();
        for (String id : top) {
            PlacedFeature p = loader.placedFeature(id);
            if (!p.feature().isPorted()) continue;
            Set<String> unported = new TreeSet<>();
            nestedUnported(p.feature(), unported, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
            if (!unported.isEmpty()) {
                partial.merge(p.feature().type(), 1, Integer::sum);
                for (String u : unported) reachedUnported.merge(u, 1, Integer::sum);
            }
        }
        System.out.println("ported top-level placed features by type: " + ported);
        System.out.println("skipped (UnportedFeature) by type: " + skipped);
        System.out.println("ported top-level features that can reach a nested unported feature, by type: " + partial);
        System.out.println("nested unported types reachable (top-level features reaching each): " + reachedUnported);
        assertEquals(new TreeMap<>(Map.ofEntries(Map.entry("minecraft:bamboo", 2), Map.entry("minecraft:block_column", 9),
                Map.entry("minecraft:disk", 5), Map.entry("minecraft:ore", 30), Map.entry("minecraft:overlay", 1),
                Map.entry("minecraft:random_boolean_selector", 2), Map.entry("minecraft:random_selector", 23),
                Map.entry("minecraft:sequence", 2), Map.entry("minecraft:simple_block", 52), Map.entry("minecraft:simple_random_selector", 5),
                Map.entry("minecraft:spring_feature", 3), Map.entry("minecraft:underwater_magma", 1), Map.entry("minecraft:vines", 2),
                Map.entry("minecraft:weighted_random_selector", 9))), ported);
        assertEquals(new TreeMap<>(Map.ofEntries(Map.entry("minecraft:block_blob", 1), Map.entry("minecraft:blue_ice", 1),
                Map.entry("minecraft:fossil", 2), Map.entry("minecraft:freeze_top_layer", 1), Map.entry("minecraft:geode", 1),
                Map.entry("minecraft:iceberg", 2), Map.entry("minecraft:lake", 2), Map.entry("minecraft:large_dripstone", 1),
                Map.entry("minecraft:monster_room", 2), Map.entry("minecraft:multiface_growth", 2), Map.entry("minecraft:root_system", 2),
                Map.entry("minecraft:speleothem_cluster", 2), Map.entry("minecraft:spike", 1), Map.entry("minecraft:tree", 2),
                Map.entry("minecraft:vegetation_patch", 3))), skipped);
        assertEquals(PARTIAL, partial.toString());
        assertEquals(REACHED, reachedUnported.toString());
        int skippedCount = skipped.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(171 - 146, skippedCount);
        for (String type : skipped.keySet()) {
            assertTrue(!FeatureLoader.PORTED_TYPES.contains(type.substring(10)), type);
        }
    }

    /** Ported top-level placed features (by type) that stop at a nested unported feature when they reach it. */
    static final String PARTIAL = "{minecraft:overlay=1, minecraft:random_boolean_selector=2, minecraft:random_selector=23, "
            + "minecraft:sequence=2, minecraft:simple_random_selector=3, minecraft:weighted_random_selector=1}";
    /** The nested unported feature types they can reach (number of top-level placed features reaching each). */
    static final String REACHED = "{minecraft:coral_claw=1, minecraft:coral_tree=1, minecraft:fallen_tree=18, "
            + "minecraft:huge_brown_mushroom=2, minecraft:huge_red_mushroom=2, minecraft:lake=1, minecraft:sculk_patch=1, "
            + "minecraft:speleothem=2, minecraft:template=1, minecraft:tree=24, minecraft:vegetation_patch=1, "
            + "minecraft:waterlogged_vegetation_patch=1}";

    /** The unported feature types a feature can reach through the composition features' nested placed features. */
    static void nestedUnported(Feature f, Set<String> out, Set<Object> seen) {
        if (!seen.add(f)) return;
        if (!f.isPorted()) {
            out.add(f.type());
            return;
        }
        List<PlacedFeature> nested = new java.util.ArrayList<>();
        if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.RandomSelector r) {
            for (var w : r.features()) nested.add(w.feature());
            nested.add(r.defaultFeature());
        } else if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.SimpleRandomSelector s) {
            nested.addAll(s.features());
        } else if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.WeightedRandomSelector w) {
            for (var e : w.features().unwrap()) nested.add(e.value());
        } else if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.RandomBooleanSelector b) {
            nested.add(b.featureTrue());
            nested.add(b.featureFalse());
        } else if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.Sequence s) {
            nested.addAll(s.features());
        } else if (f instanceof com.example.worldgen.feature.types.CompositeFeatures.Overlay o) {
            nested.addAll(o.features());
        }
        for (PlacedFeature p : nested) nestedUnported(p.feature(), out, seen);
    }
}
