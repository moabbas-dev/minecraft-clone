package com.example.world.integration;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.VanillaChunkGenerator;
import com.example.world.World;
import com.example.world.WorldIO;
import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.chunk.OverworldSpawn;
import com.example.worldgen.terrain.TerrainChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The vanilla overworld generator inside the engine ({@link VanillaChunkGenerator}): determinism, independence from the order the
 * chunks are generated in, biomes on both the generate and the load path, vanilla's bedrock floor, the engine heightmap, and the
 * spawn search. Needs the restored {@code data/} folder (skipped without it).
 */
class WorldGenerationTest {

    @BeforeAll
    static void needsData() {
        assumeTrue(Files.isDirectory(VanillaChunkGenerator.DATA_ROOT.resolve("minecraft/worldgen/noise_settings")), "vanilla data/ not restored");
        assumeTrue(new File("textures/colormap/grass.png").isFile(), "vanilla colormaps not present");
    }

    private static Chunk generate(World world, int cx, int cz) {
        Chunk chunk = world.getOrCreateChunk(cx, cz);
        world.generateChunk(chunk);
        return chunk;
    }

    @Test
    void sameSeedProducesIdenticalChunksInIndependentWorlds() {
        World a = new World(555L, new VanillaChunkGenerator(555L));
        World b = new World(555L, new VanillaChunkGenerator(555L));
        assertArrayEquals(generate(a, 3, -2).exportBlocks(), generate(b, 3, -2).exportBlocks());
    }

    /**
     * Feature decoration writes into the neighbours, so (as in vanilla) a chunk's blocks depend on the order its neighbours are
     * decorated in; what must not matter is anything else. Chunks far enough apart that their decorations cannot touch (4 apart:
     * one writes at most 1 chunk out, the other reads at most 2) come out the same in any order, and the same as alone.
     */
    @Test
    void chunksDoNotDependOnTheOrderTheyAreGeneratedIn() {
        World forward = new World(77L, new VanillaChunkGenerator(77L));
        World backward = new World(77L, new VanillaChunkGenerator(77L));
        short[][] f = new short[3][];
        short[][] r = new short[3][];
        for (int i = 0; i < 3; i++) generate(forward, (i - 1) * 4, 4);
        for (int i = 2; i >= 0; i--) generate(backward, (i - 1) * 4, 4);
        for (int i = 0; i < 3; i++) f[i] = forward.getChunk((i - 1) * 4, 4).exportBlocks();
        for (int i = 0; i < 3; i++) r[i] = backward.getChunk((i - 1) * 4, 4).exportBlocks();
        for (int i = 0; i < 3; i++) assertArrayEquals(f[i], r[i], "chunk " + (i - 1) * 4 + ",4");
        // and a chunk generated alone, with no neighbour ever generated, is the same too
        World alone = new World(77L, new VanillaChunkGenerator(77L));
        assertArrayEquals(f[1], generate(alone, 0, 4).exportBlocks());
    }

    @Test
    void theBottomLayerIsBedrockAndTheHeightMapMatchesTheBlocks() {
        World world = new World(1234L, new VanillaChunkGenerator(1234L));
        Chunk chunk = generate(world, 5, 5);
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                // material_rule bedrock_floor: vertical_gradient true at and below above_bottom 0 -> always bedrock at y -64
                assertEquals(BlockType.BEDROCK, chunk.getLocal(x, Chunk.MIN_Y, z), "y -64 at " + x + "," + z);
                int highest = Chunk.MIN_Y - 1;
                for (int y = Chunk.MAX_Y; y >= Chunk.MIN_Y; y--) {
                    if (!chunk.getLocal(x, y, z).isAir()) {
                        highest = y;
                        break;
                    }
                }
                assertEquals(highest, chunk.getColumnHeight(x, z), "engine heightmap at " + x + "," + z);
            }
        }
        Set<BlockType> types = new HashSet<>();
        for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) types.add(chunk.getLocal(8, y, 8));
        assertTrue(types.contains(BlockType.DEEPSLATE) && types.contains(BlockType.STONE), "deepslate below stone: " + types);
    }

    @Test
    void biomesAreSetWhenGeneratedAndRecomputedWhenLoadedFromDisk(@TempDir Path dir) {
        World world = new World(42L, new VanillaChunkGenerator(42L));
        world.setSaveDir(dir.toFile());
        Chunk chunk = generate(world, -3, 7);
        assertTrue(chunk.hasBiomes());
        WorldIO.saveChunk(dir.toFile(), chunk);

        World reloaded = new World(42L, new VanillaChunkGenerator(42L));
        reloaded.setSaveDir(dir.toFile());
        Chunk loaded = generate(reloaded, -3, 7);
        assertTrue(loaded.hasBiomes());
        assertArrayEquals(chunk.exportBlocks(), loaded.exportBlocks());
        for (int qy = Chunk.MIN_Y >> 2; qy <= Chunk.MAX_Y >> 2; qy += 7) {
            assertEquals(chunk.getNoiseBiome(-12, qy, 28), loaded.getNoiseBiome(-12, qy, 28));
        }
        for (int x = -48; x < -32; x += 5) {
            assertEquals(world.getBiome(x, 64, 120), reloaded.getBiome(x, 64, 120));
            assertTrue(world.getBiome(x, 64, 120).startsWith("minecraft:"));
        }
        for (int x = 0; x < Chunk.SIZE_X; x++) assertArrayEquals(chunk.getGrassTint(x, 3), loaded.getGrassTint(x, 3));
    }

    @Test
    void theBiomeAtABlockDoesNotDependOnWhichChunksAreLoaded() {
        World loaded = new World(9L, new VanillaChunkGenerator(9L));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) generate(loaded, dx, dz);
        }
        World empty = new World(9L, new VanillaChunkGenerator(9L));
        for (int x = -20; x < 36; x += 3) {
            for (int z = -20; z < 36; z += 5) {
                assertEquals(loaded.getBiome(x, 70, z), empty.getBiome(x, 70, z), x + "," + z);
            }
        }
    }

    @Test
    void theWorldSpawnStandsOnAFullBlockAndIsTheSameEveryTime() {
        OverworldGenerator generator = new OverworldGenerator(VanillaChunkGenerator.DATA_ROOT, 4242L);
        java.util.Map<Long, TerrainChunk> chunks = new java.util.HashMap<>();
        OverworldSpawn.Chunks source = (cx, cz) -> chunks.computeIfAbsent(Chunk.key(cx, cz), k -> generator.generate(cx, cz));
        OverworldSpawn.BlockPos spawn = OverworldSpawn.worldSpawn(generator, source);
        assertEquals(spawn, OverworldSpawn.worldSpawn(generator, source));
        assertNotNull(OverworldSpawn.levelRespawnPos(generator, source, spawn.x(), spawn.z()));
        TerrainChunk chunk = source.get(spawn.x() >> 4, spawn.z() >> 4);
        assertTrue(chunk.getState(spawn.x(), spawn.y() - 1, spawn.z()).id().startsWith("minecraft:"));
        assertTrue(chunk.getState(spawn.x(), spawn.y(), spawn.z()).isAir(), "the spawn block is free");
    }

    @Test
    void aMissingDataFolderIsAnErrorThatNamesTheRestoreScript(@TempDir Path dir) {
        OverworldGenerator.MissingWorldgenDataException e = assertThrows(OverworldGenerator.MissingWorldgenDataException.class,
                () -> new VanillaChunkGenerator(dir, 1L));
        assertTrue(e.getMessage().contains(OverworldGenerator.RESTORE_HINT), e.getMessage());
    }
}
