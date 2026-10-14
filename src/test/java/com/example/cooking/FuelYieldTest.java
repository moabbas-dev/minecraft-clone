package com.example.cooking;

import com.example.inventory.ItemStack;
import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many items one fuel item smelts, from the wiki's Smelting table (docs/vanilla-rules.md 2.1): burn time / 200 ticks in a
 * furnace; blast furnaces and smokers cook in half the time but burn fuel twice as fast, so the count is the same.
 */
class FuelYieldTest {
    /** Items smelted from an unlimited supply of iron ore with exactly {@code fuels} fuel items, run until the fire is out for good. */
    private static int smelted(CookingStationType type, BlockType fuel, int fuels) {
        CookingStationState s = new CookingStationState(type);
        s.setInputStack(new ItemStack(BlockType.RAW_IRON, 64));
        s.setFuelStack(new ItemStack(fuel, fuels));
        for (int tick = 0; tick < 200_000; tick++) s.tick();
        return s.getOutputStack().count();
    }

    @Test
    void oneCoalSmeltsEightItemsInEveryStation() {
        assertEquals(8, smelted(CookingStationType.FURNACE, BlockType.COAL, 1));
        assertEquals(8, smelted(CookingStationType.BLAST_FURNACE, BlockType.COAL, 1));
        assertEquals(8, smelted(CookingStationType.FURNACE, BlockType.CHARCOAL, 1));
    }

    @Test
    void fuelItemsAreConsumedOneAtATimeNotAllAtOnce() {
        assertEquals(16, smelted(CookingStationType.FURNACE, BlockType.COAL, 2));
        assertEquals(40, smelted(CookingStationType.FURNACE, BlockType.COAL, 5));
    }

    @Test
    void aPlankBurnsForOneAndAHalfItemsSoOneItemFinishesAndTheHalfIsWasted() {
        assertEquals(1, smelted(CookingStationType.FURNACE, BlockType.OAK_PLANKS, 1));
        assertEquals(1, smelted(CookingStationType.FURNACE, BlockType.OAK_LOG, 1));
        assertEquals(1, smelted(CookingStationType.BLAST_FURNACE, BlockType.OAK_PLANKS, 1), "300 ticks of burn halve to 150, and an item takes 100");
    }

    @Test
    void twoPlanksFlowIntoEachOtherWithoutLosingProgressSoTheyMakeThreeItems() {
        assertEquals(3, smelted(CookingStationType.FURNACE, BlockType.OAK_PLANKS, 2));
        assertEquals(6, smelted(CookingStationType.FURNACE, BlockType.OAK_PLANKS, 4));
    }

    @Test
    void otherWoodenFuelsFollowTheWikiTable() {
        assertEquals(1, smelted(CookingStationType.FURNACE, BlockType.WOODEN_PICKAXE, 1), "a wooden tool burns 200 ticks: exactly one item");
        assertEquals(2, smelted(CookingStationType.FURNACE, BlockType.WOODEN_SWORD, 2));
        assertEquals(1, smelted(CookingStationType.FURNACE, BlockType.CRAFTING_TABLE, 1), "300 ticks: one and a half items");
        assertEquals(3, smelted(CookingStationType.FURNACE, BlockType.CHEST, 2));
        assertEquals(300, FuelRegistry.furnaceBurnTicks(BlockType.BOOKSHELF));
        assertEquals(100, FuelRegistry.furnaceBurnTicks(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(0, FuelRegistry.furnaceBurnTicks(BlockType.OAK_LEAVES), "leaves are not fuel");
        assertEquals(0, FuelRegistry.furnaceBurnTicks(BlockType.MUD));
        assertEquals(0, FuelRegistry.furnaceBurnTicks(BlockType.DRIED_KELP));
    }

    @Test
    void aStickIsNotEnoughForAnItem() {
        assertEquals(0, smelted(CookingStationType.FURNACE, BlockType.STICK, 1));
        assertEquals(1, smelted(CookingStationType.FURNACE, BlockType.STICK, 2));
    }

    @Test
    void progressDrainsGraduallyWhenTheFireGoesOut() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.RAW_IRON, 1));
        s.setFuelStack(new ItemStack(BlockType.STICK, 1)); // 100 ticks: half an item
        for (int i = 0; i < 100; i++) s.tick();
        assertEquals(100, s.getCookProgressTicks());
        s.tick(); // the stick is spent: progress starts to drain, 2 per tick
        assertEquals(98, s.getCookProgressTicks());
        for (int i = 0; i < 49; i++) s.tick();
        assertEquals(0, s.getCookProgressTicks());
        assertEquals(0, s.getOutputStack().count());
    }

    @Test
    void refuellingQuicklyKeepsMostOfTheProgress() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.RAW_IRON, 1));
        s.setFuelStack(new ItemStack(BlockType.STICK, 1));
        for (int i = 0; i < 105; i++) s.tick(); // 100 burned, 5 draining ticks
        s.setFuelStack(new ItemStack(BlockType.COAL, 1));
        s.tick();
        assertEquals(91, s.getCookProgressTicks(), "90 left after the drain, plus the first tick on the new fuel");
    }
}
