package com.example.inventory;

import com.example.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shift-click, double-click, number keys, drag-distribute and craft-all (docs/vanilla-rules.md section 6). */
class InventoryMenuVanillaTest {
    private Inventory inv;
    private PlayerArmor armor;
    private InventoryMenu menu;

    @BeforeEach
    void setUp() {
        inv = new Inventory();
        armor = new PlayerArmor();
        menu = new InventoryMenu(inv, armor, 3);
    }

    private int total(BlockType t) {
        return inv.count(t) + menu.totalInFlight(t);
    }

    @Test
    void shiftClickMovesAHotbarStackToStorageAndBack() {
        inv.set(0, new ItemStack(BlockType.DIRT, 20));
        menu.quickMove(0);
        assertTrue(inv.get(0).isEmpty());
        assertEquals(new ItemStack(BlockType.DIRT, 20), inv.get(Inventory.HOTBAR_SIZE));
        menu.quickMove(Inventory.HOTBAR_SIZE);
        assertEquals(new ItemStack(BlockType.DIRT, 20), inv.get(0));
    }

    @Test
    void shiftClickMergesOntoExistingStacksFirst() {
        inv.set(0, new ItemStack(BlockType.DIRT, 30));
        inv.set(12, new ItemStack(BlockType.DIRT, 50));
        menu.quickMove(0);
        assertEquals(64, inv.get(12).count());
        assertEquals(16, inv.get(Inventory.HOTBAR_SIZE).count(), "the remainder takes the first empty storage slot");
        assertEquals(80, total(BlockType.DIRT));
    }

    @Test
    void shiftClickWearsAnArmorPieceAndLeavesTheRestOfTheStackAlone() {
        inv.set(3, new ItemStack(BlockType.IRON_HELMET, 1));
        menu.quickMove(3);
        assertTrue(inv.get(3).isEmpty());
        assertEquals(BlockType.IRON_HELMET, armor.get(com.example.combat.EquipmentSlot.HEAD).type());
    }

    @Test
    void shiftClickOnAWornPieceReturnsItToTheInventory() {
        armor.set(com.example.combat.EquipmentSlot.HEAD, new ItemStack(BlockType.DIAMOND_HELMET, 1));
        menu.quickMove(InventoryMenu.ARMOR_BASE);
        assertTrue(armor.get(com.example.combat.EquipmentSlot.HEAD).isEmpty());
        assertEquals(1, inv.count(BlockType.DIAMOND_HELMET));
    }

