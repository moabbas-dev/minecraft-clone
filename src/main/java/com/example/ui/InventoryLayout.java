package com.example.ui;

import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;

/**
 * Where everything on the inventory screen sits, and which slot a mouse position is over. Pure geometry (no
 * rendering, no GL) so the hit-testing is unit-testable. Coordinates are framebuffer pixels, the same ones the mouse
 * and UiRenderer use.
 *
 * The geometry is vanilla's (docs/vanilla-rules.md section 6): a 176 x 166 GUI-pixel panel drawn at the GUI scale,
 * with 18 px slot pitch and a 16 x 16 item area per slot. The player inventory (grid 2) has the armor column at
 * x=8, the 2x2 crafting grid at (98,18) and its result at (154,28); the crafting table (grid 3) has the 3x3 grid at
 * (30,17) and its result at (124,35); the player inventory also has the offhand slot at (77,62). Both put the 27 storage slots at (8,84) and the hotbar at y=142.
 */
public final class InventoryLayout {
    public static final int PANEL_W = 176, PANEL_H = 166;
    /** Slot centre-to-centre distance and the item area inside one slot, in GUI pixels. */
    public static final int PITCH = 18, ITEM = 16;

    public record Rect(float x, float y, float w, float h) {
        public boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final int gridSize;
    private final float s;
    private final float left, top;
    private final Rect panel;

    /** Auto GUI scale for the window, the same one the menus use. */
    public InventoryLayout(int windowW, int windowH, int gridSize) {
        this(windowW, windowH, gridSize, GuiScale.compute(windowW, windowH, 0));
    }

    public InventoryLayout(int windowW, int windowH, int gridSize, int scale) {
        this.gridSize = gridSize;
        this.s = scale;
        this.left = Math.round((windowW - PANEL_W * s) / 2f);
        this.top = Math.round((windowH - PANEL_H * s) / 2f);
        this.panel = new Rect(left, top, PANEL_W * s, PANEL_H * s);
    }

    public Rect panel() {
        return panel;
    }

    public int gridSize() {
        return gridSize;
    }

    public float scale() {
        return s;
    }

    /** True for the player inventory (2x2 grid, armor column), false for the crafting table. */
    public boolean hasArmorSlots() {
        return gridSize == 2;
    }

    private Rect item(float gx, float gy) {
        return new Rect(left + gx * s, top + gy * s, ITEM * s, ITEM * s);
    }

    /** Text origin for the screen title ("Crafting"): (97,8) in the player inventory, (28,6) at the table. */
    public float titleX() {
        return left + (gridSize == 2 ? 97 : 28) * s;
    }

    public float titleY() {
        return top + (gridSize == 2 ? 8 : 6) * s;
    }

    public float inventoryLabelX() {
        return left + 8 * s;
    }

    public float inventoryLabelY() {
        return top + 72 * s;
    }

    /** The "crafts into" arrow between the grid and the result: 16x13 at (135,29) in the player inventory, 22x15 at (90,35) at the table. */
    public Rect arrow() {
        return gridSize == 2 ? new Rect(left + 135 * s, top + 29 * s, 16 * s, 13 * s)
                : new Rect(left + 90 * s, top + 35 * s, 22 * s, 15 * s);
    }

    /** The inset box where vanilla shows the player preview (player inventory only). */
    public Rect previewBox() {
        return new Rect(left + 26 * s, top + 8 * s, 49 * s, 70 * s);
    }

    /** The rectangle for a menu slot id (player slot, {@link InventoryMenu#GRID_BASE}+i, the result, or armor). */
    public Rect slotRect(int slot) {
        if (slot == InventoryMenu.OFFHAND_SLOT) {
            if (!hasArmorSlots()) throw new IllegalArgumentException("the crafting table has no offhand slot");
            return item(77, 62);
        }
        if (slot >= InventoryMenu.ARMOR_BASE && slot < InventoryMenu.ARMOR_BASE + 4) {
            if (!hasArmorSlots()) throw new IllegalArgumentException("the crafting table has no armor slots: " + slot);
            return item(8, 8 + (slot - InventoryMenu.ARMOR_BASE) * PITCH);
        }
        if (slot == InventoryMenu.RESULT_SLOT) {
            return gridSize == 2 ? item(154, 28) : item(124, 35);
        }
        if (slot >= InventoryMenu.GRID_BASE) {
            int i = slot - InventoryMenu.GRID_BASE;
            if (i >= gridSize * gridSize) throw new IllegalArgumentException("no such grid slot: " + slot);
            int col = i % gridSize, row = i / gridSize;
            return gridSize == 2 ? item(98 + col * PITCH, 18 + row * PITCH) : item(30 + col * PITCH, 17 + row * PITCH);
        }
        if (slot < 0 || slot >= Inventory.DEFAULT_SLOT_COUNT) throw new IllegalArgumentException("no such slot: " + slot);
        if (slot < Inventory.HOTBAR_SIZE) return item(8 + slot * PITCH, 142);
        int storage = slot - Inventory.HOTBAR_SIZE;
        return item(8 + (storage % 9) * PITCH, 84 + (storage / 9) * PITCH);
    }

    /** The slot id under the mouse, or -1 if it's over none. */
    public int slotAt(float mx, float my) {
        if (!panel.contains(mx, my)) return -1;
        for (int sl = 0; sl < Inventory.DEFAULT_SLOT_COUNT; sl++) if (slotRect(sl).contains(mx, my)) return sl;
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (slotRect(InventoryMenu.GRID_BASE + i).contains(mx, my)) return InventoryMenu.GRID_BASE + i;
        }
        if (slotRect(InventoryMenu.RESULT_SLOT).contains(mx, my)) return InventoryMenu.RESULT_SLOT;
        if (hasArmorSlots() && slotRect(InventoryMenu.OFFHAND_SLOT).contains(mx, my)) return InventoryMenu.OFFHAND_SLOT;
        if (hasArmorSlots()) {
            for (int i = 0; i < 4; i++) {
                if (slotRect(InventoryMenu.ARMOR_BASE + i).contains(mx, my)) return InventoryMenu.ARMOR_BASE + i;
            }
        }
        return -1;
    }
}
