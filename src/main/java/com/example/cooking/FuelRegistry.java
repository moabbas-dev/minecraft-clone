package com.example.cooking;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * One shared fuel table for every station - never a per-station copy. Burn durations are always
 * expressed in normal-Furnace ticks (200 ticks/item); a station converts that to its own
 * effective burn time via {@link CookingStationType#fuelTickScale()}, which is what keeps
 * "items per fuel unit" identical across stations (see that class's docs) rather than making the
 * faster stations accidentally more fuel-efficient.
 *
 * Only fuels this project actually has an item for are mapped - the values for fuels it doesn't
 * have yet (lava bucket, blaze rod, coal block, dried kelp block, wool, boats, bamboo/
 * scaffolding) are recorded here in a comment so adding them later is a one-line map entry, not
 * an architecture change, matching the request's own "build it so new fuels can easily be
 * added" instruction. Deferred fuels this project has no item/fluid system for: Lava Bucket
 * (20,000), Block of Coal (16,000), Dried Kelp Block (4,000), Blaze Rod (2,400), Boats (1,200),
 * Wool (100), Bamboo/Scaffolding (50).
 */
public final class FuelRegistry {
    private FuelRegistry() {
    }

    private static final Map<BlockType, Integer> FURNACE_BURN_TICKS = new EnumMap<>(BlockType.class);

    static {
        FURNACE_BURN_TICKS.put(BlockType.COAL, 1600);
        FURNACE_BURN_TICKS.put(BlockType.CHARCOAL, 1600);
        FURNACE_BURN_TICKS.put(BlockType.STICK, 100);
        // "Most normal wooden blocks: 300 ticks" - every log and plank this project has.
        for (BlockType wood : new BlockType[]{
                BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG, BlockType.JUNGLE_LOG,
                BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG, BlockType.CHERRY_LOG,
                BlockType.OAK_PLANKS}) {
            FURNACE_BURN_TICKS.put(wood, 300);
        }
    }

    public static boolean isFuel(BlockType type) {
        return FURNACE_BURN_TICKS.containsKey(type);
    }

    /** Base burn duration in normal-Furnace ticks - 0 if this isn't a fuel at all. */
    public static int furnaceBurnTicks(BlockType type) {
        return FURNACE_BURN_TICKS.getOrDefault(type, 0);
    }
}
