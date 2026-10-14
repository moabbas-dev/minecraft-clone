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
    /** The offhand slot (vanilla's menu slot 45 is a different number; this id only has to be unused). */
    public static final int OFFHAND_SLOT = 3000;

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
        if (slot == OFFHAND_SLOT) return player.getOffhand();
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
        if (slot == OFFHAND_SLOT) {
            if (right) rightClick(player.offhandSlot(), 0);
            else leftClick(player.offhandSlot(), 0);
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

    // ---- vanilla's extra mouse and keyboard interactions (docs/vanilla-rules.md section 6) ----

    private boolean isPlayerSlot(int slot) {
        return slot >= 0 && slot < player.slotCount();
    }

    private boolean isGridSlot(int slot) {
        return slot >= GRID_BASE && slot < GRID_BASE + grid.slotCount();
    }

    /**
     * Puts as much of {@code stack} as fits into slots [from, to) of {@code inv}: first onto matching stacks, then into
     * empty slots. Returns whatever did not fit.
     */
    private static ItemStack insertRange(Inventory inv, ItemStack stack, int from, int to) {
        if (stack.isEmpty()) return stack;
        int max = ItemStack.maxStackFor(stack.type(), inv.getMaxStack());
        ItemStack rest = stack;
        if (max > 1) {
            for (int i = from; i < to && !rest.isEmpty(); i++) {
                ItemStack s = inv.get(i);
                if (s.sameItem(rest) && s.count() < max) {
                    int move = Math.min(max - s.count(), rest.count());
                    inv.set(i, s.withCount(s.count() + move));
                    rest = rest.withCount(rest.count() - move);
                }
            }
        }
        for (int i = from; i < to && !rest.isEmpty(); i++) {
            if (inv.get(i).isEmpty()) {
                int move = Math.min(max, rest.count());
                inv.set(i, rest.withCount(move));
                rest = rest.withCount(rest.count() - move);
            }
        }
        return rest;
    }

    /** Hotbar slots 0..8 are the first player slots, storage is 9..35. */
    private ItemStack insertIntoPlayer(ItemStack stack) {
        ItemStack rest = insertRange(player, stack, Inventory.HOTBAR_SIZE, player.slotCount());
        return insertRange(player, rest, 0, Inventory.HOTBAR_SIZE);
    }

    /**
     * Shift-click: moves the whole stack to the other half of the screen. Crafting-grid and armor stacks go back to the
     * inventory; an armor piece is worn if its slot is free; hotbar stacks go to storage and storage stacks to the hotbar;
     * the result slot crafts as many as fit ({@link #craftAll}).
     */
    public void quickMove(int slot) {
        if (slot == RESULT_SLOT) {
            craftAll();
            return;
        }
        if (slot == OFFHAND_SLOT) {
            ItemStack off = player.getOffhand();
            if (!off.isEmpty()) player.setOffhand(insertIntoPlayer(off));
            return;
        }
        if (slot >= ARMOR_BASE && slot < ARMOR_BASE + 4) {
            if (armor == null) return;
            com.example.combat.EquipmentSlot es = com.example.combat.EquipmentSlot.values()[slot - ARMOR_BASE];
            ItemStack worn = armor.get(es);
            if (worn.isEmpty()) return;
            ItemStack rest = insertIntoPlayer(worn);
            armor.set(es, rest);
            return;
        }
        if (isGridSlot(slot)) {
            int idx = slot - GRID_BASE;
            ItemStack s = grid.get(idx);
            if (s.isEmpty()) return;
            grid.set(idx, insertIntoPlayer(s));
            recomputeResult();
            return;
        }
        if (!isPlayerSlot(slot)) return;
        ItemStack s = player.get(slot);
        if (s.isEmpty()) return;
        if (armor != null && PlayerArmor.canEquip(s)) {
            com.example.combat.EquipmentSlot es = com.example.combat.ArmorStats.of(s.type()).slot();
            if (armor.get(es).isEmpty()) {
                armor.set(es, s.withCount(1));
                player.set(slot, s.count() > 1 ? s.withCount(s.count() - 1) : ItemStack.EMPTY);
                return;
            }
        }
        ItemStack rest = slot < Inventory.HOTBAR_SIZE
                ? insertRange(player, s, Inventory.HOTBAR_SIZE, player.slotCount())
                : insertRange(player, s, 0, Inventory.HOTBAR_SIZE);
        player.set(slot, rest);
    }

    /**
     * Shift-click on the result: crafts repeatedly, sending each result straight to the inventory, until the ingredients
     * run out or a whole result no longer fits (it never crafts into thin air).
     */
    public void craftAll() {
        for (int guard = 0; guard < 600 && !result.isEmpty(); guard++) {
            ItemStack made = result;
            int max = ItemStack.maxStackFor(made.type(), player.getMaxStack());
            int room = 0;
            for (int i = 0; i < player.slotCount(); i++) {
                ItemStack s = player.get(i);
                if (s.isEmpty()) room += max;
                else if (s.sameItem(made)) room += Math.max(0, max - s.count());
            }
            if (room < made.count()) return;
            insertIntoPlayer(made);
            for (int i = 0; i < grid.slotCount(); i++) grid.removeOne(i);
            recomputeResult();
        }
    }

    /**
     * Double-click while holding a stack: pulls matching items from the whole inventory and crafting grid into the
     * cursor stack up to a full stack, taking from partly-filled stacks first, then full ones.
     */
    public void gatherSame(int clicked) {
        if (cursor.isEmpty()) return;
        int max = ItemStack.maxStackFor(cursor.type(), player.getMaxStack());
        if (max <= 1) return;
        for (int pass = 0; pass < 2 && cursor.count() < max; pass++) {
            for (int i = 0; i < player.slotCount() + grid.slotCount() && cursor.count() < max; i++) {
                boolean isGrid = i >= player.slotCount();
                Inventory inv = isGrid ? grid : player;
                int idx = isGrid ? i - player.slotCount() : i;
                ItemStack s = inv.get(idx);
                if (!s.sameItem(cursor)) continue;
                boolean full = s.count() >= max;
                if ((pass == 0) == full) continue;
                int take = Math.min(max - cursor.count(), s.count());
                inv.set(idx, s.count() == take ? ItemStack.EMPTY : s.withCount(s.count() - take));
                cursor = cursor.withCount(cursor.count() + take);
            }
        }
        recomputeResult();
    }

    /** Number key while hovering a slot (cursor empty): swaps that slot's stack with hotbar slot {@code hotbar} (0..8). */
    public void swapWithHotbar(int slot, int hotbar) {
        if (!cursor.isEmpty() || hotbar < 0 || hotbar >= Inventory.HOTBAR_SIZE) return;
        if (isPlayerSlot(slot)) {
            ItemStack a = player.get(slot), b = player.get(hotbar);
            player.set(slot, b);
            player.set(hotbar, a);
        } else if (isGridSlot(slot)) {
            ItemStack a = grid.get(slot - GRID_BASE), b = player.get(hotbar);
            grid.set(slot - GRID_BASE, b);
            player.set(hotbar, a);
            recomputeResult();
        }
    }

    /** F while hovering a slot (cursor empty): swaps that slot's stack with the offhand stack. */
    public void swapWithOffhand(int slot) {
        if (!cursor.isEmpty()) return;
        if (isPlayerSlot(slot)) {
            ItemStack a = player.get(slot), b = player.getOffhand();
            player.set(slot, b);
            player.setOffhand(a);
        } else if (isGridSlot(slot)) {
            ItemStack a = grid.get(slot - GRID_BASE), b = player.getOffhand();
            grid.set(slot - GRID_BASE, b);
            player.setOffhand(a);
            recomputeResult();
        }
    }

    /** Whether a slot can take part in a drag-distribute with the current cursor stack. */
    public boolean canDragInto(int slot) {
        if (cursor.isEmpty() || !(isPlayerSlot(slot) || isGridSlot(slot))) return false;
        ItemStack s = stackAt(slot);
        int max = ItemStack.maxStackFor(cursor.type(), player.getMaxStack());
        return s.isEmpty() || (s.sameItem(cursor) && s.count() < max);
    }

    /**
     * Drag-distribute: the cursor stack is spread over the dragged slots (empty or same-item, in drag order): a left drag
     * gives each slot an equal share (rounded down, at least 1), a right drag gives each exactly one. What is left stays
     * on the cursor. Returns false (nothing done) when fewer than two usable slots were dragged, so the caller can treat
     * it as an ordinary click.
     */
    public boolean distribute(List<Integer> dragged, boolean right) {
        List<Integer> slots = new ArrayList<>();
        for (int s : dragged) if (!slots.contains(s) && canDragInto(s)) slots.add(s);
        if (slots.size() < 2) return false;
        int max = ItemStack.maxStackFor(cursor.type(), player.getMaxStack());
        int share = right ? 1 : Math.max(1, cursor.count() / slots.size());
        for (int slot : slots) {
            if (cursor.isEmpty()) break;
            ItemStack s = stackAt(slot);
            int room = max - s.count();
            int put = Math.min(Math.min(share, room), cursor.count());
            if (put <= 0) continue;
            ItemStack placed = s.isEmpty() ? cursor.withCount(put) : s.withCount(s.count() + put);
            if (isGridSlot(slot)) grid.set(slot - GRID_BASE, placed);
            else player.set(slot, placed);
            cursor = cursor.withCount(cursor.count() - put);
        }
        recomputeResult();
        return true;
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
