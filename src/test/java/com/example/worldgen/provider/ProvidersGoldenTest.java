package com.example.worldgen.provider;

import com.example.worldgen.json.Json;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.random.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Golden vectors produced by running the real 26.3 classes ({@code net.minecraft.util.valueproviders.*},
 * {@code levelgen.heightproviders.*}, {@code VerticalAnchor}) under the oracle: for every provider, 4 seeds x
 * {Legacy, Xoroshiro} random sources, 16 samples then one {@code nextLong()} (the consumed-random state). Floats are
 * compared bit for bit. The providers here are built from JSON, so the decoding is exercised too.
 */
class ProvidersGoldenTest {

    private static final WorldGenerationContext OVERWORLD = new WorldGenerationContext(-64, 384, 63);

    private static Map<String, Function<RandomSource, String>> providers() {
        Map<String, Function<RandomSource, String>> m = new LinkedHashMap<>();
        i(m, "int_constant_5", "5");
        i(m, "int_uniform_-3_7", "{\"type\":\"minecraft:uniform\",\"min_inclusive\":-3,\"max_inclusive\":7}");
        i(m, "int_biased_0_14", "{\"type\":\"biased_to_bottom\",\"min_inclusive\":0,\"max_inclusive\":14}");
        i(m, "int_very_biased_0_14", "{\"type\":\"minecraft:very_biased_to_bottom\",\"min_inclusive\":0,\"max_inclusive\":14}");
        i(m, "int_clamped_uniform_-10_10_to_-2_5", "{\"type\":\"minecraft:clamped\",\"min_inclusive\":-2,\"max_inclusive\":5,"
                + "\"source\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":-10,\"max_inclusive\":10}}");
        i(m, "int_weighted_flat", "{\"type\":\"minecraft:weighted_list\",\"distribution\":["
                + "{\"data\":1,\"weight\":3},"
                + "{\"data\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":4,\"max_inclusive\":9},\"weight\":5},"
                + "{\"data\":{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":2,\"max_inclusive\":6},\"weight\":1}]}");
        i(m, "int_weighted_compact", "{\"type\":\"minecraft:weighted_list\",\"distribution\":["
                + "{\"data\":-1,\"weight\":40},"
                + "{\"data\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":10,\"max_inclusive\":20},\"weight\":25},"
                + "{\"data\":{\"type\":\"minecraft:constant\",\"value\":7},\"weight\":0},"
                + "{\"data\":{\"type\":\"minecraft:trapezoid\",\"min\":0,\"max\":8,\"plateau\":2},\"weight\":1}]}");
        i(m, "int_clamped_normal_0.5_2_-3_4", "{\"type\":\"minecraft:clamped_normal\",\"mean\":0.5,\"deviation\":2.0,\"min_inclusive\":-3,\"max_inclusive\":4}");
        i(m, "int_trapezoid_triangle_4", "{\"type\":\"minecraft:trapezoid\",\"min\":-4,\"max\":4,\"plateau\":0}");
        i(m, "int_trapezoid_0_10_10", "{\"type\":\"minecraft:trapezoid\",\"min\":0,\"max\":10,\"plateau\":10}");
        i(m, "int_trapezoid_0_10_3", "{\"type\":\"minecraft:trapezoid\",\"min\":0,\"max\":10,\"plateau\":3}");
        i(m, "int_trapezoid_-2_9_1", "{\"type\":\"minecraft:trapezoid\",\"min\":-2,\"max\":9,\"plateau\":1}");
        f(m, "float_constant_1.5", "1.5");
        f(m, "float_uniform_-1_-0.4", "{\"type\":\"minecraft:uniform\",\"min_inclusive\":-1.0,\"max_exclusive\":-0.4}");
        f(m, "float_uniform_0.7_1.4", "{\"type\":\"minecraft:uniform\",\"min_inclusive\":0.7,\"max_exclusive\":1.4}");
        f(m, "float_clamped_normal_0.3_0.7_-0.5_1", "{\"type\":\"minecraft:clamped_normal\",\"mean\":0.3,\"deviation\":0.7,\"min\":-0.5,\"max\":1.0}");
        f(m, "float_trapezoid_0_3_1", "{\"type\":\"minecraft:trapezoid\",\"min\":0.0,\"max\":3.0,\"plateau\":1.0}");
        f(m, "float_trapezoid_0_6_2", "{\"type\":\"minecraft:trapezoid\",\"min\":0.0,\"max\":6.0,\"plateau\":2.0}");
        SampledFloat multiplied = new MultipliedFloats(fp("{\"type\":\"minecraft:uniform\",\"min_inclusive\":0.5,\"max_exclusive\":2.0}"),
                fp("{\"type\":\"minecraft:trapezoid\",\"min\":0.0,\"max\":3.0,\"plateau\":1.0}"), fp("-2.0"));
        m.put("float_multiplied", r -> Integer.toString(Float.floatToRawIntBits(multiplied.sample(r))));
        h(m, "height_constant_above_bottom_8", "{\"above_bottom\":8}");
        h(m, "height_constant_sea_plus_5", "{\"type\":\"minecraft:constant\",\"value\":{\"relative_to_sea_level\":5}}");
        h(m, "height_uniform_ab8_abs180", "{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"above_bottom\":8},\"max_inclusive\":{\"absolute\":180}}");
        h(m, "height_uniform_empty", "{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":10},\"max_inclusive\":{\"absolute\":5}}");
        h(m, "height_biased_-64_30_1", "{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":{\"absolute\":-64},\"max_inclusive\":{\"absolute\":30}}");
        h(m, "height_biased_-64_30_4", "{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":{\"absolute\":-64},\"max_inclusive\":{\"absolute\":30},\"inner\":4}");
        h(m, "height_very_biased_ab0_bt10_1", "{\"type\":\"minecraft:very_biased_to_bottom\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"below_top\":10}}");
        h(m, "height_very_biased_ab0_bt10_8", "{\"type\":\"minecraft:very_biased_to_bottom\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"below_top\":10},\"inner\":8}");
        h(m, "height_trapezoid_-16_112_0", "{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":-16},\"max_inclusive\":{\"absolute\":112}}");
        h(m, "height_trapezoid_-80_80_20", "{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":-80},\"max_inclusive\":{\"absolute\":80},\"plateau\":20}");
        h(m, "height_trapezoid_0_10_50", "{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":10},\"plateau\":50}");
        h(m, "height_weighted", "{\"type\":\"minecraft:weighted_list\",\"distribution\":["
                + "{\"data\":{\"absolute\":3},\"weight\":2},"
                + "{\"data\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":-10},\"max_inclusive\":{\"absolute\":10}},\"weight\":7}]}");
        return m;
    }

    private static void i(Map<String, Function<RandomSource, String>> m, String name, String json) {
        IntProvider p = IntProvider.parse(Json.parseWithNumberLiterals(json));
        m.put(name, r -> Integer.toString(p.sample(r)));
    }

    private static FloatProvider fp(String json) {
        return FloatProvider.parse(Json.parseWithNumberLiterals(json));
    }

    private static void f(Map<String, Function<RandomSource, String>> m, String name, String json) {
        FloatProvider p = fp(json);
        m.put(name, r -> Integer.toString(Float.floatToRawIntBits(p.sample(r))));
    }

    private static void h(Map<String, Function<RandomSource, String>> m, String name, String json) {
        HeightProvider p = HeightProvider.parse(Json.parseWithNumberLiterals(json));
        m.put(name, r -> Integer.toString(p.sample(r, OVERWORLD)));
    }

    static List<String> readGzLines(Class<?> owner, String resource) throws IOException {
        try (InputStream in = owner.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
                return reader.lines().filter(l -> !l.isEmpty()).toList();
            }
        }
    }

    @Test
    void everyProviderMatchesTheVanillaSequenceAndConsumption() throws IOException {
        Map<String, Function<RandomSource, String>> providers = providers();
        List<String> lines = readGzLines(ProvidersGoldenTest.class, "providers_golden.txt.gz");
        assertEquals(providers.size() * 4 * 2, lines.size());
        for (String line : lines) {
            String[] p = line.split("\\|");
            Function<RandomSource, String> sampler = providers.get(p[0]);
            assertNotNull(sampler, p[0]);
            long seed = Long.parseLong(p[2]);
            RandomSource random = p[1].equals("legacy") ? new LegacyRandomSource(seed) : new XoroshiroRandomSource(seed);
            StringBuilder sb = new StringBuilder();
            for (int n = 0; n < 16; n++) {
                if (n > 0) {
                    sb.append(',');
                }
                sb.append(sampler.apply(random));
            }
            assertEquals(p[3], sb.toString(), line);
            assertEquals(Long.parseLong(p[4]), random.nextLong(), "random state after " + p[0] + " " + p[1] + " " + seed);
        }
    }
}
