package com.example.inventory;

import com.example.world.BlockType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * The logic behind the inventory screen: the cursor stack, the crafting grid and its result, and
 * what a click on any slot does. No rendering, no input handling - the screen maps mouse
 * positions to slot ids and calls {@link #click}. Items are conserved by construction: every
 * click moves whole stacks or counts between the player inventory, the grid, and the cursor, and
 * {@link #close} hands back anything still in flight rather than discarding it.
 *
 * Slot ids: 0..(player slots - 1) address the player {@link Inventory}; {@link #GRID_BASE} + i
 * the crafting grid; {@link #RESULT_SLOT} the crafting output.
 */
public class InventoryMenu {
    public static final int GRID_BASE = 100;
    public static final int RESULT_SLOT = 1000;

    private final Inventory player;
    private final Inventory grid;
    @Getter
    private final int gridSize;
    @Getter
    private ItemStack cursor = ItemStack.EMPTY;
    @Getter
    private ItemStack result = ItemStack.EMPTY;

    public InventoryMenu(Inventory player, int gridSize) {
        this.player = player;
        this.gridSize = gridSize;
        int cells = gridSize * gridSize;
        this.grid = new Inventory(cells, cells, player.getMaxStack());
    }

    public ItemStack stackAt(int slot) {
        if (slot == RESULT_SLOT) return result;
        if (slot >= GRID_BASE && slot < GRID_BASE + grid.slotCount()) return grid.get(slot - GRID_BASE);
        if (slot >= 0 && slot < player.slotCount()) return player.get(slot);
        throw new IllegalArgumentException("no such slot: " + slot);
    }

    /** A mouse click on a slot. Left: pick up / place all / merge / swap. Right: pick up half / place one. */
    public void click(int slot, boolean right) {
        if (slot == RESULT_SLOT) {
            takeResult();
            return;
        }
        Inventory inv;
        int idx;
        if (slot >= GRID_BASE && slot < GRID_BASE + grid.slotCount()) {
            inv = grid;
            idx = slot - GRID_BASE;
        } else if (slot >= 0 && slot < player.slotCount()) {
            inv = player;
            idx = slot;
        } else {
            throw new IllegalArgumentException("no such slot: " + slot);
        }
        if (right) rightClick(inv, idx);
        else leftClick(inv, idx);
        if (inv == grid) recomputeResult();
    }

    private void leftClick(Inventory inv, int idx) {
        ItemStack s = inv.get(idx);
        int max = inv.getMaxStack();
        if (cursor.isEmpty()) {
            if (!s.isEmpty()) {
                cursor = s;
                inv.set(idx, ItemStack.EMPTY);
            }
        } else if (s.isEmpty()) {
            inv.set(idx, cursor);
            cursor = ItemStack.EMPTY;
        } else if (s.type() == cursor.type()) {
            int move = Math.min(max - s.count(), cursor.count());
            inv.set(idx, s.withCount(s.count() + move));
            cursor = cursor.withCount(cursor.count() - move);
        } else {
            inv.set(idx, cursor);
            cursor = s;
        }
    }

    private void rightClick(Inventory inv, int idx) {
        ItemStack s = inv.get(idx);
        int max = inv.getMaxStack();
        if (cursor.isEmpty()) {
            if (!s.isEmpty()) {
                int take = (s.count() + 1) / 2;
                cursor = new ItemStack(s.type(), take);
                inv.set(idx, s.withCount(s.count() - take));
            }
        } else if (s.isEmpty()) {
            inv.set(idx, new ItemStack(cursor.type(), 1));
            cursor = cursor.withCount(cursor.count() - 1);
        } else if (s.type() == cursor.type() && s.count() < max) {
            inv.set(idx, s.withCount(s.count() + 1));
            cursor = cursor.withCount(cursor.count() - 1);
        } else if (s.type() != cursor.type()) {
            inv.set(idx, cursor);
            cursor = s;
        }
    }

    private void takeResult() {
        if (result.isEmpty()) return;
        if (!cursor.isEmpty()) {
            boolean sameItem = cursor.type() == result.type();
            if (!sameItem || cursor.count() + result.count() > player.getMaxStack()) return;
        }
        cursor = cursor.isEmpty() ? result : cursor.withCount(cursor.count() + result.count());
        for (int i = 0; i < grid.slotCount(); i++) grid.removeOne(i);
        recomputeResult();
    }

    private void recomputeResult() {
        BlockType[] cells = new BlockType[grid.slotCount()];
        for (int i = 0; i < cells.length; i++) cells[i] = grid.get(i).type();
        result = Recipes.craft(cells, gridSize, gridSize);
    }

    /**
     * Removes and returns items from the cursor for dropping into the world (clicking outside the
     * screen): everything on a left click, one item on a right click. EMPTY if the cursor is empty.
     */
    public ItemStack dropFromCursor(boolean single) {
        if (cursor.isEmpty()) return ItemStack.EMPTY;
        int n = single ? 1 : cursor.count();
        ItemStack dropped = new ItemStack(cursor.type(), n);
        cursor = cursor.withCount(cursor.count() - n);
        return dropped;
    }

    /**
     * Closes the screen: the crafting grid and cursor contents go back into the player inventory
     * as far as they fit; whatever doesn't fit is returned so the caller can drop it in the world.
     * Nothing is ever discarded here.
     */
    public List<ItemStack> close() {
        List<ItemStack> leftovers = new ArrayList<>();
        List<ItemStack> inFlight = new ArrayList<>();
        inFlight.add(cursor);
        for (int i = 0; i < grid.slotCount(); i++) {
            inFlight.add(grid.get(i));
            grid.set(i, ItemStack.EMPTY);
        }
        for (ItemStack s : inFlight) {
            if (s.isEmpty()) continue;
            int added = player.add(s.type(), s.count());
            if (added < s.count()) leftovers.add(new ItemStack(s.type(), s.count() - added));
        }
        cursor = ItemStack.EMPTY;
        result = ItemStack.EMPTY;
        return leftovers;
    }

    /** Debug summary of the grid, e.g. "OAK_LOGx1" per non-empty cell. */
    public String gridSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < grid.slotCount(); i++) {
            ItemStack s = grid.get(i);
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(i).append(':').append(s.type().name()).append('x').append(s.count());
        }
        return sb.toString();
    }

    /** How many of {@code type} the cursor and crafting grid currently hold (for conservation checks). */
    public int totalInFlight(BlockType type) {
        int n = cursor.type() == type ? cursor.count() : 0;
        for (int i = 0; i < grid.slotCount(); i++) if (grid.get(i).type() == type) n += grid.get(i).count();
        return n;
    }
}
