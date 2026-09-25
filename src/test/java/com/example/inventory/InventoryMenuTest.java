package com.example.inventory;

import com.example.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMenuTest {
    private Inventory inv;
    private InventoryMenu menu;

    @BeforeEach
    void setUp() {
        inv = new Inventory();
        menu = new InventoryMenu(inv, 2);
    }

    private int totalOf(BlockType t) {
        return inv.count(t) + menu.totalInFlight(t);
    }

    @Test
    void leftClickPicksUpAWholeStackAndPlacesItInAnEmptySlot() {
        inv.add(BlockType.DIRT, 10);
        menu.click(0, false);
        assertEquals(new ItemStack(BlockType.DIRT, 10), menu.getCursor());
        assertTrue(inv.get(0).isEmpty());
        menu.click(5, false);
        assertTrue(menu.getCursor().isEmpty());
        assertEquals(10, inv.get(5).count());
    }

    @Test
    void leftClickMergesSameItemsUpToTheStackLimitAndKeepsTheRemainderOnTheCursor() {
        inv.set(0, new ItemStack(BlockType.DIRT, 60));
        inv.set(1, new ItemStack(BlockType.DIRT, 10));
        menu.click(1, false);
        menu.click(0, false);
        assertEquals(64, inv.get(0).count());
        assertEquals(6, menu.getCursor().count());
        assertEquals(70, totalOf(BlockType.DIRT), "merging must conserve items");
    }

    @Test
    void leftClickOnADifferentItemSwapsIt() {
        inv.add(BlockType.DIRT, 3);
        inv.add(BlockType.STONE, 4);
        menu.click(0, false);
        menu.click(1, false);
        assertEquals(new ItemStack(BlockType.DIRT, 3), inv.get(1));
        assertEquals(new ItemStack(BlockType.STONE, 4), menu.getCursor());
    }

    @Test
    void rightClickPicksUpHalfRoundedUpAndPlacesOneAtATime() {
        inv.add(BlockType.DIRT, 5);
        menu.click(0, true);
        assertEquals(3, menu.getCursor().count());
        assertEquals(2, inv.get(0).count());
        menu.click(4, true);
        assertEquals(1, inv.get(4).count());
        assertEquals(2, menu.getCursor().count());
        menu.click(4, true);
        assertEquals(2, inv.get(4).count());
        assertEquals(5, totalOf(BlockType.DIRT));
    }

    @Test
    void clickingEmptySlotsWithAnEmptyCursorDoesNothing() {
        menu.click(3, false);
        menu.click(3, true);
        assertTrue(menu.getCursor().isEmpty());
        assertTrue(inv.get(3).isEmpty());
    }

    @Test
    void craftingPlanksFromALogThroughTheGrid() {
        inv.add(BlockType.OAK_LOG, 2);
        menu.click(0, false);                     // pick up both logs
        menu.click(InventoryMenu.GRID_BASE, true); // place ONE in the first grid cell
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), menu.getResult());

        // The cursor still holds the second log, and a different item can't be merged with the
        // result, so taking the planks must be refused until the log is put back.
        menu.click(InventoryMenu.RESULT_SLOT, false);
        assertEquals(new ItemStack(BlockType.OAK_LOG, 1), menu.getCursor());
        menu.click(0, false); // put the spare log back
        menu.click(InventoryMenu.RESULT_SLOT, false); // now take the planks
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), menu.getCursor());
        assertTrue(menu.getResult().isEmpty(), "the log was consumed, so nothing more is craftable");
        assertEquals(1, inv.count(BlockType.OAK_LOG), "the second log stays in the inventory");
    }

    @Test
    void craftingConsumesOneOfEachGridCellPerCraftAndCanRepeat() {
        // Two logs in the grid's first cell: each take uses one.
        inv.add(BlockType.OAK_LOG, 2);
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE, false); // both logs into one cell
        menu.click(InventoryMenu.RESULT_SLOT, false);
        assertEquals(4, menu.getCursor().count());
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), menu.getResult(), "one log remains, still craftable");
        menu.click(InventoryMenu.RESULT_SLOT, false);
        assertEquals(8, menu.getCursor().count());
        assertTrue(menu.getResult().isEmpty());
    }

    @Test
    void theResultCannotBeTakenOntoADifferentItemOrPastTheStackLimit() {
        inv.add(BlockType.OAK_LOG, 1);
        inv.add(BlockType.DIRT, 1);
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE, false); // log in grid -> result available
        menu.click(1, false);                       // pick up dirt onto the cursor
        menu.click(InventoryMenu.RESULT_SLOT, false);
        assertEquals(BlockType.DIRT, menu.getCursor().type(), "cursor holds a different item: result must stay put");
        assertFalse(menu.getResult().isEmpty());
        assertEquals(1, menu.totalInFlight(BlockType.OAK_LOG));
    }

    @Test
    void closingReturnsGridAndCursorToTheInventoryAndLosesNothing() {
        inv.add(BlockType.OAK_LOG, 3);
        inv.add(BlockType.DIRT, 5);
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE + 1, false);
        menu.click(1, false); // dirt on the cursor
        List<ItemStack> overflow = menu.close();
        assertTrue(overflow.isEmpty());
        assertEquals(3, inv.count(BlockType.OAK_LOG));
        assertEquals(5, inv.count(BlockType.DIRT));
        assertTrue(menu.getCursor().isEmpty());
        assertTrue(menu.getResult().isEmpty());
    }

    @Test
    void closingWithAFullInventoryReturnsTheLeftoversInsteadOfDiscardingThem() {
        Inventory tiny = new Inventory(1, 1, 4);
        InventoryMenu m = new InventoryMenu(tiny, 2);
        tiny.add(BlockType.STONE, 4);          // inventory full
        m.click(0, false);                     // stone onto the cursor: the slot is now empty...
        m.click(InventoryMenu.GRID_BASE, false); // ...and into the grid
        tiny.add(BlockType.STONE, 4);          // refill the inventory behind the menu's back
        List<ItemStack> overflow = m.close();
        assertEquals(1, overflow.size());
        assertEquals(new ItemStack(BlockType.STONE, 4), overflow.get(0));
    }

    @Test
    void droppingFromTheCursorTakesAllOrOne() {
        inv.add(BlockType.DIRT, 5);
        menu.click(0, false);
        assertEquals(new ItemStack(BlockType.DIRT, 1), menu.dropFromCursor(true));
        assertEquals(4, menu.getCursor().count());
        assertEquals(new ItemStack(BlockType.DIRT, 4), menu.dropFromCursor(false));
        assertTrue(menu.getCursor().isEmpty());
        assertTrue(menu.dropFromCursor(false).isEmpty());
    }

    @Test
    void anInvalidSlotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> menu.click(-1, false));
        assertThrows(IllegalArgumentException.class, () -> menu.click(InventoryMenu.GRID_BASE + 4, false));
        assertThrows(IllegalArgumentException.class, () -> menu.stackAt(50));
    }

    @Test
    void aThreeByThreeMenuCanCraftAFurnaceFromEightCobblestone() {
        InventoryMenu big = new InventoryMenu(inv, 3);
        inv.add(BlockType.COBBLESTONE, 8);
        big.click(0, false); // all eight onto the cursor
        for (int cell : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) {
            big.click(InventoryMenu.GRID_BASE + cell, true); // one into each ring cell
        }
        assertTrue(big.getCursor().isEmpty());
        assertEquals(BlockType.FURNACE, big.getResult().type());
        assertEquals(8, big.totalInFlight(BlockType.COBBLESTONE));
    }
}
