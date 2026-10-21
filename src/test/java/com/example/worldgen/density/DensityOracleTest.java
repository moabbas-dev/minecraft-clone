package com.example.worldgen.density;

import com.example.worldgen.BoundedFloatFunction;
import com.example.worldgen.CubicSpline;
import com.example.worldgen.Interval;
import com.example.worldgen.biome.Climate;
import com.example.worldgen.biome.MultiNoiseBiomeSourceParameterList;
import com.example.worldgen.json.Json;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.NoiseGeneratorSettingsLoader;
import com.example.worldgen.levelgen.RandomState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden-vector comparison against the real 26.3 classes. The vectors in {@code golden_density_26_3.txt.gz} were
 * produced by an oracle that ran the vanilla {@code RandomState}, {@code DensityFunctionCompiler}, every vanilla
 * density-function class, {@code Climate}, {@code OverworldBiomeBuilder} and {@code CubicSpline} from the 26.3 client
 * jar (codec/registry classes shadowed by inert stubs; the JSON-to-object step transcribed from the 26.3 codecs and
 * fed with Gson numbers). Covered: the routers, aquifer and debug functions of all seven noise settings, every
 * registry density function, {@code synthetic_functions.json} (every 26.3 type and every compile-time
 * specialisation), declared ranges and domain axes, point values, volume values through a caching pooled context
 * plus cached point reads afterwards, overworld/nether biome selection, and direct spline samples.
 * All floats are compared as {@code floatToIntBits}.
 */
class DensityOracleTest {

    private static final String[] SETTINGS = {"overworld", "large_biomes", "amplified", "nether", "end", "caves", "floating_islands"};
    private static final Path DATA = Path.of("data");

    private static List<String> lines;
    private static List<int[]> points;
    private static List<DensityVolume> volumes;

