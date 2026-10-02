package com.example.combat;

/** Armor damage reduction (Java): research in docs/research/armor.md. */
public final class ArmorRules {
    private ArmorRules() {
    }

    /** Armor points the formula works with, capped at 20. */
    public static float effectiveArmor(float damage, float armor, float toughness) {
        return Math.min(20f, Math.max(armor / 5f, armor - damage / (2f + toughness / 4f)));
    }

    /** Damage taken after armor: D * (1 - effective / 25). */
    public static float reduce(float damage, float armor, float toughness) {
        if (damage <= 0f || armor <= 0f) return Math.max(0f, damage);
        return damage * (1f - effectiveArmor(damage, armor, toughness) / 25f);
    }
}
