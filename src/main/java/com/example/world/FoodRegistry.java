package com.example.world;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every real food item this project has, with real-vanilla nutrition/saturation numbers where
 * confidently known, and clearly adapted values where not (see the per-item comments below) -
 * this file's honesty convention is the same one {@code ToolEffectiveness}/{@code PlayerSurvival}
 * already use. See {@link BlockType}'s own docs on why none of these have a crafting recipe or
 * legitimate mob-drop/farming source yet (the cooking-system milestone only needed them to be
 * real, smeltable/cookable items - not to also build farming or mobs).
 */
public final class FoodRegistry {
    private FoodRegistry() {
    }

    private static final Map<BlockType, FoodProperties> FOODS = new EnumMap<>(BlockType.class);

    static {
        FOODS.put(BlockType.APPLE, new FoodProperties(4f, 2.4f, false));
        FOODS.put(BlockType.BREAD, new FoodProperties(5f, 6.0f, false));
        FOODS.put(BlockType.COOKED_BEEF, new FoodProperties(8f, 12.8f, false));

        // Real vanilla values (raw meats are less filling and far less satisfying than cooked).
        FOODS.put(BlockType.RAW_BEEF, new FoodProperties(3f, 1.8f, false));
        FOODS.put(BlockType.RAW_PORKCHOP, new FoodProperties(3f, 1.8f, false));
        FOODS.put(BlockType.COOKED_PORKCHOP, new FoodProperties(8f, 12.8f, false));
        FOODS.put(BlockType.RAW_CHICKEN, new FoodProperties(2f, 1.2f, false));
        FOODS.put(BlockType.COOKED_CHICKEN, new FoodProperties(6f, 7.2f, false));
        FOODS.put(BlockType.RAW_MUTTON, new FoodProperties(2f, 1.2f, false));
        FOODS.put(BlockType.COOKED_MUTTON, new FoodProperties(6f, 9.6f, false));
        FOODS.put(BlockType.RAW_RABBIT, new FoodProperties(3f, 1.8f, false));
        FOODS.put(BlockType.COOKED_RABBIT, new FoodProperties(5f, 6.0f, false));

        // Fish/plant foods - values recalled with less confidence than the meats above; adapted
        // rather than independently verified against a current vanilla data dump.
        FOODS.put(BlockType.COD, new FoodProperties(2f, 0.4f, false));
        FOODS.put(BlockType.COOKED_COD, new FoodProperties(5f, 6.0f, false));
        FOODS.put(BlockType.SALMON, new FoodProperties(2f, 0.4f, false));
        FOODS.put(BlockType.COOKED_SALMON, new FoodProperties(6f, 9.6f, false));
        FOODS.put(BlockType.POTATO, new FoodProperties(1f, 0.6f, false));
        FOODS.put(BlockType.BAKED_POTATO, new FoodProperties(5f, 6.0f, false));
        FOODS.put(BlockType.KELP, new FoodProperties(1f, 0.6f, false));
        FOODS.put(BlockType.DRIED_KELP, new FoodProperties(1f, 0.6f, false));

        // Real vanilla value; real vanilla also gives rotten flesh an 80% chance of inflicting
        // Hunger on eating - not modeled (no status-effect system exists), documented simplification.
        FOODS.put(BlockType.SPIDER_EYE, new FoodProperties(2f, 3.2f, false)); // poisons for 5 s, see PlayerSurvival.applyFoodEffects
        FOODS.put(BlockType.GOLDEN_APPLE, new FoodProperties(4f, 9.6f, true)); // always edible; regeneration II 5 s + absorption 2 min
        FOODS.put(BlockType.ROTTEN_FLESH, new FoodProperties(4f, 0.8f, false));
    }

    public static boolean isFood(BlockType type) {
        return FOODS.containsKey(type);
    }

    /** Null for anything that isn't a food item. */
    public static FoodProperties propertiesFor(BlockType type) {
        return FOODS.get(type);
    }
}
