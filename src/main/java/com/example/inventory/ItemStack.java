package com.example.inventory;

import com.example.combat.Durability;
import com.example.world.BlockType;

/**
 * An immutable snapshot of one inventory slot: an item, how many of it, and (for tools, swords and
 * armor) how many durability points have been spent. Items are currently always {@link BlockType}s.
 * Empty is represented one consistent way - {@code type == null, count == 0, damage == 0} - never a
 * stack of AIR or a zero/negative count. A damaged-out item (damage >= max durability) is never kept:
 * callers use {@link #damaged} which returns {@link #EMPTY} when the item breaks.
 */
public record ItemStack(BlockType type, int count, int damage) {
    public static final ItemStack EMPTY = new ItemStack(null, 0, 0);

    public ItemStack {
        if (type == null || type == BlockType.AIR || count <= 0) {
            type = null;
            count = 0;
            damage = 0;
        } else if (!Durability.isDamageable(type)) {
            damage = 0;
        } else {
            damage = Math.max(0, damage);
        }
    }

    public ItemStack(BlockType type, int count) {
        this(type, count, 0);
    }

    public boolean isEmpty() {
        return type == null;
    }

    public ItemStack withCount(int newCount) {
        return new ItemStack(type, newCount, damage);
    }

    /** Largest stack size for this item: damageable items never stack. */
    public static int maxStackFor(BlockType type, int inventoryMax) {
        return Durability.isDamageable(type) ? 1 : inventoryMax;
    }

    /** Two stacks can merge only if they are the same item with the same wear. */
    public boolean sameItem(ItemStack other) {
        return !isEmpty() && !other.isEmpty() && type == other.type && damage == other.damage;
    }

    /** This stack after spending {@code uses} durability; {@link #EMPTY} once the item has broken. */
    public ItemStack damaged(int uses) {
        if (isEmpty() || !Durability.isDamageable(type) || uses <= 0) return this;
        int next = damage + uses;
        return next >= Durability.max(type) ? EMPTY : new ItemStack(type, count, next);
    }

    /** Remaining uses (0 for non-damageable items). */
    public int durabilityLeft() {
        return Durability.isDamageable(type) ? Durability.max(type) - damage : 0;
    }
}
