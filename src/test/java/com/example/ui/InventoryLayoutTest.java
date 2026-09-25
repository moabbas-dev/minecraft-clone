package com.example.ui;

import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryLayoutTest {

    private static List<Integer> allSlots(int gridSize) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < Inventory.DEFAULT_SLOT_COUNT; i++) ids.add(i);
        for (int i = 0; i < gridSize * gridSize; i++) ids.add(InventoryMenu.GRID_BASE + i);
        ids.add(InventoryMenu.RESULT_SLOT);
        return ids;
    }

    @Test
    void clickingTheCenterOfEverySlotFindsExactlyThatSlot() {
        for (int grid : new int[]{2, 3}) {
            InventoryLayout layout = new InventoryLayout(1280, 720, grid);
            for (int id : allSlots(grid)) {
                InventoryLayout.Rect r = layout.slotRect(id);
                assertEquals(id, layout.slotAt(r.x() + r.w() / 2, r.y() + r.h() / 2), "slot " + id + " grid " + grid);
            }
        }
    }

    @Test
    void noTwoSlotsOverlapAndEverySlotIsInsideThePanel() {
        for (int grid : new int[]{2, 3}) {
            InventoryLayout layout = new InventoryLayout(1280, 720, grid);
            List<Integer> ids = allSlots(grid);
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
    }

    @Test
    void gapsBetweenSlotsAndPositionsOutsideThePanelHitNothing() {
        InventoryLayout layout = new InventoryLayout(1280, 720, 2);
        InventoryLayout.Rect s0 = layout.slotRect(0);
        assertEquals(-1, layout.slotAt(s0.x() + s0.w() + 2f, s0.y() + 5f), "the gap between slots");
        assertEquals(-1, layout.slotAt(0f, 0f));
        assertEquals(-1, layout.slotAt(5000f, 5000f));
    }

    @Test
    void thePanelIsCenteredInTheWindow() {
        InventoryLayout layout = new InventoryLayout(1280, 720, 3);
        InventoryLayout.Rect p = layout.panel();
        assertEquals(1280f / 2, p.x() + p.w() / 2, 1f);
        assertEquals(720f / 2, p.y() + p.h() / 2, 1f);
    }

    @Test
    void hotbarSitsBelowTheStorageRowsAndSlotZeroIsTheLeftmostHotbarSlot() {
        InventoryLayout layout = new InventoryLayout(1280, 720, 2);
        assertTrue(layout.slotRect(0).y() > layout.slotRect(35).y(), "hotbar is below the storage rows");
        assertTrue(layout.slotRect(0).x() < layout.slotRect(1).x());
    }

    @Test
    void invalidSlotIdsAreRejected() {
        InventoryLayout layout = new InventoryLayout(1280, 720, 2);
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(Inventory.DEFAULT_SLOT_COUNT));
        assertThrows(IllegalArgumentException.class, () -> layout.slotRect(InventoryMenu.GRID_BASE + 4));
    }
}
