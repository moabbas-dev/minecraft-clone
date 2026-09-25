package com.example.inventory;

import com.example.world.BlockType;
import lombok.Getter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The player's item container: a fixed number of slots, the first {@link #getHotbarSize()} of
 * which are the hotbar. This is the single source of truth for item counts - the hotbar UI and
 * held-item view model only ever read from it, never keep their own copy.
 *
 * Slots are stored as two parallel arrays rather than mutable stack objects, so nothing outside
 * this class can hold a reference that aliases a slot; {@link #get} hands out immutable
 * {@link ItemStack} snapshots. Every mutating method keeps counts within [0, maxStack] and
 * normalizes emptied slots to the one canonical empty form.
 */
public class Inventory {
    /** Stack limit for every item - one place to change it (no per-item limits exist yet). */
    public static final int DEFAULT_MAX_STACK = 64;
    public static final int HOTBAR_SIZE = 9;
    /** 9 hotbar + 27 storage; storage has no UI yet but the model is ready for one. */
    public static final int DEFAULT_SLOT_COUNT = 36;

    private final BlockType[] types;
    private final int[] counts;
    @Getter
    private final int hotbarSize;
    @Getter
    private final int maxStack;
    @Getter
    private int selectedSlot = 0;

    public Inventory() {
        this(DEFAULT_SLOT_COUNT, HOTBAR_SIZE, DEFAULT_MAX_STACK);
    }

    public Inventory(int slotCount, int hotbarSize, int maxStack) {
        if (slotCount <= 0 || hotbarSize <= 0 || hotbarSize > slotCount || maxStack <= 0) {
            throw new IllegalArgumentException("invalid inventory shape: slots=" + slotCount
                    + " hotbar=" + hotbarSize + " maxStack=" + maxStack);
        }
        this.types = new BlockType[slotCount];
        this.counts = new int[slotCount];
        this.hotbarSize = hotbarSize;
        this.maxStack = maxStack;
    }

    public int slotCount() {
        return types.length;
    }

    private static void requireItem(BlockType type) {
        if (type == null || type == BlockType.AIR) {
            throw new IllegalArgumentException("not a valid item: " + type);
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= types.length) {
            throw new IndexOutOfBoundsException("slot " + slot + " out of range 0.." + (types.length - 1));
        }
    }

    public ItemStack get(int slot) {
        requireSlot(slot);
        return new ItemStack(types[slot], counts[slot]);
    }

    /** Replaces a slot's contents exactly. Counts above the stack limit are rejected, not clamped. */
    public void set(int slot, ItemStack stack) {
        requireSlot(slot);
        if (stack.count() > maxStack) {
            throw new IllegalArgumentException("stack of " + stack.count() + " exceeds max " + maxStack);
        }
        types[slot] = stack.type();
        counts[slot] = stack.count();
    }

    /**
     * Adds up to {@code amount} of {@code type}: merges into existing compatible stacks first
     * (lowest slot first), then fills empty slots. Returns how many were actually added - the
     * caller keeps responsibility for the remainder ({@code amount - returned}); nothing is ever
     * silently discarded here.
     */
    public int add(BlockType type, int amount) {
        requireItem(type);
        if (amount < 0) throw new IllegalArgumentException("negative amount: " + amount);
        int remaining = amount;
        for (int i = 0; i < types.length && remaining > 0; i++) {
            if (types[i] == type && counts[i] < maxStack) {
                int take = Math.min(remaining, maxStack - counts[i]);
                counts[i] += take;
                remaining -= take;
            }
        }
        for (int i = 0; i < types.length && remaining > 0; i++) {
            if (types[i] == null) {
                int take = Math.min(remaining, maxStack);
                types[i] = type;
                counts[i] = take;
                remaining -= take;
            }
        }
        return amount - remaining;
    }

    /** How many more of {@code type} would currently fit (existing stack room + empty slots). */
    public int spaceFor(BlockType type) {
        requireItem(type);
        long space = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == null) space += maxStack;
            else if (types[i] == type) space += maxStack - counts[i];
        }
        return (int) Math.min(space, Integer.MAX_VALUE);
    }

    public boolean canAdd(BlockType type, int amount) {
        return amount >= 0 && spaceFor(type) >= amount;
    }

    /** Total held of {@code type} across every slot. */
    public int count(BlockType type) {
        long total = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type && type != null) total += counts[i];
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /** Removes up to {@code amount} from one slot; returns how many were actually removed. */
    public int removeFromSlot(int slot, int amount) {
        requireSlot(slot);
        if (amount < 0) throw new IllegalArgumentException("negative amount: " + amount);
        if (types[slot] == null) return 0;
        int removed = Math.min(amount, counts[slot]);
        counts[slot] -= removed;
        if (counts[slot] <= 0) {
            counts[slot] = 0;
            types[slot] = null;
        }
        return removed;
    }

    /** Removes up to {@code amount} of {@code type} across slots (highest slot first); returns how many were removed. */
    public int remove(BlockType type, int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative amount: " + amount);
        int remaining = amount;
        for (int i = types.length - 1; i >= 0 && remaining > 0; i--) {
            if (types[i] == type && type != null) remaining -= removeFromSlot(i, remaining);
        }
        return amount - remaining;
    }

    /** True if exactly one item was consumed from the slot (false for an empty slot). */
    public boolean removeOne(int slot) {
        return removeFromSlot(slot, 1) == 1;
    }

    public void selectSlot(int slot) {
        if (slot < 0 || slot >= hotbarSize) {
            throw new IllegalArgumentException("hotbar slot " + slot + " out of range 0.." + (hotbarSize - 1));
        }
        selectedSlot = slot;
    }

    /** Moves the selection by {@code delta}, wrapping around the hotbar. */
    public void cycleSelection(int delta) {
        selectedSlot = Math.floorMod(selectedSlot + delta, hotbarSize);
    }

    public ItemStack selected() {
        return get(selectedSlot);
    }

    public void clear() {
        java.util.Arrays.fill(types, null);
        java.util.Arrays.fill(counts, 0);
        selectedSlot = 0;
    }

    // ---- persistence (see WorldIO) - block IDs, not enum ordinals, so reordering BlockType is safe ----

    public void write(DataOutput out) throws IOException {
        out.writeInt(types.length);
        out.writeInt(selectedSlot);
        for (int i = 0; i < types.length; i++) {
            out.writeInt(types[i] == null ? 0 : types[i].id);
            out.writeInt(counts[i]);
        }
    }

    /**
     * Reads what {@link #write} wrote into a default-shaped inventory. Tolerates a stored slot
     * count different from ours (extra slots dropped, missing ones left empty) and any unknown
     * block ID or out-of-range count (that slot is left empty) - a bad save can lose a slot,
     * never crash or produce an invalid stack.
     */
    public static Inventory read(DataInput in) throws IOException {
        Inventory inv = new Inventory();
        int stored = in.readInt();
        int selected = in.readInt();
        for (int i = 0; i < stored; i++) {
            int id = in.readInt();
            int count = in.readInt();
            BlockType type = BlockType.byId(id);
            if (i < inv.types.length && type != BlockType.AIR && count > 0) {
                inv.types[i] = type;
                inv.counts[i] = Math.min(count, inv.maxStack);
            }
        }
        inv.selectedSlot = selected >= 0 && selected < inv.hotbarSize ? selected : 0;
        return inv;
    }
}
