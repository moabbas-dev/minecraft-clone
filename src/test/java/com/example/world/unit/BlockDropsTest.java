package com.example.world.unit;

import com.example.world.BlockDrops;
import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BlockDropsTest {

    private static List<BlockDrops.Resolved> roll(BlockType type) {
        return BlockDrops.resolve(type, new Random(1));
    }

    @Test
    void anOrdinaryBlockDropsItself() {
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIRT, 1)), roll(BlockType.DIRT));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.OAK_LOG, 1)), roll(BlockType.OAK_LOG));
    }

    @Test
    void stoneDropsCobblestoneAndGrassDropsDirt() {
        assertEquals(List.of(), roll(BlockType.STONE), "vanilla: stone needs a pickaxe to drop anything");
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COBBLESTONE, 1)),
                BlockDrops.resolve(BlockType.STONE, new Random(1), BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIRT, 1)), roll(BlockType.GRASS_BLOCK));
    }

    @Test
    void leavesGlassAndIceDropNothing() {
        assertTrue(roll(BlockType.OAK_LEAVES).isEmpty());
        assertTrue(roll(BlockType.CHERRY_LEAVES).isEmpty());
        assertTrue(roll(BlockType.GLASS).isEmpty());
        assertTrue(roll(BlockType.ICE).isEmpty());
    }

    @Test
    void everySaplingStageDropsTheStageZeroItem() {
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            assertEquals(List.of(new BlockDrops.Resolved(BlockType.OAK_SAPLING_STAGE_0, 1)), roll(stage), stage.name());
        }
    }

    @Test
    void airAndUnbreakableBlocksNeverDrop() {
        assertTrue(roll(BlockType.AIR).isEmpty());
        assertTrue(roll(BlockType.BEDROCK).isEmpty());
        assertTrue(roll(BlockType.WATER).isEmpty());
        assertTrue(BlockDrops.resolve(null, new Random(1)).isEmpty(), "an invalid/null block must not throw or drop");
    }

    @Test
    void dropQuantityRangesResolveWithinBoundsAndFixedQuantitiesAreExact() {
        assertEquals(3, new BlockDrops.Drop(BlockType.DIRT, 3, 3).min());
        BlockDrops.Drop range = new BlockDrops.Drop(BlockType.DIRT, 2, 4);
        assertTrue(range.min() <= range.max());
        assertThrows(IllegalArgumentException.class, () -> new BlockDrops.Drop(BlockType.DIRT, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> new BlockDrops.Drop(BlockType.DIRT, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new BlockDrops.Drop(BlockType.AIR, 1, 1));
    }

    @Test
    void everyDropIsARealItemWithAPositiveCount() {
        // Not "every drop must be placeable" (that stopped being true the moment a real block -
        // COAL_ORE - started dropping a real non-placeable item, COAL, exactly like vanilla's
        // own raw-material drops) - just the invariant that actually matters: a resolved drop is
        // never AIR and never a non-positive count.
        for (BlockType type : BlockType.values()) {
            for (BlockDrops.Resolved r : roll(type)) {
                assertNotEquals(BlockType.AIR, r.item(), type + " must never drop AIR");
                assertTrue(r.count() > 0);
            }
        }
    }

    @Test
    void coalOreDropsTheCoalItemNotTheOreBlock() {
        // Coal ore is harvest-gated (any pickaxe - see ToolEffectivenessTest), so a bare-hand
        // roll is correctly empty; a pickaxe is what actually exercises the drop-table change.
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COAL, 1)),
                BlockDrops.resolve(BlockType.COAL_ORE, new Random(1), BlockType.WOODEN_PICKAXE));
    }

    @Test
    void resolveIsDeterministicForAGivenRandomAndNeverMutatesTheTable() {
        assertEquals(roll(BlockType.STONE), roll(BlockType.STONE));
        assertEquals(1, BlockDrops.dropsFor(BlockType.STONE).size());
    }

    @Test
    void theTwoArgOverloadIsExactlyABareHand() {
        // Coal is gated behind "any pickaxe" (see ToolEffectivenessTest) - the bare 2-arg
        // resolve() must apply that gating exactly like passing heldTool=null explicitly.
        assertTrue(BlockDrops.resolve(BlockType.COAL_ORE, new Random(1)).isEmpty());
        assertEquals(BlockDrops.resolve(BlockType.COAL_ORE, new Random(1)),
                BlockDrops.resolve(BlockType.COAL_ORE, new Random(1), null));
    }

    @Test
    void aGatedOreDropsNothingWithoutTheRightToolButDropsNormallyWithIt() {
        assertTrue(BlockDrops.resolve(BlockType.DIAMOND_ORE, new Random(1), BlockType.STONE_PICKAXE).isEmpty(),
                "stone is below diamond's minimum tier");
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIAMOND, 1)),
                BlockDrops.resolve(BlockType.DIAMOND_ORE, new Random(1), BlockType.IRON_PICKAXE));
    }

    @Test
    void anUngatedBlockDropsTheSameWayRegardlessOfTheHeldTool() {
        assertEquals(BlockDrops.resolve(BlockType.DIRT, new Random(1), null),
                BlockDrops.resolve(BlockType.DIRT, new Random(1), BlockType.WOODEN_PICKAXE));
    }

    @Test
    void leavesRollSaplingStickAndAppleIndependentlyAtVanillaChances() {
        Random r = new Random(5);
        int saplings = 0, sticks = 0, apples = 0, n = 200000;
        for (int i = 0; i < n; i++) {
            for (BlockDrops.Resolved d : BlockDrops.resolve(BlockType.OAK_LEAVES, r)) {
                if (d.item() == BlockType.OAK_SAPLING_STAGE_0) saplings++;
                if (d.item() == BlockType.STICK) sticks += d.count();
                if (d.item() == BlockType.APPLE) apples++;
            }
        }
        assertEquals(0.05, saplings / (double) n, 0.003);
        assertEquals(0.02 * 1.5, sticks / (double) n, 0.004, "2% x 1-2 sticks");
        assertEquals(0.005, apples / (double) n, 0.0012);
        for (BlockDrops.Resolved d : BlockDrops.resolve(BlockType.BIRCH_LEAVES, new Random(1))) assertNotEquals(BlockType.APPLE, d.item());
    }

    @Test
    void gravelDropsFlintTenPercentOfTheTimeElseItself() {
        Random r = new Random(9);
        int flint = 0, n = 50000;
        for (int i = 0; i < n; i++) {
            List<BlockDrops.Resolved> d = BlockDrops.resolve(BlockType.GRAVEL, r);
            assertEquals(1, d.size());
            if (d.get(0).item() == BlockType.FLINT) flint++;
            else assertEquals(BlockType.GRAVEL, d.get(0).item());
        }
        assertEquals(0.1, flint / (double) n, 0.01);
    }

    @Test
    void itemDropsMatchVanilla() {
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.CLAY_BALL, 4)), roll(BlockType.CLAY));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.BOOK, 3)), roll(BlockType.BOOKSHELF));
        assertEquals(List.of(), roll(BlockType.SNOW_BLOCK), "snowballs need a shovel");
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.SNOWBALL, 4)),
                BlockDrops.resolve(BlockType.SNOW_BLOCK, new Random(1), BlockType.WOODEN_SHOVEL));
        int dust = BlockDrops.resolve(BlockType.GLOWSTONE, new Random(2)).get(0).count();
        assertTrue(dust >= 2 && dust <= 4);
    }

    @Test
    void harvestTiersFollowVanilla() {
        assertTrue(com.example.world.ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, BlockType.GOLD_ORE));
        assertFalse(com.example.world.ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, BlockType.GOLD_ORE));
        assertFalse(com.example.world.ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, BlockType.OBSIDIAN));
        assertTrue(com.example.world.ToolEffectiveness.canHarvest(BlockType.DIAMOND_PICKAXE, BlockType.OBSIDIAN));
        assertEquals(7.5f, com.example.world.ToolEffectiveness.breakSeconds(null, BlockType.STONE), 1e-4f, "1.5 hardness x 5 by hand");
        // 1.5 x 1.5 / 2 = 1.125 s, rounded up to whole ticks (23 ticks = 1.15 s) like vanilla's own break-time tables
        assertEquals(1.15f, com.example.world.ToolEffectiveness.breakSeconds(BlockType.WOODEN_PICKAXE, BlockType.STONE), 1e-4f);
    }
}
