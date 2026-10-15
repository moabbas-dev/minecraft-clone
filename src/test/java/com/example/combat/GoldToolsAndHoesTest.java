package com.example.combat;

import com.example.inventory.Recipes;
import com.example.world.BlockType;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gold tools and the hoes: numbers read from Items / ToolMaterial in the 26.3 client (damage = 1 + tier bonus + tool value, speed = 4 + tool value). */
class GoldToolsAndHoesTest {
    private static BlockType[] grid(int w, int h, Object... cells) {
        BlockType[] g = new BlockType[w * h];
        for (int i = 0; i < cells.length; i += 2) g[(Integer) cells[i]] = (BlockType) cells[i + 1];
        return g;
    }

    @Test
    void goldToolsHaveVanillaAttackStatsAndThirtyTwoUses() {
        assertEquals(4f, WeaponStats.damage(BlockType.GOLD_SWORD));
        assertEquals(1.6f, WeaponStats.attackSpeed(BlockType.GOLD_SWORD));
        assertEquals(7f, WeaponStats.damage(BlockType.GOLD_AXE));
        assertEquals(1.0f, WeaponStats.attackSpeed(BlockType.GOLD_AXE));
        assertEquals(2f, WeaponStats.damage(BlockType.GOLD_PICKAXE));
        assertEquals(2.5f, WeaponStats.damage(BlockType.GOLD_SHOVEL));
        for (BlockType t : new BlockType[]{BlockType.GOLD_SWORD, BlockType.GOLD_AXE, BlockType.GOLD_PICKAXE, BlockType.GOLD_SHOVEL, BlockType.GOLD_HOE}) {
            assertEquals(32, Durability.max(t), t.name());
        }
        assertTrue(WeaponStats.isSword(BlockType.GOLD_SWORD));
    }

    @Test
    void everyHoeDealsOneDamageAndSwingsFasterWithBetterMaterial() {
        BlockType[] hoes = {BlockType.WOODEN_HOE, BlockType.STONE_HOE, BlockType.IRON_HOE, BlockType.GOLD_HOE, BlockType.DIAMOND_HOE};
        float[] speed = {1f, 2f, 3f, 1f, 4f};
        int[] uses = {59, 131, 250, 32, 1561};
        for (int i = 0; i < hoes.length; i++) {
            assertEquals(1f, WeaponStats.damage(hoes[i]), hoes[i].name());
            assertEquals(speed[i], WeaponStats.attackSpeed(hoes[i]), hoes[i].name());
            assertEquals(uses[i], Durability.max(hoes[i]), hoes[i].name());
            assertEquals(ToolEffectiveness.Kind.HOE, ToolEffectiveness.kindOf(hoes[i]));
        }
    }

    @Test
    void aGoldPickaxeMinesStoneTwelveTimesFasterButCannotHarvestIron() {
        float hand = ToolEffectiveness.rawBreakSeconds(null, BlockType.STONE);
        float gold = ToolEffectiveness.rawBreakSeconds(BlockType.GOLD_PICKAXE, BlockType.STONE);
        float wood = ToolEffectiveness.rawBreakSeconds(BlockType.WOODEN_PICKAXE, BlockType.STONE);
        assertTrue(gold < wood, "x12 against x2");
        assertEquals(wood * 2f / 12f, gold, 1e-3f * wood);
        assertTrue(hand > gold);
        assertTrue(ToolEffectiveness.canHarvest(BlockType.GOLD_PICKAXE, BlockType.STONE), "mining level of wood");
        assertFalse(ToolEffectiveness.canHarvest(BlockType.GOLD_PICKAXE, BlockType.IRON_ORE), "gold has mining level 0, so no iron");
        assertTrue(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, BlockType.IRON_ORE));
    }

    @Test
    void aHoeSpeedsUpNoBlockThatExists() {
        for (BlockType b : new BlockType[]{BlockType.DIRT, BlockType.STONE, BlockType.OAK_LOG, BlockType.SAND}) {
            assertEquals(1f, ToolEffectiveness.speedMultiplier(BlockType.DIAMOND_HOE, b), b.name());
        }
    }

    @Test
    void goldToolAndHoeRecipes() {
        BlockType G = BlockType.GOLD_INGOT, S = BlockType.STICK, P = BlockType.OAK_PLANKS, C = BlockType.COBBLESTONE;
        assertEquals(BlockType.GOLD_PICKAXE, Recipes.craft(grid(3, 3, 0, G, 1, G, 2, G, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.GOLD_SWORD, Recipes.craft(grid(3, 3, 1, G, 4, G, 7, S), 3, 3).type());
        assertEquals(BlockType.GOLD_SHOVEL, Recipes.craft(grid(3, 3, 1, G, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.GOLD_AXE, Recipes.craft(grid(3, 3, 0, G, 1, G, 3, G, 4, S, 7, S), 3, 3).type());
        // hoes, both ways round
        assertEquals(BlockType.GOLD_HOE, Recipes.craft(grid(3, 3, 0, G, 1, G, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.GOLD_HOE, Recipes.craft(grid(3, 3, 0, G, 1, G, 3, S, 6, S), 3, 3).type());
        assertEquals(BlockType.WOODEN_HOE, Recipes.craft(grid(3, 3, 0, P, 1, P, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.STONE_HOE, Recipes.craft(grid(3, 3, 0, C, 1, C, 3, S, 6, S), 3, 3).type());
        assertEquals(BlockType.IRON_HOE, Recipes.craft(grid(3, 3, 0, BlockType.IRON_INGOT, 1, BlockType.IRON_INGOT, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.DIAMOND_HOE, Recipes.craft(grid(3, 3, 0, BlockType.DIAMOND, 1, BlockType.DIAMOND, 4, S, 7, S), 3, 3).type());
    }
}
