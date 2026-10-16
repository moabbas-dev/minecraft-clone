package com.example.world;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** A tree is never cut off at a chunk border: the part that lies in the neighbouring chunk is handed over, whichever chunk comes first. */
class TreeSpillTest {
    private static World flat() {
        return new World(1L, new FlatWorldGenerator());
    }

    @Test
    void aBlockSpilledIntoAChunkThatIsNotGeneratedYetAppearsWhenItIsGenerated() {
        World w = flat();
        w.spill(1, 0, 3, 5, 7, BlockType.OAK_LEAVES);
        w.generateChunk(w.getOrCreateChunk(1, 0));
        assertEquals(BlockType.OAK_LEAVES, w.getBlock(16 + 3, 5, 7));
        assertEquals(BlockType.GRASS_BLOCK, w.getBlock(16 + 3, FlatWorldGenerator.GRASS_HEIGHT, 7), "the ground is untouched");
    }

    @Test
    void aBlockSpilledIntoAGeneratedChunkIsPlacedAtOnceButNeverOverwritesTerrain() {
        World w = flat();
        w.generateChunk(w.getOrCreateChunk(1, 0));
        w.spill(1, 0, 3, 6, 7, BlockType.OAK_LOG);
        assertEquals(BlockType.OAK_LOG, w.getBlock(19, 6, 7));
        w.spill(1, 0, 4, FlatWorldGenerator.GRASS_HEIGHT, 7, BlockType.OAK_LEAVES);
        assertEquals(BlockType.GRASS_BLOCK, w.getBlock(20, FlatWorldGenerator.GRASS_HEIGHT, 7), "a leaf does not replace ground");
    }

    @Test
    void spilledBlocksSurviveClosingTheWorldBetweenTheTwoChunks() throws Exception {
        File dir = Files.createTempDirectory("spill").toFile();
        World first = flat();
        first.setSaveDir(dir);
        first.spill(2, 1, 0, 8, 15, BlockType.BIRCH_LEAVES);
        first.spill(2, 1, 15, 9, 0, BlockType.BIRCH_LOG);
        World second = flat();
        second.setSaveDir(dir);
        second.generateChunk(second.getOrCreateChunk(2, 1));
        assertEquals(BlockType.BIRCH_LEAVES, second.getBlock(32, 8, 16 + 15));
        assertEquals(BlockType.BIRCH_LOG, second.getBlock(32 + 15, 9, 16));
        World third = flat();
        third.setSaveDir(dir);
        third.generateChunk(third.getOrCreateChunk(2, 1));
        assertEquals(0, WorldIO.takeSpill(dir, 2, 1).size(), "the file is used up once");
    }

    @Test
    void aTreeOnTheBorderIsWholeInBothChunksInEitherGenerationOrder() {
        for (boolean westFirst : new boolean[]{true, false}) {
            World w = flat();
            Chunk west = w.getOrCreateChunk(0, 0), east = w.getOrCreateChunk(1, 0);
            if (westFirst) {
                w.generateChunk(west);
                w.generateChunk(east);
            } else {
                w.generateChunk(east);
                w.generateChunk(west);
            }
            TreePlanner.Plan plan = TreePlanner.plan(TreeSpecies.DARK_OAK, west, w, 14, FlatWorldGenerator.GRASS_HEIGHT + 1, 8, new Random(4));
            assertFalse(plan.fits, "the 2 x 2 trunk and the wide crown reach over the border");
            plan.commit(west, w);
            int logsEast = 0, leavesEast = 0, leavesWest = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 4; y < 20; y++) {
                if (east.getLocal(x, y, z) == BlockType.DARK_OAK_LEAVES) leavesEast++;
                if (west.getLocal(x, y, z) == BlockType.DARK_OAK_LEAVES) leavesWest++;
                if (east.getLocal(x, y, z) == BlockType.DARK_OAK_LOG) logsEast++;
            }
            assertTrue(leavesEast > 10, "the crown continues into the east chunk (" + (westFirst ? "west" : "east") + " first): " + leavesEast);
            assertTrue(leavesWest > 10);
            assertEquals(plan.count(BlockType.DARK_OAK_LEAVES), leavesEast + leavesWest, "every leaf of the plan landed, on one side or the other");
        }
    }
}
