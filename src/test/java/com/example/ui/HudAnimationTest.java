package com.example.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules from docs/vanilla-rules.md 5.2: 3-tick flash phases, 20/10 tick durations, shake at <= 4 HP, food shake period. */
class HudAnimationTest {
    @Test
    void flashAlternatesInThreeTickPhasesAndEndsAtItsDeadline() {
        long end = 100;
        // remaining ticks 20..18 -> (r/3)=6 even -> off; 17..15 -> 5 odd -> on; 14..12 off; 11..9 on; ...
        assertFalse(HudAnimation.flashOn(end, end - 20));
        assertTrue(HudAnimation.flashOn(end, end - 17));
        assertTrue(HudAnimation.flashOn(end, end - 15));
        assertFalse(HudAnimation.flashOn(end, end - 14));
        assertTrue(HudAnimation.flashOn(end, end - 9));
        assertTrue(HudAnimation.flashOn(end, end - 3));
        assertFalse(HudAnimation.flashOn(end, end - 2), "last ticks are the 'off' phase");
        assertFalse(HudAnimation.flashOn(end, end), "never on at or after the deadline");
        assertFalse(HudAnimation.flashOn(end, end + 5));
    }

    @Test
    void damageFlashIsTwentyTicksAndHealingTen() {
        assertEquals(20, HudAnimation.DAMAGE_FLASH_TICKS);
        assertEquals(10, HudAnimation.HEAL_FLASH_TICKS);
    }

    @Test
    void heartsShakeAtFourHealthOrLess() {
        assertTrue(HudAnimation.lowHealthShake(4));
        assertTrue(HudAnimation.lowHealthShake(1));
        assertFalse(HudAnimation.lowHealthShake(5));
        assertFalse(HudAnimation.lowHealthShake(20));
    }

    @Test
    void hungerOnlyShakesWithNoSaturationAndOnItsPeriod() {
        assertFalse(HudAnimation.hungerShake(0, 20, 5f), "saturation left: no shake");
        assertTrue(HudAnimation.hungerShake(0, 20, 0f));
        assertTrue(HudAnimation.hungerShake(61, 20, 0f), "period is food*3+1 = 61 ticks");
        assertFalse(HudAnimation.hungerShake(30, 20, 0f));
        assertTrue(HudAnimation.hungerShake(10, 3, 0f), "period 10 at food 3");
    }
}
