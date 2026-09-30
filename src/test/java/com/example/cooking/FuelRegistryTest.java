package com.example.cooking;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FuelRegistryTest {

    @Test
    void coalAndCharcoalBurnForTheRequestedDuration() {
        assertEquals(1600, FuelRegistry.furnaceBurnTicks(BlockType.COAL));
        assertEquals(1600, FuelRegistry.furnaceBurnTicks(BlockType.CHARCOAL));
        assertTrue(FuelRegistry.isFuel(BlockType.COAL));
        assertTrue(FuelRegistry.isFuel(BlockType.CHARCOAL));
    }

    @Test
    void logsAndPlanksBurnForThreeHundredTicks() {
        for (BlockType wood : new BlockType[]{BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.OAK_PLANKS}) {
            assertEquals(300, FuelRegistry.furnaceBurnTicks(wood));
            assertTrue(FuelRegistry.isFuel(wood));
        }
    }

    @Test
    void sticksBurnForOneHundredTicks() {
        assertEquals(100, FuelRegistry.furnaceBurnTicks(BlockType.STICK));
    }

    @Test
    void nonFuelItemsAreNotFuelAndReportZeroBurnTicks() {
        for (BlockType notFuel : new BlockType[]{BlockType.DIRT, BlockType.IRON_INGOT, BlockType.RAW_BEEF, BlockType.STONE}) {
            assertFalse(FuelRegistry.isFuel(notFuel), notFuel + " is not a fuel");
            assertEquals(0, FuelRegistry.furnaceBurnTicks(notFuel));
        }
    }
}
