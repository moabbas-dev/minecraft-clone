package com.example.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Flowing water and lava as FlowingFluid, LavaFluid and LiquidBlock do it: levels, delays, falling, sources and the lava reactions. */
class FluidSimTest {
    /** A flat world: grass at y 3, air from y 4. */
    private static World flat() {
        World world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        return world;
    }

    private static void run(World w, int ticks) {
        for (int i = 0; i < ticks; i++) w.fluids().tick(w);
    }

    @Test
    void everyFluidBlockHasItsAmountAndRoundTrips() {
        assertEquals(8, BlockType.WATER.fluidAmount());
        assertEquals(8, BlockType.WATER_FALLING.fluidAmount());
        assertEquals(3, BlockType.WATER_FLOW_3.fluidAmount());
        assertEquals(4, BlockType.LAVA_FLOW_4.fluidAmount());
        for (int a = 1; a <= 7; a++) assertEquals(a, BlockType.fluidBlock(false, a, false).fluidAmount());
        for (int a : new int[]{2, 4, 6}) assertEquals(a, BlockType.fluidBlock(true, a, false).fluidAmount());
        assertSame(BlockType.WATER, BlockType.fluidBlock(false, 8, false));
        assertSame(BlockType.LAVA_FALLING, BlockType.fluidBlock(true, 8, true));
        assertTrue(BlockType.WATER_FLOW_5.isWater() && !BlockType.WATER_FLOW_5.isLava() && BlockType.LAVA.isLava());
        assertFalse(BlockType.WATER_FLOW_5.isFluidSource());
        assertEquals(15f, BlockType.LAVA_FLOW_2.lightEmission);
        assertEquals(0f, BlockType.WATER_FLOW_2.lightEmission);
        assertFalse(BlockType.WATER.isSolidCollision() || BlockType.LAVA.isSolidCollision());
    }

    @Test
    void aWaterSourceSpreadsSevenBlocksOnFlatGroundLosingOneLevelPerBlock() {
        World w = flat();
        w.setBlock(8, 4, 8, BlockType.WATER);
        run(w, 4);
        assertTrue(w.getBlock(9, 4, 8).isAir(), "nothing happens before the 5 tick delay");
        run(w, 2);
        assertEquals(BlockType.WATER_FLOW_7, w.getBlock(9, 4, 8));
        assertEquals(BlockType.WATER_FLOW_7, w.getBlock(8, 4, 7));
        w.fluids().settle(w, 400);
        assertEquals(BlockType.WATER_FLOW_5, w.getBlock(8, 4, 11));
        assertEquals(BlockType.WATER_FLOW_1, w.getBlock(15, 4, 8));
        assertEquals(BlockType.WATER_FLOW_1, w.getBlock(8, 4, 1));
        assertTrue(w.getBlock(8, 4, 0).isAir(), "seven blocks is the reach");
        assertEquals(BlockType.WATER_FLOW_3, w.getBlock(10, 4, 11), "diagonals count both steps: 8 - 5");
        assertEquals(BlockType.WATER, w.getBlock(8, 4, 8));
    }

    @Test
    void waterFallsInAColumnThenSpreadsOnTheGround() {
        World w = flat();
        w.setBlock(8, 10, 8, BlockType.WATER);
        w.fluids().settle(w, 600);
        for (int y = 4; y <= 9; y++) assertEquals(BlockType.WATER_FALLING, w.getBlock(8, y, 8), "y " + y);
        // the source in mid-air also spills sideways at its own level (the waterfall's umbrella), and those spills fall too
        assertEquals(BlockType.WATER_FLOW_7, w.getBlock(9, 10, 8));
        assertEquals(BlockType.WATER_FALLING, w.getBlock(9, 9, 8));
        assertTrue(w.getBlock(12, 4, 8).isWater(), "and it spreads along the ground");
    }

    @Test
    void twoSourcesMakeAThirdBetweenThemAndTheSourceCannotBeLostWhileTheyStay() {
        World w = flat();
        w.setBlock(5, 4, 5, BlockType.WATER);
        w.setBlock(7, 4, 5, BlockType.WATER);
        w.fluids().settle(w, 400);
        assertEquals(BlockType.WATER, w.getBlock(6, 4, 5), "an infinite water source");
        w.setBlock(5, 4, 5, BlockType.AIR);
        w.fluids().settle(w, 600);
        assertTrue(w.getBlock(6, 4, 5).isFluid());
    }

    @Test
    void takingTheSourceAwayDrainsTheFlow() {
        World w = flat();
        w.setBlock(8, 4, 8, BlockType.WATER);
        w.fluids().settle(w, 400);
        assertTrue(w.getBlock(11, 4, 8).isWater());
        w.setBlock(8, 4, 8, BlockType.AIR);
        w.fluids().settle(w, 600);
        for (int x = 1; x <= 15; x++) assertTrue(w.getBlock(x, 4, 8).isAir(), "x " + x + " " + w.getBlock(x, 4, 8));
    }

