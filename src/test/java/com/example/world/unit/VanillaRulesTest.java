package com.example.world.unit;

import com.example.inventory.ItemStack;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected values come from docs/vanilla-rules.md (each cell is quoted from a minecraft.wiki block page), not from
 * the implementation: break times are the wiki's "breaking time (sec)" cells, so they also pin the whole-tick rounding.
 */
class VanillaRulesTest {
    private static void time(float expected, BlockType tool, BlockType block) {
        assertEquals(expected, ToolEffectiveness.breakSeconds(tool, block), 1e-4f, block + " with " + tool);
    }

    @Test
    void breakTimesMatchTheWikiTables() {
        time(10f, null, BlockType.COBBLESTONE);
        time(1.5f, BlockType.WOODEN_PICKAXE, BlockType.COBBLESTONE);
        time(0.75f, BlockType.STONE_PICKAXE, BlockType.COBBLESTONE);
        time(0.5f, BlockType.IRON_PICKAXE, BlockType.COBBLESTONE);
        time(0.4f, BlockType.DIAMOND_PICKAXE, BlockType.COBBLESTONE);
        time(0.75f, null, BlockType.DIRT);
        time(0.9f, null, BlockType.GRASS_BLOCK);
        time(0.15f, BlockType.IRON_SHOVEL, BlockType.GRASS_BLOCK);
        time(3f, null, BlockType.OAK_LOG);
        time(0.5f, BlockType.IRON_AXE, BlockType.OAK_LOG);
        time(1.15f, BlockType.STONE_PICKAXE, BlockType.IRON_ORE);
        time(0.75f, BlockType.IRON_PICKAXE, BlockType.IRON_ORE);
        time(0.6f, BlockType.DIAMOND_PICKAXE, BlockType.IRON_ORE);
        time(0.75f, BlockType.IRON_PICKAXE, BlockType.DIAMOND_ORE);
        time(3.75f, BlockType.STONE_PICKAXE, BlockType.DIAMOND_ORE); // cannot harvest: x5 base
        time(9.4f, BlockType.DIAMOND_PICKAXE, BlockType.OBSIDIAN);
        time(1.35f, BlockType.STONE_PICKAXE, BlockType.FURNACE);
        time(3.75f, null, BlockType.CRAFTING_TABLE);
        time(1.5f, null, BlockType.PUMPKIN);
        time(0.25f, BlockType.IRON_AXE, BlockType.PUMPKIN);
        time(0.45f, null, BlockType.GLASS);
    }

    @Test
    void blocksThatBreakInUnderOneTickAreInstant() {
        time(0f, null, BlockType.OAK_SAPLING_STAGE_0);
        time(0f, BlockType.IRON_SHOVEL, BlockType.SNOW_BLOCK); // 0.2 x 1.5 / 6 = 0.05 s
        time(0.3f, null, BlockType.OAK_LEAVES);
    }

    @Test
    void bedrockStaysUnbreakable() {
        time(-1f, BlockType.DIAMOND_PICKAXE, BlockType.BEDROCK);
    }

    @Test
    void eggsAndSnowballsStackTo16OthersTo64ToolsTo1() {
        assertEquals(16, ItemStack.maxStackFor(BlockType.EGG, 64));
        assertEquals(16, ItemStack.maxStackFor(BlockType.SNOWBALL, 64));
        assertEquals(64, ItemStack.maxStackFor(BlockType.APPLE, 64));
        assertEquals(64, ItemStack.maxStackFor(BlockType.COBBLESTONE, 64));
        assertEquals(1, ItemStack.maxStackFor(BlockType.WOODEN_SWORD, 64));
        assertEquals(1, ItemStack.maxStackFor(BlockType.DIAMOND_CHESTPLATE, 64));
        assertEquals(8, ItemStack.maxStackFor(BlockType.EGG, 8), "never above the inventory's own cap");
    }

    private static List<BlockDrops.Resolved> mine(BlockType ore, BlockType tool, long seed) {
        return BlockDrops.resolve(ore, new Random(seed), tool);
    }

    @Test
    void oresDropRawMaterialsLikeVanilla() {
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.RAW_IRON, 1)), mine(BlockType.IRON_ORE, BlockType.STONE_PICKAXE, 1));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.RAW_GOLD, 1)), mine(BlockType.GOLD_ORE, BlockType.IRON_PICKAXE, 1));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COAL, 1)), mine(BlockType.COAL_ORE, BlockType.WOODEN_PICKAXE, 1));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIAMOND, 1)), mine(BlockType.DIAMOND_ORE, BlockType.IRON_PICKAXE, 1));
        for (long seed = 0; seed < 50; seed++) {
            List<BlockDrops.Resolved> copper = mine(BlockType.COPPER_ORE, BlockType.STONE_PICKAXE, seed);
            assertEquals(1, copper.size());
            assertEquals(BlockType.RAW_COPPER, copper.get(0).item());
            assertTrue(copper.get(0).count() >= 2 && copper.get(0).count() <= 5, "copper ore drops 2-5 raw copper");
        }
    }

    @Test
    void orePickaxeTiersStillGateTheDrops() {
        assertEquals(List.of(), mine(BlockType.IRON_ORE, BlockType.WOODEN_PICKAXE, 1));
        assertEquals(List.of(), mine(BlockType.GOLD_ORE, BlockType.STONE_PICKAXE, 1));
        assertEquals(List.of(), mine(BlockType.OBSIDIAN, BlockType.IRON_PICKAXE, 1));
    }
}
