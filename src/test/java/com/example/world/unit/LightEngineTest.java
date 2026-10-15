package com.example.world.unit;

import com.example.world.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The light rules of the 26.3 client: dampening per block, sources, two-queue updates that cross chunk borders. */
class LightEngineTest {
    private static final int G = FlatWorldGenerator.GRASS_HEIGHT; // y of the grass top block; air starts at G + 1

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) LightEngine.initChunk(world.getChunk(cx, cz), world);
    }

    private int sky(int x, int y, int z) {
        return world.getChunk(World.worldToChunk(x), World.worldToChunk(z)).getSkyLight(World.worldToLocal(x), y, World.worldToLocal(z));
    }

    private int block(int x, int y, int z) {
        return world.getChunk(World.worldToChunk(x), World.worldToChunk(z)).getBlockLight(World.worldToLocal(x), y, World.worldToLocal(z));
    }

    // ---- block properties ----

    @Test
    void dampeningFollowsVanilla() {
        assertEquals(0, LightProperties.dampening(BlockType.AIR));
        assertEquals(15, LightProperties.dampening(BlockType.STONE));
        assertEquals(15, LightProperties.dampening(BlockType.GLOWSTONE));
        assertEquals(1, LightProperties.dampening(BlockType.OAK_LEAVES));
        assertEquals(1, LightProperties.dampening(BlockType.SPRUCE_LEAVES));
        assertEquals(1, LightProperties.dampening(BlockType.WATER));
        assertEquals(1, LightProperties.dampening(BlockType.ICE));
        assertEquals(0, LightProperties.dampening(BlockType.GLASS));
        assertEquals(0, LightProperties.dampening(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(0, LightProperties.dampening(BlockType.CHEST));
    }

    @Test
    void cornerShadeIsPointTwoNextToFullBlocksAndOneNextToGlassMudAndAir() {
        assertEquals(0.2f, LightProperties.shadeBrightness(BlockType.STONE));
        assertEquals(0.2f, LightProperties.shadeBrightness(BlockType.DIRT));
        assertEquals(1.0f, LightProperties.shadeBrightness(BlockType.GLASS));
        assertEquals(1.0f, LightProperties.shadeBrightness(BlockType.MUD));
        assertEquals(1.0f, LightProperties.shadeBrightness(BlockType.AIR));
        assertEquals(1.0f, LightProperties.shadeBrightness(BlockType.WATER));
    }

    // ---- block light ----

    @Test
    void aLightSourceSpreadsOneLevelLessPerBlock() {
        world.setBlock(8, G + 3, 8, BlockType.GLOWSTONE);
        assertEquals(15, block(8, G + 3, 8));
        assertEquals(14, block(9, G + 3, 8));
        assertEquals(14, block(8, G + 4, 8));
        assertEquals(13, block(9, G + 4, 8));
        assertEquals(15 - 6, block(8 + 3, G + 3, 8 + 3), "the distance is the number of steps");
        assertEquals(1, block(8 + 14, G + 3, 8), "it reaches 14 blocks away");
        assertEquals(0, block(8 + 15, G + 3, 8));
    }

    @Test
    void anOpaqueBlockStopsLightAndItsRemovalLetsItThrough() {
        world.setBlock(8, G + 3, 8, BlockType.GLOWSTONE);
        for (int d = 0; d < 6; d++) {
            int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            world.setBlock(8 + around[d][0], G + 3 + around[d][1], 8 + around[d][2], BlockType.STONE);
        }
        assertEquals(0, block(10, G + 3, 8), "boxed in by stone");
        world.setBlock(9, G + 3, 8, BlockType.AIR);
        assertEquals(14, block(9, G + 3, 8));
        assertEquals(13, block(10, G + 3, 8));
    }

    @Test
    void removingASourceLeavesNoLightBehindEvenAcrossAChunkBorder() {
        world.setBlock(14, G + 3, 8, BlockType.GLOWSTONE); // two blocks from the border between chunk 0 and chunk 1
        assertEquals(13, block(16, G + 3, 8), "light crossed the border");
        world.setBlock(14, G + 3, 8, BlockType.AIR);
        for (int x = 0; x < 32; x++) for (int y = G; y < G + 10; y++) for (int z = 0; z < 16; z++) {
            assertEquals(0, block(x, y, z), "stale light at " + x + "," + y + "," + z);
        }
    }

    @Test
    void twoSourcesKeepTheirLightWhenOneIsRemoved() {
        world.setBlock(5, G + 3, 8, BlockType.GLOWSTONE);
        world.setBlock(9, G + 3, 8, BlockType.GLOWSTONE);
        assertEquals(13, block(7, G + 3, 8), "two blocks from either source");
        world.setBlock(5, G + 3, 8, BlockType.AIR);
        assertEquals(13, block(7, G + 3, 8), "the other source still lights it, two blocks away");
        assertEquals(10, block(4, G + 3, 8), "and 5 blocks from it on the far side");
        assertEquals(11, block(5, G + 3, 8), "x=5 is four blocks from the remaining source");
    }

    @Test
    void lightSourceNearTheBorderReachesTheNextChunk() {
        world.setBlock(13, G + 3, 8, BlockType.GLOWSTONE);
        assertEquals(12, block(16, G + 3, 8));
        assertEquals(13, block(15, G + 3, 8));
    }

    @Test
    void aBurningFurnaceGivesOffLevelThirteenAndPutsItOutAgain() {
        world.setBlock(8, G + 3, 8, BlockType.FURNACE);
        assertEquals(0, block(9, G + 3, 8));
        world.setLightEmissionOverride(8, G + 3, 8, 13);
        assertEquals(13, block(8, G + 3, 8));
        assertEquals(12, block(9, G + 3, 8));
        world.setLightEmissionOverride(8, G + 3, 8, 0);
        assertEquals(0, block(8, G + 3, 8));
        assertEquals(0, block(9, G + 3, 8));
    }

    @Test
    void breakingABurningFurnaceTakesItsLightWithIt() {
        world.setBlock(8, G + 3, 8, BlockType.FURNACE);
        world.setLightEmissionOverride(8, G + 3, 8, 13);
        world.setBlock(8, G + 3, 8, BlockType.AIR);
        assertEquals(0, block(8, G + 3, 8));
        assertEquals(0, block(9, G + 3, 8));
    }

    // ---- sky light ----

    @Test
    void openAirHasFullSkyLightAllTheWayDown() {
        assertEquals(15, sky(8, G + 1, 8));
        assertEquals(15, sky(8, 255, 8));
        assertEquals(0, sky(8, G - 1, 8), "inside the ground");
    }

    @Test
    void aRoofLetsLightInFromTheSidesOneLevelLessPerBlock() {
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) world.setBlock(x, G + 4, z, BlockType.STONE);
        assertEquals(14, sky(6, G + 2, 8), "first covered column");
        assertEquals(13, sky(7, G + 2, 8));
        assertEquals(12, sky(8, G + 2, 8), "the middle of a 5x5 roof");
        world.setBlock(8, G + 4, 8, BlockType.AIR); // a skylight hole
        assertEquals(15, sky(8, G + 2, 8), "straight below the hole it is full again: no loss down an open column");
        assertEquals(14, sky(9, G + 2, 8));
    }

    @Test
    void breakingTheRoofRestoresTheLightAndPlacingOneTakesItAway() {
        world.setBlock(8, G + 4, 8, BlockType.STONE);
        assertEquals(14, sky(8, G + 2, 8), "light from the neighbouring columns");
        world.setBlock(8, G + 4, 8, BlockType.AIR);
        assertEquals(15, sky(8, G + 2, 8));
        assertEquals(15, sky(8, G + 4, 8));
    }

    @Test
    void leavesAndWaterDimSkyLightByOnePerBlock() {
        // a 3x3 canopy of leaves
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) world.setBlock(x, G + 5, z, BlockType.OAK_LEAVES);
        assertEquals(14, sky(8, G + 5, 8), "the leaf block itself");
        assertEquals(13, sky(8, G + 3, 8), "under the middle of the canopy: 14 from the sides... then one less");
        // a 5x5 pool three blocks deep: light comes down from above (one less per block) and in from the sides
        for (int x = 10; x <= 14; x++) for (int z = 2; z <= 6; z++) for (int y = G + 1; y <= G + 3; y++) world.setBlock(x, y, z, BlockType.WATER);
        assertEquals(14, sky(12, G + 3, 4), "the top water block");
        assertEquals(13, sky(12, G + 2, 4));
        assertEquals(12, sky(12, G + 1, 4));
    }

    @Test
    void lightFromEitherSideOfABorderMeetsAcrossIt() {
        // a long roof across the border between chunk 0 and chunk 1
        for (int x = 8; x <= 23; x++) for (int z = 6; z <= 10; z++) world.setBlock(x, G + 4, z, BlockType.STONE);
        assertEquals(14, sky(8, G + 2, 8), "the first covered column at the west end");
        assertEquals(14, sky(23, G + 2, 8), "and at the east end, in the other chunk");
        assertEquals(12, sky(16, G + 2, 8), "the middle row is three columns from the open side, on both sides of the border");
        assertEquals(12, sky(15, G + 2, 8));
    }

    @Test
    void relightingAChunkTwiceGivesTheSameResult() {
        world.setBlock(8, G + 3, 8, BlockType.GLOWSTONE);
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) world.setBlock(x, G + 6, z, BlockType.STONE);
        int a = sky(8, G + 3, 8), b = block(11, G + 3, 8), c = sky(9, G + 4, 8);
        LightEngine.initChunk(world.getChunk(0, 0), world);
        assertEquals(a, sky(8, G + 3, 8));
        assertEquals(b, block(11, G + 3, 8));
        assertEquals(c, sky(9, G + 4, 8));
    }

    @Test
    void lightChangesMarkTheAffectedChunksForRemeshing() {
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.getChunk(cx, cz).clearMeshDirty();
        world.setBlock(14, G + 3, 8, BlockType.GLOWSTONE);
        assertTrue(world.getChunk(0, 0).isMeshDirty());
        assertTrue(world.getChunk(1, 0).isMeshDirty(), "its light reached the next chunk");
    }
}
