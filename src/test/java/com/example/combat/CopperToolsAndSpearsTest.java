package com.example.combat;

import com.example.inventory.Recipes;
import com.example.world.BlockType;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Copper tools and the spears: numbers read from Items, Item.Properties.spear, ToolMaterial and the recipe files of the 26.3 client
 * (copper = durability 190, speed 5, bonus 1; spear damage 1 + bonus, attack speed 1 / swing seconds).
 */
class CopperToolsAndSpearsTest {
    private static BlockType[] grid(int w, int h, Object... cells) {
        BlockType[] g = new BlockType[w * h];
        for (int i = 0; i < cells.length; i += 2) g[(Integer) cells[i]] = (BlockType) cells[i + 1];
        return g;
    }

    @Test
    void copperToolsHaveStoneAttackStatsAndOneHundredNinetyUses() {
        assertEquals(5f, WeaponStats.damage(BlockType.COPPER_SWORD));
        assertEquals(1.6f, WeaponStats.attackSpeed(BlockType.COPPER_SWORD));
        assertEquals(9f, WeaponStats.damage(BlockType.COPPER_AXE));
        assertEquals(0.8f, WeaponStats.attackSpeed(BlockType.COPPER_AXE));
        assertEquals(3f, WeaponStats.damage(BlockType.COPPER_PICKAXE));
        assertEquals(3.5f, WeaponStats.damage(BlockType.COPPER_SHOVEL));
        assertEquals(1f, WeaponStats.damage(BlockType.COPPER_HOE));
        assertEquals(2f, WeaponStats.attackSpeed(BlockType.COPPER_HOE));
        for (BlockType t : new BlockType[]{BlockType.COPPER_SWORD, BlockType.COPPER_AXE, BlockType.COPPER_PICKAXE, BlockType.COPPER_SHOVEL, BlockType.COPPER_HOE}) {
            assertEquals(190, Durability.max(t), t.name());
        }
        assertTrue(WeaponStats.isSword(BlockType.COPPER_SWORD));
    }

    @Test
    void aCopperPickaxeMinesAtSpeedFiveAndHarvestsIronOreButNotDiamond() {
        float copper = ToolEffectiveness.rawBreakSeconds(BlockType.COPPER_PICKAXE, BlockType.STONE);
        float stone = ToolEffectiveness.rawBreakSeconds(BlockType.STONE_PICKAXE, BlockType.STONE);
        assertEquals(BlockType.STONE.hardness * 1.5f / 5f, copper, 1e-4f);
        assertTrue(copper < stone, "5 against 4");
        assertTrue(ToolEffectiveness.canHarvest(BlockType.COPPER_PICKAXE, BlockType.IRON_ORE));
        assertFalse(ToolEffectiveness.canHarvest(BlockType.COPPER_PICKAXE, BlockType.DIAMOND_ORE));
    }

    @Test
    void spearsDealOnePlusTheMaterialBonusAndSwingAtOneOverTheirSwingTime() {
        BlockType[] spears = {BlockType.WOODEN_SPEAR, BlockType.STONE_SPEAR, BlockType.COPPER_SPEAR, BlockType.IRON_SPEAR, BlockType.GOLD_SPEAR, BlockType.DIAMOND_SPEAR};
        float[] damage = {1f, 2f, 2f, 3f, 1f, 4f};
        float[] swing = {0.65f, 0.75f, 0.85f, 0.95f, 0.95f, 1.05f};
        int[] uses = {59, 131, 190, 250, 32, 1561};
        for (int i = 0; i < spears.length; i++) {
            assertEquals(damage[i], WeaponStats.damage(spears[i]), spears[i].name());
            assertEquals(1f / swing[i], WeaponStats.attackSpeed(spears[i]), 1e-5f, spears[i].name());
            assertEquals(uses[i], Durability.max(spears[i]), spears[i].name());
            assertTrue(WeaponStats.isSpear(spears[i]));
            assertEquals(1, Durability.attackCost(spears[i]), "Weapon(1): one use per attack");
            assertEquals(0, Durability.blockBreakCost(spears[i], 1f), "a spear is not a digging tool");
            assertEquals(1f, ToolEffectiveness.speedMultiplier(spears[i], BlockType.STONE), "it mines at hand speed");
        }
    }

    @Test
    void copperToolAndSpearRecipes() {
        BlockType C = BlockType.COPPER_INGOT, S = BlockType.STICK;
        assertEquals(BlockType.COPPER_PICKAXE, Recipes.craft(grid(3, 3, 0, C, 1, C, 2, C, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.COPPER_SWORD, Recipes.craft(grid(3, 3, 1, C, 4, C, 7, S), 3, 3).type());
        assertEquals(BlockType.COPPER_SHOVEL, Recipes.craft(grid(3, 3, 1, C, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.COPPER_AXE, Recipes.craft(grid(3, 3, 0, C, 1, C, 3, C, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.COPPER_HOE, Recipes.craft(grid(3, 3, 0, C, 1, C, 4, S, 7, S), 3, 3).type());
        // a spear: the head in the top right corner, a stick in the middle and one in the bottom left corner (and the mirror image)
        assertEquals(BlockType.COPPER_SPEAR, Recipes.craft(grid(3, 3, 2, C, 4, S, 6, S), 3, 3).type());
        assertEquals(BlockType.COPPER_SPEAR, Recipes.craft(grid(3, 3, 0, C, 4, S, 8, S), 3, 3).type());
        assertEquals(BlockType.WOODEN_SPEAR, Recipes.craft(grid(3, 3, 2, BlockType.OAK_PLANKS, 4, S, 6, S), 3, 3).type());
        assertEquals(BlockType.STONE_SPEAR, Recipes.craft(grid(3, 3, 2, BlockType.COBBLESTONE, 4, S, 6, S), 3, 3).type());
        assertEquals(BlockType.IRON_SPEAR, Recipes.craft(grid(3, 3, 2, BlockType.IRON_INGOT, 4, S, 6, S), 3, 3).type());
        assertEquals(BlockType.GOLD_SPEAR, Recipes.craft(grid(3, 3, 2, BlockType.GOLD_INGOT, 4, S, 6, S), 3, 3).type());
        assertEquals(BlockType.DIAMOND_SPEAR, Recipes.craft(grid(3, 3, 2, BlockType.DIAMOND, 4, S, 6, S), 3, 3).type());
    }
}
