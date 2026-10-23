package com.example.world;

import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.TerrainChunk;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The engine side of the vanilla generator: {@link VanillaChunkGenerator#generate} must copy the finished
 * {@link OverworldGenerator} chunk (verified block for block against the real 26.3 chain by
 * {@code OverworldChainOracleTest}) into the engine {@link Chunk} unchanged: every cell is the {@link VanillaBlocks#byVanilla}
 * block of the vanilla state, the per-column height map is the highest non-air block (vanilla's {@code WORLD_SURFACE}
 * heightmap minus one, which uses the same not-air predicate), and the quart biomes are the chunk's.
 */
class VanillaChunkGeneratorCopyTest {

    private static final Path DATA = Path.of("data");

    @Test
    void engineChunkIsTheVanillaChunk() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen")), "vanilla data/ folder not present");
        // chunks of OverworldChainOracleTest's golden: spawn origin, peaks, badlands, deep dark / lush caves, desert, far
        long[][] cases = {{0L, 0, 0}, {0L, -40, 108}, {0L, 36, -104}, {0L, 44, 52}, {123456789L, -19, -21}, {123456789L, -187500, 187499},
                {42L, -77, -111}, {42L, -1, 1}};
        List<String> mismatches = new ArrayList<>();
        VanillaChunkGenerator engine = null;
        OverworldGenerator reference = null;
        for (long[] c : cases) {
            long seed = c[0];
            int cx = (int) c[1], cz = (int) c[2];
            if (engine == null || engine.overworld().seed() != seed) {
                engine = new VanillaChunkGenerator(DATA, seed);
                reference = new OverworldGenerator(DATA, seed);
            }
            Chunk chunk = new Chunk(cx, cz);
            engine.generate(chunk, null);
            TerrainChunk terrain = reference.generate(cx, cz);
            String where = "seed " + seed + " chunk " + cx + "," + cz;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int highest = Chunk.MIN_Y - 1;
                    for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) {
                        StateRef state = terrain.getState(terrain.minBlockX() + x, y, terrain.minBlockZ() + z);
                        BlockType expected = VanillaBlocks.byVanilla(state.id(), state.properties());
                        BlockType actual = chunk.getLocal(x, y, z);
                        if (actual != expected && mismatches.size() < 20) {
                            mismatches.add(where + " " + x + "," + y + "," + z + ": engine " + actual + " vs " + state + " -> " + expected);
                        }
                        if (!expected.isAir()) highest = y;
                    }
                    if (chunk.getColumnHeight(x, z) != highest) {
                        mismatches.add(where + " column " + x + "," + z + ": height " + chunk.getColumnHeight(x, z) + " vs highest non-air " + highest);
                    }
                    int worldSurface = terrain.getOrCreateHeightmapUnprimed(HeightmapType.WORLD_SURFACE).getFirstAvailable(x, z) - 1;
                    if (highest != worldSurface) {
                        mismatches.add(where + " column " + x + "," + z + ": highest non-air " + highest + " vs WORLD_SURFACE - 1 = " + worldSurface);
                    }
                }
            }
            String[] biomes = new String[16 * (Chunk.SIZE_Y >> 2)];
            for (int qy = 0; qy < Chunk.SIZE_Y >> 2; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        biomes[(qy * 4 + qz) * 4 + qx] = chunk.getNoiseBiome(cx * 4 + qx, (Chunk.MIN_Y >> 2) + qy, cz * 4 + qz);
                    }
                }
            }
            assertArrayEquals(terrain.quartBiomes(), biomes, where + " quart biomes");
        }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }
}
