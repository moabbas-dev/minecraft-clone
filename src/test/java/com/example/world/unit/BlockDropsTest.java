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
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COBBLESTONE, 1)), roll(BlockType.STONE));
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
        assertEquals(BlockDrops.resolve(BlockType.STONE, new Random(1), null),
                BlockDrops.resolve(BlockType.STONE, new Random(1), BlockType.WOODEN_PICKAXE));
    }
}
