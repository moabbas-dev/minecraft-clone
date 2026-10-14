package com.example.ui;

import com.example.cooking.CookingMenu;
import com.example.inventory.Inventory;

/**
 * Where everything on a cooking-station screen (Furnace / Blast Furnace / Smoker) sits, and which slot a mouse
 * position is over. Pure geometry with vanilla's numbers (docs/vanilla-rules.md section 6): a 176 x 166 GUI-pixel
 * panel at the GUI scale, input at (56,17), fuel at (56,53), output at (116,35), the flame at (56,36), the progress
 * arrow at (79,34), then the 27 storage slots at (8,84) and the hotbar at y=142 - the same bottom half as
 * {@link InventoryLayout}.
 */
public final class CookingLayout {
    private final float s;
    private final float left, top;
    private final InventoryLayout.Rect panel;

    public CookingLayout(int windowW, int windowH) {
        this(windowW, windowH, GuiScale.compute(windowW, windowH, 0));
    }

    public CookingLayout(int windowW, int windowH, int scale) {
        this.s = scale;
        this.left = Math.round((windowW - InventoryLayout.PANEL_W * s) / 2f);
        this.top = Math.round((windowH - InventoryLayout.PANEL_H * s) / 2f);
        this.panel = new InventoryLayout.Rect(left, top, InventoryLayout.PANEL_W * s, InventoryLayout.PANEL_H * s);
    }

    public InventoryLayout.Rect panel() {
        return panel;
    }

    public float scale() {
        return s;
    }

    private InventoryLayout.Rect item(float gx, float gy) {
        return new InventoryLayout.Rect(left + gx * s, top + gy * s, InventoryLayout.ITEM * s, InventoryLayout.ITEM * s);
    }

    /** Text origin of the title, centred like vanilla's furnace screens: pass the title's pixel width in GUI pixels. */
    public float titleX(float titleWidthGui) {
        return left + (InventoryLayout.PANEL_W - titleWidthGui) / 2f * s;
    }

    public float titleY() {
        return top + 6 * s;
    }

    public float inventoryLabelX() {
        return left + 8 * s;
    }

    public float inventoryLabelY() {
        return top + 72 * s;
    }

    /** The 22 x 15 progress arrow at (80,35) (measured from the real furnace screen). */
    public InventoryLayout.Rect arrow() {
        return new InventoryLayout.Rect(left + 80 * s, top + 35 * s, 22 * s, 15 * s);
    }

    /** The 14 x 14 area of the flame icon. */
    public InventoryLayout.Rect flame() {
        return new InventoryLayout.Rect(left + 56 * s, top + 36 * s, 14 * s, 14 * s);
    }

    /** The rectangle for a menu slot id (player slot, or one of CookingMenu's 3 station slots). */
    public InventoryLayout.Rect slotRect(int slot) {
        if (slot == CookingMenu.INPUT_SLOT) return item(56, 17);
        if (slot == CookingMenu.FUEL_SLOT) return item(56, 53);
        if (slot == CookingMenu.OUTPUT_SLOT) return item(116, 35);
        if (slot < 0 || slot >= Inventory.DEFAULT_SLOT_COUNT) throw new IllegalArgumentException("no such slot: " + slot);
        if (slot < Inventory.HOTBAR_SIZE) return item(8 + slot * InventoryLayout.PITCH, 142);
        int storage = slot - Inventory.HOTBAR_SIZE;
        return item(8 + (storage % 9) * InventoryLayout.PITCH, 84 + (storage / 9) * InventoryLayout.PITCH);
    }

    /** The slot id under the mouse, or -1 if it's over none. */
    public int slotAt(float mx, float my) {
        if (!panel.contains(mx, my)) return -1;
        for (int sl = 0; sl < Inventory.DEFAULT_SLOT_COUNT; sl++) if (slotRect(sl).contains(mx, my)) return sl;
        for (int slot : new int[]{CookingMenu.INPUT_SLOT, CookingMenu.FUEL_SLOT, CookingMenu.OUTPUT_SLOT}) {
            if (slotRect(slot).contains(mx, my)) return slot;
        }
        return -1;
    }
}
