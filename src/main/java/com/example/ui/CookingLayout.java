package com.example.ui;

import com.example.cooking.CookingMenu;
import com.example.inventory.Inventory;

/**
 * Where everything on a cooking-station screen (Furnace/Blast Furnace/Smoker - see
 * {@code com.example.cooking.CookingStationType}) sits, and which slot a mouse position is over.
 * Pure geometry, mirroring {@link InventoryLayout}'s exact shape and conventions (same panel
 * width, same storage/hotbar layout below) so the two screens read as one family.
 *
 * Layout, top to bottom: input slot above the fuel slot (left column) with the output slot to
 * their right, then the 27 storage slots and the 9 hotbar slots - identical to InventoryLayout's
 * own bottom section.
 */
public final class CookingLayout {
    public static final float SLOT = InventoryLayout.SLOT;
    public static final float GAP = InventoryLayout.GAP;
    public static final float PITCH = InventoryLayout.PITCH;
    public static final float PAD = InventoryLayout.PAD;
    public static final float LABEL_H = InventoryLayout.LABEL_H;

    public record Rect(float x, float y, float w, float h) {
        public boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final InventoryLayout.Rect panel;
    private final float inputY, fuelY, storageY, hotbarY, inputX, outputX, panelX;

    public CookingLayout(int windowW, int windowH) {
        float columnH = 2 * PITCH - GAP; // input + fuel, stacked
        float panelW = 9 * PITCH - GAP + 2 * PAD;
        float panelH = PAD + LABEL_H + columnH + 12f + LABEL_H + 3 * PITCH - GAP + 10f + SLOT + PAD;
        panelX = Math.round((windowW - panelW) / 2f);
        float panelY = Math.round((windowH - panelH) / 2f);
        panel = new InventoryLayout.Rect(panelX, panelY, panelW, panelH);

        inputY = panelY + PAD + LABEL_H;
        fuelY = inputY + PITCH;
        storageY = inputY + columnH + 12f + LABEL_H;
        hotbarY = storageY + 3 * PITCH - GAP + 10f;
        inputX = panelX + PAD;
        outputX = inputX + PITCH * 3f;
    }

    public InventoryLayout.Rect panel() {
        return panel;
    }

    public float titleLabelY() {
        return inputY - LABEL_H;
    }

    public float inventoryLabelY() {
        return storageY - LABEL_H;
    }

    public float arrowX() {
        return inputX + PITCH + 10f;
    }

    public float arrowY() {
        return inputY + (PITCH - GAP) / 2f + GAP / 2f;
    }

    public float leftX() {
        return panelX + PAD;
    }

    /** The rectangle for a menu slot id (player slot, or one of CookingMenu's 3 station slots). */
    public InventoryLayout.Rect slotRect(int slot) {
        if (slot == CookingMenu.INPUT_SLOT) return new InventoryLayout.Rect(inputX, inputY, SLOT, SLOT);
        if (slot == CookingMenu.FUEL_SLOT) return new InventoryLayout.Rect(inputX, fuelY, SLOT, SLOT);
        if (slot == CookingMenu.OUTPUT_SLOT) return new InventoryLayout.Rect(outputX, inputY + (PITCH - GAP) / 2f, SLOT, SLOT);
        if (slot < 0 || slot >= Inventory.DEFAULT_SLOT_COUNT) throw new IllegalArgumentException("no such slot: " + slot);
        float x0 = panelX + PAD;
        if (slot < Inventory.HOTBAR_SIZE) return new InventoryLayout.Rect(x0 + slot * PITCH, hotbarY, SLOT, SLOT);
        int storage = slot - Inventory.HOTBAR_SIZE;
        return new InventoryLayout.Rect(x0 + (storage % 9) * PITCH, storageY + (storage / 9) * PITCH, SLOT, SLOT);
    }

    /** The slot id under the mouse, or -1 if it's over none. */
    public int slotAt(float mx, float my) {
        if (!panel.contains(mx, my)) return -1;
        for (int s = 0; s < Inventory.DEFAULT_SLOT_COUNT; s++) if (slotRect(s).contains(mx, my)) return s;
        for (int slot : new int[]{CookingMenu.INPUT_SLOT, CookingMenu.FUEL_SLOT, CookingMenu.OUTPUT_SLOT}) {
            if (slotRect(slot).contains(mx, my)) return slot;
        }
        return -1;
    }
}
