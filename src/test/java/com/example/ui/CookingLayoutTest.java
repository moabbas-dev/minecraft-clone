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
    void slotsSitAtVanillaGuiPixelPositions() {
        CookingLayout layout = new CookingLayout(1280, 720, 3);
        InventoryLayout.Rect p = layout.panel();
        assertEquals(528f, p.w());
        assertEquals(498f, p.h());
        check(layout, p, CookingMenu.INPUT_SLOT, 56, 17);
        check(layout, p, CookingMenu.FUEL_SLOT, 56, 53);
        check(layout, p, CookingMenu.OUTPUT_SLOT, 116, 35);
        check(layout, p, Inventory.HOTBAR_SIZE, 8, 84);
        check(layout, p, 0, 8, 142);
        InventoryLayout.Rect arrow = layout.arrow(), flame = layout.flame();
        assertEquals(p.x() + 79 * 3f, arrow.x(), 0.01f);
        assertEquals(p.y() + 34 * 3f, arrow.y(), 0.01f);
        assertEquals(p.x() + 56 * 3f, flame.x(), 0.01f);
        assertEquals(p.y() + 36 * 3f, flame.y(), 0.01f);
    }

    private static void check(CookingLayout layout, InventoryLayout.Rect panel, int slot, int gx, int gy) {
        InventoryLayout.Rect r = layout.slotRect(slot);
        assertEquals(panel.x() + gx * 3f, r.x(), 0.01f, "x of slot " + slot);
        assertEquals(panel.y() + gy * 3f, r.y(), 0.01f, "y of slot " + slot);
        assertEquals(48f, r.w(), 0.01f);
    }

    @Test
    void invalidSlotIdsAreRejected() {
        CookingLayout layout = new CookingLayout(1280, 720);
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(Inventory.DEFAULT_SLOT_COUNT));
    }
}