    @Test
    void waterHeadsForANearbyHoleBeforeSpreadingEverywhere() {
        World w = flat();
        for (int y = 1; y <= 3; y++) w.setBlock(11, y, 8, BlockType.AIR);
        w.setBlock(8, 4, 8, BlockType.WATER);
        run(w, 6);
        assertTrue(w.getBlock(9, 4, 8).isWater(), "towards the hole");
        assertTrue(w.getBlock(7, 4, 8).isAir(), "not away from it");
        assertTrue(w.getBlock(8, 4, 9).isAir());
        w.fluids().settle(w, 800);
        assertTrue(w.getBlock(11, 3, 8).isWater(), "it falls into the hole");
    }

    @Test
    void lavaIsSlowAndShortFlowingThreeBlocksInSteps() {
        World w = flat();
        w.setBlock(8, 4, 8, BlockType.LAVA);
        run(w, 29);
        assertTrue(w.getBlock(9, 4, 8).isAir(), "30 tick delay");
        run(w, 2);
        assertEquals(BlockType.LAVA_FLOW_6, w.getBlock(9, 4, 8));
        w.fluids().settle(w, 2000);
        assertEquals(BlockType.LAVA_FLOW_4, w.getBlock(10, 4, 8));
        assertEquals(BlockType.LAVA_FLOW_2, w.getBlock(11, 4, 8));
        assertTrue(w.getBlock(12, 4, 8).isAir(), "lava reaches three blocks");
        assertEquals(BlockType.LAVA, w.getBlock(8, 4, 8), "lava does not make new sources");
    }

    @Test
    void lavaSourceBesideWaterBecomesObsidianAndFlowingLavaCobblestone() {
        World w = flat();
        w.setBlock(8, 4, 8, BlockType.LAVA);
        w.setBlock(9, 4, 8, BlockType.WATER);
        assertEquals(BlockType.OBSIDIAN, w.getBlock(8, 4, 8));
        World f = flat();
        f.setBlock(8, 4, 8, BlockType.LAVA);
        f.fluids().settle(f, 2000);
        f.setBlock(10, 5, 8, BlockType.WATER_FLOW_3);
        f.setBlock(10, 4, 8, BlockType.AIR);
        f.setBlock(10, 4, 8, BlockType.WATER_FLOW_3);
        assertEquals(BlockType.COBBLESTONE, f.getBlock(9, 4, 8), "flowing lava next to water");
    }

    @Test
    void waterPouredOntoLavaFromAboveMakesObsidianAndLavaFlowingDownOntoWaterMakesStone() {
        World w = flat();
        w.setBlock(8, 4, 8, BlockType.LAVA);
        w.setBlock(8, 5, 8, BlockType.WATER);
        assertEquals(BlockType.OBSIDIAN, w.getBlock(8, 4, 8));
        World s = flat();
        s.setBlock(8, 2, 8, BlockType.AIR);
        s.setBlock(8, 3, 8, BlockType.WATER);
        s.setBlock(8, 4, 8, BlockType.LAVA); // lava above water: the lava reacts at once (water below does not count), then flows down
        s.fluids().settle(s, 400);
        assertFalse(s.getBlock(8, 3, 8).isLava());
    }

    @Test
    void fluidsDoNotSpreadIntoUnloadedTerrain() {
        World w = new World(1L, new FlatWorldGenerator());
        w.generateChunk(w.getOrCreateChunk(0, 0));
        w.setBlock(15, 4, 8, BlockType.WATER);
        w.fluids().settle(w, 400);
        assertNull(w.getChunk(1, 0), "no chunk was created on the other side of the border");
        assertTrue(w.getBlock(14, 4, 8).isWater());
    }

    @Test
    void unsettledFluidKeepsFlowingAfterTheChunkIsSavedAndLoadedAgain() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("fluidsave").toFile();
        World first = flat();
        first.setSaveDir(dir);
        first.setBlock(8, 4, 8, BlockType.WATER);
        first.fluids().tick(first); // one tick only: nothing has spread yet, and the pending tick is not saved
        WorldIO.saveChunk(dir, first.getChunk(0, 0));
        World second = new World(1L, new FlatWorldGenerator());
        second.setSaveDir(dir);
        second.generateChunk(second.getOrCreateChunk(0, 0));
        assertEquals(BlockType.WATER, second.getBlock(8, 4, 8));
        assertTrue(second.fluids().pending() > 0, "the source with room beside it was scheduled again");
        second.fluids().settle(second, 800);
        assertEquals(BlockType.WATER_FLOW_7, second.getBlock(9, 4, 8));
        assertEquals(BlockType.WATER_FLOW_1, second.getBlock(15, 4, 8));
    }

    @Test
    void aSettledOceanDoesNotWakeUpWhenItsChunkLoads() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("fluidsave2").toFile();
        World first = flat();
        first.setSaveDir(dir);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) first.getChunk(0, 0).setLocal(x, 4, z, BlockType.WATER);
        WorldIO.saveChunk(dir, first.getChunk(0, 0));
        World second = new World(1L, new FlatWorldGenerator());
        second.setSaveDir(dir);
        second.generateChunk(second.getOrCreateChunk(0, 0));
        assertEquals(0, second.fluids().pending(), "sources with only water or ground beside them have nothing to do");
    }

    @Test
    void thePlayersBlockLightComesFromLava() {
        assertEquals(15, LightProperties.emission(BlockType.LAVA));
        assertEquals(1, LightProperties.dampening(BlockType.LAVA_FLOW_2));
        assertEquals(1, LightProperties.dampening(BlockType.WATER_FLOW_4));
    }
}
