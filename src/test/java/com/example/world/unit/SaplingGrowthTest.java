package com.example.world.unit;

import com.example.world.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sapling registration, placement rules, growth-stage progression, tree-growth integration,
 * failure handling, and chunk load/unload safety - all against real World/Chunk state, not
 * fakes. A dt large enough to always exceed the configured delay range (45-90s / 20-40s) is
 * used to march through stages deterministically in one update() call each, rather than testing
 * real wall-clock timing - this still genuinely exercises the dt-accumulation mechanism (proving
 * it's time-based, not frame-count-based), just compressed.
 */
class SaplingGrowthTest {

    private static final float FAR_PAST_ANY_DELAY = 100f;

    /** A flat all-grass chunk at y=70 with open air above - isolates growth/space-validation
     *  logic from real terrain generation (the chunk is filled by hand, the generator is never run). */
    private World buildFlatWorld(long seed) {
        World world = new World(seed, new FlatWorldGenerator());
        Chunk chunk = world.getOrCreateChunk(0, 0);
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y <= 70; y++) {
                    chunk.setLocal(x, y, z, y == 70 ? BlockType.GRASS_BLOCK : BlockType.DIRT);
                }
            }
        }
        chunk.markGenerated();
        return world;
    }

    // --- Registration ---

    @Test
    void bySaplingBlockResolvesAllFourOakStagesAndNothingElse() {
        assertEquals(SaplingSpecies.OAK, SaplingSpecies.bySaplingBlock(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(SaplingSpecies.OAK, SaplingSpecies.bySaplingBlock(BlockType.OAK_SAPLING_STAGE_1));
        assertEquals(SaplingSpecies.OAK, SaplingSpecies.bySaplingBlock(BlockType.OAK_SAPLING_STAGE_2));
        assertEquals(SaplingSpecies.OAK, SaplingSpecies.bySaplingBlock(BlockType.OAK_SAPLING_STAGE_3));
        assertNull(SaplingSpecies.bySaplingBlock(BlockType.OAK_LOG));
        assertNull(SaplingSpecies.bySaplingBlock(BlockType.GRASS_BLOCK));
    }

    @Test
    void stageIndexAndStageBlockRoundTrip() {
        for (int i = 0; i < 4; i++) {
            assertEquals(i, SaplingSpecies.OAK.stageIndex(SaplingSpecies.OAK.stageBlock(i)));
        }
    }

    @Test
    void onlyStage0IsPlayerPlaceable() {
        assertTrue(java.util.Arrays.asList(BlockType.PLACEABLE).contains(BlockType.OAK_SAPLING_STAGE_0));
        assertFalse(java.util.Arrays.asList(BlockType.PLACEABLE).contains(BlockType.OAK_SAPLING_STAGE_1));
        assertFalse(java.util.Arrays.asList(BlockType.PLACEABLE).contains(BlockType.OAK_SAPLING_STAGE_2));
        assertFalse(java.util.Arrays.asList(BlockType.PLACEABLE).contains(BlockType.OAK_SAPLING_STAGE_3));
    }

    // --- Growth stage progression ---

    @Test
    void plantedSaplingProgressesThroughAllStagesThenBecomesATree() {
        World world = buildFlatWorld(1L);
        SaplingGrowth growth = new SaplingGrowth(1L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        assertEquals(BlockType.AIR, world.getBlock(8, 71, 8)); // not placed by registerPlanted itself
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);

        assertEquals(1, growth.activeCount());
        growth.update(world, FAR_PAST_ANY_DELAY); // -> stage 1
        assertEquals(BlockType.OAK_SAPLING_STAGE_1, world.getBlock(8, 71, 8));
        growth.update(world, FAR_PAST_ANY_DELAY); // -> stage 2
        assertEquals(BlockType.OAK_SAPLING_STAGE_2, world.getBlock(8, 71, 8));
        growth.update(world, FAR_PAST_ANY_DELAY); // -> stage 3
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, 71, 8));
        growth.update(world, FAR_PAST_ANY_DELAY); // -> attempts tree growth, plenty of space here
        assertEquals(BlockType.OAK_LOG, world.getBlock(8, 71, 8), "the sapling position should now be the trunk's base");
        assertEquals(0, growth.activeCount(), "growth completed - no longer tracked");
    }

    @Test
    void aSmallDtDoesNotAdvanceTheStageImmediately() {
        World world = buildFlatWorld(2L);
        SaplingGrowth growth = new SaplingGrowth(2L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);

        // Configured minimum delay is 45s - a single small dt must never be enough by itself.
        growth.update(world, 0.05f);
        assertEquals(BlockType.OAK_SAPLING_STAGE_0, world.getBlock(8, 71, 8),
                "growth must not depend on a single frame's dt being large enough - proves it's time-accumulated, not per-frame-instant");
    }

    @Test
    void manySmallDtStepsAccumulateTheSameAsOneLargeStep() {
        // Directly proves "not frame-rate dependent": summing many small dt calls must reach the
        // same stage as one big call whose total dt matches.
        World worldA = buildFlatWorld(3L);
        SaplingGrowth growthA = new SaplingGrowth(3L);
        growthA.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        worldA.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        growthA.update(worldA, FAR_PAST_ANY_DELAY);

        World worldB = buildFlatWorld(3L);
        SaplingGrowth growthB = new SaplingGrowth(3L);
        growthB.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        worldB.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        for (int i = 0; i < 2000; i++) growthB.update(worldB, FAR_PAST_ANY_DELAY / 2000f);

        assertEquals(worldA.getBlock(8, 71, 8), worldB.getBlock(8, 71, 8));
    }

    @Test
    void growthIsDeterministicForTheSameSeedAndRegistrationOrder() {
        World worldA = buildFlatWorld(42L);
        SaplingGrowth growthA = new SaplingGrowth(42L);
        World worldB = buildFlatWorld(42L);
        SaplingGrowth growthB = new SaplingGrowth(42L);

        int[][] positions = {{4, 71, 4}, {8, 71, 8}, {12, 71, 12}};
        for (World w : new World[]{worldA, worldB}) {
            for (int[] p : positions) w.setBlock(p[0], p[1], p[2], BlockType.OAK_SAPLING_STAGE_0);
        }
        for (int[] p : positions) {
            growthA.registerPlanted(p[0], p[1], p[2], SaplingSpecies.OAK);
            growthB.registerPlanted(p[0], p[1], p[2], SaplingSpecies.OAK);
        }
        for (int i = 0; i < 4; i++) {
            growthA.update(worldA, FAR_PAST_ANY_DELAY);
            growthB.update(worldB, FAR_PAST_ANY_DELAY);
        }
        for (int[] p : positions) {
            assertEquals(worldA.getBlock(p[0], p[1], p[2]), worldB.getBlock(p[0], p[1], p[2]));
        }
    }

    // --- Growth failure handling ---

    @Test
    void insufficientSpaceLeavesTheSaplingUntouchedAndStillTracked() {
        World world = buildFlatWorld(4L);
        // Block the space directly above the sapling with stone, so the tree can never fit.
        for (int y = 72; y < 82; y++) world.setBlock(8, y, 8, BlockType.STONE);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_3);
        SaplingGrowth growth = new SaplingGrowth(4L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        // registerPlanted always rolls a stage-0 delay - force it to stage 3's own retry timing
        // isn't directly reachable from the public API, so drive it there the same way real play
        // would: three stage advances, then attempt growth.
        for (int i = 0; i < 3; i++) growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, 71, 8));

        growth.update(world, FAR_PAST_ANY_DELAY); // the actual growth attempt - must fail safely
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, 71, 8),
                "sapling must never be deleted when growth fails for lack of space");
        assertEquals(1, growth.activeCount(), "must still be tracked for a later retry");
        assertEquals(BlockType.STONE, world.getBlock(8, 75, 8), "no partial tree blocks may appear");
    }

    @Test
    void growthSucceedsOnceSpaceIsAvailableEvenAfterAnEarlierFailure() {
        World world = buildFlatWorld(5L);
        for (int y = 72; y < 82; y++) world.setBlock(8, y, 8, BlockType.STONE);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        SaplingGrowth growth = new SaplingGrowth(5L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        for (int i = 0; i < 4; i++) growth.update(world, FAR_PAST_ANY_DELAY); // 3 stage advances + 1 failed growth attempt
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, 71, 8), "still a sapling after the blocked attempt");

        for (int y = 72; y < 82; y++) world.setBlock(8, y, 8, BlockType.AIR); // clear the obstruction
        growth.update(world, FAR_PAST_ANY_DELAY); // retry
        assertEquals(BlockType.OAK_LOG, world.getBlock(8, 71, 8), "should grow now that space is free");
        assertEquals(0, growth.activeCount());
    }

    @Test
    void invalidGroundPreventsGrowthEvenWithPlentyOfOverheadSpace() {
        World world = buildFlatWorld(6L);
        world.setBlock(8, 70, 8, BlockType.STONE); // replace the grass under the sapling
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        SaplingGrowth growth = new SaplingGrowth(6L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        for (int i = 0; i < 4; i++) growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, 71, 8), "must not grow on invalid ground");
    }

    // --- Chunk boundaries and world height ---

    @Test
    void growthNearAChunkEdgeFailsSafelyRatherThanReachingIntoAnUnloadedChunk() {
        World world = buildFlatWorld(7L);
        // x=1 with OAK's canopyRadius=2 would need x=-1, outside this chunk (and any neighbor is
        // unloaded here) - must be rejected, not silently write into/past chunk bounds.
        world.setBlock(1, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        SaplingGrowth growth = new SaplingGrowth(7L);
        growth.registerPlanted(1, 71, 8, SaplingSpecies.OAK);
        for (int i = 0; i < 4; i++) growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(1, 71, 8), "must defer, not grow, this close to the chunk edge");
    }

    @Test
    void growthNearWorldHeightLimitFailsSafely() {
        World world = new World(8L, new FlatWorldGenerator());
        Chunk chunk = world.getOrCreateChunk(0, 0);
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setLocal(x, Chunk.MAX_Y - 2, z, BlockType.GRASS_BLOCK);
            }
        }
        chunk.markGenerated();
        world.setBlock(8, Chunk.MAX_Y - 1, 8, BlockType.OAK_SAPLING_STAGE_0);
        SaplingGrowth growth = new SaplingGrowth(8L);
        growth.registerPlanted(8, Chunk.MAX_Y - 1, 8, SaplingSpecies.OAK);
        for (int i = 0; i < 4; i++) growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(BlockType.OAK_SAPLING_STAGE_3, world.getBlock(8, Chunk.MAX_Y - 1, 8), "must defer, not overflow world height");
    }

    // --- Chunk load/unload (persistence-adjacent) ---

    @Test
    void unregisteringAChunkPausesItsSaplingsAndRegisteringAgainResumesThem() {
        World world = buildFlatWorld(9L);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_1);
        SaplingGrowth growth = new SaplingGrowth(9L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK); // simulates it already being mid-growth

        growth.unregisterChunk(0, 0);
        assertEquals(0, growth.activeCount());
        growth.update(world, FAR_PAST_ANY_DELAY); // must be a no-op - nothing tracked
        assertEquals(BlockType.OAK_SAPLING_STAGE_1, world.getBlock(8, 71, 8), "must not advance while its chunk is unloaded");

        Chunk chunk = world.getChunk(0, 0);
        growth.registerChunk(chunk); // simulates the chunk reloading
        assertEquals(1, growth.activeCount(), "reload must rediscover the sapling from its own persisted block ID");
        growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(BlockType.OAK_SAPLING_STAGE_2, world.getBlock(8, 71, 8), "growth resumes correctly from its persisted stage after reload");
    }

    @Test
    void registerChunkNeverDuplicatesAnAlreadyTrackedSapling() {
        World world = buildFlatWorld(10L);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_0);
        SaplingGrowth growth = new SaplingGrowth(10L);
        Chunk chunk = world.getChunk(0, 0);
        growth.registerChunk(chunk);
        growth.registerChunk(chunk); // called twice, as a real reload-of-an-already-loaded-chunk edge case might
        assertEquals(1, growth.activeCount(), "re-scanning the same chunk must not create a duplicate entry");
    }

    @Test
    void aBrokenSaplingBlockStopsBeingTrackedOnItsNextTick() {
        World world = buildFlatWorld(11L);
        world.setBlock(8, 71, 8, BlockType.OAK_SAPLING_STAGE_1);
        SaplingGrowth growth = new SaplingGrowth(11L);
        growth.registerPlanted(8, 71, 8, SaplingSpecies.OAK);
        world.setBlock(8, 71, 8, BlockType.AIR); // player broke it before its next tick

        growth.update(world, FAR_PAST_ANY_DELAY);
        assertEquals(0, growth.activeCount(), "a since-broken sapling must stop being tracked, not error or resurrect it");
    }
}
