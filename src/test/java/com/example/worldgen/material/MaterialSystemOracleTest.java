package com.example.worldgen.material;

import com.example.worldgen.density.DensityBufferPool;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.NoiseGeneratorSettingsLoader;
import com.example.worldgen.levelgen.RandomState;
import com.example.worldgen.material.rule.MaterialRule;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden vectors from the real 26.3 {@code MaterialSystem} ({@code buildSurface} and {@code topMaterial}, with the real
 * {@code MaterialRuleContext}, rules, conditions and {@code Biome} temperature from the client jar, run by an out-of-repo
 * oracle). Four seeds, 15 chunks each (the origin plus chunks whose surface biome is badlands, eroded badlands, wooded
 * badlands, frozen ocean, peaks, slopes, swamp, mangrove swamp, mushroom fields, desert, windswept, old-growth taiga, shore
 * and warm ocean). Input per chunk: the column fill as run-length lists (a synthetic fill from the real
 * {@code final_density}: stone where it is positive, else lava below -54, water below 63, air), the quart biome table
 * ({@code (x >> 2, clamp(y >> 2, -16, 79), z >> 2)} of the real climate sampler) and the possible-biome set; the samplers
 * are a fresh caching {@code SamplerContext} on both sides. The whole resulting chunk is compared column by column, plus
 * the post-processing mark count and 128 {@code topMaterial} results per chunk.
 */
class MaterialSystemOracleTest {

    private static final Path DATA = Path.of("data");

    @Test
    void buildSurfaceMatchesRealVanilla() throws Exception {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/material_rule")), "vanilla data not restored");
        WorldgenRegistries registries = new WorldgenRegistries(DATA);
        NoiseGeneratorSettings settings = new NoiseGeneratorSettingsLoader(registries).load("minecraft:overworld");
        MaterialRule rule = new MaterialRuleLoader(registries).rule("minecraft:overworld");
        BiomeClimate climate = BiomeClimate.vanilla(DATA);
        WorldGenerationContext context = new WorldGenerationContext(-64, 384, 63);
        int chunks = 0;
        int columns = 0;
        int tops = 0;
        long nanos = 0;
        int timed = 0;
        try (InputStream in = getClass().getResourceAsStream("material_golden.txt.gz")) {
            assertNotNull(in, "golden resource");
            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            RandomState randomState = null;
            MaterialSystem system = null;
            String line = reader.readLine();
            while (line != null) {
                String[] p = line.split(" ");
                if (p[0].equals("SEED")) {
                    randomState = RandomState.create(registries, Long.parseLong(p[1]), settings);
                    system = MaterialSystem.create(randomState, StateRef.of("minecraft:stone"), settings.seaLevel());
                    line = reader.readLine();
                    continue;
                }
                assertEquals("CHUNK", p[0], line);
                int cx = Integer.parseInt(p[1]);
                int cz = Integer.parseInt(p[2]);
                String label = p[3] + " " + cx + "," + cz;
                String[] quartTable = expand(reader.readLine().substring(3).split(" "), 4 * 4 * 96);
                String[] possibleLine = reader.readLine().split(" ");
                Set<String> possible = new LinkedHashSet<>(List.of(possibleLine).subList(1, possibleLine.length));
                TestChunk chunk = new TestChunk(cx, cz, -64, 384);
                for (int i = 0; i < 256; i++) {
                    String[] c = reader.readLine().split(" ", 4);
                    String[] states = expand(c[3].split(" "), 384);
                    for (int y = 0; y < 384; y++) {
                        chunk.fillRaw(cx * 16 + Integer.parseInt(c[1]), y - 64, cz * 16 + Integer.parseInt(c[2]),
                                StateRef.of("minecraft:" + states[y]));
                    }
                }
                chunk.primeHeights();
                BiomeAt biomes = (x, y, z) -> "minecraft:" + quartTable[((((x >> 2) - cx * 4) * 4) + ((z >> 2) - cz * 4)) * 96
                        + Math.max(-16, Math.min(79, y >> 2)) + 16];
                DensityVolume volume = new DensityVolume(16, 384, 16, cx * 16, -64, cz * 16);
                long t = System.nanoTime();
                system.buildSurface(randomState, biomes, climate, context, chunk, volume, freshSamplers(randomState), rule, possible);
                if (chunks >= 8) {
                    nanos += System.nanoTime() - t;
                    timed++;
                }
                for (int i = 0; i < 256; i++) {
                    String[] c = reader.readLine().split(" ", 4);
                    assertEquals("OUT", c[0]);
                    int x = cx * 16 + Integer.parseInt(c[1]);
                    int z = cz * 16 + Integer.parseInt(c[2]);
                    assertEquals(c[3], rle(chunk, x, z), label + " column " + c[1] + "," + c[2]);
                    columns++;
                }
                String[] marks = reader.readLine().split(" ");
                assertEquals(Integer.parseInt(marks[1]), chunk.marks, label + " post-processing marks");
                line = reader.readLine();
                while (line != null && line.startsWith("TOP ")) {
                    String[] q = line.split(" ");
                    int x = Integer.parseInt(q[1]);
                    int y = Integer.parseInt(q[2]);
                    int z = Integer.parseInt(q[3]);
                    Optional<StateRef> top = system.topMaterial(rule, randomState, context, biomes, climate, chunk,
                            freshSamplers(randomState), x, y, z, Boolean.parseBoolean(q[4]));
                    assertEquals(q[5], top.map(s -> s.id().substring("minecraft:".length())).orElse("-"), label + " " + line);
                    tops++;
                    line = reader.readLine();
                }
                chunks++;
            }
        }
        assertEquals(60, chunks);
        assertTrue(tops >= 60 * 128);
        System.out.printf("material oracle: %d chunks, %d columns, %d topMaterial vectors; buildSurface %.2f ms/chunk after warm-up%n",
                chunks, columns, tops, nanos / 1e6 / Math.max(1, timed));
    }

    private static DensitySamplerSet freshSamplers(RandomState randomState) {
        return randomState.samplersWithContext(SamplerContext.builder().useBufferArena(new DensityBufferPool(20)).enableCaches().build());
    }

    private static String[] expand(String[] runs, int size) {
        List<String> out = new ArrayList<>(size);
        for (String run : runs) {
            int star = run.lastIndexOf('*');
            String id = run.substring(0, star);
            for (int i = Integer.parseInt(run.substring(star + 1)); i > 0; i--) {
                out.add(id);
            }
        }
        assertEquals(size, out.size());
        return out.toArray(new String[0]);
    }

    private static String rle(TestChunk chunk, int x, int z) {
        StringBuilder sb = new StringBuilder();
        String prev = null;
        int run = 0;
        Map<String, String> shortNames = new HashMap<>();
        for (int y = -64; y < 320; y++) {
            String id = shortNames.computeIfAbsent(chunk.getState(x, y, z).toString(), s -> s.startsWith("minecraft:") ? s.substring(10) : s);
            if (id.equals(prev)) {
                run++;
            } else {
                if (prev != null) {
                    sb.append(prev).append('*').append(run).append(' ');
                }
                prev = id;
                run = 1;
            }
        }
        return sb.append(prev).append('*').append(run).toString();
    }
}
