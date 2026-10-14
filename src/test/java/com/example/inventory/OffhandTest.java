package com.example.inventory;

import com.example.ui.InventoryLayout;
import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** The offhand slot: storage, pick-up, clicks, F swaps, shift-click and the saved format. */
class OffhandTest {
    private final Inventory inv = new Inventory();
    private final InventoryMenu menu = new InventoryMenu(inv, null, 2);

    @Test
    void startsEmptyAndIsNotOneOfTheNormalSlots() {
        assertTrue(inv.getOffhand().isEmpty());
        assertEquals(36, inv.slotCount());
    }

    @Test
    void pickedUpItemsTopUpAMatchingOffhandStackButNeverFillAnEmptyOffhand() {
        assertEquals(5, inv.add(BlockType.DIRT, 5));
        assertTrue(inv.getOffhand().isEmpty(), "an empty offhand is never filled by pick-ups");
        inv.setOffhand(new ItemStack(BlockType.COBBLESTONE, 60));
        inv.add(BlockType.COBBLESTONE, 10);
        assertEquals(64, inv.getOffhand().count(), "a matching offhand stack is topped up first");
        assertEquals(6, inv.count(BlockType.COBBLESTONE) - 64 + 0, "the rest went to a normal slot");
    }

    @Test
    void countAndRemoveSeeTheOffhand() {
        inv.setOffhand(new ItemStack(BlockType.STICK, 3));
        inv.add(BlockType.STICK, 2);
        assertEquals(5, inv.count(BlockType.STICK));
        assertEquals(4, inv.remove(BlockType.STICK, 4));
        assertEquals(1, inv.count(BlockType.STICK));
    }

    @Test
    void clicksOnTheOffhandSlotBehaveLikeAnyOtherSlot() {
        inv.set(0, new ItemStack(BlockType.DIRT, 10));
        menu.click(0, false);
        menu.click(InventoryMenu.OFFHAND_SLOT, false);
        assertEquals(10, inv.getOffhand().count());
        assertTrue(menu.getCursor().isEmpty());
        menu.click(InventoryMenu.OFFHAND_SLOT, true); // right click takes half
        assertEquals(5, menu.getCursor().count());
        assertEquals(5, inv.getOffhand().count());
    }

    @Test
    void fSwapsTheHoveredSlotWithTheOffhand() {
        inv.set(12, new ItemStack(BlockType.GLASS, 3));
        inv.setOffhand(new ItemStack(BlockType.SAND, 7));
        menu.swapWithOffhand(12);
        assertEquals(BlockType.SAND, inv.get(12).type());
        assertEquals(BlockType.GLASS, inv.getOffhand().type());
        assertEquals(3, inv.getOffhand().count());
    }

    @Test
    void shiftClickMovesTheOffhandStackBackIntoTheInventory() {
        inv.setOffhand(new ItemStack(BlockType.SAND, 7));
        menu.quickMove(InventoryMenu.OFFHAND_SLOT);
        assertTrue(inv.getOffhand().isEmpty());
        assertEquals(7, inv.count(BlockType.SAND));
    }

    @Test
    void theOffhandIsSavedAndLoaded() throws IOException {
        inv.setOffhand(new ItemStack(BlockType.APPLE, 4));
        inv.set(3, new ItemStack(BlockType.DIRT, 9));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inv.write(new DataOutputStream(bytes));
        Inventory loaded = Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(new ItemStack(BlockType.APPLE, 4), loaded.getOffhand());
        assertEquals(9, loaded.get(3).count());
    }

    @Test
    void savesFromBeforeTheOffhandExistedStillLoad() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(36); // the old format: exactly the 36 normal slots
        out.writeInt(2);
        for (int i = 0; i < 36; i++) {
            out.writeInt(i == 5 ? BlockType.DIRT.id : 0);
            out.writeInt(i == 5 ? 12 : 0);
        }
        Inventory loaded = Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(12, loaded.get(5).count());
        assertTrue(loaded.getOffhand().isEmpty());
        assertEquals(2, loaded.getSelectedSlot());
    }

    @Test
    void theOffhandSlotSitsAtGuiPixel77x62OnlyInThePlayerInventory() {
        InventoryLayout layout = new InventoryLayout(1280, 720, 2, 1);
        InventoryLayout.Rect panel = layout.panel();
        InventoryLayout.Rect r = layout.slotRect(InventoryMenu.OFFHAND_SLOT);
        assertEquals(panel.x() + 77, r.x(), 1e-4f);
        assertEquals(panel.y() + 62, r.y(), 1e-4f);
        assertEquals(InventoryMenu.OFFHAND_SLOT, layout.slotAt(r.x() + 3, r.y() + 3));
        InventoryLayout table = new InventoryLayout(1280, 720, 3, 1);
        assertThrows(IllegalArgumentException.class, () -> table.slotRect(InventoryMenu.OFFHAND_SLOT));
    }
}
