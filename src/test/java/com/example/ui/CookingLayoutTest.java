package com.example.ui;

import com.example.cooking.CookingMenu;
import com.example.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookingLayoutTest {

    private static List<Integer> allSlots() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < Inventory.DEFAULT_SLOT_COUNT; i++) ids.add(i);
        ids.add(CookingMenu.INPUT_SLOT);
        ids.add(CookingMenu.FUEL_SLOT);
        ids.add(CookingMenu.OUTPUT_SLOT);
        return ids;
    }

    @Test
    void clickingTheCenterOfEverySlotFindsExactlyThatSlot() {
        CookingLayout layout = new CookingLayout(1280, 720);
        for (int id : allSlots()) {
            InventoryLayout.Rect r = layout.slotRect(id);
            assertEquals(id, layout.slotAt(r.x() + r.w() / 2, r.y() + r.h() / 2), "slot " + id);
        }
    }

    @Test
    void noTwoSlotsOverlapAndEverySlotIsInsideThePanel() {
        CookingLayout layout = new CookingLayout(1280, 720);
        List<Integer> ids = allSlots();
        InventoryLayout.Rect panel = layout.panel();
        for (int i = 0; i < ids.size(); i++) {
            InventoryLayout.Rect a = layout.slotRect(ids.get(i));
            assertTrue(a.x() >= panel.x() && a.x() + a.w() <= panel.x() + panel.w(), "x inside panel: " + ids.get(i));
            assertTrue(a.y() >= panel.y() && a.y() + a.h() <= panel.y() + panel.h(), "y inside panel: " + ids.get(i));
            for (int j = i + 1; j < ids.size(); j++) {
                InventoryLayout.Rect b = layout.slotRect(ids.get(j));
                boolean overlap = a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
                assertFalse(overlap, "slots " + ids.get(i) + " and " + ids.get(j) + " overlap");
            }
        }
    }

    @Test
    void positionsOutsideThePanelHitNothing() {
        CookingLayout layout = new CookingLayout(1280, 720);
        assertEquals(-1, layout.slotAt(0f, 0f));
        assertEquals(-1, layout.slotAt(5000f, 5000f));
    }

    @Test
    void thePanelIsCenteredInTheWindow() {
        CookingLayout layout = new CookingLayout(1280, 720);
        InventoryLayout.Rect p = layout.panel();
        assertEquals(1280f / 2, p.x() + p.w() / 2, 1f);
        assertEquals(720f / 2, p.y() + p.h() / 2, 1f);
    }

    @Test
    void inputSitsDirectlyAboveFuelAndOutputSitsToTheirRight() {
        CookingLayout layout = new CookingLayout(1280, 720);
        InventoryLayout.Rect input = layout.slotRect(CookingMenu.INPUT_SLOT);
        InventoryLayout.Rect fuel = layout.slotRect(CookingMenu.FUEL_SLOT);
        InventoryLayout.Rect output = layout.slotRect(CookingMenu.OUTPUT_SLOT);
        assertEquals(input.x(), fuel.x(), "input and fuel share a column");
        assertTrue(fuel.y() > input.y(), "fuel is below input");
        assertTrue(output.x() > input.x(), "output is to the right of the input/fuel column");
    }

    @Test
    void invalidSlotIdsAreRejected() {
        CookingLayout layout = new CookingLayout(1280, 720);
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(Inventory.DEFAULT_SLOT_COUNT));
    }
}
