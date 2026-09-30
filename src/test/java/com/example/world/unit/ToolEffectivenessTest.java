package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolEffectivenessTest {

    @Test
    void everyToolReportsItsOwnKindAndTier() {
        assertEquals(ToolEffectiveness.Kind.PICKAXE, ToolEffectiveness.kindOf(BlockType.WOODEN_PICKAXE));
        assertEquals(ToolEffectiveness.Kind.PICKAXE, ToolEffectiveness.kindOf(BlockType.STONE_PICKAXE));
        assertEquals(ToolEffectiveness.Kind.PICKAXE, ToolEffectiveness.kindOf(BlockType.IRON_PICKAXE));
        assertEquals(ToolEffectiveness.Kind.AXE, ToolEffectiveness.kindOf(BlockType.WOODEN_AXE));
        assertEquals(ToolEffectiveness.Kind.SHOVEL, ToolEffectiveness.kindOf(BlockType.STONE_SHOVEL));
        assertEquals(ToolEffectiveness.Tier.WOOD, ToolEffectiveness.tierOf(BlockType.WOODEN_AXE));
        assertEquals(ToolEffectiveness.Tier.STONE, ToolEffectiveness.tierOf(BlockType.STONE_SHOVEL));
        assertEquals(ToolEffectiveness.Tier.IRON, ToolEffectiveness.tierOf(BlockType.IRON_PICKAXE));
    }

    @Test
    void nonToolsAreNeverToolsAndHaveNoKindOrTier() {
        for (BlockType b : new BlockType[]{BlockType.STONE, BlockType.STICK, BlockType.AIR, BlockType.OAK_LOG}) {
            assertFalse(ToolEffectiveness.isTool(b), b + " is not a tool");
            assertNull(ToolEffectiveness.kindOf(b));
            assertNull(ToolEffectiveness.tierOf(b));
        }
        assertFalse(ToolEffectiveness.isTool(null));
    }

    @Test
    void everyTierIsStrictlyFasterThanTheOneBelowIt() {
        assertTrue(ToolEffectiveness.Tier.STONE.speedMultiplier > ToolEffectiveness.Tier.WOOD.speedMultiplier);
        assertTrue(ToolEffectiveness.Tier.IRON.speedMultiplier > ToolEffectiveness.Tier.STONE.speedMultiplier);
        assertTrue(ToolEffectiveness.Tier.STONE.harvestLevel > ToolEffectiveness.Tier.WOOD.harvestLevel);
        assertTrue(ToolEffectiveness.Tier.IRON.harvestLevel > ToolEffectiveness.Tier.STONE.harvestLevel);
    }

    @Test
    void theCorrectToolKindSpeedsUpItsOwnBlockCategory() {
        assertTrue(ToolEffectiveness.speedMultiplier(BlockType.IRON_PICKAXE, BlockType.STONE) > 1f);
        assertTrue(ToolEffectiveness.speedMultiplier(BlockType.STONE_AXE, BlockType.OAK_LOG) > 1f);
        assertTrue(ToolEffectiveness.speedMultiplier(BlockType.WOODEN_SHOVEL, BlockType.DIRT) > 1f);
        // A higher tier of the same correct kind is always faster, never slower or equal.
        assertTrue(ToolEffectiveness.speedMultiplier(BlockType.STONE_PICKAXE, BlockType.STONE)
                > ToolEffectiveness.speedMultiplier(BlockType.WOODEN_PICKAXE, BlockType.STONE));
    }

    @Test
    void theWrongToolOrNoToolNeverSpeedsUpAnything() {
        assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.WOODEN_AXE, BlockType.STONE), "an axe doesn't speed up stone");
        assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.WOODEN_SHOVEL, BlockType.OAK_LOG), "a shovel doesn't speed up wood");
        assertEquals(1f, ToolEffectiveness.speedMultiplier(null, BlockType.STONE), "an empty hand never speeds anything up");
        assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.DIRT, BlockType.STONE), "holding a plain block isn't a tool");
        assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.IRON_PICKAXE, BlockType.DIRT), "a pickaxe doesn't speed up dirt");
    }

    @Test
    void gatedOresRequireAtLeastTheirMinimumTierToDropAnything() {
        assertTrue(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, BlockType.COAL_ORE), "coal only needs any pickaxe");
        assertFalse(ToolEffectiveness.canHarvest(null, BlockType.COAL_ORE), "bare hand can't even get coal");

        assertFalse(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, BlockType.IRON_ORE), "wood is below iron ore's minimum");
        assertTrue(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, BlockType.IRON_ORE));
        assertTrue(ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, BlockType.IRON_ORE));

        assertFalse(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, BlockType.DIAMOND_ORE), "stone is below diamond ore's minimum");
        assertTrue(ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, BlockType.DIAMOND_ORE));

        assertFalse(ToolEffectiveness.canHarvest(BlockType.IRON_AXE, BlockType.DIAMOND_ORE), "the wrong tool kind never harvests a gated ore, whatever its tier");
    }

    @Test
    void ungatedBlocksAreAlwaysHarvestableToolOrNot() {
        for (BlockType b : new BlockType[]{BlockType.STONE, BlockType.DIRT, BlockType.OAK_LOG, BlockType.SAND}) {
            assertTrue(ToolEffectiveness.canHarvest(null, b), b + " needs no tool at all");
            assertTrue(ToolEffectiveness.canHarvest(BlockType.WOODEN_AXE, b), b + " is harvestable with any (even wrong) tool");
        }
    }

    @Test
    void nullBlockIsNeverGatedOrSpedUp() {
        assertTrue(ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, null));
        assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.IRON_PICKAXE, null));
    }
}
