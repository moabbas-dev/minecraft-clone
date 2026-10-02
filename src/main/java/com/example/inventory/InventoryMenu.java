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
    /** Armor slots: ARMOR_BASE + EquipmentSlot ordinal (head, chest, legs, feet). */
    public static final int ARMOR_BASE = 2000;

    private final Inventory player;
    private final Inventory grid;
    private final PlayerArmor armor;
    @Getter
    private final int gridSize;
    @Getter
    private ItemStack cursor = ItemStack.EMPTY;
    @Getter
    private ItemStack result = ItemStack.EMPTY;

    public InventoryMenu(Inventory player, int gridSize) {
        this(player, null, gridSize);
    }

    public InventoryMenu(Inventory player, PlayerArmor armor, int gridSize) {
        this.player = player;
        this.armor = armor;
        this.gridSize = gridSize;
        int cells = gridSize * gridSize;
        this.grid = new Inventory(cells, cells, player.getMaxStack());
    }

    public ItemStack stackAt(int slot) {
        if (slot == RESULT_SLOT) return result;
        if (slot >= ARMOR_BASE && slot < ARMOR_BASE + 4 && armor != null) return armor.get(com.example.combat.EquipmentSlot.values()[slot - ARMOR_BASE]);
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
        if (slot >= ARMOR_BASE && slot < ARMOR_BASE + 4) {
            if (armor != null) clickArmor(com.example.combat.EquipmentSlot.values()[slot - ARMOR_BASE]);
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

    /** Armor slot click: pick the worn piece up, or place/swap a matching piece (wrong-slot items are ignored). */
    private void clickArmor(com.example.combat.EquipmentSlot slot) {
        ItemStack worn = armor.get(slot);
        if (cursor.isEmpty()) {
            if (!worn.isEmpty()) {
                cursor = worn;
                armor.set(slot, ItemStack.EMPTY);
            }
        } else if (PlayerArmor.canEquip(cursor) && com.example.combat.ArmorStats.of(cursor.type()).slot() == slot) {
            armor.set(slot, cursor.withCount(1));
            cursor = worn;
        }
    }

    private void leftClick(Inventory inv, int idx) {
        cursor = leftClickSlot(inv, idx, cursor);
    }

    private void rightClick(Inventory inv, int idx) {
        cursor = rightClickSlot(inv, idx, cursor);
    }

    /**
     * The pick-up/place-all/merge/swap primitive behind a left click on any {@link Inventory}
     * slot, factored out so other menus (see {@code com.example.cooking.CookingMenu}) can reuse
     * the exact same rule instead of duplicating it. Pure: reads/writes only {@code inv}'s one
     * slot and returns what the cursor becomes - the caller owns storing that.
     */
    public static ItemStack leftClickSlot(Inventory inv, int idx, ItemStack cursor) {
        ItemStack s = inv.get(idx);
        int max = ItemStack.maxStackFor(cursor.type(), inv.getMaxStack());
        if (cursor.isEmpty()) {
            if (!s.isEmpty()) {
                inv.set(idx, ItemStack.EMPTY);
                return s;
            }
            return cursor;
        } else if (s.isEmpty()) {
            inv.set(idx, cursor);
            return ItemStack.EMPTY;
        } else if (s.sameItem(cursor) && max > 1) {
            int move = Math.min(max - s.count(), cursor.count());
            inv.set(idx, s.withCount(s.count() + move));
            return cursor.withCount(cursor.count() - move);
        } else {
            inv.set(idx, cursor);
            return s;
        }
    }

    /** Same as {@link #leftClickSlot}, for a right click (pick up half / place one). */
    public static ItemStack rightClickSlot(Inventory inv, int idx, ItemStack cursor) {
        ItemStack s = inv.get(idx);
        int max = ItemStack.maxStackFor(cursor.type(), inv.getMaxStack());
        if (cursor.isEmpty()) {
            if (!s.isEmpty()) {
                int take = (s.count() + 1) / 2;
                inv.set(idx, s.withCount(s.count() - take));
                return s.withCount(take);
            }
            return cursor;
        } else if (s.isEmpty()) {
            inv.set(idx, cursor.withCount(1));
            return cursor.withCount(cursor.count() - 1);
        } else if (s.sameItem(cursor) && s.count() < max) {
            inv.set(idx, s.withCount(s.count() + 1));
            return cursor.withCount(cursor.count() - 1);
        } else if (!s.sameItem(cursor)) {
            inv.set(idx, cursor);
            return s;
        }
        return cursor;
    }

    private void takeResult() {
        if (result.isEmpty()) return;
        if (!cursor.isEmpty()) {
            boolean sameItem = cursor.sameItem(result);
            if (!sameItem || cursor.count() + result.count() > ItemStack.maxStackFor(result.type(), player.getMaxStack())) return;
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
        ItemStack dropped = cursor.withCount(n);
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
            int added = player.add(s.type(), s.count(), s.damage());
            if (added < s.count()) leftovers.add(s.withCount(s.count() - added));
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
