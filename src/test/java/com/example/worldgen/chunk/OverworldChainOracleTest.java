package com.example.worldgen.chunk;

import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.TerrainChunk;
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
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end golden comparison of {@link OverworldGenerator#generate} (BIOMES, then TERRAIN: noise fill + aquifer, surface
 * rules and ore veins, carvers + carving mask, final heightmaps) against the real 26.3 chain.
 *
 * <p>{@code chain_golden_26_3.txt.gz} was produced by an oracle that ran, from the 26.3 client jar,
 * {@code ChunkGenerator.doCreateBiomes} for the chunk and its 8 neighbours, {@code ChunkStatusTasks.collectPossibleBiomes},
 * the private supplier of {@code NoiseBasedChunkGenerator.buildTerrain} ({@code lambda$buildTerrain$0}: createNoiseChunk,
 * doFill, buildSurface with the real {@code MaterialSystem} and rules, generateCarvers with the real cave/canyon carvers,
 * {@code applyCarvingMask} and its lambdas), then {@code ChunkStatusTasks.lambda$buildTerrain$0} (final heightmaps), over the
 * real {@code RandomState}, density engine, {@code NoiseChunk}, aquifer, {@code BiomeManager}, {@code Heightmap} and
 * {@code Biome} temperature. Stand-ins: the chunk container ({@code ProtoChunk}/{@code ChunkAccess}/{@code LevelChunkSection}
 * with method bodies transcribed from the bytecode), {@code WorldGenRegion} (chunk lookup and region biome manager), the
 * empty {@code Blender}, the beardifier of a world without structures, block states (ids with air/fluid flags and the data
 * pack tags) and codec/registry plumbing. Running the oracle with the biome palettes holding the stored biomes only, and
 * again with {@code minecraft:plains} (the containers' initial value) added to every palette, gives identical blocks, so
 * the possible-biome set {@link OverworldGenerator} uses (the stored biomes) does not change the result.
 *
 * <p>Per seed (0, 123456789, -8172891231246, 42, 777, 2026) and 10 chunks (spawn origin, a negative chunk, one at about
 * +-3 000 000 blocks, and the nearest ocean, river, peaks/snowy slopes, badlands, desert, swamp/mangrove swamp and
 * deep dark/lush caves (quart y -10) chunks found from the seed), the complete chunk: every block ({@code COL} run lengths,
 * bottom to top), the WG and the four final heightmaps for all 256 columns, every post-processing mark in section and
 * call order, and every quart biome. Most chunks are carved into by caves or canyons that start in other chunks
 * ({@code carverSources} in the {@code CHUNK} line).
 */
class OverworldChainOracleTest {

    private static final Path DATA = Path.of("data");
    private static final HeightmapType[] TYPES = {HeightmapType.WORLD_SURFACE_WG, HeightmapType.OCEAN_FLOOR_WG, HeightmapType.MOTION_BLOCKING,
            HeightmapType.MOTION_BLOCKING_NO_LEAVES, HeightmapType.OCEAN_FLOOR, HeightmapType.WORLD_SURFACE};

    record GoldenChunk(long seed, int cx, int cz, String label, String biomes, String[] columns, String[] heightmaps, String marks) {
    }

    private static final List<GoldenChunk> GOLDEN = new ArrayList<>();

    @BeforeAll
    static void read() throws IOException {
        try (InputStream in = OverworldChainOracleTest.class.getResourceAsStream("chain_golden_26_3.txt.gz");
             BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            long seed = 0;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("SEED ")) {
                    seed = Long.parseLong(line.substring(5));
                } else if (line.startsWith("CHUNK ")) {
                    String[] t = line.split(" ");
                    r.readLine(); // POSSIBLE (oracle information only)
                    String bq = r.readLine();
                    String[] cols = new String[256];
                    for (int i = 0; i < 256; i++) {
                        String col = r.readLine();
                        String[] c = col.split(" ", 4);
                        cols[Integer.parseInt(c[2]) * 16 + Integer.parseInt(c[1])] = c[3];
                    }
                    String[] hms = new String[TYPES.length];
                    for (int i = 0; i < TYPES.length; i++) {
                        String hm = r.readLine();
                        assertTrue(hm.startsWith("HM " + TYPES[i].name() + " "), hm);
                        hms[i] = hm.substring(4 + TYPES[i].name().length());
                    }
                    String pp = r.readLine();
                    GOLDEN.add(new GoldenChunk(seed, Integer.parseInt(t[1]), Integer.parseInt(t[2]), t[3], bq.substring(3), cols, hms,
                            pp.length() > 2 ? pp.substring(3) : ""));
                }
            }
        }
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String rle(int n, java.util.function.IntFunction<String> f) {
        StringBuilder b = new StringBuilder();
        String cur = null;
        int run = 0;
        for (int i = 0; i < n; i++) {
            String s = f.apply(i);
            if (s.equals(cur)) {
                run++;
            } else {
                if (cur != null) b.append(cur).append('*').append(run).append(' ');
                cur = s;
                run = 1;
            }
        }
        return b.append(cur).append('*').append(run).toString();
    }

    @Test
    void goldenCoversTheRequestedCases() {
        assertEquals(60, GOLDEN.size());
        assertEquals(6, GOLDEN.stream().mapToLong(GoldenChunk::seed).distinct().count());
        for (String label : new String[]{"origin", "negative", "far", "ocean", "river", "peaks", "badlands", "desert", "swamp", "cave"}) {
            assertEquals(6, GOLDEN.stream().filter(g -> g.label().equals(label)).count(), label);
        }
        assertTrue(GOLDEN.stream().anyMatch(g -> Math.abs(g.cx()) >= 187_000), "a chunk near +-3 000 000 blocks");
        assertTrue(GOLDEN.stream().anyMatch(g -> g.cx() < 0 && g.cz() < 0), "a negative chunk");
        assertTrue(GOLDEN.stream().anyMatch(g -> String.join(" ", g.columns()).contains("snow")), "snow on the peaks");
    }

    @Test
    void wholeChunksMatchTheVanillaChain() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen")), "vanilla data/ folder not present");
        List<String> mismatches = new ArrayList<>();
        long blocks = 0;
        OverworldGenerator generator = null;
        for (GoldenChunk g : GOLDEN) {
            if (generator == null || generator.seed() != g.seed()) generator = new OverworldGenerator(DATA, g.seed());
            TerrainChunk chunk = generator.generate(g.cx(), g.cz());
            String where = "seed " + g.seed() + " chunk " + g.cx() + "," + g.cz() + " (" + g.label() + ")";
            String[] quart = chunk.quartBiomes();
            String biomes = rle(quart.length, i -> shortId(quart[i]));
            if (!biomes.equals(g.biomes())) mismatches.add(where + " quart biomes: " + biomes + " vs vanilla " + g.biomes());
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = chunk.minBlockX() + lx, z = chunk.minBlockZ() + lz;
                    String col = rle(chunk.height(), i -> {
                        StateRef s = chunk.getState(x, chunk.minY() + i, z);
                        return shortId(s.id());
                    });
                    blocks += chunk.height();
                    if (!col.equals(g.columns()[lz * 16 + lx])) {
                        mismatches.add(where + " column " + lx + "," + lz + ": " + col + "\n   vanilla: " + g.columns()[lz * 16 + lx]);
                    }
                }
            }
            for (int t = 0; t < TYPES.length; t++) {
                StringBuilder b = new StringBuilder();
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (b.length() > 0) b.append(' ');
                        b.append(chunk.getOrCreateHeightmapUnprimed(TYPES[t]).getFirstAvailable(lx, lz));
                    }
                }
                if (!b.toString().equals(g.heightmaps()[t])) mismatches.add(where + " heightmap " + TYPES[t] + ": " + b + "\n   vanilla: " + g.heightmaps()[t]);
            }
            StringBuilder pp = new StringBuilder();
            for (int[] p : chunk.postProcessingPositions()) {
                if (pp.length() > 0) pp.append(' ');
                pp.append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
            }
            if (!pp.toString().equals(g.marks())) mismatches.add(where + " post-processing marks: " + pp + "\n   vanilla: " + g.marks());
        }
        assertEquals(60L * 256 * 384, blocks);
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatches, first ones:\n"
                + String.join("\n", mismatches.subList(0, Math.min(12, mismatches.size()))));
    }
}
