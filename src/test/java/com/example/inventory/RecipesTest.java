package com.example.inventory;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipesTest {
    private static final BlockType P = BlockType.OAK_PLANKS;
    private static final BlockType C = BlockType.COBBLESTONE;

    private static BlockType[] grid(int w, int h, Object... cellsByIndex) {
        BlockType[] g = new BlockType[w * h];
        for (int i = 0; i < cellsByIndex.length; i += 2) g[(Integer) cellsByIndex[i]] = (BlockType) cellsByIndex[i + 1];
        return g;
    }

    @Test
    void aLogCraftsFourPlanksFromAnyCellInAnyGridSize() {
        for (int cell = 0; cell < 4; cell++) {
            assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), Recipes.craft(grid(2, 2, cell, BlockType.OAK_LOG), 2, 2), "2x2 cell " + cell);
        }
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), Recipes.craft(grid(3, 3, 8, BlockType.BIRCH_LOG), 3, 3));
    }

    @Test
    void everyLogSpeciesWorks() {
        for (BlockType log : new BlockType[]{BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG, BlockType.JUNGLE_LOG,
                BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG, BlockType.CHERRY_LOG}) {
            assertEquals(BlockType.OAK_PLANKS, Recipes.craft(grid(2, 2, 0, log), 2, 2).type(), log.name());
        }
    }

    @Test
    void fourPlanksMakeACraftingTableAndItWorksAtAnyPositionInTheBigGrid() {
        assertEquals(new ItemStack(BlockType.CRAFTING_TABLE, 1), Recipes.craft(grid(2, 2, 0, P, 1, P, 2, P, 3, P), 2, 2));
        // The same 2x2 block of planks in the bottom-right corner of a 3x3 grid.
        assertEquals(BlockType.CRAFTING_TABLE, Recipes.craft(grid(3, 3, 4, P, 5, P, 7, P, 8, P), 3, 3).type());
    }

    @Test
    void aHollowRingOfCobblestoneMakesAFurnaceOnlyInTheThreeByThreeGrid() {
        BlockType[] ring = grid(3, 3, 0, C, 1, C, 2, C, 3, C, 5, C, 6, C, 7, C, 8, C);
        assertEquals(BlockType.FURNACE, Recipes.craft(ring, 3, 3).type());
        BlockType[] filled = ring.clone();
        filled[4] = C;
        assertTrue(Recipes.craft(filled, 3, 3).isEmpty(), "a full 3x3 of cobblestone is not the furnace pattern");
    }

    @Test
    void aRingOfPlanksMakesAChest() {
        BlockType[] ring = grid(3, 3, 0, P, 1, P, 2, P, 3, P, 5, P, 6, P, 7, P, 8, P);
        assertEquals(BlockType.CHEST, Recipes.craft(ring, 3, 3).type());
    }

    @Test
    void fourSandMakesSandstone() {
        assertEquals(BlockType.SANDSTONE, Recipes.craft(grid(2, 2, 0, BlockType.SAND, 1, BlockType.SAND, 2, BlockType.SAND, 3, BlockType.SAND), 2, 2).type());
    }

    @Test
    void anEmptyGridAndUnknownCombinationsCraftNothing() {
        assertTrue(Recipes.craft(new BlockType[4], 2, 2).isEmpty());
        assertTrue(Recipes.craft(grid(2, 2, 0, P, 1, C), 2, 2).isEmpty(), "mixed materials");
        assertTrue(Recipes.craft(grid(2, 2, 0, P, 1, P, 2, P), 2, 2).isEmpty(), "three planks is not a table");
        assertTrue(Recipes.craft(grid(2, 2, 0, BlockType.DIRT), 2, 2).isEmpty());
    }

    @Test
    void aWrongShapeOfTheRightMaterialDoesNotMatch() {
        // Two planks side by side and a stray extra one diagonal - bounding box 2x2 but not full.
        assertTrue(Recipes.craft(grid(3, 3, 0, P, 1, P, 5, P), 3, 3).isEmpty());
    }

    @Test
    void aMismatchedGridArrayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Recipes.craft(new BlockType[3], 2, 2));
    }
}
