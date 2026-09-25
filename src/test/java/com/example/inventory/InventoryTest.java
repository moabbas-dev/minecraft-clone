package com.example.inventory;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    @Test
    void addingToAnEmptyInventoryUsesTheFirstSlot() {
        Inventory inv = new Inventory();
        assertEquals(5, inv.add(BlockType.DIRT, 5));
        assertEquals(new ItemStack(BlockType.DIRT, 5), inv.get(0));
    }

    @Test
    void addingMergesIntoAnExistingCompatibleStack() {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 5);
        inv.add(BlockType.DIRT, 3);
        assertEquals(8, inv.get(0).count());
        assertTrue(inv.get(1).isEmpty(), "must merge, not open a second stack");
    }

    @Test
    void anAmountLargerThanOneStackSplitsAcrossSlots() {
        Inventory inv = new Inventory();
        assertEquals(150, inv.add(BlockType.STONE, 150));
        assertEquals(64, inv.get(0).count());
        assertEquals(64, inv.get(1).count());
        assertEquals(22, inv.get(2).count());
    }

    @Test
    void stackLimitIsNeverExceeded() {
        Inventory inv = new Inventory();
        inv.add(BlockType.STONE, 64);
        inv.add(BlockType.STONE, 1);
        assertEquals(64, inv.get(0).count());
        assertEquals(1, inv.get(1).count());
    }

    @Test
    void differentItemsNeverMerge() {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 1);
        inv.add(BlockType.STONE, 1);
        assertEquals(BlockType.DIRT, inv.get(0).type());
        assertEquals(BlockType.STONE, inv.get(1).type());
    }

    @Test
    void addingToAFullInventoryAddsNothingAndReportsIt() {
        Inventory inv = new Inventory(2, 2, 4);
        assertEquals(8, inv.add(BlockType.DIRT, 8));
        assertEquals(0, inv.add(BlockType.DIRT, 1), "full inventory must not accept anything");
        assertEquals(0, inv.add(BlockType.STONE, 1));
        assertEquals(8, inv.count(BlockType.DIRT), "nothing may be lost or duplicated");
    }

    @Test
    void partialInsertionReportsExactlyHowMuchFit() {
        Inventory inv = new Inventory(2, 2, 4);
        inv.add(BlockType.DIRT, 3);
        // room: 1 in the DIRT stack + 4 in the empty slot = 5
        assertEquals(5, inv.add(BlockType.DIRT, 9));
        assertEquals(8, inv.count(BlockType.DIRT));
    }

    @Test
    void removingReducesTheCountAndReportsWhatWasRemoved() {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 10);
        assertEquals(4, inv.remove(BlockType.DIRT, 4));
        assertEquals(6, inv.count(BlockType.DIRT));
    }

    @Test
    void removingMoreThanAvailableRemovesOnlyWhatExists() {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 10);
        assertEquals(10, inv.remove(BlockType.DIRT, 99));
        assertEquals(0, inv.count(BlockType.DIRT));
    }

    @Test
    void removingTheLastItemLeavesACanonicalEmptySlot() {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 1);
        assertTrue(inv.removeOne(0));
        assertEquals(ItemStack.EMPTY, inv.get(0));
        assertFalse(inv.removeOne(0), "removing from an empty slot must report failure, never go negative");
    }

    @Test
    void removeAcrossSlotsSpansStacks() {
        Inventory inv = new Inventory();
        inv.add(BlockType.STONE, 100);
        assertEquals(80, inv.remove(BlockType.STONE, 80));
        assertEquals(20, inv.count(BlockType.STONE));
    }

    @Test
    void zeroAmountIsANoOp() {
        Inventory inv = new Inventory();
        assertEquals(0, inv.add(BlockType.DIRT, 0));
        assertEquals(0, inv.remove(BlockType.DIRT, 0));
        assertTrue(inv.get(0).isEmpty());
    }

    @Test
    void invalidQuantitiesAndItemsAreRejected() {
        Inventory inv = new Inventory();
        assertThrows(IllegalArgumentException.class, () -> inv.add(BlockType.DIRT, -1));
        assertThrows(IllegalArgumentException.class, () -> inv.remove(BlockType.DIRT, -1));
        assertThrows(IllegalArgumentException.class, () -> inv.add(BlockType.AIR, 1));
        assertThrows(IllegalArgumentException.class, () -> inv.add(null, 1));
    }

    @Test
    void itemStackNormalizesEveryEmptyFormToEmpty() {
        assertEquals(ItemStack.EMPTY, new ItemStack(BlockType.DIRT, 0));
        assertEquals(ItemStack.EMPTY, new ItemStack(BlockType.DIRT, -5));
        assertEquals(ItemStack.EMPTY, new ItemStack(BlockType.AIR, 3));
        assertEquals(ItemStack.EMPTY, new ItemStack(null, 3));
    }

    @Test
    void spaceAndCapacityAreCalculatedFromStacksAndEmptySlots() {
        Inventory inv = new Inventory(3, 3, 10);
        assertEquals(30, inv.spaceFor(BlockType.DIRT));
        inv.add(BlockType.DIRT, 4);
        assertEquals(26, inv.spaceFor(BlockType.DIRT));
        assertEquals(20, inv.spaceFor(BlockType.STONE), "a different item can only use the 2 empty slots");
        assertTrue(inv.canAdd(BlockType.DIRT, 26));
        assertFalse(inv.canAdd(BlockType.DIRT, 27));
    }

    @Test
    void hotbarSelectionWrapsAndRejectsOutOfRangeSlots() {
        Inventory inv = new Inventory();
        inv.cycleSelection(-1);
        assertEquals(Inventory.HOTBAR_SIZE - 1, inv.getSelectedSlot());
        inv.cycleSelection(1);
        assertEquals(0, inv.getSelectedSlot());
        assertThrows(IllegalArgumentException.class, () -> inv.selectSlot(Inventory.HOTBAR_SIZE));
        assertThrows(IllegalArgumentException.class, () -> inv.selectSlot(-1));
    }

    @Test
    void selectedReflectsTheRealSlotAndAnEmptySelectionIsSafe() {
        Inventory inv = new Inventory();
        assertTrue(inv.selected().isEmpty(), "selecting an empty slot must not crash");
        inv.add(BlockType.DIRT, 3);
        assertEquals(new ItemStack(BlockType.DIRT, 3), inv.selected());
        inv.removeFromSlot(0, 3);
        assertTrue(inv.selected().isEmpty());
        assertEquals(0, inv.getSelectedSlot(), "selection must stay valid when its stack empties");
    }

    @Test
    void setRejectsAStackAboveTheLimit() {
        Inventory inv = new Inventory();
        assertThrows(IllegalArgumentException.class, () -> inv.set(0, new ItemStack(BlockType.DIRT, 65)));
    }

    @Test
    void saveAndLoadRoundTripsEveryStackAndTheSelection() throws IOException {
        Inventory inv = new Inventory();
        inv.add(BlockType.DIRT, 70);
        inv.add(BlockType.OAK_SAPLING_STAGE_0, 3);
        inv.selectSlot(2);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inv.write(new DataOutputStream(bytes));
        Inventory loaded = Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        for (int i = 0; i < inv.slotCount(); i++) assertEquals(inv.get(i), loaded.get(i), "slot " + i);
        assertEquals(2, loaded.getSelectedSlot());
    }

    @Test
    void loadingCorruptStacksLeavesThoseSlotsEmptyInsteadOfCrashing() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(2);       // slot count
        out.writeInt(99);      // out-of-range selection
        out.writeInt(255);     // unknown block id
        out.writeInt(5);
        out.writeInt(BlockType.DIRT.id);
        out.writeInt(100000);  // absurd count
        Inventory loaded = Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertTrue(loaded.get(0).isEmpty());
        assertEquals(loaded.getMaxStack(), loaded.get(1).count(), "absurd counts clamp to the stack limit");
        assertEquals(0, loaded.getSelectedSlot());
    }
}
