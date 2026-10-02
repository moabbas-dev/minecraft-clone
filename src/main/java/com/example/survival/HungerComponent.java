package com.example.survival;

import com.example.world.FoodProperties;
import lombok.Getter;

/**
 * Reusable food/saturation/exhaustion state - real vanilla's own model (an exhaustion
 * accumulator that periodically converts into visible food/saturation loss), not a countdown
 * timer, per the request's explicit "do not implement hunger as a timer." Knows nothing about
 * Player, input, or rendering - {@code PlayerSurvival} is what actually decides how much
 * exhaustion a tick of sprinting/jumping/etc. adds; this class only ever applies whatever amount
 * it's given.
 */
public class HungerComponent {
    public static final float MAX_FOOD = 20f;
    /** Real vanilla starts here (not specified as a start value in the request, which only gives
     *  the 0..20 cap) - the one number in this class sourced from real vanilla rather than
     *  adapted/invented. */
    public static final float DEFAULT_STARTING_SATURATION = 5f;
    public static final float EXHAUSTION_THRESHOLD = 4f;
    /** food <= this disables sprinting (request: "food <= 6: sprinting must be disabled"). */
    public static final float SPRINT_DISABLE_THRESHOLD = 6f;
    /** food >= this allows natural regeneration. */
    public static final float REGEN_ENABLE_THRESHOLD = 18f;

    @Getter
    private float foodLevel = MAX_FOOD;
    @Getter
    private float saturation = DEFAULT_STARTING_SATURATION;
    @Getter
    private float exhaustion = 0f;

    /**
     * Adds exhaustion, converting every full {@link #EXHAUSTION_THRESHOLD} crossed into one
     * point of saturation loss (or, once saturation is already 0, one point of food loss) -
     * repeatedly, so a single large jump (e.g. several seconds of accumulated dt at once) drains
     * exactly as many points as smaller increments would have, not just one. {@code amount} must
     * be non-negative; a negative value is rejected rather than silently reducing exhaustion
     * (nothing in this system ever needs to subtract exhaustion directly).
     */
    public void addExhaustion(float amount) {
        if (amount <= 0f) return;
        exhaustion += amount;
        while (exhaustion >= EXHAUSTION_THRESHOLD) {
            exhaustion -= EXHAUSTION_THRESHOLD;
            if (saturation > 0f) {
                saturation = Math.max(0f, saturation - 1f);
            } else {
                foodLevel = Math.max(0f, foodLevel - 1f);
            }
        }
    }

    /** True when {@link #eat} would accept this food (not full, or the food can be eaten while full). */
    public boolean canEat(FoodProperties food) {
        return foodLevel < MAX_FOOD || food.canEatWhenFull();
    }

    public boolean canSprint() {
        return foodLevel > SPRINT_DISABLE_THRESHOLD;
    }

    public boolean regenerationAllowed() {
        return foodLevel >= REGEN_ENABLE_THRESHOLD;
    }

    public boolean isStarving() {
        return foodLevel <= 0f;
    }

    /**
     * Eats one food item: restores food and saturation (saturation clamped so it never exceeds
     * the new food level - the request's explicit rule), unless already at max food and this
     * food can't be eaten while full.
     *
     * @return true if it was actually eaten (the caller should consume the inventory item only
     *         then); false if blocked (already full and not overeat-capable) - nothing changes
     *         in that case.
     */
    public boolean eat(FoodProperties food) {
        if (foodLevel >= MAX_FOOD && !food.canEatWhenFull()) return false;
        foodLevel = Math.min(MAX_FOOD, foodLevel + food.nutrition());
        saturation = Math.min(foodLevel, saturation + food.saturationRestored());
        return true;
    }

    /** Back to fresh-player defaults - a new world/respawn, not a save load (see {@link #forceState}). */
    public void reset() {
        foodLevel = MAX_FOOD;
        saturation = DEFAULT_STARTING_SATURATION;
        exhaustion = 0f;
    }

    /** Restores exact values from a save file, clamped the same way every mutator here already
     *  clamps (food/saturation to [0, MAX_FOOD] with saturation never above food, exhaustion
     *  non-negative) so a hand-edited or corrupted save file can't produce an invalid state. */
    public void forceState(float foodLevel, float saturation, float exhaustion) {
        this.foodLevel = Math.max(0f, Math.min(MAX_FOOD, foodLevel));
        this.saturation = Math.max(0f, Math.min(this.foodLevel, saturation));
        this.exhaustion = Math.max(0f, exhaustion);
    }
}
