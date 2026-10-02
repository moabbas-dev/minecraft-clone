package com.example.survival;

/** Vanilla experience curve (research: docs/research/survival-mechanics.md). Pure math. */
public final class Experience {
    private Experience() {
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static int costOfLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** Total XP at which {@code level} is reached from zero. */
    public static float totalForLevel(int level) {
        if (level <= 16) return level * level + 6f * level;
        if (level <= 31) return 2.5f * level * level - 40.5f * level + 360f;
        return 4.5f * level * level - 162.5f * level + 2220f;
    }

    /** The level a total XP amount displays as. */
    public static int levelFor(float totalXp) {
        int level = 0;
        while (totalForLevel(level + 1) <= totalXp) level++;
        return level;
    }

    /** Fill of the XP bar within the current level, 0..1. */
    public static float progress(float totalXp) {
        int level = levelFor(totalXp);
        float base = totalForLevel(level);
        return Math.max(0f, Math.min(1f, (totalXp - base) / (totalForLevel(level + 1) - base)));
    }

    /** XP dropped as orbs when a player dies at {@code level}: min(7 x level, 100). */
    public static float deathDrop(int level) {
        return Math.min(7f * level, 100f);
    }
}
