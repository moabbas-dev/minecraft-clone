package com.example.ui;

/**
 * The timing rules behind the HUD's heart and hunger animations, kept free of rendering so they are unit-testable.
 * Values come from docs/vanilla-rules.md section 5.2 (decompiled-source figures, tagged [M] there).
 */
public final class HudAnimation {
    private HudAnimation() {
    }

    /** Ticks the heart row flashes after taking damage. */
    public static final int DAMAGE_FLASH_TICKS = 20;
    /** Ticks it flashes after regaining health. */
    public static final int HEAL_FLASH_TICKS = 10;
    /** At or below this many half-hearts (4 HP) every heart jitters. */
    public static final int LOW_HEALTH_SHAKE_AT = 4;

    /** True while the flash is in its "on" phase: the flash lasts until {@code endTick}, alternating every 3 ticks. */
    public static boolean flashOn(long endTick, long tick) {
        return endTick > tick && ((endTick - tick) / 3) % 2 == 1;
    }

    public static boolean lowHealthShake(int hearts) {
        return hearts <= LOW_HEALTH_SHAKE_AT;
    }

    /** Hunger icons jitter on every {@code food*3+1}th tick while saturation has run out. */
    public static boolean hungerShake(long tick, int food, float saturation) {
        return saturation <= 0f && tick % (food * 3L + 1L) == 0L;
    }
}
