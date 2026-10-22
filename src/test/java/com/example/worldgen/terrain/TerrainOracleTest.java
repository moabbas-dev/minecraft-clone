package com.example.worldgen.terrain;

import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.biome.MultiNoiseBiomeSourceParameterList;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
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
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden comparison of the BIOMES + TERRAIN-fill stage against the real 26.3 classes. {@code golden_terrain_26_3.txt.gz}
 * was produced by an oracle that ran, from the 26.3 client jar, {@code ChunkGenerator.doCreateBiomes}
 * ({@code MultiNoiseBiomeSource.createResolverForChunk}, {@code RandomState.createClimateSampler}),
 * {@code NoiseChunk}, {@code Aquifer.Config.create}/{@code NoiseBasedAquifer}, {@code NoiseBasedChunkGenerator.doFill},
 * {@code createFluidPicker}, {@code chunkVolume}, the real {@code Heightmap} and {@code BiomeManager}, over the real
 * {@code RandomState}/density engine. Only the chunk container (block array, section, post-processing list),
 * {@code Block/BlockState} (ids with air/fluid flags and the data-pack heightmap tags) and codec/registry plumbing were
 * stand-ins.
 *
 * <p>Per seed (0, 123456789, -8172891231246, 42) and chunk (10 chunks, including negative and |x| = 3 000 000 block
 * positions): every block of the chunk ({@code COL} run lengths), both WG heightmaps for all 256 columns, every position
 * marked for post-processing, the biome of every quart cell, and 400 post-fill {@code computeSubstance(x, y, z, 0.0)}
 * calls with their {@code shouldScheduleFluidUpdate()} (the carver path), and the four final heightmaps after the real
 * {@code Heightmap.primeHeightmaps} of the TERRAIN completion. Then {@code BiomeManager.getBiome} for 900
 * block positions over chunk-stored biomes of 3x3 chunks, and 200 uncached-resolver quart lookups.
 */
class TerrainOracleTest {

    private static final Path DATA = Path.of("data");
    private static List<String> lines;

    @BeforeAll
    static void read() throws IOException {
        lines = new ArrayList<>();
        try (InputStream in = TerrainOracleTest.class.getResourceAsStream("golden_terrain_26_3.txt.gz");
             BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        }
    }

    private static String shortId(StateRef s) {
        return s.id().substring("minecraft:".length());
    }

    @Test
    void obfuscatedSeedMatchesGuavaSha256() {
        for (String line : lines) {
            if (line.startsWith("BM ")) {
                String[] t = line.split(" ", 6);
                assertEquals(Long.parseLong(t[4]), BiomeManager.obfuscateSeed(Long.parseLong(t[1])));
            }
        }
    }

