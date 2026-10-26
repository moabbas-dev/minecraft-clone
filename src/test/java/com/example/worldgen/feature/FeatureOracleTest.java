package com.example.worldgen.feature;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.biome.MultiNoiseBiomeSourceParameterList;
import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.level.TerrainFeatureChunk;
import com.example.worldgen.feature.placement.PlacementModifier;
import com.example.worldgen.feature.placement.PlacementModifiers;
import com.example.worldgen.json.Json;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.random.WorldgenRandom;
import com.example.worldgen.random.XoroshiroRandomSource;
import com.example.worldgen.terrain.BlockTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden vectors from an out-of-repo oracle that runs the REAL 26.3 classes of the client jar
 * ({@code ChunkGenerator.applyBiomeDecoration}, {@code FeatureSorter}, {@code FeaturePlacer}, every placement modifier the
 * data and the synthetic cases use, the block predicates, rule tests, {@code SimpleStateProvider}, {@code RuleBasedStateProvider},
 * {@code OreFeature}, {@code AbstractOreFeature}, {@code BulkSectionAccess}, {@code DiskFeature}, {@code SpringFeature},
 * {@code UnderwaterMagmaFeature}, {@code Column}) against a {@code WorldGenLevel} stub over a 5 x 5 chunk neighbourhood
 * produced by the repo's TERRAIN port ({@link OverworldGenerator}). Shadowed by the oracle (harness): block states (StateRef
 * wrappers with the vanilla tag files and {@code BlockStateFacts}), the region (WorldGenRegion's FEATURES semantics), the
 * chunk storage (the repo's TerrainChunk), codecs/registries.
 * <ul>
 *     <li>{@code STEP}: the real sorter's per-step feature order for the 56 overworld biomes;</li>
 *     <li>{@code SYNTH}: synthetic placed features (count_on_every_layer, fixed_placement, cuboid, randomly_selected,
 *     environment_scan, offset with every int provider type, the predicate types, noise counts, surface filters, height
 *     providers) through the real {@code FeaturePlacer.place}, logging positions and draw counts;</li>
 *     <li>{@code MODE A}: the whole decoration loop with every top-level placed feature's real modifier chain feeding a logging
 *     feature (features whose chain needs the unported {@code would_survive} are skipped on both sides);</li>
 *     <li>{@code MODE B}: the loop with the real ported features writing blocks (every other top-level feature skipped on both
 *     sides; a nested unported feature, a light read or a region-random read throws on both sides and stops further feature
 *     calls of that top-level placed feature, see {@link UnportedFeature}); every {@code setBlock}, section write,
 *     post-processing mark, fluid tick and block tick is compared in order, plus the random draw count at each feature start
 *     and at the end;</li>
 *     <li>{@code SURV}: the real block classes' {@code canSurvive} (FlowerBlock, SaplingBlock, DoublePlantBlock, CactusBlock,
 *     SugarCaneBlock, coral, ... allocated without constructors) for every ported block over supports and contexts;</li>
 *     <li>{@code PROV}: every block state provider type at fixed positions (states and draw counts);</li>
 *     <li>{@code FEAT}: every named configured feature of the ported types placed directly at fixed positions over fresh
 *     terrain of 12 regions.</li>
 * </ul>
 * Real vanilla in these sections: the feature, provider, predicate, placement and block classes. Harness (shared with the
 * port): block-state shape / fluid facts ({@code BlockStateFacts}), state definitions ({@code BlockStateDefinitions}), tag
 * contents, the terrain chunks.
 */
class FeatureOracleTest {

    static final Path DATA = Path.of("data");
    static final String[] SYNTH = {
            "[{\"type\":\"minecraft:count_on_every_layer\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":3}}]",
            "[{\"type\":\"minecraft:fixed_placement\",\"positions\":[[3,70,5],[40,60,2],[-5,10,9],[15,-64,15]]}]",
            "[{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},{\"type\":\"minecraft:cuboid\",\"xz_size\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":3},\"y_size\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":2},\"include_edges\":false}]",
            "[{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":-30},\"max_inclusive\":{\"absolute\":60}}},{\"type\":\"minecraft:cuboid\",\"xz_size\":2,\"y_size\":2,\"include_interior\":false}]",
            "[{\"type\":\"minecraft:count\",\"count\":6},{\"type\":\"minecraft:randomly_selected\",\"placements\":[{\"type\":\"minecraft:offset\",\"x\":{\"type\":\"minecraft:trapezoid\",\"min\":-4,\"max\":4,\"plateau\":0},\"y\":0,\"z\":3},{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":0,\"max_inclusive\":2}},{\"type\":\"minecraft:random_chance\",\"chance\":0.5}]}]",
            "[{\"type\":\"minecraft:count\",\"count\":8},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"absolute\":100}}},{\"type\":\"minecraft:environment_scan\",\"direction_of_search\":\"down\",\"max_steps\":12,\"target_condition\":{\"type\":\"minecraft:solid\"},\"allowed_search_condition\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":[\"minecraft:air\",\"minecraft:water\"]}}]",
            "[{\"type\":\"minecraft:count\",\"count\":8},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"absolute\":100}}},{\"type\":\"minecraft:environment_scan\",\"direction_of_search\":\"up\",\"max_steps\":5,\"target_condition\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]",
            "[{\"type\":\"minecraft:count\",\"count\":20},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},{\"type\":\"minecraft:offset\",\"x\":{\"type\":\"minecraft:clamped_normal\",\"mean\":0.0,\"deviation\":3.0,\"min_inclusive\":-6,\"max_inclusive\":6},\"y\":{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":-3,\"max_inclusive\":2},\"z\":{\"type\":\"minecraft:clamped\",\"source\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":-9,\"max_inclusive\":9},\"min_inclusive\":-5,\"max_inclusive\":5}},{\"type\":\"minecraft:surface_water_depth_filter\",\"max_water_depth\":0},{\"type\":\"minecraft:rarity_filter\",\"chance\":2},{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":{\"type\":\"minecraft:any_of\",\"predicates\":[{\"type\":\"minecraft:volume_match\",\"min\":[-1,-1,-1],\"max\":[1,0,1],\"match\":{\"type\":\"minecraft:not\",\"predicate\":{\"type\":\"minecraft:matching_block_tag\",\"tag\":\"minecraft:base_stone_overworld\"}}},{\"type\":\"minecraft:all_of\",\"predicates\":[{\"type\":\"minecraft:has_sturdy_face\",\"direction\":\"up\",\"offset\":[0,-1,0]},{\"type\":\"minecraft:not\",\"predicate\":{\"type\":\"minecraft:inside_world_bounds\",\"offset\":[0,16,0]}}]},{\"type\":\"minecraft:replaceable\",\"offset\":[0,0,0]}]}}]",
            "[{\"type\":\"minecraft:noise_based_count\",\"noise_to_count_ratio\":12,\"noise_factor\":80.0,\"noise_offset\":0.3},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"OCEAN_FLOOR\"},{\"type\":\"minecraft:surface_relative_threshold_filter\",\"heightmap\":\"WORLD_SURFACE\",\"min_inclusive\":-3,\"max_inclusive\":0},{\"type\":\"minecraft:noise_threshold_count\",\"noise_level\":-0.2,\"below_noise\":1,\"above_noise\":2},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:very_biased_to_bottom\",\"min_inclusive\":{\"above_bottom\":4},\"max_inclusive\":{\"below_top\":10},\"inner\":3}}]",
            "[{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:weighted_list\",\"distribution\":[{\"data\":1,\"weight\":3},{\"data\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":2,\"max_inclusive\":5},\"weight\":2}]}},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":-60},\"max_inclusive\":{\"absolute\":40},\"plateau\":20}},{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":{\"type\":\"minecraft:all_of\",\"predicates\":[{\"type\":\"minecraft:height_range\",\"min_inclusive\":{\"above_bottom\":10},\"max_inclusive\":{\"absolute\":30}},{\"type\":\"minecraft:unobstructed\"},{\"type\":\"minecraft:true\"},{\"type\":\"minecraft:matching_fluids\",\"fluids\":[\"minecraft:water\",\"minecraft:lava\"],\"offset\":[0,1,0]}]}}]"
    };

    static BlockTags tags;
    static BiomeDefinition.Noises noises;
    static FeatureLoader loader;
    static List<String> biomes;
    static Map<String, BiomeDefinition> biomeDefs = new HashMap<>();

    @BeforeAll
    static void setUp() throws Exception {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/placed_feature")), "vanilla data not restored");
        tags = BlockTags.load(DATA);
        noises = BiomeDefinition.Noises.vanilla();
        loader = new FeatureLoader(DATA, tags, noises);
        biomes = new ArrayList<>(MultiNoiseBiomeSource.createFromPreset(
                new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD)).possibleBiomes());
        com.example.worldgen.biome.BiomeDefinitionLoader bl = new com.example.worldgen.biome.BiomeDefinitionLoader(DATA);
        for (String b : biomes) biomeDefs.put(b, bl.load(b));
    }

    /** Records positions and draw counts (the oracle's logging feature). */
    static final class Logging implements Feature {
        final List<String> out;

        Logging(List<String> out) {
            this.out = out;
        }

        @Override
        public String type() {
            return "test:logging";
        }

        @Override
        public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
            out.add("P " + origin.x() + " " + origin.y() + " " + origin.z() + " " + ((WorldgenRandom) random).getCount());
            return true;
        }
    }

    static Object json(String path) {
        try {
            return Json.parseWithNumberLiterals(Files.readString(DATA.resolve(path)));
        } catch (Exception e) {
            throw new RuntimeException(path, e);
        }
    }

    /** Mode A placed features: the real modifier chain into a logging feature. */
    static java.util.function.Function<String, PlacedFeature> positionsLibrary(List<String> out) {
        Map<String, PlacedFeature> cache = new HashMap<>();
        Map<String, Feature> features = new HashMap<>();
        return id -> cache.computeIfAbsent(id, k -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) json("minecraft/worldgen/placed_feature/" + k.substring(10) + ".json");
            Object placement = m.get("placement");
            Object f = m.get("feature");
            Feature feature = f instanceof String s ? features.computeIfAbsent(s, x -> new Logging(out)) : new Logging(out);
            List<PlacementModifier> mods = PlacementModifiers.list(placement, tags, noises);
            return new PlacedFeature(k, feature, mods, placement);
        });
    }

    static Map<Long, FeatureChunk> region(OverworldGenerator terrain, int cx, int cz) {
        Map<Long, FeatureChunk> region = new HashMap<>();
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                region.put(key(cx + dx, cz + dz), new TerrainFeatureChunk(terrain.generate(cx + dx, cz + dz)));
            }
        }
        return region;
    }

    static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    static GenRegion genRegion(long seed, int cx, int cz, Map<Long, FeatureChunk> chunks, List<String> out) {
        GenRegion r = new GenRegion(seed, cx, cz, (x, z) -> chunks.get(key(x, z)), tags, -64, 384, 63);
        r.setListener(new GenRegion.Listener() {
            @Override
            public void setBlock(int x, int y, int z, StateRef state) {
                out.add("S " + x + " " + y + " " + z + " " + state);
            }

            @Override
            public void sectionWrite(int x, int y, int z, StateRef state) {
                out.add("B " + x + " " + y + " " + z + " " + state);
            }

            @Override
            public void postProcessMark(int x, int y, int z) {
                out.add("M " + x + " " + y + " " + z);
            }

            @Override
            public void fluidTick(int x, int y, int z, String fluid) {
                out.add("T " + x + " " + y + " " + z + " " + fluid);
            }

            @Override
            public void blockTick(int x, int y, int z, String block, int delay) {
                out.add("K " + x + " " + y + " " + z + " " + block + " " + delay);
            }
        });
        return r;
    }

    static void decorate(BiomeDecoration decoration, GenRegion region, List<String> out) {
        decoration.applyBiomeDecoration(region, new BiomeDecoration.Listener() {
            @Override
            public void beforeFeature(int step, int index, PlacedFeature feature, WorldgenRandom random) {
                out.add("F " + feature.id() + " " + random.getCount());
            }

            @Override
            public void afterChunk(WorldgenRandom random) {
                out.add("E " + random.getCount());
            }
        });
    }

    /** Compares the port's events with the oracle's (an oracle count of "-" means the oracle could not read it). */
    static int compare(List<String> expected, List<String> actual, String label) {
        assertEquals(expected.size(), actual.size(), label + " event count; first difference at " + firstDiff(expected, actual));
        for (int i = 0; i < expected.size(); i++) {
            String e = expected.get(i);
            String a = actual.get(i);
            if (e.startsWith("F ") && e.endsWith(" -")) {
                assertEquals(e.substring(0, e.length() - 2), a.substring(0, a.lastIndexOf(' ')), label + " line " + i);
            } else {
                assertEquals(e, a, label + " line " + i);
            }
        }
        return expected.size();
    }

    static String firstDiff(List<String> e, List<String> a) {
        for (int i = 0; i < Math.min(e.size(), a.size()); i++) {
            if (!e.get(i).equals(a.get(i)) && !(e.get(i).endsWith(" -") && a.get(i).startsWith(e.get(i).substring(0, e.get(i).length() - 1)))) {
                return i + ": expected " + e.get(i) + " got " + a.get(i);
            }
        }
        return "end";
    }

    @Test
    void matchesRealVanilla() throws Exception {
        List<String> modeAOut = new ArrayList<>();
        BiomeDecoration decorationA = new BiomeDecoration(biomes, biomeDefs::get, positionsLibrary(modeAOut), -64, 384, 63);
        BiomeDecoration decorationB = new BiomeDecoration(biomes, biomeDefs::get, loader, -64, 384, 63);
        int steps = 0;
        int synth = 0;
        int cases = 0;
        long aEvents = 0;
        long bEvents = 0;
        long writes = 0;
        long nanos = 0;
        int survivalVectors = 0;
        int providerVectors = 0;
        int featCases = 0;
        long featureEvents = 0;
        long featureWrites = 0;
        Map<Long, OverworldGenerator> terrains = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("features_golden.txt.gz")) {
            assertNotNull(in, "golden resource");
            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (!line.equals("END")) {
                String[] p = line.split(" ");
                if (p[0].equals("STEP")) {
                    int s = Integer.parseInt(p[1]);
                    List<String> ids = new ArrayList<>();
                    for (PlacedFeature f : decorationB.featuresPerStep().get(s).features()) ids.add(f.id());
                    assertEquals(List.of(p).subList(2, p.length), ids, "sorter step " + s);
                    steps++;
                    line = reader.readLine();
                } else if (p[0].equals("SYNTH")) {
                    long seed = Long.parseLong(p[1]);
                    int cx = Integer.parseInt(p[2]);
                    int cz = Integer.parseInt(p[3]);
                    int k = Integer.parseInt(p[4]);
                    List<String> expected = new ArrayList<>();
                    line = reader.readLine();
                    while (line.startsWith("P ") || line.startsWith("E ")) {
                        expected.add(line);
                        boolean end = line.startsWith("E ");
                        line = reader.readLine();
                        if (end) break;
                    }
                    List<String> out = new ArrayList<>();
                    GenRegion region = genRegion(seed, cx, cz,
                            region(terrains.computeIfAbsent(seed, s -> new OverworldGenerator(DATA, s)), cx, cz), new ArrayList<>());
                    PlacedFeature pf = new PlacedFeature(null, new Logging(out), PlacementModifiers.list(Json.parseWithNumberLiterals(SYNTH[k]), tags, noises), null);
                    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed * 31 + k));
                    DecorationGenerator gen = new DecorationGenerator() {
                        public int minY() {
                            return -64;
                        }

                        public int genDepth() {
                            return 384;
                        }

                        public int seaLevel() {
                            return 63;
                        }

                        public boolean biomeHasFeature(String biome, PlacedFeature feature) {
                            throw new IllegalStateException();
                        }
                    };
                    pf.place(region, gen, random, new Pos(cx * 16, -64, cz * 16));
                    out.add("E " + random.getCount());
                    compare(expected, out, "synthetic " + k + " seed " + seed + " " + cx + "," + cz);
                    synth++;
                } else if (p[0].equals("CASE")) {
                    long seed = Long.parseLong(p[1]);
                    int cx = Integer.parseInt(p[2]);
                    int cz = Integer.parseInt(p[3]);
                    String label = "seed " + seed + " chunk " + cx + "," + cz;
                    assertEquals("MODE A", reader.readLine());
                    List<String> expectedA = new ArrayList<>();
                    line = reader.readLine();
                    while (!line.equals("MODE B")) {
                        expectedA.add(line);
                        line = reader.readLine();
                    }
                    List<String> expectedB = new ArrayList<>();
                    line = reader.readLine();
                    while (!line.startsWith("CASE") && !line.startsWith("FEATCASE") && !line.equals("END")) {
                        expectedB.add(line);
                        line = reader.readLine();
                    }
                    OverworldGenerator terrain = terrains.computeIfAbsent(seed, s -> new OverworldGenerator(DATA, s));
                    Map<Long, FeatureChunk> chunks = region(terrain, cx, cz);
                    modeAOut.clear();
                    decorate(decorationA, genRegion(seed, cx, cz, chunks, modeAOut), modeAOut);
                    aEvents += compare(expectedA, modeAOut, "mode A " + label);
                    List<String> outB = new ArrayList<>();
                    GenRegion regionB = genRegion(seed, cx, cz, chunks, outB);
                    long t = System.nanoTime();
                    decorate(decorationB, regionB, outB);
                    nanos += System.nanoTime() - t;
                    bEvents += compare(expectedB, outB, "mode B " + label);
                    writes += outB.stream().filter(s -> s.startsWith("S ") || s.startsWith("B ")).count();
                    cases++;
                } else if (p[0].equals("SURVSPEC")) {
                    List<String> states = List.of(p).subList(1, p.length);
                    String[] sup = reader.readLine().split(" ");
                    String[] ctx = reader.readLine().split(" ");
                    List<String> supports = List.of(sup).subList(1, sup.length);
                    List<String> contexts = List.of(ctx).subList(1, ctx.length);
                    line = reader.readLine();
                    while (line.startsWith("V ")) {
                        String[] v = line.split(" ");
                        String state = states.get(Integer.parseInt(v[1]));
                        String support = supports.get(Integer.parseInt(v[2]));
                        assertEquals(v[3], VegetationVectors.survival(tags, state, support, contexts),
                                "canSurvive of " + state + " on " + support + " in " + contexts);
                        survivalVectors += v[3].length();
                        line = reader.readLine();
                    }
                } else if (p[0].equals("PROVSPEC")) {
                    int k = Integer.parseInt(p[1]);
                    List<String> expected = new ArrayList<>();
                    line = reader.readLine();
                    while (line.startsWith("Q ")) {
                        expected.add(line);
                        line = reader.readLine();
                    }
                    List<String> out = new ArrayList<>();
                    VegetationVectors.provider(loader.providers(), tags, k, p[2], out);
                    compare(expected, out, "provider " + p[2]);
                    providerVectors += out.size();
                } else if (p[0].equals("FEATCASE")) {
                    long seed = Long.parseLong(p[1]);
                    int cx = Integer.parseInt(p[2]);
                    int cz = Integer.parseInt(p[3]);
                    List<String> ids = List.of(p[4].split(","));
                    List<String> expected = new ArrayList<>();
                    line = reader.readLine();
                    while (!line.startsWith("FEATCASE") && !line.equals("END")) {
                        expected.add(line);
                        line = reader.readLine();
                    }
                    List<String> out = new ArrayList<>();
                    GenRegion region = genRegion(seed, cx, cz,
                            region(terrains.computeIfAbsent(seed, s -> new OverworldGenerator(DATA, s)), cx, cz), out);
                    VegetationVectors.features(loader, ids, region, seed, cx, cz, out);
                    featureEvents += compare(expected, out, "features seed " + seed + " chunk " + cx + "," + cz);
                    featureWrites += out.stream().filter(s -> s.startsWith("S ")).count();
                    featCases++;
                } else {
                    throw new AssertionError("unexpected golden line " + line);
                }
            }
        }
        assertEquals(11, steps);
        assertEquals(40, synth);
        assertTrue(cases >= 20);
        assertTrue(writes > 10000, "block writes compared: " + writes);
        assertTrue(survivalVectors > 10000, "survival vectors: " + survivalVectors);
        assertTrue(providerVectors >= 48 * 20, "provider vectors: " + providerVectors);
        assertTrue(featCases >= 12 && featureWrites > 5000, "feature vectors: " + featCases + " cases, " + featureWrites + " writes");
        System.out.printf("feature oracle: %d sorter steps, %d synthetic chains, %d cases, %d mode-A events, %d mode-B events (%d block writes); "
                + "decoration %.2f ms/chunk; %d canSurvive vectors, %d provider samples, %d feature cases (%d events, %d block writes)%n",
                steps, synth, cases, aEvents, bEvents, writes, nanos / 1e6 / cases, survivalVectors, providerVectors, featCases,
                featureEvents, featureWrites);
    }
}
