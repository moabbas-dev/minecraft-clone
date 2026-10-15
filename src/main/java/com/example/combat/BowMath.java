package com.example.combat;

import java.util.Random;

/** The bow as BowItem and AbstractArrow compute it (26.3 client): how a draw becomes power, speed and damage. */
public final class BowMath {
    private BowMath() {
    }

    /** Ticks of drawing for full power. */
    public static final float FULL_DRAW_TICKS = 20f;
    /** A draw weaker than this shoots nothing. */
    public static final float MIN_POWER = 0.1f;
    public static final float ARROW_SPEED_PER_POWER = 3.0f; // blocks per tick at power 1
    public static final float ARROW_BASE_DAMAGE = 2.0f;
    public static final int BOW_DURABILITY = 384;

    /** BowItem.getPowerForTime: f = t / 20, then (f f + 2 f) / 3, at most 1. */
    public static float power(float drawTicks) {
        float f = drawTicks / FULL_DRAW_TICKS;
        f = (f * f + f * 2f) / 3f;
        return Math.min(1f, f);
    }

    /** Only a fully drawn bow (power 1) makes a critical arrow. */
    public static boolean critical(float power) {
        return power >= 1f;
    }

    /** The bow's pull animation: which of the three pulling textures (0, 1 or 2) shows after this many ticks of drawing. */
    public static int pullingStage(float drawTicks) {
        float pull = Math.min(1f, drawTicks / FULL_DRAW_TICKS);
        return pull >= 0.9f ? 2 : pull >= 0.65f ? 1 : 0;
    }

    /** The field of view multiplier while drawing: 1 - 0.15 x (min(t / 20, 1))^2 (LocalPlayer.getFieldOfViewModifier). */
    public static float fovFactor(float drawTicks) {
        float f = Math.min(drawTicks / FULL_DRAW_TICKS, 1f);
        return 1f - f * f * 0.15f;
    }

    /**
     * AbstractArrow's damage on a hit: {@code ceil(speed * 2)} with the speed in blocks per tick, and for a critical arrow a random
     * 0 to half of that plus 1 on top (nextInt(damage / 2 + 2) + damage).
     */
    public static float hitDamage(float speedBlocksPerTick, boolean critical, Random random) {
        int damage = (int) Math.ceil(Math.max(0f, speedBlocksPerTick * ARROW_BASE_DAMAGE));
        if (critical) damage = Math.min(Integer.MAX_VALUE, random.nextInt(damage / 2 + 2) + damage);
        return damage;
    }
}
