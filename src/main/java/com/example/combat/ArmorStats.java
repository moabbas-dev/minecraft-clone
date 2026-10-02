package com.example.combat;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/** Defense points and toughness per armor item (research: docs/research/armor.md). */
public final class ArmorStats {
    private ArmorStats() {
    }

    public record Piece(EquipmentSlot slot, int defense, float toughness) {
    }

    private static final Map<BlockType, Piece> PIECES = new EnumMap<>(BlockType.class);

    static {
        set(BlockType.LEATHER_HELMET, BlockType.LEATHER_CHESTPLATE, BlockType.LEATHER_LEGGINGS, BlockType.LEATHER_BOOTS, 0f, 1, 3, 2, 1);
        set(BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS, 0f, 2, 6, 5, 2);
        set(BlockType.GOLD_HELMET, BlockType.GOLD_CHESTPLATE, BlockType.GOLD_LEGGINGS, BlockType.GOLD_BOOTS, 0f, 2, 5, 3, 1);
        set(BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE, BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS, 2f, 3, 8, 6, 3);
    }

    private static void set(BlockType h, BlockType c, BlockType l, BlockType b, float tough, int dh, int dc, int dl, int db) {
        PIECES.put(h, new Piece(EquipmentSlot.HEAD, dh, tough));
        PIECES.put(c, new Piece(EquipmentSlot.CHEST, dc, tough));
        PIECES.put(l, new Piece(EquipmentSlot.LEGS, dl, tough));
        PIECES.put(b, new Piece(EquipmentSlot.FEET, db, tough));
    }

    public static boolean isArmor(BlockType t) {
        return t != null && PIECES.containsKey(t);
    }

    /** Null if {@code t} is not an armor item. */
    public static Piece of(BlockType t) {
        return t == null ? null : PIECES.get(t);
    }
}
