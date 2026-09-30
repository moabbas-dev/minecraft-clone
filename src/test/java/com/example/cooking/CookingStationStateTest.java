package com.example.cooking;

import com.example.inventory.ItemStack;
import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookingStationStateTest {

    private static void tick(CookingStationState s, int n) {
        for (int i = 0; i < n; i++) s.tick();
    }

    @Test
    void anEmptyStationDoesNothingWhenTicked() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        tick(s, 500);
        assertFalse(s.isBurning());
        assertEquals(0, s.getCookProgressTicks());
        assertTrue(s.getOutputStack().isEmpty());
    }

    @Test
    void fuelIsNeverConsumedWithoutAValidInput() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setFuelStack(new ItemStack(BlockType.COAL, 5));
        tick(s, 500);
        assertFalse(s.isBurning(), "must never light with nothing valid to cook");
        assertEquals(5, s.getFuelStack().count(), "fuel must not be consumed for nothing");
    }

    @Test
    void aFullCookProducesOutputAndConsumesExactlyOneInputAndOneFuel() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 5));
        s.setFuelStack(new ItemStack(BlockType.COAL, 3));
        tick(s, 200); // exactly one Furnace cook
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 1), s.getOutputStack());
        assertEquals(4, s.getInputStack().count());
        assertTrue(s.getStoredExperience() > 0f);
    }

    @Test
    void furnaceTakesExactlyTwoHundredTicksPerItem() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        s.setFuelStack(new ItemStack(BlockType.COAL, 1));
        tick(s, 199);
        assertTrue(s.getOutputStack().isEmpty(), "not yet - one tick short");
        tick(s, 1);
        assertEquals(BlockType.IRON_INGOT, s.getOutputStack().type());
    }

    @Test
    void blastFurnaceAndSmokerTakeExactlyOneHundredTicksPerItem() {
        CookingStationState blast = new CookingStationState(CookingStationType.BLAST_FURNACE);
        blast.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        blast.setFuelStack(new ItemStack(BlockType.COAL, 1));
        tick(blast, 99);
        assertTrue(blast.getOutputStack().isEmpty());
        tick(blast, 1);
        assertEquals(BlockType.IRON_INGOT, blast.getOutputStack().type());

        CookingStationState smoker = new CookingStationState(CookingStationType.SMOKER);
        smoker.setInputStack(new ItemStack(BlockType.RAW_BEEF, 1));
        smoker.setFuelStack(new ItemStack(BlockType.COAL, 1));
        tick(smoker, 99);
        assertTrue(smoker.getOutputStack().isEmpty());
        tick(smoker, 1);
        assertEquals(BlockType.COOKED_BEEF, smoker.getOutputStack().type());
    }

    @Test
    void oneCoalYieldsTheSameItemCountInEveryStation() {
        // Each station gets an input its own recipe set actually accepts (Smoker rejects ore).
        java.util.Map<CookingStationType, BlockType> inputFor = java.util.Map.of(
                CookingStationType.FURNACE, BlockType.IRON_ORE,
                CookingStationType.BLAST_FURNACE, BlockType.IRON_ORE,
                CookingStationType.SMOKER, BlockType.RAW_BEEF);
        for (CookingStationType type : CookingStationType.values()) {
            CookingStationState s = new CookingStationState(type);
            s.setInputStack(new ItemStack(inputFor.get(type), 64));
            s.setFuelStack(new ItemStack(BlockType.COAL, 1));
            // Run far more ticks than any station could possibly need for one coal, then check
            // the result - not "does it look about right", the literal request's own number.
            tick(s, 3000);
            assertEquals(8, s.getOutputStack().count(), type + ": one coal should yield exactly 8 items");
            assertFalse(s.isBurning(), type + ": the coal should be fully spent by now");
        }
    }

    @Test
    void theSpecializedStationsAreNotMoreFuelEfficientTheyAreJustFaster() {
        CookingStationState furnace = new CookingStationState(CookingStationType.FURNACE);
        furnace.setInputStack(new ItemStack(BlockType.IRON_ORE, 64));
        furnace.setFuelStack(new ItemStack(BlockType.COAL, 1));
        CookingStationState blast = new CookingStationState(CookingStationType.BLAST_FURNACE);
        blast.setInputStack(new ItemStack(BlockType.IRON_ORE, 64));
        blast.setFuelStack(new ItemStack(BlockType.COAL, 1));

        int furnaceTicksToFirstItem = -1, blastTicksToFirstItem = -1;
        for (int i = 1; i <= 3000; i++) {
            furnace.tick();
            if (furnaceTicksToFirstItem < 0 && !furnace.getOutputStack().isEmpty()) furnaceTicksToFirstItem = i;
            blast.tick();
            if (blastTicksToFirstItem < 0 && !blast.getOutputStack().isEmpty()) blastTicksToFirstItem = i;
        }
        assertEquals(200, furnaceTicksToFirstItem);
        assertEquals(100, blastTicksToFirstItem, "twice as fast per item");
        tick(furnace, 0);
        tick(blast, 0); // no-op, just documenting nothing further happens here
        // Both still end up with the SAME total item count from the one coal (checked in the
        // dedicated test above) - this test only checks the speed difference in isolation.
    }

    @Test
    void anIncompatibleOutputBlocksFurtherCookingWithoutLosingAnything() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 2));
        s.setFuelStack(new ItemStack(BlockType.COAL, 5));
        tick(s, 200);
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 1), s.getOutputStack());
        s.restoreOutputStack(new ItemStack(BlockType.GOLD_INGOT, 1)); // simulate an incompatible item ending up there
        int inputBefore = s.getInputStack().count();
        tick(s, 500);
        assertEquals(inputBefore, s.getInputStack().count(), "must not consume input it can't produce compatible output for");
        assertEquals(new ItemStack(BlockType.GOLD_INGOT, 1), s.getOutputStack(), "incompatible output must never be overwritten");
    }

    @Test
    void aFullOutputStopsCookingWithoutOverflowingOrLosingInput() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.restoreOutputStack(new ItemStack(BlockType.IRON_INGOT, 64)); // already at the stack cap
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 10));
        s.setFuelStack(new ItemStack(BlockType.COAL, 5));
        tick(s, 1000);
        assertEquals(64, s.getOutputStack().count(), "output must never exceed the stack limit");
        assertEquals(10, s.getInputStack().count(), "input must be untouched while output is full");
    }

    @Test
    void removingInputMidBurnStillLetsTheAlreadyCommittedFuelBurnOutWithoutCompletingAnItem() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        s.setFuelStack(new ItemStack(BlockType.COAL, 1));
        tick(s, 50);
        assertTrue(s.isBurning());
        int progressBefore = s.getCookProgressTicks();
        s.setInputStack(ItemStack.EMPTY); // player yanks the input out mid-smelt
        tick(s, 40);
        assertEquals(progressBefore, s.getCookProgressTicks(), "progress must not advance with no valid input");
        assertTrue(s.isBurning(), "the already-lit fuel keeps burning down regardless");
    }

    @Test
    void takeOutputGrantsProportionalExperienceAndNeverLosesFractionalXp() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 4));
        s.setFuelStack(new ItemStack(BlockType.COAL, 5));
        tick(s, 800); // 4 full cooks
        assertEquals(4, s.getOutputStack().count());
        float totalXp = s.getStoredExperience();
        assertEquals(0.7f * 4, totalXp, 0.001f);

        float firstGrant = s.takeOutput(1);
        assertEquals(totalXp / 4, firstGrant, 0.001f);
        assertEquals(3, s.getOutputStack().count());
        assertEquals(totalXp - firstGrant, s.getStoredExperience(), 0.001f);

        float restGrant = s.takeOutput(3);
        assertEquals(s.getOutputStack().isEmpty(), true);
        assertEquals(totalXp - firstGrant, restGrant, 0.001f, "taking everything grants everything remaining");
        assertEquals(0f, s.getStoredExperience());
    }

    @Test
    void takingMoreThanAvailableOrANonPositiveAmountIsRejected() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.restoreOutputStack(new ItemStack(BlockType.IRON_INGOT, 2));
        assertEquals(0f, s.takeOutput(0));
        assertEquals(0f, s.takeOutput(-1));
        assertEquals(0f, s.takeOutput(3));
        assertEquals(2, s.getOutputStack().count(), "a rejected take must change nothing");
    }

    @Test
    void contentsListsOnlyTheNonEmptyStacks() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        assertTrue(s.contents().isEmpty());
        s.setInputStack(new ItemStack(BlockType.IRON_ORE, 1));
        assertEquals(1, s.contents().size());
        s.setFuelStack(new ItemStack(BlockType.COAL, 1));
        s.restoreOutputStack(new ItemStack(BlockType.IRON_INGOT, 1));
        assertEquals(3, s.contents().size());
    }

    @Test
    void placingAnItemWithNoRecipeInInputNeverCooksButDoesNotCrash() {
        CookingStationState s = new CookingStationState(CookingStationType.FURNACE);
        s.setInputStack(new ItemStack(BlockType.DIRT, 5));
        s.setFuelStack(new ItemStack(BlockType.COAL, 5));
        assertDoesNotThrow(() -> tick(s, 1000));
        assertTrue(s.getOutputStack().isEmpty());
        assertEquals(5, s.getInputStack().count(), "no fuel/input consumed for an uncookable item");
    }
}
