package com.example.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** The world spans Y -64..319 (noise_settings/overworld.json: min_y -64, height 384) and every system must accept negative Y as real world Y. */
class WorldHeightTest {
    private static World flat() {
        World w = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) w.generateChunk(w.getOrCreateChunk(cx, cz));
        return w;
    }

    @Test
    void theVerticalRangeIsTheVanillaOverworldOne() {
        assertEquals(-64, Chunk.MIN_Y);
        assertEquals(384, Chunk.SIZE_Y);
        assertEquals(319, Chunk.MAX_Y);
        assertTrue(Chunk.inWorld(-64) && Chunk.inWorld(319));
        assertFalse(Chunk.inWorld(-65) || Chunk.inWorld(320));
    }

    @Test
    void blocksAtTheBottomAndTopOfTheWorldAreStoredAtTheirRealY() {
        World w = flat();
        w.setBlock(3, -64, 3, BlockType.DIAMOND_ORE);
        w.setBlock(3, 319, 3, BlockType.GLOWSTONE);
        w.setBlock(3, -1, 3, BlockType.GOLD_ORE);
        w.setBlock(3, 0, 3, BlockType.IRON_ORE);
        assertEquals(BlockType.DIAMOND_ORE, w.getBlock(3, -64, 3));
        assertEquals(BlockType.GLOWSTONE, w.getBlock(3, 319, 3));
        assertEquals(BlockType.GOLD_ORE, w.getBlock(3, -1, 3));
        assertEquals(BlockType.IRON_ORE, w.getBlock(3, 0, 3), "y 0 is an ordinary cell, not the bottom");
        assertEquals(BlockType.AIR, w.getBlock(3, -65, 3));
        assertEquals(BlockType.AIR, w.getBlock(3, 320, 3));
        w.setBlock(3, -65, 3, BlockType.STONE);
        w.setBlock(3, 320, 3, BlockType.STONE);
        assertEquals(BlockType.AIR, w.getBlock(3, -65, 3), "writes outside the world are ignored");
    }

    @Test
    void theColumnHeightIsTheRealYOfTheHighestBlockAndEmptyColumnsHaveNone() {
        Chunk c = new Chunk(0, 0);
        assertEquals(Chunk.MIN_Y - 1, c.getColumnHeight(2, 2));
        c.setLocal(2, -50, 2, BlockType.STONE);
        assertEquals(-50, c.getColumnHeight(2, 2));
        c.setLocal(2, 10, 2, BlockType.STONE);
        assertEquals(10, c.getColumnHeight(2, 2));
        Chunk d = new Chunk(0, 0);
        d.importBlocks(c.exportBlocks());
        assertEquals(10, d.getColumnHeight(2, 2));
        assertEquals(Chunk.MIN_Y - 1, d.getColumnHeight(5, 5));
    }

    @Test
    void theFlatWorldStartsAtTheBottomOfTheWorldLikeVanillasClassicFlat() {
        World w = flat();
        assertEquals(BlockType.BEDROCK, w.getBlock(0, -64, 0));
        assertEquals(BlockType.DIRT, w.getBlock(0, -63, 0));
        assertEquals(BlockType.DIRT, w.getBlock(0, -62, 0));
        assertEquals(BlockType.GRASS_BLOCK, w.getBlock(0, -61, 0));
        assertEquals(-61, FlatWorldGenerator.GRASS_HEIGHT);
        assertEquals(BlockType.AIR, w.getBlock(0, -60, 0));
    }

    @Test
    void lightWorksBelowYZero() {
        World w = flat();
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) LightEngine.initChunk(w.getChunk(cx, cz), w);
        assertEquals(15, w.getSkyLight(4, -60, 4), "open sky above the flat ground");
        w.setBlock(4, -60, 4, BlockType.GLOWSTONE);
        assertTrue(w.getBlockLight(5, -60, 4) >= 13, "glowstone lights its neighbour at y -60");
        w.setBlock(4, -60, 4, BlockType.AIR);
        assertEquals(0, w.getBlockLight(5, -60, 4), "and the light is removed again");
    }

    @Test
    void waterFlowsDownwardAtNegativeY() {
        World w = flat();
        w.setBlock(4, -60, 4, BlockType.WATER);
        for (int i = 0; i < 400; i++) w.fluids().tick(w);
        assertTrue(w.getBlock(5, -60, 4).isWater(), "water spreads sideways over the grass at y -61");
    }

    @Test
    void aChunkSavedWithTheVanillaRangeRoundTripsAndALegacyOneIsNotMisread(@TempDir File dir) throws Exception {
        Chunk c = new Chunk(0, 0);
        c.setLocal(1, -64, 1, BlockType.BEDROCK);
        c.setLocal(1, 319, 1, BlockType.GLOWSTONE);
        WorldIO.saveChunk(dir, c);
        short[] loaded = WorldIO.loadChunk(dir, 0, 0);
        assertNotNull(loaded);
        Chunk d = new Chunk(0, 0);
        d.importBlocks(loaded);
        assertEquals(BlockType.BEDROCK, d.getLocal(1, -64, 1));
        assertEquals(BlockType.GLOWSTONE, d.getLocal(1, 319, 1));

        File legacy = new File(new File(dir, "chunks"), "5_5.chunk");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(legacy))) {
            out.write(new byte[16 * 256 * 16]); // the old headerless 256 high, 8-bit format
        }
        assertNull(WorldIO.loadChunk(dir, 5, 5), "a legacy chunk is not read as the new format");
    }

    @Test
    void spillRecordsKeepNegativeYAndWideIds(@TempDir File dir) {
        WorldIO.appendSpill(dir, 3, 4, 5, -40, 6, BlockType.OAK_LEAVES);
        java.util.List<int[]> records = WorldIO.takeSpill(dir, 3, 4);
        assertEquals(1, records.size());
        assertArrayEquals(new int[]{5, -40, 6, BlockType.OAK_LEAVES.id}, records.get(0));
    }

    @Test
    void entitiesFallOutOfTheWorldBelowMinYMinus64() {
        assertEquals(-128f, com.example.survival.PlayerSurvival.VOID_Y);
    }
}
