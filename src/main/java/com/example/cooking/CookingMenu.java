package com.example.cooking;

import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;
import com.example.inventory.ItemStack;
import lombok.Getter;

/**
 * The logic behind one open cooking-station screen: maps a click on the input/fuel/output slots
 * (or the player's own inventory) to a change, reusing {@link InventoryMenu}'s own pick/place/
 * merge/split primitives for input and fuel (see {@link InventoryMenu#leftClickSlot}) rather
 * than duplicating that logic a third time. The one genuinely different rule is the output slot:
 * the player can only ever take from it, never place anything in - matching real vanilla and
 * this class' own "never overwrite incompatible output" safety requirement.
 *
 * Reads and writes the real {@link CookingStationState} directly - there is no separate copy of
 * input/fuel/output for this menu to own, so closing the screen changes nothing about the
 * station (see {@link #close}): it keeps simulating exactly as if the screen had never opened.
 */
public class CookingMenu {
    public static final int INPUT_SLOT = 200;
    public static final int FUEL_SLOT = 201;
    public static final int OUTPUT_SLOT = 202;

    private final Inventory player;
    @Getter
    private final CookingStationState station;
    @Getter
    private ItemStack cursor = ItemStack.EMPTY;
    /** XP earned by taking output since this menu was opened - Game grants it to PlayerSurvival
     *  and drains it via {@link #takeEarnedExperience()}, so nothing is granted twice. */
    private float earnedExperience = 0f;

    public CookingMenu(Inventory player, CookingStationState station) {
        this.player = player;
        this.station = station;
    }

    public ItemStack stackAt(int slot) {
        if (slot == INPUT_SLOT) return station.getInputStack();
        if (slot == FUEL_SLOT) return station.getFuelStack();
        if (slot == OUTPUT_SLOT) return station.getOutputStack();
        if (slot >= 0 && slot < player.slotCount()) return player.get(slot);
        throw new IllegalArgumentException("no such slot: " + slot);
    }

    public void click(int slot, boolean right) {
        if (slot == OUTPUT_SLOT) {
            clickOutput(right);
            return;
        }
        if (slot == INPUT_SLOT) {
            cursor = clickStationSlot(station.getInputStack(), right, station::setInputStack);
        } else if (slot == FUEL_SLOT) {
            cursor = clickStationSlot(station.getFuelStack(), right, station::setFuelStack);
        } else if (slot >= 0 && slot < player.slotCount()) {
            cursor = right ? InventoryMenu.rightClickSlot(player, slot, cursor) : InventoryMenu.leftClickSlot(player, slot, cursor);
        } else {
            throw new IllegalArgumentException("no such slot: " + slot);
        }
    }

    /** Runs the shared single-slot click primitive against one of the station's own stacks - a
     *  throwaway single-slot Inventory is the adapter, since {@link InventoryMenu}'s primitive
     *  operates on an {@link Inventory}, not a bare {@link ItemStack}; the result is written
     *  straight back through {@code setter}. */
    private ItemStack clickStationSlot(ItemStack current, boolean right, java.util.function.Consumer<ItemStack> setter) {
        Inventory single = new Inventory(1, 1, player.getMaxStack());
        single.set(0, current);
        ItemStack newCursor = right ? InventoryMenu.rightClickSlot(single, 0, cursor) : InventoryMenu.leftClickSlot(single, 0, cursor);
        setter.accept(single.get(0));
        return newCursor;
    }

    /** The player may only ever take from output, never place into it - taking grants
     *  proportional XP (see {@link CookingStationState#takeOutput}). */
    private void clickOutput(boolean right) {
        ItemStack out = station.getOutputStack();
        if (out.isEmpty()) return;
        if (!cursor.isEmpty() && cursor.type() != out.type()) return;
        int want = right ? (out.count() + 1) / 2 : out.count();
        int cursorRoom = player.getMaxStack() - cursor.count();
        int take = Math.min(want, cursorRoom);
        if (take <= 0) return;
        earnedExperience += station.takeOutput(take);
        cursor = cursor.isEmpty() ? new ItemStack(out.type(), take) : cursor.withCount(cursor.count() + take);
    }

    /** Removes and returns all XP earned so far this menu session - Game calls this once per
     *  click to grant it immediately, so nothing is lost if the screen is closed right after. */
    public float takeEarnedExperience() {
        float xp = earnedExperience;
        earnedExperience = 0f;
        return xp;
    }

    /**
     * Removes and returns items from the cursor for dropping into the world (clicking outside
     * the screen) - everything on a left click, one item on a right click. Does NOT touch the
     * station: its input/fuel/output stay exactly as they are and keep simulating.
     */
    public ItemStack dropFromCursor(boolean single) {
        if (cursor.isEmpty()) return ItemStack.EMPTY;
        int n = single ? 1 : cursor.count();
        ItemStack dropped = new ItemStack(cursor.type(), n);
        cursor = cursor.withCount(cursor.count() - n);
        return dropped;
    }

    /**
     * Closes the screen: only the cursor goes back into the player inventory (as far as it
     * fits, overflow returned to drop in the world) - the station's own contents are left
     * completely alone, exactly matching "closing the GUI must not affect cooking."
     */
    public ItemStack close() {
        ItemStack leftover = ItemStack.EMPTY;
        if (!cursor.isEmpty()) {
            int added = player.add(cursor.type(), cursor.count());
            if (added < cursor.count()) leftover = new ItemStack(cursor.type(), cursor.count() - added);
        }
        cursor = ItemStack.EMPTY;
        return leftover;
    }
}
