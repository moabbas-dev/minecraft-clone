package com.example.world;

/**
 * What one food item restores when eaten - see {@link FoodRegistry} for the actual item table.
 * {@code eatingDurationSeconds} is recorded for a future item-use-over-time animation (this
 * project has no such system yet - see {@code Game}'s eat handling, which is instant); it's
 * unused today, not a claim that eating already takes this long.
 */
public record FoodProperties(float nutrition, float saturationRestored, boolean canEatWhenFull,
                              float eatingDurationSeconds) {
    public FoodProperties(float nutrition, float saturationRestored, boolean canEatWhenFull) {
        this(nutrition, saturationRestored, canEatWhenFull, 1.6f); // real vanilla's own default eat duration
    }
}
