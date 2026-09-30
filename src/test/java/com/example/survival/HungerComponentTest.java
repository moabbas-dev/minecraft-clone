package com.example.survival;

import com.example.world.FoodProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HungerComponentTest {
    private static final FoodProperties APPLE_LIKE = new FoodProperties(4f, 2.4f, false);

    @Test
    void startsFullFoodDefaultSaturationZeroExhaustion() {
        HungerComponent h = new HungerComponent();
        assertEquals(20f, h.getFoodLevel());
        assertEquals(HungerComponent.DEFAULT_STARTING_SATURATION, h.getSaturation());
        assertEquals(0f, h.getExhaustion());
    }

    @Test
    void exhaustionDrainsSaturationBeforeFood() {
        HungerComponent h = new HungerComponent(); // saturation starts at 5
        float foodBefore = h.getFoodLevel();
        h.addExhaustion(HungerComponent.EXHAUSTION_THRESHOLD);
        assertEquals(4f, h.getSaturation(), "one point of saturation lost, food untouched");
        assertEquals(foodBefore, h.getFoodLevel());
        assertEquals(0f, h.getExhaustion(), "the threshold amount is fully consumed");
    }

    @Test
    void foodOnlyDropsOnceSaturationIsFullyDepleted() {
        HungerComponent h = new HungerComponent();
        // Drain the starting saturation (5) first, food must stay untouched throughout.
        for (int i = 0; i < 5; i++) {
            h.addExhaustion(HungerComponent.EXHAUSTION_THRESHOLD);
            assertEquals(20f, h.getFoodLevel(), "food must not drop while saturation remains");
        }
        assertEquals(0f, h.getSaturation());
        h.addExhaustion(HungerComponent.EXHAUSTION_THRESHOLD);
        assertEquals(19f, h.getFoodLevel(), "food drops only once saturation is empty");
    }

    @Test
    void aLargeExhaustionJumpDrainsMultiplePointsAtOnce() {
        HungerComponent h = new HungerComponent();
        // 5 saturation to clear, then 3 more thresholds into food: 8 thresholds total.
        h.addExhaustion(HungerComponent.EXHAUSTION_THRESHOLD * 8);
        assertEquals(0f, h.getSaturation());
        assertEquals(17f, h.getFoodLevel());
    }

    @Test
    void negativeOrZeroExhaustionIsRejected() {
        HungerComponent h = new HungerComponent();
        h.addExhaustion(-5f);
        h.addExhaustion(0f);
        assertEquals(0f, h.getExhaustion());
    }

    @Test
    void sprintIsAllowedAboveSixAndDisabledAtOrBelowSix() {
        HungerComponent h = new HungerComponent();
        h.forceState(7f, 0f, 0f);
        assertTrue(h.canSprint());
        h.forceState(6f, 0f, 0f);
        assertFalse(h.canSprint(), "food == 6 must disable sprinting");
        h.forceState(3f, 0f, 0f);
        assertFalse(h.canSprint());
    }

    @Test
    void regenerationIsAllowedAtOrAboveEighteenOnly() {
        HungerComponent h = new HungerComponent();
        h.forceState(18f, 0f, 0f);
        assertTrue(h.regenerationAllowed());
        h.forceState(17.9f, 0f, 0f);
        assertFalse(h.regenerationAllowed());
    }

    @Test
    void isStarvingOnlyAtExactlyZeroFood() {
        HungerComponent h = new HungerComponent();
        h.forceState(0.1f, 0f, 0f);
        assertFalse(h.isStarving());
        h.forceState(0f, 0f, 0f);
        assertTrue(h.isStarving());
    }

    @Test
    void eatingRestoresFoodAndSaturationClampedToFood() {
        HungerComponent h = new HungerComponent();
        h.forceState(10f, 0f, 0f);
        assertTrue(h.eat(APPLE_LIKE));
        assertEquals(14f, h.getFoodLevel());
        assertEquals(2.4f, h.getSaturation(), 0.001f);
    }

    @Test
    void saturationNeverExceedsFoodEvenWithAHighSaturationFood() {
        HungerComponent h = new HungerComponent();
        h.forceState(1f, 0f, 0f);
        FoodProperties richFood = new FoodProperties(1f, 50f, false); // absurdly high saturation on purpose
        h.eat(richFood);
        assertEquals(2f, h.getFoodLevel());
        assertEquals(2f, h.getSaturation(), "saturation must never exceed the new food level");
    }

    @Test
    void eatingAtMaxFoodIsBlockedUnlessTheFoodAllowsOvereating() {
        HungerComponent h = new HungerComponent();
        assertEquals(20f, h.getFoodLevel());
        assertFalse(h.eat(APPLE_LIKE), "already full, and apple can't be eaten while full");
        assertEquals(20f, h.getFoodLevel(), "nothing should change");

        FoodProperties overeatCapable = new FoodProperties(2f, 1f, true);
        assertTrue(h.eat(overeatCapable));
        assertEquals(20f, h.getFoodLevel(), "still clamped to the max even though it was allowed to eat");
    }

    @Test
    void resetGoesBackToFreshDefaultsNotSavedValues() {
        HungerComponent h = new HungerComponent();
        h.forceState(3f, 3f, 2f);
        h.reset();
        assertEquals(20f, h.getFoodLevel());
        assertEquals(HungerComponent.DEFAULT_STARTING_SATURATION, h.getSaturation());
        assertEquals(0f, h.getExhaustion());
    }

    @Test
    void forceStateClampsOutOfRangeInputs() {
        HungerComponent h = new HungerComponent();
        h.forceState(-5f, 10f, -2f);
        assertEquals(0f, h.getFoodLevel());
        assertEquals(0f, h.getSaturation(), "saturation can't exceed the (now-clamped) food level");
        assertEquals(0f, h.getExhaustion());

        h.forceState(100f, 100f, 5f);
        assertEquals(20f, h.getFoodLevel());
        assertEquals(20f, h.getSaturation());
        assertEquals(5f, h.getExhaustion());
    }
}
