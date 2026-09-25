package com.example.inventory;

import com.example.world.BlockType;

/**
 * An immutable snapshot of one inventory slot: an item and how many of it. Items are currently
 * always {@link BlockType}s (this project has no non-block items yet); when that changes, this
 * is the one type to generalize. Empty is represented one consistent way - {@code type == null,
 * count == 0} - never a stack of AIR or a zero/negative count.
 */
public record ItemStack(BlockType type, int count) {
    public static final ItemStack EMPTY = new ItemStack(null, 0);

    public ItemStack {
        if (type == null || type == BlockType.AIR || count <= 0) {
            type = null;
            count = 0;
        }
    }

    public boolean isEmpty() {
        return type == null;
    }

    public ItemStack withCount(int newCount) {
        return new ItemStack(type, newCount);
    }
}
