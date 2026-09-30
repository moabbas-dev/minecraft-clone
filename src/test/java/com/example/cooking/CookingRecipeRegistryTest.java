package com.example.cooking;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookingRecipeRegistryTest {

    @Test
    void furnaceAcceptsEveryCategoryFromTheTestMatrix() {
        for (BlockType input : new BlockType[]{
                BlockType.RAW_IRON, BlockType.IRON_ORE, BlockType.RAW_BEEF, BlockType.POTATO,
                BlockType.SAND, BlockType.COBBLESTONE, BlockType.OAK_LOG}) {
            assertTrue(CookingRecipeRegistry.accepts(input, CookingStationType.FURNACE), input + " must work in the Furnace");
        }
    }

    @Test
    void blastFurnaceAcceptsOnlyMetalRecipesAndRejectsEverythingElse() {
        for (BlockType input : new BlockType[]{
                BlockType.RAW_IRON, BlockType.IRON_ORE, BlockType.RAW_GOLD, BlockType.GOLD_ORE,
                BlockType.RAW_COPPER, BlockType.COPPER_ORE, BlockType.IRON_PICKAXE, BlockType.IRON_AXE, BlockType.IRON_SHOVEL}) {
            assertTrue(CookingRecipeRegistry.accepts(input, CookingStationType.BLAST_FURNACE), input + " must work in the Blast Furnace");
        }
        for (BlockType rejected : new BlockType[]{
                BlockType.RAW_BEEF, BlockType.POTATO, BlockType.SAND, BlockType.COBBLESTONE, BlockType.OAK_LOG}) {
            assertFalse(CookingRecipeRegistry.accepts(rejected, CookingStationType.BLAST_FURNACE), rejected + " must be rejected by the Blast Furnace");
        }
    }

    @Test
    void smokerAcceptsOnlyFoodAndRejectsEverythingElse() {
        for (BlockType input : new BlockType[]{
                BlockType.RAW_BEEF, BlockType.RAW_PORKCHOP, BlockType.RAW_CHICKEN, BlockType.RAW_MUTTON,
                BlockType.RAW_RABBIT, BlockType.COD, BlockType.SALMON, BlockType.POTATO, BlockType.KELP}) {
            assertTrue(CookingRecipeRegistry.accepts(input, CookingStationType.SMOKER), input + " must work in the Smoker");
        }
        for (BlockType rejected : new BlockType[]{
                BlockType.RAW_IRON, BlockType.IRON_ORE, BlockType.GOLD_ORE, BlockType.SAND, BlockType.COBBLESTONE, BlockType.OAK_LOG}) {
            assertFalse(CookingRecipeRegistry.accepts(rejected, CookingStationType.SMOKER), rejected + " must be rejected by the Smoker");
        }
    }

    @Test
    void aRecipeTaggedFurnaceOnlyNeverWorksInTheSpecializedStations() {
        assertTrue(CookingRecipeRegistry.accepts(BlockType.SAND, CookingStationType.FURNACE));
        assertFalse(CookingRecipeRegistry.accepts(BlockType.SAND, CookingStationType.BLAST_FURNACE));
        assertFalse(CookingRecipeRegistry.accepts(BlockType.SAND, CookingStationType.SMOKER));
    }

    @Test
    void eachRecipesOutputAndCountAndExperienceAreCorrect() {
        CookingRecipe iron = CookingRecipeRegistry.recipeFor(BlockType.IRON_ORE);
        assertNotNull(iron);
        assertEquals(BlockType.IRON_INGOT, iron.output());
        assertEquals(1, iron.outputCount());
        assertEquals(0.7f, iron.experience(), 0.001f);

        CookingRecipe cod = CookingRecipeRegistry.recipeFor(BlockType.COD);
        assertEquals(BlockType.COOKED_COD, cod.output());
        assertEquals(0.35f, cod.experience(), 0.001f);
    }

    @Test
    void anInputWithNoRecipeReturnsNullNotAThrow() {
        assertNull(CookingRecipeRegistry.recipeFor(BlockType.DIRT));
        assertNull(CookingRecipeRegistry.recipeFor(BlockType.DIRT, CookingStationType.FURNACE));
        assertFalse(CookingRecipeRegistry.accepts(BlockType.DIRT, CookingStationType.FURNACE));
    }

    @Test
    void everyRecipeInputIsUnique() {
        java.util.Set<BlockType> seen = new java.util.HashSet<>();
        for (CookingRecipe recipe : CookingRecipeRegistry.all()) {
            assertTrue(seen.add(recipe.input()), "duplicate input: " + recipe.input());
        }
    }

    @Test
    void chorusFruitIsNotASmokerRecipeBecauseItDoesNotExistInThisProject() {
        // Guards the request's explicit "don't allow Chorus Fruit -> Popped Chorus Fruit unless
        // intentionally supported" instruction the only way that's meaningful here: confirming
        // no such BlockType/recipe was added at all.
        for (BlockType b : BlockType.values()) {
            assertFalse(b.name().contains("CHORUS"), "no chorus fruit item should exist in this project");
        }
    }
}
