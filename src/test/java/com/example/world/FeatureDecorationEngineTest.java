package com.example.world;

import com.example.worldgen.chunk.OverworldGenerator;
import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.level.TerrainFeatureChunk;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.TerrainChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Feature decoration inside the engine (PARITY section 31): the TERRAIN -> FEATURES -> FULL pipeline of {@link World} and
 * {@link VanillaChunkGenerator} against the worldgen decoration on plain {@link TerrainChunk}s (the oracle-verified path of
 * {@code FeatureOracleTest}), determinism, heightmaps, writes into FULL chunks, the decoration journal and the v3 save format.
 */
class FeatureDecorationEngineTest {

    @BeforeAll
    static void needsData() {
        assumeTrue(Files.isDirectory(VanillaChunkGenerator.DATA_ROOT.resolve("minecraft/worldgen/placed_feature")), "vanilla data/ not restored");
        assumeTrue(new File("textures/colormap/grass.png").isFile(), "vanilla colormaps not present");
    }

    private static Chunk full(World world, int cx, int cz) {
        Chunk c = world.getOrCreateChunk(cx, cz);
        world.generateChunk(c);
        return c;
    }

    /** The worldgen-side reference: the same decoration loop on TerrainFeatureChunks, chunks decorated in the given order. */
    private static Map<Long, TerrainFeatureChunk> reference(VanillaChunkGenerator gen, int[][] order) {
        OverworldGenerator terrain = gen.overworld();
        Map<Long, TerrainFeatureChunk> chunks = new HashMap<>();
        for (int[] c : order) {
            int cx = c[0], cz = c[1];
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = cx + dx, z = cz + dz;
                    chunks.computeIfAbsent(Chunk.key(x, z), k -> new TerrainFeatureChunk(terrain.generate(x, z)));
                }
            }
            GenRegion.ChunkLookup lookup = (x, z) -> {
                FeatureChunk f = chunks.get(Chunk.key(x, z));
                return f != null ? f : new EmptyFeatureChunk(x, z, terrain.minY());
            };
            gen.decoration().applyBiomeDecoration(new GenRegion(terrain.seed(), cx, cz, lookup, terrain.tags(), terrain.minY(),
                    terrain.height(), terrain.seaLevel()), null);
        }
        return chunks;
    }

    @Test
    void engineChunksEqualTheWorldgenDecorationInTheSameOrder() {
        long seed = 8675309L;
        // neighbours decorated one after the other, so later decorations read and write FULL engine chunks
        int[][] order = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {-1, 0}, {3, 0}};
        VanillaChunkGenerator gen = new VanillaChunkGenerator(seed);
        World world = new World(seed, gen);
        for (int[] c : order) full(world, c[0], c[1]);
        Map<Long, TerrainFeatureChunk> ref = reference(new VanillaChunkGenerator(seed), order);
        Map<BlockType, Integer> counts = new EnumMap<>(BlockType.class);
        for (int[] c : order) {
            Chunk chunk = world.getChunk(c[0], c[1]);
            TerrainChunk expected = ref.get(Chunk.key(c[0], c[1])).chunk();
            EngineFeatureChunk adapter = new EngineFeatureChunk(world, chunk, gen.overworld().tags(), new HashMap<>());
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int x = c[0] * 16 + lx, z = c[1] * 16 + lz;
                    for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) {
                        StateRef s = expected.getState(x, y, z);
                        BlockType want = VanillaBlocks.byVanilla(s.id(), s.properties());
                        BlockType got = chunk.getLocal(lx, y, lz);
                        if (want != got) throw new AssertionError("chunk " + c[0] + "," + c[1] + " at " + x + "," + y + "," + z + ": " + got + " vs " + s);
                        counts.merge(got, 1, Integer::sum);
                    }
                    for (HeightmapType t : TerrainChunk.FINAL_HEIGHTMAPS) {
                        assertEquals(expected.getHeight(t, lx, lz), adapter.getHeight(t, x, z), t + " at " + x + "," + z);
                    }
                }
            }
        }
        // the ported features did write their blocks
        for (BlockType b : new BlockType[]{BlockType.COAL_ORE, BlockType.IRON_ORE, BlockType.DEEPSLATE_IRON_ORE, BlockType.GRANITE,
                BlockType.ANDESITE, BlockType.DIORITE, BlockType.TUFF}) {
            assertTrue(counts.getOrDefault(b, 0) > 0, b + " in " + counts);
        }
    }

    @Test
    void theResultDependsOnlyOnTheDecorationOrder() {
        long seed = 31337L;
        int[][] order = {{5, 5}, {6, 5}, {5, 6}};
        World a = new World(seed, new VanillaChunkGenerator(seed));
        for (int[] c : order) full(a, c[0], c[1]);
        // b: the TERRAIN chunks are made in another order and partly ahead of time, a far chunk is decorated in between, and the
        // chunks are made FULL through the streaming's step-by-step path
        World b = new World(seed, new VanillaChunkGenerator(seed));
        for (int x = 8; x >= 3; x--) b.generator().ensureTerrain(x, 7, null);
        full(b, 40, -40);
        for (int[] c : order) {
            while (b.generateStep(c[0], c[1]) == null) {
                // one dependency per step
            }
        }
        for (int[] c : order) {
            assertArrayEquals(a.getChunk(c[0], c[1]).exportBlocks(), b.getChunk(c[0], c[1]).exportBlocks(), "chunk " + c[0] + "," + c[1]);
        }
        assertTrue(b.generationFullChunks() >= 3);
    }

    @Test
    void evictedProtosKeepTheWritesTheyReceived() {
        long seed = 99L;
        World kept = new World(seed, new VanillaChunkGenerator(seed));
        full(kept, 0, 0);
        full(kept, 1, 0);
        World evicted = new World(seed, new VanillaChunkGenerator(seed));
        full(evicted, 0, 0);
        evicted.evictTerrain(1000, 1000, 0); // drop every TERRAIN chunk; (1, 0) received writes from (0, 0)'s decoration
        assertEquals(0, ((VanillaChunkGenerator) evicted.generator()).terrainProtoCount());
        full(evicted, 1, 0);
        assertArrayEquals(kept.getChunk(1, 0).exportBlocks(), evicted.getChunk(1, 0).exportBlocks());
        assertArrayEquals(kept.getChunk(0, 0).exportBlocks(), evicted.getChunk(0, 0).exportBlocks());
    }

    @Test
    void aSavedFullChunkAndTheJournalsRoundTrip(@TempDir File dir) {
        long seed = 4242L;
        World a = new World(seed, new VanillaChunkGenerator(seed));
        a.setSaveDir(dir);
        Chunk c = full(a, 2, 3);
        WorldIO.saveChunk(dir, c);
        a.saveGenerationState();
        String[] journals = new File(dir, "decoration").list();
        assertNotNull(journals, "the decoration of (2, 3) wrote into a neighbour, whose journal is saved");
        assertTrue(journals.length > 0);
        String[] xz = journals[0].replace(".journal", "").split("_");
        int nx = Integer.parseInt(xz[0]), nz = Integer.parseInt(xz[1]);

        // b reloads from disk: (2, 3) is FULL from its save, the neighbour is generated and replays its journal
        World b = new World(seed, new VanillaChunkGenerator(seed));
        b.setSaveDir(dir);
        Chunk loaded = full(b, 2, 3);
        assertFalse(loaded.isFreshlyGenerated());
        assertArrayEquals(c.exportBlocks(), loaded.exportBlocks());
        full(b, nx, nz);
        full(a, nx, nz);
        assertArrayEquals(a.getChunk(nx, nz).exportBlocks(), b.getChunk(nx, nz).exportBlocks());
        assertArrayEquals(a.getChunk(2, 3).exportBlocks(), b.getChunk(2, 3).exportBlocks(), "the loaded chunk received the same writes");
        assertFalse(new File(new File(dir, "decoration"), journals[0]).isFile(), "a FULL chunk has no journal");
        boolean ore = false;
        for (short id : loaded.exportBlocks()) ore |= BlockType.byId(id & 0xFFFF).name().endsWith("_ORE");
        assertTrue(ore, "the saved chunk holds its ores");
    }

    @Test
    void chunkFilesHaveTheV3HeaderAndV2IsLegacy(@TempDir File dir) throws Exception {
        Chunk c = new Chunk(1, 2);
        c.setLocal(0, 0, 0, BlockType.LAPIS_ORE);
        WorldIO.saveChunk(dir, c);
        File file = new File(new File(dir, "chunks"), "1_2.chunk");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            assertEquals(0x4D434B32, in.readInt());
            assertEquals(3, in.readInt());
            assertEquals(Chunk.MIN_Y, in.readInt());
            assertEquals(Chunk.SIZE_Y, in.readInt());
            assertEquals("minecraft:full", in.readUTF());
        }
        short[] blocks = WorldIO.loadChunk(dir, 1, 2);
        assertNotNull(blocks);
        assertEquals(BlockType.LAPIS_ORE.id, blocks[Chunk.index(0, 0, 0)]);

        File v2 = new File(new File(dir, "chunks"), "4_4.chunk");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(v2))) {
            out.writeInt(0x4D434B32);
            out.writeInt(2);
            out.writeInt(Chunk.MIN_Y);
            out.writeInt(Chunk.SIZE_Y);
            out.write(new byte[16 * 16 * Chunk.SIZE_Y * 2]);
        }
        assertNull(WorldIO.loadChunk(dir, 4, 4), "a version 2 chunk (undecorated) is regenerated, not read");
    }

    @Test
    void featureWritesIntoAFullChunkRelightAndRemesh() {
        long seed = 7L;
        World world = new World(seed, new VanillaChunkGenerator(seed));
        Chunk a = full(world, 0, 0);
        Chunk b = full(world, 1, 0);
        LightEngine.initChunk(a, world);
        LightEngine.initChunk(b, world);
        a.clearMeshDirty();
        b.clearMeshDirty();
        int y = Chunk.MAX_Y - 1; // open sky
        world.setBlockFromFeature(15, y, 4, BlockType.MAGMA_BLOCK);
        assertEquals(BlockType.MAGMA_BLOCK, world.getBlock(15, y, 4));
        assertTrue(a.isMeshDirty(), "the written chunk is remeshed");
        assertTrue(b.isMeshDirty(), "the neighbour across the border is remeshed");
        assertEquals(3, world.getBlockLight(15, y, 4), "magma block emits 3");
        assertEquals(2, world.getBlockLight(16, y, 4), "and the light crosses into the neighbour");
        assertEquals(14, world.getSkyLight(15, y - 1, 4), "the sky column below is shaded by the new block");
        // the adapter maintains the heightmaps by Heightmap.update
        EngineFeatureChunk adapter = new EngineFeatureChunk(world, a, ((VanillaChunkGenerator) world.generator()).overworld().tags(), new HashMap<>());
        assertEquals(y, adapter.getHeight(HeightmapType.MOTION_BLOCKING, 15, 4));
        adapter.setBlockState(15, y, 4, StateRef.AIR);
        assertEquals(adapter.scanHighest(HeightmapType.MOTION_BLOCKING, 15, 4), adapter.getHeight(HeightmapType.MOTION_BLOCKING, 15, 4));
        assertEquals(0, world.getBlockLight(15, y, 4));
    }

    @Test
    void terrainChunksAreInvisibleUntilDecorated() {
        long seed = 5L;
        World world = new World(seed, new VanillaChunkGenerator(seed));
        assertTrue(world.generator().ensureTerrain(9, 9, null));
        assertFalse(world.generator().ensureTerrain(9, 9, null));
        assertNull(world.getChunk(9, 9));
        assertEquals(BlockType.AIR, world.getBlock(9 * 16, -64, 9 * 16), "a TERRAIN chunk reads as air like an unloaded one");
        Chunk c = full(world, 9, 9);
        assertEquals(BlockType.BEDROCK, world.getBlock(9 * 16, -64, 9 * 16));
        assertTrue(world.drainNewlyReady().contains(c));
        // the 5 x 5 rule: every chunk within 2 is TERRAIN (or FULL)
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) continue;
                assertTrue(world.generator().hasTerrain(9 + dx, 9 + dz), dx + "," + dz);
            }
        }
        List<Chunk> none = world.drainNewlyReady();
        assertTrue(none.isEmpty());
    }
}
