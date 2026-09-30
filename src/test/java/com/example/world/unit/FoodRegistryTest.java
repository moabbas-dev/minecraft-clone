package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.FoodProperties;
import com.example.world.FoodRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FoodRegistryTest {

    @Test
    void theThreeRequestedFoodsMatchTheirExactRequestedValues() {
        FoodProperties apple = FoodRegistry.propertiesFor(BlockType.APPLE);
        assertEquals(4f, apple.nutrition());
        assertEquals(2.4f, apple.saturationRestored(), 0.001f);
        assertFalse(apple.canEatWhenFull());

        FoodProperties bread = FoodRegistry.propertiesFor(BlockType.BREAD);
        assertEquals(5f, bread.nutrition());
        assertEquals(6.0f, bread.saturationRestored(), 0.001f);

        FoodProperties beef = FoodRegistry.propertiesFor(BlockType.COOKED_BEEF);
        assertEquals(8f, beef.nutrition());
        assertEquals(12.8f, beef.saturationRestored(), 0.001f);
    }

    @Test
    void isFoodIsTrueOnlyForTheThreeRegisteredFoods() {
        assertTrue(FoodRegistry.isFood(BlockType.APPLE));
        assertTrue(FoodRegistry.isFood(BlockType.BREAD));
        assertTrue(FoodRegistry.isFood(BlockType.COOKED_BEEF));
        assertFalse(FoodRegistry.isFood(BlockType.STONE));
        assertFalse(FoodRegistry.isFood(BlockType.STICK));
        assertFalse(FoodRegistry.isFood(null));
    }

    @Test
    void propertiesForANonFoodIsNull() {
        assertNull(FoodRegistry.propertiesFor(BlockType.DIRT));
        assertNull(FoodRegistry.propertiesFor(null));
    }

    @Test
    void noneOfTheThreeFoodsAreEverPlaceableBlocks() {
        for (BlockType food : new BlockType[]{BlockType.APPLE, BlockType.BREAD, BlockType.COOKED_BEEF}) {
            assertFalse(java.util.Arrays.asList(BlockType.PLACEABLE).contains(food), food + " is an item, not a block");
            assertEquals(-1f, food.handBreakSeconds());
        }
    }
}
