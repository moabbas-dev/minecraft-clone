package com.example.inventory;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recipes from docs/vanilla-rules.md section 2. */
class RecipesVanillaTest {
    private static ItemStack craft3(BlockType... cells) {
        return Recipes.craft(cells, 3, 3);
    }

    private static BlockType[] fill(int n, BlockType t) {
        BlockType[] a = new BlockType[n];
        Arrays.fill(a, t);
        return a;
    }

    @Test
    void nineItemsMakeAStorageBlockAndOneBlockUnpacksToNine() {
        assertEquals(new ItemStack(BlockType.IRON_BLOCK, 1), craft3(fill(9, BlockType.IRON_INGOT)));
        assertEquals(new ItemStack(BlockType.GOLD_BLOCK, 1), craft3(fill(9, BlockType.GOLD_INGOT)));
        assertEquals(new ItemStack(BlockType.DIAMOND_BLOCK, 1), craft3(fill(9, BlockType.DIAMOND)));
        BlockType[] one = new BlockType[9];
        one[4] = BlockType.IRON_BLOCK;
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 9), craft3(one));
        BlockType[] gem = new BlockType[4];
        gem[2] = BlockType.DIAMOND_BLOCK;
        assertEquals(new ItemStack(BlockType.DIAMOND, 9), Recipes.craft(gem, 2, 2), "unpacking works in the 2x2 grid too");
    }

    @Test
    void eightIngotsAreNotEnoughForABlock() {
        BlockType[] cells = fill(9, BlockType.IRON_INGOT);
        cells[8] = null;
        assertTrue(craft3(cells).isEmpty());
        assertTrue(Recipes.craft(fill(4, BlockType.IRON_INGOT), 2, 2).isEmpty(), "a block needs the 3x3 table");
    }

    @Test
    void fourOfAMaterialMakeItsBlockInAnyGridSize() {
        assertEquals(new ItemStack(BlockType.BRICKS, 1), Recipes.craft(fill(4, BlockType.BRICK), 2, 2));
        assertEquals(new ItemStack(BlockType.SNOW_BLOCK, 1), Recipes.craft(fill(4, BlockType.SNOWBALL), 2, 2));
        assertEquals(new ItemStack(BlockType.CLAY, 1), Recipes.craft(fill(4, BlockType.CLAY_BALL), 2, 2));
        assertEquals(new ItemStack(BlockType.GLOWSTONE, 1), Recipes.craft(fill(4, BlockType.GLOWSTONE_DUST), 2, 2));
        BlockType[] in3 = new BlockType[9];
        in3[4] = in3[5] = in3[7] = in3[8] = BlockType.BRICK;
        assertEquals(new ItemStack(BlockType.BRICKS, 1), craft3(in3), "the 2x2 shape fits anywhere in the table");
    }

    @Test
    void tntNeedsGunpowderOnTheCornersAndCentreWithSandBetween() {
        BlockType g = BlockType.GUNPOWDER, s = BlockType.SAND;
        assertEquals(new ItemStack(BlockType.TNT, 1), craft3(g, s, g, s, g, s, g, s, g));
        assertEquals(new ItemStack(BlockType.TNT, 1), craft3(g, BlockType.RED_SAND, g, s, g, BlockType.RED_SAND, g, s, g));
        assertTrue(craft3(s, g, s, g, s, g, s, g, s).isEmpty());
    }

    @Test
    void existingRecipesStillWork() {
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), Recipes.craft(new BlockType[]{BlockType.OAK_LOG}, 1, 1));
        assertEquals(new ItemStack(BlockType.STICK, 4),
                Recipes.craft(new BlockType[]{BlockType.OAK_PLANKS, BlockType.OAK_PLANKS}, 1, 2));
    }
}
