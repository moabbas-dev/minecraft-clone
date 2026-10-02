package com.example.combat;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/** Player attack damage and attack speed per held item (Java 1.9+ values, research: melee-combat.md). */
public final class WeaponStats {
    private WeaponStats() {
    }

    private record Stats(float damage, float speed) {
    }

    public static final float HAND_DAMAGE = 1f;
    public static final float HAND_SPEED = 4.0f;

    private static final Map<BlockType, Stats> STATS = new EnumMap<>(BlockType.class);

    static {
        put(BlockType.WOODEN_SWORD, 4, 1.6f);
        put(BlockType.STONE_SWORD, 5, 1.6f);
        put(BlockType.IRON_SWORD, 6, 1.6f);
        put(BlockType.WOODEN_AXE, 7, 0.8f);
        put(BlockType.STONE_AXE, 9, 0.8f);
        put(BlockType.IRON_AXE, 9, 0.9f);
        put(BlockType.WOODEN_PICKAXE, 2, 1.2f);
        put(BlockType.STONE_PICKAXE, 3, 1.2f);
        put(BlockType.IRON_PICKAXE, 4, 1.2f);
        put(BlockType.WOODEN_SHOVEL, 2.5f, 1.0f);
        put(BlockType.STONE_SHOVEL, 3.5f, 1.0f);
        put(BlockType.IRON_SHOVEL, 4.5f, 1.0f);
    }

    private static void put(BlockType t, float damage, float speed) {
        STATS.put(t, new Stats(damage, speed));
    }

    /** True for swords and the pickaxe/axe/shovel family (items with their own attack stats). */
    public static boolean isTool(BlockType t) {
        return t != null && STATS.containsKey(t);
    }

    public static boolean isSword(BlockType t) {
        return t == BlockType.WOODEN_SWORD || t == BlockType.STONE_SWORD || t == BlockType.IRON_SWORD;
    }

    public static float damage(BlockType held) {
        Stats s = held == null ? null : STATS.get(held);
        return s == null ? HAND_DAMAGE : s.damage();
    }

    public static float attackSpeed(BlockType held) {
        Stats s = held == null ? null : STATS.get(held);
        return s == null ? HAND_SPEED : s.speed();
    }

    /** Cooldown period in ticks: 20 / attack speed. */
    public static float periodTicks(BlockType held) {
        return 20f / attackSpeed(held);
    }
}