    @Test
    void shiftClickOnAGridStackSendsItBackToTheInventory() {
        menu.click(0, false); // empty cursor, nothing yet
        inv.set(0, new ItemStack(BlockType.COBBLESTONE, 8));
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE, false);
        assertEquals(8, menu.totalInFlight(BlockType.COBBLESTONE));
        menu.quickMove(InventoryMenu.GRID_BASE);
        assertEquals(0, menu.totalInFlight(BlockType.COBBLESTONE));
        assertEquals(8, inv.count(BlockType.COBBLESTONE));
    }

    @Test
    void shiftClickOnTheResultCraftsEverythingTheIngredientsAllow() {
        // 3 logs in one grid cell craft 3 times (12 planks); the rest of the logs stay put
        inv.set(0, new ItemStack(BlockType.OAK_LOG, 3));
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE, false);
        assertEquals(new ItemStack(BlockType.OAK_PLANKS, 4), menu.getResult());
        menu.quickMove(InventoryMenu.RESULT_SLOT);
        assertEquals(12, inv.count(BlockType.OAK_PLANKS));
        assertEquals(0, menu.totalInFlight(BlockType.OAK_LOG));
        assertTrue(menu.getResult().isEmpty());
    }

    @Test
    void craftAllNeverCraftsWhenTheResultWouldNotFit() {
        for (int i = 0; i < Inventory.DEFAULT_SLOT_COUNT; i++) inv.set(i, new ItemStack(BlockType.DIRT, 64));
        inv.set(0, new ItemStack(BlockType.OAK_LOG, 1));
        menu.click(0, false);
        menu.click(InventoryMenu.GRID_BASE, false);
        inv.set(0, new ItemStack(BlockType.DIRT, 64)); // the slot the log left is full again: no room anywhere
        menu.quickMove(InventoryMenu.RESULT_SLOT);
        assertEquals(1, menu.totalInFlight(BlockType.OAK_LOG), "the log is still in the grid");
        assertEquals(0, inv.count(BlockType.OAK_PLANKS));
    }

    @Test
    void doubleClickGathersMatchingStacksIntoTheCursorUpToAFullStack() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        inv.set(9, new ItemStack(BlockType.DIRT, 30));
        inv.set(10, new ItemStack(BlockType.DIRT, 40));
        inv.set(11, new ItemStack(BlockType.STONE, 5));
        menu.click(0, false);
        menu.gatherSame(0);
        assertEquals(64, menu.getCursor().count());
        assertEquals(80 + 0, total(BlockType.DIRT));
        assertEquals(5, inv.count(BlockType.STONE), "other items untouched");
        assertEquals(0, inv.get(9).count(), "the partly-filled stack is drained first");
    }

    @Test
    void numberKeySwapsTheHoveredSlotWithThatHotbarSlot() {
        inv.set(2, new ItemStack(BlockType.DIRT, 5));
        inv.set(20, new ItemStack(BlockType.STONE, 7));
        menu.swapWithHotbar(20, 2);
        assertEquals(new ItemStack(BlockType.STONE, 7), inv.get(2));
        assertEquals(new ItemStack(BlockType.DIRT, 5), inv.get(20));
    }

    @Test
    void numberKeyDoesNothingWhileTheCursorHoldsSomething() {
        inv.set(0, new ItemStack(BlockType.DIRT, 5));
        inv.set(20, new ItemStack(BlockType.STONE, 7));
        menu.click(0, false);
        menu.swapWithHotbar(20, 1);
        assertEquals(new ItemStack(BlockType.STONE, 7), inv.get(20));
    }

    @Test
    void leftDragSplitsTheCursorStackEvenlyAndKeepsTheRemainder() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        menu.click(0, false);
        assertTrue(menu.distribute(List.of(1, 2, 3), false));
        assertEquals(3, inv.get(1).count());
        assertEquals(3, inv.get(2).count());
        assertEquals(3, inv.get(3).count());
        assertEquals(1, menu.getCursor().count());
        assertEquals(10, total(BlockType.DIRT));
    }

    @Test
    void rightDragPlacesExactlyOnePerSlot() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        menu.click(0, false);
        assertTrue(menu.distribute(List.of(4, 5, 6, 7), true));
        for (int s = 4; s <= 7; s++) assertEquals(1, inv.get(s).count());
        assertEquals(6, menu.getCursor().count());
    }

    @Test
    void dragSkipsSlotsHoldingOtherItemsAndRespectsTheStackLimit() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        inv.set(2, new ItemStack(BlockType.STONE, 3));
        inv.set(3, new ItemStack(BlockType.DIRT, 63));
        menu.click(0, false);
        assertTrue(menu.distribute(List.of(1, 2, 3), false));
        assertEquals(new ItemStack(BlockType.STONE, 3), inv.get(2));
        assertEquals(64, inv.get(3).count(), "only one fits");
        assertEquals(73, total(BlockType.DIRT), "10 on the cursor/slots plus the 63 already in slot 3");
    }

    @Test
    void aDragOverASingleSlotIsNotADragSoTheCallerDoesAnOrdinaryClick() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        menu.click(0, false);
        assertFalse(menu.distribute(List.of(5), false));
        assertFalse(menu.distribute(List.of(5, 5), false));
        assertEquals(10, menu.getCursor().count());
    }

    @Test
    void dragIntoTheCraftingGridUpdatesTheResult() {
        inv.set(0, new ItemStack(BlockType.OAK_PLANKS, 8));
        menu.click(0, false);
        assertTrue(menu.distribute(List.of(InventoryMenu.GRID_BASE, InventoryMenu.GRID_BASE + 1,
                InventoryMenu.GRID_BASE + 3, InventoryMenu.GRID_BASE + 4), true));
        assertEquals(new ItemStack(BlockType.CRAFTING_TABLE, 1), menu.getResult());
    }
}
