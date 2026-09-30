package com.example.cooking;

import com.example.inventory.Inventory;
import com.example.inventory.ItemStack;
import com.example.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookingMenuTest {
    private Inventory player;
    private CookingStationState station;
    private CookingMenu menu;

    @BeforeEach
    void setUp() {
        player = new Inventory();
        station = new CookingStationState(0, 0, 0, CookingStationType.FURNACE);
        menu = new CookingMenu(player, station);
    }

    @Test
    void placingInputAndFuelFromThePlayerInventoryWorksLikeAnyOtherSlot() {
        player.add(BlockType.IRON_ORE, 10);
        player.add(BlockType.COAL, 5);
        menu.click(0, false); // pick up ore
        menu.click(CookingMenu.INPUT_SLOT, true); // place one
        assertEquals(new ItemStack(BlockType.IRON_ORE, 1), station.getInputStack());
        assertEquals(9, menu.getCursor().count());

        menu.click(0, false); // put the rest back
        menu.click(1, false); // pick up coal
        menu.click(CookingMenu.FUEL_SLOT, false); // place all
        assertEquals(new ItemStack(BlockType.COAL, 5), station.getFuelStack());
        assertTrue(menu.getCursor().isEmpty());
    }

    @Test
    void mergingAndSwappingInInputSlotFollowsTheSharedRule() {
        station.setInputStack(new ItemStack(BlockType.IRON_ORE, 60));
        player.set(0, new ItemStack(BlockType.IRON_ORE, 10));
        menu.click(0, false); // cursor = 10 ore
        menu.click(CookingMenu.INPUT_SLOT, false); // merge into the 60 already there, capped at 64
        assertEquals(64, station.getInputStack().count());
        assertEquals(6, menu.getCursor().count(), "merging must conserve items");
    }

    @Test
    void theOutputSlotCanOnlyBeTakenFromNeverPlacedInto() {
        player.set(0, new ItemStack(BlockType.DIRT, 5));
        menu.click(0, false); // cursor now holds dirt
        menu.click(CookingMenu.OUTPUT_SLOT, false); // output is empty - nothing should happen
        assertEquals(new ItemStack(BlockType.DIRT, 5), menu.getCursor(), "an empty output takes no click at all");

        station.restoreOutputStack(new ItemStack(BlockType.IRON_INGOT, 3));
        menu.click(CookingMenu.OUTPUT_SLOT, false); // still holding dirt - incompatible, must be refused
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 3), station.getOutputStack(), "output must never accept a placed item");
        assertEquals(new ItemStack(BlockType.DIRT, 5), menu.getCursor());
    }

    @Test
    void takingOutputWithAnEmptyCursorGrantsProportionalXpAndMovesTheItems() {
        station.setInputStack(new ItemStack(BlockType.IRON_ORE, 4));
        station.setFuelStack(new ItemStack(BlockType.COAL, 5));
        for (int i = 0; i < 800; i++) station.tick(); // 4 full cooks
        assertEquals(4, station.getOutputStack().count());
        float totalXp = station.getStoredExperience();

        menu.click(CookingMenu.OUTPUT_SLOT, true); // right-click: take half (2)
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 2), menu.getCursor());
        assertEquals(2, station.getOutputStack().count());
        assertEquals(totalXp / 2, menu.takeEarnedExperience(), 0.001f);
        assertEquals(0f, menu.takeEarnedExperience(), "already drained - a second read must be empty");

        menu.click(CookingMenu.OUTPUT_SLOT, false); // take the rest, merging onto the cursor
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 4), menu.getCursor());
        assertTrue(station.getOutputStack().isEmpty());
    }

    @Test
    void closingReturnsOnlyTheCursorAndNeverTouchesTheStation() {
        station.setInputStack(new ItemStack(BlockType.IRON_ORE, 3));
        station.setFuelStack(new ItemStack(BlockType.COAL, 2));
        player.add(BlockType.DIRT, 5);
        menu.click(0, false); // cursor = 5 dirt

        ItemStack leftover = menu.close();
        assertTrue(leftover.isEmpty());
        assertEquals(5, player.count(BlockType.DIRT), "the cursor went back to the player inventory");
        assertTrue(menu.getCursor().isEmpty());
        assertEquals(new ItemStack(BlockType.IRON_ORE, 3), station.getInputStack(), "the station's own contents must be untouched by closing");
        assertEquals(new ItemStack(BlockType.COAL, 2), station.getFuelStack());
    }

    @Test
    void closingWithAFullPlayerInventoryReturnsTheLeftoverInsteadOfDiscardingIt() {
        Inventory tinyPlayer = new Inventory(1, 1, 4);
        tinyPlayer.add(BlockType.DIRT, 4); // already full
        CookingMenu m = new CookingMenu(tinyPlayer, station);
        station.restoreOutputStack(new ItemStack(BlockType.IRON_INGOT, 2));
        m.click(CookingMenu.OUTPUT_SLOT, false); // cursor = 2 iron ingots, nowhere to put them on close
        ItemStack leftover = m.close();
        assertEquals(new ItemStack(BlockType.IRON_INGOT, 2), leftover);
    }

    @Test
    void dropFromCursorTakesAllOrOne() {
        player.set(0, new ItemStack(BlockType.DIRT, 5));
        menu.click(0, false);
        assertEquals(new ItemStack(BlockType.DIRT, 1), menu.dropFromCursor(true));
        assertEquals(4, menu.getCursor().count());
        assertEquals(new ItemStack(BlockType.DIRT, 4), menu.dropFromCursor(false));
        assertTrue(menu.getCursor().isEmpty());
    }

    @Test
    void anInvalidSlotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> menu.click(-1, false));
        assertThrows(IllegalArgumentException.class, () -> menu.click(999, false));
        assertThrows(IllegalArgumentException.class, () -> menu.stackAt(999));
    }
}
