package com.example.worldgen.noise;

import com.example.worldgen.json.Json;
import com.example.worldgen.random.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NoiseParametersLoaderTest {

    private static final Path NOISE_DIR = Path.of("data", "minecraft", "worldgen", "noise");

    @Test
    void decodesDefaultsLikeTheVanillaCodec() {
        NormalNoise.Parameters p = NoiseParametersLoader.decode(Json.parse("{\"base_octave\": -3}"));
        assertEquals(new NormalNoise.Parameters(1.0, -3, 1, NormalNoise.Normalization.ENABLED, List.of()), p);
        NormalNoise.Parameters legacy = NoiseParametersLoader.decode(Json.parse("{\"base_octave\": 0, \"normalize\": \"legacy\"}"));
        assertEquals(NormalNoise.Normalization.LEGACY, legacy.normalize());
        NormalNoise.Parameters off = NoiseParametersLoader.decode(Json.parse("{\"base_octave\": 0, \"normalize\": false}"));
        assertEquals(NormalNoise.Normalization.DISABLED, off.normalize());
    }

    @Test
    void rejectsWhatTheVanillaCodecRejects() {
        String[] bad = {"{}", "{\"base_octave\": 33}", "{\"base_octave\": 0, \"octave_count\": 0}",
                "{\"base_octave\": 0, \"base_amplitude\": 0.0}", "{\"base_octave\": 0, \"amplitude_modifiers\": [1.0, 1.0]}",
                "{\"base_octave\": 0, \"amplitude_modifiers\": [-1.0]}", "{\"base_octave\": 0, \"normalize\": \"yes\"}", "[]"};
        for (String s : bad) {
            assertThrows(IllegalArgumentException.class, () -> NoiseParametersLoader.decode(Json.parse(s)), s);
        }
    }

    @Test
    void loadsEveryVanillaNoiseDefinition() throws IOException {
        assumeTrue(Files.isDirectory(NOISE_DIR), "data/ not restored (run scripts/restore-worldgen-data.py)");
        NoiseParametersLoader loader = NoiseParametersLoader.fromWorkingDirectory();
        List<Path> files;
        try (Stream<Path> s = Files.walk(NOISE_DIR)) {
            files = s.filter(f -> f.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() >= 60, "expected the full 26.3 noise set, found " + files.size());
        for (Path f : files) {
            String rel = NOISE_DIR.relativize(f).toString().replace('\\', '/');
            String id = "minecraft:" + rel.substring(0, rel.length() - ".json".length());
            NormalNoise noise = loader.load(id);
            Noise sampled = noise.create(new XoroshiroRandomSource(1L).forkPositional().fromHashOf(id));
            float v = sampled.get(12.5, 64.0, -7.25);
            assertTrue(sampled.range().contains(v), id + " value " + v + " outside " + sampled.range());
        }
    }

    @Test
    void continentalnessFileMatchesTheGoldenVectorDefinition() {
        assumeTrue(Files.isDirectory(NOISE_DIR), "data/ not restored (run scripts/restore-worldgen-data.py)");
        NormalNoise.Parameters p = NoiseParametersLoader.fromWorkingDirectory().loadParameters("minecraft:continentalness");
        assertEquals(new NormalNoise.Parameters(0.8880832896205223, -9, 9, NormalNoise.Normalization.ENABLED,
                List.of(1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0)), p);
    }
}
