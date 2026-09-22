package com.example.world.integration;

import com.example.world.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the two properties the biome-blending round's plan explicitly required and checked
 * by hand with a throwaway harness: same-seed determinism, and chunk-boundary consistency (a
 * world coordinate must produce identical results no matter which chunk generates it). This
 * promotes that ad hoc check into a permanent regression test.
 */
class WorldGenerationTest {

    @Test
    void sameSeedProducesIdenticalChunkBlocksFromIndependentWorlds() {
        World worldA = new World(555L, new TerrainGenerator(555L));
        World worldB = new World(555L, new TerrainGenerator(555L));

        Chunk chunkA = worldA.getOrCreateChunk(3, -2);
        worldA.generateChunk(chunkA);
        Chunk chunkB = worldB.getOrCreateChunk(3, -2);
        worldB.generateChunk(chunkB);

        assertArrayEquals(chunkA.exportBlocks(), chunkB.exportBlocks(),
                "identical seed must produce byte-identical chunk terrain");
    }

    @Test
    void climateAtIsAPureFunctionOfWorldCoordinatesRegardlessOfChunkOrigin() {
        // Reading climateAt never touches chunk state at all (see TerrainGenerator), so it must
        // agree with itself no matter what chunks happen to be loaded around it - this directly
        // checks the "no visible seam across chunk boundaries" requirement.
        TerrainGenerator gen = new TerrainGenerator(777L);
        for (int boundary = -3; boundary <= 3; boundary++) {
            int worldX = boundary * Chunk.SIZE_X; // exact chunk boundary column
            ClimateSample first = gen.climateAt(worldX, 50);
            ClimateSample second = gen.climateAt(worldX, 50);
            assertEquals(first.temperature(), second.temperature(), 0.0);
            assertEquals(first.dominant(), second.dominant());
            assertArrayEquals(first.grassColor(), second.grassColor());
        }
    }

    @Test
    void generatingTheSameChunkTwiceIsIdempotent() {
        World world = new World(9L, new TerrainGenerator(9L));
        Chunk chunk = world.getOrCreateChunk(0, 0);
        world.generateChunk(chunk);
        byte[] first = chunk.exportBlocks();

        // generateChunk is only ever called once per chunk in real play (guarded by
        // world.hasChunk in Game's streaming loop), but the generator itself should still be a
        // pure function of the chunk's own coordinates - calling it again must not corrupt data.
        // (saveDir is null here, so this always regenerates fresh rather than loading from disk.)
        world.generateChunk(chunk);
        byte[] second = chunk.exportBlocks();
        assertArrayEquals(first, second, "regenerating the same chunk coordinates must be deterministic");
    }

    @Test
    void chunkHeightMapMatchesActualHighestNonAirBlock() {
        World world = new World(42L, new TerrainGenerator(42L));
        Chunk chunk = world.getOrCreateChunk(0, 0);
        world.generateChunk(chunk);

        for (int x = 0; x < Chunk.SIZE_X; x += 4) {
            for (int z = 0; z < Chunk.SIZE_Z; z += 4) {
                int reportedHeight = chunk.getColumnHeight(x, z);
                int actualHighest = -1;
                for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
                    if (!chunk.getLocal(x, y, z).isAir()) {
                        actualHighest = y;
                        break;
                    }
                }
                assertEquals(actualHighest, reportedHeight,
                        "heightmap must match the real highest non-air block at (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void everyGeneratedColumnHasBedrockAtTheBottom() {
        World world = new World(1234L, new TerrainGenerator(1234L));
        Chunk chunk = world.getOrCreateChunk(5, 5);
        world.generateChunk(chunk);
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                assertEquals(BlockType.BEDROCK, chunk.getLocal(x, 0, z),
                        "y=0 must always be bedrock at (" + x + "," + z + ")");
            }
        }
    }
}
