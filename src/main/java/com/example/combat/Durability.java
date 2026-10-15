package com.example.combat;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Max uses per damageable item and what each action costs (research: docs/research/melee-combat.md and
 * armor.md). A damageable item always stacks to 1. Anything absent is not damageable (max 0).
 */
public final class Durability {
    private Durability() {
    }

    private static final Map<BlockType, Integer> MAX = new EnumMap<>(BlockType.class);

    static {
        for (BlockType t : new BlockType[]{BlockType.WOODEN_PICKAXE, BlockType.WOODEN_AXE, BlockType.WOODEN_SHOVEL, BlockType.WOODEN_SWORD}) MAX.put(t, 59);
        for (BlockType t : new BlockType[]{BlockType.STONE_PICKAXE, BlockType.STONE_AXE, BlockType.STONE_SHOVEL, BlockType.STONE_SWORD}) MAX.put(t, 131);
        for (BlockType t : new BlockType[]{BlockType.IRON_PICKAXE, BlockType.IRON_AXE, BlockType.IRON_SHOVEL, BlockType.IRON_SWORD}) MAX.put(t, 250);
        for (BlockType t : new BlockType[]{BlockType.DIAMOND_PICKAXE, BlockType.DIAMOND_AXE, BlockType.DIAMOND_SHOVEL, BlockType.DIAMOND_SWORD}) MAX.put(t, 1561);
        for (BlockType t : new BlockType[]{BlockType.GOLD_PICKAXE, BlockType.GOLD_AXE, BlockType.GOLD_SHOVEL, BlockType.GOLD_SWORD, BlockType.GOLD_HOE}) MAX.put(t, 32);
        MAX.put(BlockType.WOODEN_HOE, 59);
        MAX.put(BlockType.STONE_HOE, 131);
        MAX.put(BlockType.IRON_HOE, 250);
        MAX.put(BlockType.DIAMOND_HOE, 1561);
        // Armor: piece constant (helmet 11, chest 16, legs 15, boots 13) times the material multiplier.
        armor(5, BlockType.LEATHER_HELMET, BlockType.LEATHER_CHESTPLATE, BlockType.LEATHER_LEGGINGS, BlockType.LEATHER_BOOTS);
        armor(15, BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS);
        armor(33, BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE, BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS);
        armor(7, BlockType.GOLD_HELMET, BlockType.GOLD_CHESTPLATE, BlockType.GOLD_LEGGINGS, BlockType.GOLD_BOOTS);
    }

    private static void armor(int mult, BlockType helmet, BlockType chest, BlockType legs, BlockType boots) {
        MAX.put(helmet, 11 * mult);
        MAX.put(chest, 16 * mult);
        MAX.put(legs, 15 * mult);
        MAX.put(boots, 13 * mult);
    }

    /** 0 when the item has no durability. */
    public static int max(BlockType type) {
        return type == null ? 0 : MAX.getOrDefault(type, 0);
    }

    public static boolean isDamageable(BlockType type) {
        return max(type) > 0;
    }

    private static boolean isMiningTool(BlockType t) {
        return isDamageable(t) && WeaponStats.isTool(t) && !WeaponStats.isSword(t);
    }

    /** Durability spent by a successful attack on a mob with this item in hand. */
    public static int attackCost(BlockType held) {
        if (WeaponStats.isSword(held)) return 1;
        if (isMiningTool(held)) return 2;
        return 0;
    }

    /** Durability spent by fully breaking a block (0 for instant-break blocks). */
    public static int blockBreakCost(BlockType held, float blockHardness) {
        if (blockHardness <= 0f) return 0;
        if (WeaponStats.isSword(held)) return 2;
        if (isMiningTool(held)) return 1;
        return 0;
    }

    /** Wear per worn piece when armor absorbs an armor-reducible hit of {@code incoming} HP. */
    public static int armorWear(float incoming) {
        return Math.max(1, (int) Math.floor(incoming / 4f));
    }
}