    @Test
    void terrainFillBiomesAndBiomeManagerMatchVanilla() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen")), "vanilla data/ folder not present");
        WorldgenRegistries registries = new WorldgenRegistries(DATA);
        NoiseGeneratorSettings settings = new NoiseGeneratorSettingsLoader(registries).load("minecraft:overworld");
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(
                new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD));
        BlockTags tags = BlockTags.load(DATA);
        TerrainFiller filler = new TerrainFiller(settings);

        RandomState rs = null;
        long seed = 0;
        int i = 0;
        int chunks = 0;
        long blocks = 0;
        int aquiferCalls = 0;
        int biomeChecks = 0;
        int postProcessing = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String[] t = line.split(" ");
            switch (t[0]) {
                case "SEED" -> {
                    seed = Long.parseLong(t[1]);
                    rs = RandomState.create(registries, seed, settings);
                    i++;
                }
                case "CHUNK" -> {
                    int cx = Integer.parseInt(t[2]);
                    int cz = Integer.parseInt(t[3]);
                    String where = "seed " + seed + " chunk " + cx + "," + cz;
                    TerrainChunk chunk = VanillaTerrain.createBiomes(rs, settings, source, tags, cx, cz);
                    NoiseChunk noiseChunk = VanillaTerrain.beginTerrain(rs, filler, chunk);
                    VanillaTerrain.fill(filler, noiseChunk, chunk);
                    i++;
                    for (int c = 0; c < 256; c++, i++) {
                        String[] col = lines.get(i).split(" ");
                        int lx = Integer.parseInt(col[1]);
                        int lz = Integer.parseInt(col[2]);
                        int y = chunk.minY();
                        for (int k = 3; k < col.length; k++) {
                            int star = col[k].indexOf('*');
                            String id = col[k].substring(0, star);
                            int n = Integer.parseInt(col[k].substring(star + 1));
                            for (int j = 0; j < n; j++, y++) {
                                StateRef s = chunk.getState(chunk.minBlockX() + lx, y, chunk.minBlockZ() + lz);
                                assertEquals(id, shortId(s), where + " block " + lx + "," + y + "," + lz);
                                blocks++;
                            }
                        }
                        assertEquals(chunk.minY() + chunk.height(), y, where + " column height");
                    }
                    for (int h = 0; h < 2; h++, i++) {
                        String[] hm = lines.get(i).split(" ");
                        Heightmap map = chunk.getOrCreateHeightmapUnprimed(HeightmapType.valueOf(hm[1]));
                        for (int k = 0; k < 256; k++) {
                            assertEquals(Integer.parseInt(hm[k + 2]), map.getFirstAvailable(k & 15, k >> 4), where + " " + hm[1] + " column " + k);
                        }
                    }
                    List<String> expectedPp = new ArrayList<>(List.of(lines.get(i).split(" "))).subList(1, lines.get(i).split(" ").length);
                    List<String> actualPp = new ArrayList<>();
                    for (int[] p : chunk.postProcessingPositions()) actualPp.add(p[0] + "," + p[1] + "," + p[2]);
                    List<String> e = new ArrayList<>(expectedPp);
                    e.sort(null);
                    actualPp.sort(null);
                    assertEquals(e, actualPp, where + " post-processing");
                    postProcessing += e.size();
                    i++;
                    String[] bq = lines.get(i).split(" ");
                    int quartMinY = chunk.minY() >> 2;
                    for (int k = 1; k < bq.length; k++) {
                        int idx = k - 1;
                        int qx = idx & 3, qz = (idx >> 2) & 3, qy = idx >> 4;
                        assertEquals("minecraft:" + bq[k], chunk.getNoiseBiome((chunk.minBlockX() >> 2) + qx, quartMinY + qy,
                                (chunk.minBlockZ() >> 2) + qz), where + " quart " + idx);
                        biomeChecks++;
                    }
                    i++;
                    String[] aq = lines.get(i).split(" ");
                    Aquifer aquifer = noiseChunk.aquifer();
                    for (int k = 1; k < aq.length; k++) {
                        String[] kv = aq[k].split("=");
                        String[] p = kv[0].split(",");
                        String[] res = kv[1].split(",");
                        StateRef s = aquifer.computeSubstance(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), 0.0);
                        assertEquals(res[0], s == null ? "null" : shortId(s), where + " aquifer " + kv[0]);
                        assertEquals(res[1].equals("1"), aquifer.shouldScheduleFluidUpdate(), where + " aquifer flag " + kv[0]);
                        aquiferCalls++;
                    }
                    i++;
                    noiseChunk.close();
                    VanillaTerrain.finishTerrainStatus(chunk);
                    for (int h = 0; h < 4; h++, i++) {
                        String[] hf = lines.get(i).split(" ");
                        HeightmapType type = HeightmapType.valueOf(hf[1]);
                        for (int k = 0; k < 256; k++) {
                            assertEquals(Integer.parseInt(hf[k + 2]) - 1, chunk.getHeight(type, chunk.minBlockX() + (k & 15), chunk.minBlockZ() + (k >> 4)),
                                    where + " primed " + hf[1] + " column " + k);
                        }
                    }
                    chunks++;
                }
                case "BM" -> {
                    int cx = Integer.parseInt(t[2]);
                    int cz = Integer.parseInt(t[3]);
                    Map<Long, TerrainChunk> region = new HashMap<>();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            region.put(key(cx + dx, cz + dz), VanillaTerrain.createBiomes(rs, settings, source, tags, cx + dx, cz + dz));
                        }
                    }
                    BiomeResolver uncached = TerrainBiomes.createUncachedResolver(rs, source);
                    BiomeManager bm = new BiomeManager(TerrainBiomes.regionResolver((x, z) -> region.get(key(x, z)), uncached),
                            BiomeManager.obfuscateSeed(seed));
                    for (int k = 5; k < t.length; k++) {
                        String[] kv = t[k].split("=");
                        String[] p = kv[0].split(",");
                        assertEquals(kv[1], bm.getBiome(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])),
                                "seed " + seed + " getBiome " + kv[0]);
                        biomeChecks++;
                    }
                    i++;
                }
                case "UQ" -> {
                    BiomeResolver uncached = TerrainBiomes.createUncachedResolver(rs, source);
                    for (int k = 2; k < t.length; k++) {
                        String[] kv = t[k].split("=");
                        String[] p = kv[0].split(",");
                        assertEquals(kv[1], uncached.getNoiseBiome(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])),
                                "seed " + seed + " uncached " + kv[0]);
                        biomeChecks++;
                    }
                    i++;
                }
                default -> throw new IllegalStateException("unexpected golden line " + t[0]);
            }
        }
        System.out.println("[terrain oracle] " + chunks + " chunks, " + blocks + " blocks, " + postProcessing + " post-processing marks, "
                + aquiferCalls + " aquifer calls, " + biomeChecks + " biome checks: all equal");
        assertEquals(40, chunks);
        assertTrue(blocks == 40L * 16 * 16 * 384);
    }

    private static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }
}