    @BeforeAll
    static void readGolden() throws IOException {
        lines = new ArrayList<>();
        try (InputStream in = DensityOracleTest.class.getResourceAsStream("golden_density_26_3.txt.gz");
             BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        points = new ArrayList<>();
        volumes = new ArrayList<>();
        for (String line : lines) {
            String[] t = line.split(" ");
            if (t[0].equals("POINTS")) {
                for (int i = 1; i < t.length; i++) {
                    String[] c = t[i].split(",");
                    points.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
                }
            } else if (t[0].equals("VOLS")) {
                for (int i = 1; i < t.length; i++) {
                    String[] c = t[i].split(",");
                    int[] v = new int[9];
                    for (int k = 0; k < 9; k++) {
                        v[k] = Integer.parseInt(c[k]);
                    }
                    volumes.add(new DensityVolume(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
                }
            }
        }
    }

    private static String bits(float f) {
        return Integer.toHexString(Float.floatToIntBits(f));
    }

    private static final class Mismatches {
        int compared;
        final List<String> first = new ArrayList<>();
        int count;

        void check(String key, String expected, float actual) {
            compared++;
            if (!expected.equals(bits(actual))) {
                count++;
                if (first.size() < 25) {
                    first.add(key + " expected " + expected + " (" + Float.intBitsToFloat(Integer.parseUnsignedInt(expected, 16))
                            + ") got " + bits(actual) + " (" + actual + ")");
                }
            }
        }

        void assertNone(String what) {
            System.out.println("[density oracle] " + what + ": " + compared + " compared, " + count + " mismatches");
            assertTrue(compared > 0, "nothing compared for " + what);
            assertEquals(0, count, what + ": " + count + " of " + compared + " differ, first: " + String.join("\n", first));
        }
    }

    /** The functions of one settings file, in the oracle's order. */
    private static LinkedHashMap<String, DensityFunction> functions(WorldgenRegistries registries, String name, NoiseGeneratorSettings s,
                                                                   boolean withExtras) throws IOException {
        LinkedHashMap<String, DensityFunction> fns = new LinkedHashMap<>();
        fns.put(name + "/router/temperature", s.noiseRouter().temperature());
        fns.put(name + "/router/vegetation", s.noiseRouter().vegetation());
        fns.put(name + "/router/continents", s.noiseRouter().continents());
        fns.put(name + "/router/erosion", s.noiseRouter().erosion());
        fns.put(name + "/router/depth", s.noiseRouter().depth());
        fns.put(name + "/router/ridges", s.noiseRouter().ridges());
        fns.put(name + "/router/chunk_surface_level", s.noiseRouter().chunkSurfaceLevel());
        fns.put(name + "/router/final_density", s.noiseRouter().finalDensity());
        s.aquifers().ifPresent(a -> {
            fns.put(name + "/aquifer/barrier", a.barrierNoise());
            fns.put(name + "/aquifer/fluid_level_floodedness", a.fluidLevelFloodednessNoise());
            fns.put(name + "/aquifer/fluid_level_spread", a.fluidLevelSpreadNoise());
            fns.put(name + "/aquifer/lava", a.lavaNoise());
            fns.put(name + "/aquifer/exclusion", a.exclusion());
            fns.put(name + "/aquifer/surface_level", a.surfaceLevel());
        });
        for (NoiseGeneratorSettings.DebugFunctionEntry e : s.debugFunctions()) {
            fns.put(name + "/debug/" + e.label(), e.function());
        }
        if (withExtras) {
            for (String id : registries.listIds("density_function")) {
                fns.put("df/" + id, registries.parser().parse(id));
            }
            String text;
            try (InputStream in = DensityOracleTest.class.getResourceAsStream("synthetic_functions.json")) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> syn = (Map<String, Object>) Json.parseWithNumberLiterals(text);
            for (Map.Entry<String, Object> e : syn.entrySet()) {
                fns.put("syn/" + e.getKey(), registries.parser().parse(e.getValue()));
            }
        }
        return fns;
    }

    @Test
    void everyFunctionMatchesTheVanillaOracle() throws IOException {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/density_function")), "vanilla data not restored");
        WorldgenRegistries registries = new WorldgenRegistries(DATA);
        NoiseGeneratorSettingsLoader loader = new NoiseGeneratorSettingsLoader(registries);
        Map<String, Map<String, DensityFunction>> bySettings = new HashMap<>();
        Map<String, NoiseGeneratorSettings> settings = new HashMap<>();
        for (String name : SETTINGS) {
            NoiseGeneratorSettings s = loader.load(name);
            settings.put(name, s);
            bySettings.put(name, functions(registries, name, s, name.equals("overworld")));
        }
        Map<String, RandomState> states = new HashMap<>();
        Mismatches ranges = new Mismatches();
        Mismatches pointValues = new Mismatches();
        Mismatches volumeValues = new Mismatches();
        Mismatches cachedPoints = new Mismatches();
        Mismatches biomes = new Mismatches();
        Map<String, SamplerContext> contexts = new HashMap<>();
        Map<String, DensityFunction> all = new HashMap<>();
        Map<String, String> settingsOf = new HashMap<>();
        for (String name : SETTINGS) {
            for (Map.Entry<String, DensityFunction> e : bySettings.get(name).entrySet()) {
                all.put(e.getKey(), e.getValue());
                settingsOf.put(e.getKey(), name);
            }
        }
        for (String line : lines) {
            String[] t = line.split(" ");
            switch (t[0]) {
                case "R" -> {
                    DensityFunction f = all.get(t[1]);
                    Interval range = f.range();
                    ranges.check(t[1] + " min", t[2], range.min());
                    ranges.check(t[1] + " max", t[3], range.max());
                    ranges.compared++;
                    if (f.domainAxes() != Integer.parseInt(t[4])) {
                        ranges.count++;
                        ranges.first.add(t[1] + " axes expected " + t[4] + " got " + f.domainAxes());
                    }
                }
                case "P" -> {
                    long seed = Long.parseLong(t[2]);
                    RandomState rs = state(states, settings, settingsOf.get(t[1]), seed, registries);
                    DensityFunction f = all.get(t[1]);
                    for (int i = 3; i < t.length; i++) {
                        int[] p = points.get(i - 3);
                        pointValues.check(t[1] + " seed " + seed + " at " + p[0] + "," + p[1] + "," + p[2], t[i],
                                rs.sampleBlockValueUncached(f, p[0], p[1], p[2]));
                    }
                }
                case "V" -> {
                    long seed = Long.parseLong(t[2]);
                    int vi = Integer.parseInt(t[3]);
                    String settingsName = settingsOf.get(t[1]);
                    RandomState rs = state(states, settings, settingsName, seed, registries);
                    SamplerContext ctx = contexts.computeIfAbsent(settingsName + "|" + seed + "|" + vi,
                            k -> SamplerContext.builder().useBufferArena(new DensityBufferPool(20)).enableCaches().build());
                    DensityVolume v = volumes.get(vi);
                    try (ScopedDensityBuffer buffer = ctx.acquireBuffer(v)) {
                        rs.getSampler(all.get(t[1])).sampleVolume(ctx, buffer, v);
                        assertEquals(t.length - 4, buffer.size());
                        for (int i = 4; i < t.length; i++) {
                            volumeValues.check(t[1] + " seed " + seed + " volume " + vi + " index " + (i - 4), t[i], buffer.get(i - 4));
                        }
                    }
                }
                case "C" -> {
                    long seed = Long.parseLong(t[2]);
                    int vi = Integer.parseInt(t[3]);
                    String settingsName = settingsOf.get(t[1]);
                    RandomState rs = state(states, settings, settingsName, seed, registries);
                    SamplerContext ctx = contexts.get(settingsName + "|" + seed + "|" + vi);
                    DensityVolume v = volumes.get(vi);
                    DensitySampler s = rs.getSampler(all.get(t[1]));
                    List<int[]> at = new ArrayList<>(points.subList(0, 12));
                    at.add(new int[]{v.minBlockX(), v.minBlockY(), v.minBlockZ()});
                    at.add(new int[]{v.blockX(v.sizeX() - 1), v.blockY(v.sizeY() - 1), v.blockZ(v.sizeZ() - 1)});
                    at.add(new int[]{v.minBlockX() + 1, v.minBlockY(), v.minBlockZ() + 1});
                    for (int i = 4; i < t.length; i++) {
                        int[] p = at.get(i - 4);
                        cachedPoints.check(t[1] + " seed " + seed + " volume " + vi + " point " + (i - 4), t[i], s.sampleValue(ctx, p[0], p[1], p[2]));
                    }
                }
                case "B" -> {
                    String name = t[1];
                    long seed = Long.parseLong(t[2]);
                    RandomState rs = state(states, settings, name, seed, registries);
                    Climate.Sampler sampler = rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
                    Climate.ParameterList<String> list = name.equals("nether")
                            ? MultiNoiseBiomeSourceParameterList.Preset.NETHER.provider().get()
                            : overworldList();
                    for (int i = 3; i < t.length; i++) {
                        String[] kv = t[i].split("=");
                        String[] q = kv[0].split(",");
                        String biome = list.findValue(sampler.sample(Integer.parseInt(q[0]), Integer.parseInt(q[1]), Integer.parseInt(q[2])));
                        biomes.compared++;
                        if (!biome.equals(kv[1])) {
                            biomes.count++;
                            if (biomes.first.size() < 25) {
                                biomes.first.add(name + " " + seed + " " + kv[0] + " expected " + kv[1] + " got " + biome);
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        ranges.assertNone("ranges/axes");
        pointValues.assertNone("point values");
        volumeValues.assertNone("volume values");
        cachedPoints.assertNone("cached point values");
        biomes.assertNone("biomes");
    }

    private static Climate.ParameterList<String> overworldList;

    private static synchronized Climate.ParameterList<String> overworldList() {
        if (overworldList == null) {
            overworldList = MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD.provider().get();
        }
        return overworldList;
    }

    private static RandomState state(Map<String, RandomState> states, Map<String, NoiseGeneratorSettings> settings, String name,
                                     long seed, WorldgenRegistries registries) {
        return states.computeIfAbsent(name + "|" + seed, k -> RandomState.create(registries, seed, settings.get(name)));
    }

    @Test
    void cubicSplinesMatchTheVanillaOracle() {
        BoundedFloatFunction<Float> id = BoundedFloatFunction.IDENTITY;
        CubicSpline<BoundedFloatFunction<Float>> s1 = CubicSpline.builder(id)
                .addPoint(-1.0F, -0.5F, 0.0F).addPoint(0.0F, 0.25F, 1.5F).addPoint(0.5F, 0.1F, -2.0F).addPoint(1.0F, 1.0F, 0.0F).build();
        CubicSpline<BoundedFloatFunction<Float>> s2 = CubicSpline.builder(id)
                .addPoint(-0.3F, s1).addPoint(0.4F, 0.75F, 0.2F).addPoint(0.9F, -0.4F, 3.0F).build();
        CubicSpline<BoundedFloatFunction<Float>> s3 = CubicSpline.builder(id).addPoint(0.2F, 0.6F, 0.9F).build();
        CubicSpline<BoundedFloatFunction<Float>> s4 = CubicSpline.builder(id, x -> x * 2.5F)
                .addPoint(-0.7F, 0.3F).addPoint(-0.1F, -0.2F, -1.0F).addPoint(0.3F, s3).addPoint(0.8F, 0.05F, 0.5F).build();
        List<CubicSpline<BoundedFloatFunction<Float>>> all = List.of(s1, s2, s3, s4);
        Mismatches m = new Mismatches();
        for (String line : lines) {
            if (!line.startsWith("S ")) {
                continue;
            }
            String[] t = line.split(" ");
            CubicSpline<BoundedFloatFunction<Float>> s = all.get(Integer.parseInt(t[1]));
            m.check("spline " + t[1] + " range min", t[2], s.range().min());
            m.check("spline " + t[1] + " range max", t[3], s.range().max());
            int k = 4;
            for (int i = -45; i <= 45; i++) {
                m.check("spline " + t[1] + " at " + i, t[k++], CubicSpline.sample(s, i * 0.05F));
            }
            m.check("spline " + t[1] + " at -1", t[k++], CubicSpline.sample(s, -1.0F));
            m.check("spline " + t[1] + " at 0.5", t[k++], CubicSpline.sample(s, 0.5F));
            m.check("spline " + t[1] + " at 1", t[k], CubicSpline.sample(s, 1.0F));
        }
        m.assertNone("cubic splines");
    }
}
