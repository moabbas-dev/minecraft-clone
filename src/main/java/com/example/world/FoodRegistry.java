package com.example.world;

import java.util.EnumMap;
import java.util.Map;

/**
 * The 3 foods this milestone's request explicitly asks for, with its exact real-vanilla
 * nutrition/saturation numbers. See {@link BlockType}'s own docs on APPLE/BREAD/COOKED_BEEF for
 * why none of them have a crafting recipe yet.
 */
public final class FoodRegistry {
    private FoodRegistry() {
    }

    private static final Map<BlockType, FoodProperties> FOODS = new EnumMap<>(BlockType.class);

    static {
        FOODS.put(BlockType.APPLE, new FoodProperties(4f, 2.4f, false));
        FOODS.put(BlockType.BREAD, new FoodProperties(5f, 6.0f, false));
        FOODS.put(BlockType.COOKED_BEEF, new FoodProperties(8f, 12.8f, false));
    }

    public static boolean isFood(BlockType type) {
        return FOODS.containsKey(type);
    }

    /** Null for anything that isn't a food item. */
    public static FoodProperties propertiesFor(BlockType type) {
        return FOODS.get(type);
    }
}
