package com.example.ui;

import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;

/**
 * Where everything on the inventory screen sits, and which slot a mouse position is over. Pure
 * geometry (no rendering, no GL) so the hit-testing is unit-testable. Coordinates are in the
 * same framebuffer pixels the mouse and UiRenderer use.
 *
 * Layout, top to bottom: crafting grid (2x2 or 3x3) with an arrow and the result slot on the
 * right, the 27 storage slots (player slots 9..35), then the 9 hotbar slots (0..8).
 */
public final class InventoryLayout {
    public static final float SLOT = 40f;
    public static final float GAP = 4f;
    public static final float PITCH = SLOT + GAP;
    public static final float PAD = 16f;
    public static final float LABEL_H = 30f;

    public record Rect(float x, float y, float w, float h) {
        public boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final int gridSize;
    private final Rect panel;
    private final float craftY, storageY, hotbarY, gridX, resultX, panelX;

    public InventoryLayout(int windowW, int windowH, int gridSize) {
        this.gridSize = gridSize;
        float gridH = gridSize * PITCH - GAP;
        float panelW = 9 * PITCH - GAP + 2 * PAD;
        float panelH = PAD + LABEL_H + gridH + 12f + LABEL_H + 3 * PITCH - GAP + 10f + SLOT + PAD;
        panelX = Math.round((windowW - panelW) / 2f);
        float panelY = Math.round((windowH - panelH) / 2f);
        panel = new Rect(panelX, panelY, panelW, panelH);

        craftY = panelY + PAD + LABEL_H;
        storageY = craftY + gridH + 12f + LABEL_H;
        hotbarY = storageY + 3 * PITCH - GAP + 10f;
        resultX = panelX + panelW - PAD - SLOT - PITCH;
        gridX = resultX - 56f - (gridSize * PITCH - GAP);
    }

    public Rect panel() {
        return panel;
    }

    public int gridSize() {
        return gridSize;
    }

    public float craftingLabelY() {
        return craftY - LABEL_H;
    }

    public float inventoryLabelY() {
        return storageY - LABEL_H;
    }

    public float arrowX() {
        return gridX + gridSize * PITCH - GAP + 10f;
    }

    public float arrowY() {
        return craftY + (gridSize * PITCH - GAP) / 2f;
    }

    public float leftX() {
        return panelX + PAD;
    }

    /** The rectangle for a menu slot id (player slot, {@link InventoryMenu#GRID_BASE}+i, or the result slot). */
    public Rect slotRect(int slot) {
        if (slot >= InventoryMenu.ARMOR_BASE && slot < InventoryMenu.ARMOR_BASE + 4) {
            int i = slot - InventoryMenu.ARMOR_BASE; // head, chest / legs, feet in a 2x2 block
            return new Rect(panelX + PAD + (i % 2) * PITCH, craftY + (i / 2) * PITCH, SLOT, SLOT);
        }
        if (slot == InventoryMenu.RESULT_SLOT) {
            return new Rect(resultX, craftY + (gridSize * PITCH - GAP - SLOT) / 2f, SLOT, SLOT);
        }
        if (slot >= InventoryMenu.GRID_BASE) {
            int i = slot - InventoryMenu.GRID_BASE;
            if (i >= gridSize * gridSize) throw new IllegalArgumentException("no such grid slot: " + slot);
            return new Rect(gridX + (i % gridSize) * PITCH, craftY + (i / gridSize) * PITCH, SLOT, SLOT);
        }
        if (slot < 0 || slot >= Inventory.DEFAULT_SLOT_COUNT) throw new IllegalArgumentException("no such slot: " + slot);
        float x0 = panelX + PAD;
        if (slot < Inventory.HOTBAR_SIZE) return new Rect(x0 + slot * PITCH, hotbarY, SLOT, SLOT);
        int storage = slot - Inventory.HOTBAR_SIZE;
        return new Rect(x0 + (storage % 9) * PITCH, storageY + (storage / 9) * PITCH, SLOT, SLOT);
    }

    /** The slot id under the mouse, or -1 if it's over none. */
    public int slotAt(float mx, float my) {
        if (!panel.contains(mx, my)) return -1;
        for (int s = 0; s < Inventory.DEFAULT_SLOT_COUNT; s++) if (slotRect(s).contains(mx, my)) return s;
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (slotRect(InventoryMenu.GRID_BASE + i).contains(mx, my)) return InventoryMenu.GRID_BASE + i;
        }
        if (slotRect(InventoryMenu.RESULT_SLOT).contains(mx, my)) return InventoryMenu.RESULT_SLOT;
        for (int i = 0; i < 4; i++) {
            if (slotRect(InventoryMenu.ARMOR_BASE + i).contains(mx, my)) return InventoryMenu.ARMOR_BASE + i;
        }
        return -1;
    }
}
