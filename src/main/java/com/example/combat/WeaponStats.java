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
        put(BlockType.DIAMOND_SWORD, 7, 1.6f);
        put(BlockType.DIAMOND_AXE, 9, 1.0f);
        put(BlockType.DIAMOND_PICKAXE, 5, 1.2f);
        put(BlockType.DIAMOND_SHOVEL, 5.5f, 1.0f);
        put(BlockType.GOLD_SWORD, 4, 1.6f);
        put(BlockType.GOLD_AXE, 7, 1.0f);
        put(BlockType.GOLD_PICKAXE, 2, 1.2f);
        put(BlockType.GOLD_SHOVEL, 2.5f, 1.0f);
        // hoes: 1 damage for every tier, speed 1 / 2 / 3 / 4 (wood, stone, iron, diamond), gold 1
        put(BlockType.WOODEN_HOE, 1, 1.0f);
        put(BlockType.STONE_HOE, 1, 2.0f);
        put(BlockType.IRON_HOE, 1, 3.0f);
        put(BlockType.GOLD_HOE, 1, 1.0f);
        put(BlockType.DIAMOND_HOE, 1, 4.0f);
        // Copper tools use the stone numbers (Items: sword 3 / -2.4, shovel 1.5 / -3.0, pickaxe 1 / -2.8, axe 7 / -3.2, hoe -1 / -2.0
        // on a material with attack bonus 1).
        put(BlockType.COPPER_SWORD, 5, 1.6f);
        put(BlockType.COPPER_AXE, 9, 0.8f);
        put(BlockType.COPPER_PICKAXE, 3, 1.2f);
        put(BlockType.COPPER_SHOVEL, 3.5f, 1.0f);
        put(BlockType.COPPER_HOE, 1, 2.0f);
        // Spears (Item.Properties.spear): damage 1 + the material's attack bonus (0 wood, 1 stone and copper, 2 iron, 0 gold, 3 diamond),
        // attack speed 1 / swing seconds (0.65, 0.75, 0.85, 0.95, 0.95, 1.05). Only the jab is modelled, not the charge or lunge.
        put(BlockType.WOODEN_SPEAR, 1, 1f / 0.65f);
        put(BlockType.STONE_SPEAR, 2, 1f / 0.75f);
        put(BlockType.COPPER_SPEAR, 2, 1f / 0.85f);
        put(BlockType.IRON_SPEAR, 3, 1f / 0.95f);
        put(BlockType.GOLD_SPEAR, 1, 1f / 0.95f);
        put(BlockType.DIAMOND_SPEAR, 4, 1f / 1.05f);
    }

    private static void put(BlockType t, float damage, float speed) {
        STATS.put(t, new Stats(damage, speed));
    }

    /** True for swords and the pickaxe/axe/shovel family (items with their own attack stats). */
    public static boolean isTool(BlockType t) {
        return t != null && STATS.containsKey(t);
    }

    public static boolean isSword(BlockType t) {
        return t == BlockType.WOODEN_SWORD || t == BlockType.STONE_SWORD || t == BlockType.IRON_SWORD
                || t == BlockType.DIAMOND_SWORD || t == BlockType.GOLD_SWORD || t == BlockType.COPPER_SWORD;
    }

    public static boolean isSpear(BlockType t) {
        return t == BlockType.WOODEN_SPEAR || t == BlockType.STONE_SPEAR || t == BlockType.COPPER_SPEAR
                || t == BlockType.IRON_SPEAR || t == BlockType.GOLD_SPEAR || t == BlockType.DIAMOND_SPEAR;
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
