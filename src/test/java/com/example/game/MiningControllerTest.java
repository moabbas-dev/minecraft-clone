package com.example.game;

import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.Raycast;
import com.example.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MiningControllerTest {
    private static final float DT = 1f / 60f;

    private World world;
    private MiningController mining;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        mining = new MiningController(new Random(1));
    }

    private Raycast.Hit hit(int x, int y, int z) {
        return new Raycast.Hit(x, y, z, x, y + 1, z, world.getBlock(x, y, z));
    }

    /** Ticks until a break happens or maxTicks pass; returns the breaking result or null. */
    private MiningController.Result mineUntilBroken(Raycast.Hit h, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            MiningController.Result r = mining.tick(world, h, true, DT);
            if (r.broken()) return r;
        }
        return null;
    }

    @Test
    void theBlockSurvivesUntilTheTimerCompletes() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        float required = BlockType.STONE.handBreakSeconds();
        int ticksBefore = (int) (required / DT) - 3;
        for (int i = 0; i < ticksBefore; i++) {
            assertFalse(mining.tick(world, hit(5, 10, 5), true, DT).broken());
        }
        assertEquals(BlockType.STONE, world.getBlock(5, 10, 5), "block must not be removed before the timer completes");
    }

    @Test
    void completionRemovesTheBlockAndProducesExactlyOneBreakWithItsDrops() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        MiningController.Result r = mineUntilBroken(hit(5, 10, 5), 600);
        assertNotNull(r);
        assertEquals(BlockType.AIR, world.getBlock(5, 10, 5));
        assertEquals(BlockType.STONE, r.brokenType());
        assertEquals(1, r.drops().size());
        assertEquals(BlockType.COBBLESTONE, r.drops().get(0).item());
        assertEquals(1, r.drops().get(0).count());
        // The block is gone, so continuing to hold the button on that spot must never break/drop again.
        for (int i = 0; i < 120; i++) assertFalse(mining.tick(world, hit(5, 10, 5), true, DT).broken());
    }

    @Test
    void cancellingByReleasingTheButtonProducesNoDropAndKeepsTheBlock() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        for (int i = 0; i < 20; i++) mining.tick(world, hit(5, 10, 5), true, DT);
        assertTrue(mining.isMining());
        assertFalse(mining.tick(world, hit(5, 10, 5), false, DT).broken());
        assertFalse(mining.isMining());
        assertEquals(-1, mining.crackStage());
        assertEquals(BlockType.STONE, world.getBlock(5, 10, 5));
    }

    @Test
    void lookingAwayCancelsAndResetsProgress() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        for (int i = 0; i < 20; i++) mining.tick(world, hit(5, 10, 5), true, DT);
        mining.tick(world, null, true, DT);
        assertFalse(mining.isMining());
        assertEquals(-1, mining.crackStage());
    }

    @Test
    void switchingTargetsRestartsProgressAndNeverBreaksTheOldBlock() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        world.setBlock(6, 10, 5, BlockType.STONE);
        int nearlyDone = (int) (BlockType.STONE.handBreakSeconds() / DT) - 5;
        for (int i = 0; i < nearlyDone; i++) mining.tick(world, hit(5, 10, 5), true, DT);
        int stageBefore = mining.crackStage();
        assertTrue(stageBefore >= MiningController.CRACK_STAGES - 2, "sanity: nearly finished, stage=" + stageBefore);

        MiningController.Result r = mining.tick(world, hit(6, 10, 5), true, DT);
        assertFalse(r.broken());
        assertEquals(0, mining.crackStage(), "a new target starts at the first crack stage");
        assertEquals(BlockType.STONE, world.getBlock(5, 10, 5), "the old block must be untouched");
        assertEquals(BlockType.STONE, world.getBlock(6, 10, 5));
    }

    @Test
    void aBlockChangedByAnotherSystemMidBreakRestartsInsteadOfUsingTheOldTimer() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        int nearlyDone = (int) (BlockType.STONE.handBreakSeconds() / DT) - 5;
        for (int i = 0; i < nearlyDone; i++) mining.tick(world, hit(5, 10, 5), true, DT);

        world.setBlock(5, 10, 5, BlockType.OBSIDIAN); // something else swapped the block
        MiningController.Result r = mining.tick(world, hit(5, 10, 5), true, DT);
        assertFalse(r.broken(), "must not inherit stone's nearly-finished timer for obsidian");
        assertEquals(0, mining.crackStage());
    }

    @Test
    void aTargetThatVanishesMidBreakProducesNothing() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        for (int i = 0; i < 10; i++) mining.tick(world, hit(5, 10, 5), true, DT);
        world.setBlock(5, 10, 5, BlockType.AIR);
        MiningController.Result r = mining.tick(world, hit(5, 10, 5), true, DT);
        assertFalse(r.broken());
        assertTrue(r.drops().isEmpty());
        assertFalse(mining.isMining());
    }

    @Test
    void unbreakableBlocksNeverProgressBreakOrDrop() {
        world.setBlock(5, 10, 5, BlockType.BEDROCK);
        for (int i = 0; i < 2000; i++) {
            MiningController.Result r = mining.tick(world, hit(5, 10, 5), true, DT);
            assertFalse(r.mining());
            assertFalse(r.broken());
        }
        assertEquals(BlockType.BEDROCK, world.getBlock(5, 10, 5));
        assertEquals(-1, mining.crackStage());
    }

    @Test
    void aBlockWithNoDropStillBreaksButDropsNothing() {
        world.setBlock(5, 10, 5, BlockType.OAK_LEAVES);
        MiningController.Result r = mineUntilBroken(hit(5, 10, 5), 600);
        assertNotNull(r);
        assertTrue(r.drops().isEmpty());
        assertEquals(BlockType.AIR, world.getBlock(5, 10, 5));
    }

    @Test
    void crackStageAdvancesInOrderFromZeroToTheLastStage() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        int last = -1;
        int maxStage = -1;
        for (int i = 0; i < 600; i++) {
            MiningController.Result r = mining.tick(world, hit(5, 10, 5), true, DT);
            if (r.broken()) break;
            int stage = mining.crackStage();
            assertTrue(stage >= last, "crack stage must never go backwards: " + last + " -> " + stage);
            assertTrue(stage >= 0 && stage < MiningController.CRACK_STAGES);
            last = stage;
            maxStage = Math.max(maxStage, stage);
        }
        assertEquals(MiningController.CRACK_STAGES - 1, maxStage, "must reach the final stage before breaking");
        assertEquals(-1, mining.crackStage(), "crack disappears once the block is broken");
    }

    @Test
    void aHeldButtonCannotChainBreakBlocksFasterThanTheCooldownAllows() {
        world.setBlock(5, 10, 5, BlockType.OAK_SAPLING_STAGE_0); // instant-break blocks
        world.setBlock(6, 10, 5, BlockType.OAK_SAPLING_STAGE_0);
        assertTrue(mining.tick(world, hit(5, 10, 5), true, DT).broken());
        assertFalse(mining.tick(world, hit(6, 10, 5), true, DT).broken(), "cooldown must block an immediate second break");
        boolean brokeLater = false;
        for (int i = 0; i < 30; i++) brokeLater |= mining.tick(world, hit(6, 10, 5), true, DT).broken();
        assertTrue(brokeLater, "the second block must break once the cooldown has passed");
    }

    @Test
    void unavailableChunkTargetIsTreatedAsAirNotACrash() {
        Raycast.Hit far = new Raycast.Hit(9999, 10, 9999, 9999, 11, 9999, BlockType.STONE);
        assertDoesNotThrow(() -> mining.tick(world, far, true, DT));
        assertFalse(mining.isMining());
    }

    @Test
    void theCorrectToolBreaksStrictlyFasterThanABareHand() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        world.setBlock(6, 10, 5, BlockType.STONE);
        int handTicks = 0;
        while (!mining.tick(world, hit(5, 10, 5), true, DT).broken()) handTicks++;
        MiningController pickaxeMining = new MiningController(new Random(1));
        int pickaxeTicks = 0;
        while (!pickaxeMining.tick(world, hit(6, 10, 5), true, DT, BlockType.STONE_PICKAXE).broken()) pickaxeTicks++;
        assertTrue(pickaxeTicks < handTicks, "a stone pickaxe must break stone faster than a bare hand");
    }

    @Test
    void theWrongToolBreaksAtTheSameSpeedAsABareHand() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        world.setBlock(6, 10, 5, BlockType.STONE);
        int handTicks = 0;
        while (!mining.tick(world, hit(5, 10, 5), true, DT).broken()) handTicks++;
        MiningController axeMining = new MiningController(new Random(1));
        int axeTicks = 0;
        while (!axeMining.tick(world, hit(6, 10, 5), true, DT, BlockType.WOODEN_AXE).broken()) axeTicks++;
        assertEquals(handTicks, axeTicks, "an axe is the wrong tool for stone, so it's no faster than a bare hand");
    }

    @Test
    void aGatedOreProducesNoDropWithoutTheRightTierEvenThoughItStillBreaks() {
        world.setBlock(5, 10, 5, BlockType.DIAMOND_ORE);
        MiningController.Result r = null;
        for (int i = 0; i < 2000 && r == null; i++) {
            MiningController.Result tick = mining.tick(world, hit(5, 10, 5), true, DT, BlockType.STONE_PICKAXE);
            if (tick.broken()) r = tick;
        }
        assertNotNull(r, "a stone pickaxe can still break diamond ore (just not obtain it)");
        assertEquals(BlockType.AIR, world.getBlock(5, 10, 5));
        assertTrue(r.drops().isEmpty(), "stone tier is below diamond ore's minimum harvest level");
    }

    @Test
    void aGatedOreDropsNormallyWithTheRequiredTier() {
        world.setBlock(5, 10, 5, BlockType.DIAMOND_ORE);
        MiningController.Result r = null;
        for (int i = 0; i < 2000 && r == null; i++) {
            MiningController.Result tick = mining.tick(world, hit(5, 10, 5), true, DT, BlockType.IRON_PICKAXE);
            if (tick.broken()) r = tick;
        }
        assertNotNull(r);
        assertEquals(1, r.drops().size());
        assertEquals(BlockType.DIAMOND, r.drops().get(0).item());
    }

    @Test
    void theFourArgOverloadIsExactlyAnEmptyHand() {
        world.setBlock(5, 10, 5, BlockType.STONE);
        world.setBlock(6, 10, 5, BlockType.STONE);
        MiningController a = new MiningController(new Random(7));
        MiningController b = new MiningController(new Random(7));
        for (int i = 0; i < 5; i++) {
            MiningController.Result ra = a.tick(world, hit(5, 10, 5), true, DT);
            MiningController.Result rb = b.tick(world, hit(6, 10, 5), true, DT, null);
            assertEquals(ra.broken(), rb.broken());
        }
    }
}
