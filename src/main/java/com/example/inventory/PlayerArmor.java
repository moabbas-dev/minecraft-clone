package com.example.inventory;

import com.example.combat.ArmorStats;
import com.example.combat.Durability;
import com.example.combat.EquipmentSlot;
import com.example.world.BlockType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The player's four worn armor pieces. Totals feed {@code ArmorRules}; {@link #wear} spends durability on
 * every worn piece when an armor-reducible hit lands (research: docs/research/armor.md), removing any that
 * break.
 */
public class PlayerArmor {
    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    public ItemStack get(EquipmentSlot slot) {
        return slots[slot.ordinal()];
    }

    public void set(EquipmentSlot slot, ItemStack stack) {
        slots[slot.ordinal()] = stack;
    }

    /** True if the stack is an armor item (any slot). */
    public static boolean canEquip(ItemStack stack) {
        return !stack.isEmpty() && ArmorStats.isArmor(stack.type());
    }

    /** Puts {@code piece} in its matching slot and returns whatever was worn there (EMPTY if nothing). */
    public ItemStack equip(ItemStack piece) {
        if (!canEquip(piece)) throw new IllegalArgumentException("not armor: " + piece);
        EquipmentSlot slot = ArmorStats.of(piece.type()).slot();
        ItemStack previous = slots[slot.ordinal()];
        slots[slot.ordinal()] = piece.withCount(1);
        return previous;
    }

    public int totalDefense() {
        int total = 0;
        for (ItemStack s : slots) if (!s.isEmpty()) total += ArmorStats.of(s.type()).defense();
        return total;
    }

    public float totalToughness() {
        float total = 0f;
        for (ItemStack s : slots) if (!s.isEmpty()) total += ArmorStats.of(s.type()).toughness();
        return total;
    }

    /** Spends durability on every worn piece for an armor-reducible hit of {@code incoming} HP. */
    public void wear(float incoming) {
        int loss = Durability.armorWear(incoming);
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) slots[i] = slots[i].damaged(loss);
        }
    }

    public void clear() {
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    public void write(DataOutput out) throws IOException {
        for (ItemStack s : slots) {
            out.writeInt(s.isEmpty() ? 0 : s.type().id);
            out.writeInt(s.damage());
        }
    }

    public void read(DataInput in) throws IOException {
        for (int i = 0; i < slots.length; i++) {
            BlockType type = BlockType.byId(in.readInt());
            int damage = in.readInt();
            ArmorStats.Piece piece = ArmorStats.of(type);
            // Only accept a piece into the slot it belongs to - a corrupt save can lose a piece, never misplace one.
            slots[i] = piece != null && piece.slot().ordinal() == i ? new ItemStack(type, 1, damage) : ItemStack.EMPTY;
        }
    }
}
