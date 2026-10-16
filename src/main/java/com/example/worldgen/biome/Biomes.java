package com.example.worldgen.biome;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.biome.Biomes} (Minecraft Java Edition 26.3): every biome
 * {@code ResourceKey} the class registers, in declaration order, as its resource id string
 * ({@code Identifier.withDefaultNamespace(name)} gives the {@code minecraft:} namespace).
 * Read from the {@code register("...")} calls in the class's static initialiser in the 26.3 client jar.
 */
public final class Biomes {
    public static final String THE_VOID = "minecraft:the_void";
    public static final String PLAINS = "minecraft:plains";
    public static final String SUNFLOWER_PLAINS = "minecraft:sunflower_plains";
    public static final String SNOWY_PLAINS = "minecraft:snowy_plains";
    public static final String ICE_SPIKES = "minecraft:ice_spikes";
    public static final String DESERT = "minecraft:desert";
    public static final String SWAMP = "minecraft:swamp";
    public static final String MANGROVE_SWAMP = "minecraft:mangrove_swamp";
    public static final String FOREST = "minecraft:forest";
    public static final String FLOWER_FOREST = "minecraft:flower_forest";
    public static final String BIRCH_FOREST = "minecraft:birch_forest";
    public static final String DAPPLED_FOREST = "minecraft:dappled_forest";
    public static final String DARK_FOREST = "minecraft:dark_forest";
    public static final String PALE_GARDEN = "minecraft:pale_garden";
    public static final String OLD_GROWTH_BIRCH_FOREST = "minecraft:old_growth_birch_forest";
    public static final String OLD_GROWTH_PINE_TAIGA = "minecraft:old_growth_pine_taiga";
    public static final String OLD_GROWTH_SPRUCE_TAIGA = "minecraft:old_growth_spruce_taiga";
    public static final String TAIGA = "minecraft:taiga";
    public static final String SNOWY_TAIGA = "minecraft:snowy_taiga";
    public static final String SAVANNA = "minecraft:savanna";
    public static final String SAVANNA_PLATEAU = "minecraft:savanna_plateau";
    public static final String WINDSWEPT_HILLS = "minecraft:windswept_hills";
    public static final String WINDSWEPT_GRAVELLY_HILLS = "minecraft:windswept_gravelly_hills";
    public static final String WINDSWEPT_FOREST = "minecraft:windswept_forest";
    public static final String WINDSWEPT_SAVANNA = "minecraft:windswept_savanna";
    public static final String JUNGLE = "minecraft:jungle";
    public static final String SPARSE_JUNGLE = "minecraft:sparse_jungle";
    public static final String BAMBOO_JUNGLE = "minecraft:bamboo_jungle";
    public static final String BADLANDS = "minecraft:badlands";
    public static final String ERODED_BADLANDS = "minecraft:eroded_badlands";
    public static final String WOODED_BADLANDS = "minecraft:wooded_badlands";
    public static final String MEADOW = "minecraft:meadow";
    public static final String CHERRY_GROVE = "minecraft:cherry_grove";
    public static final String GROVE = "minecraft:grove";
    public static final String SNOWY_SLOPES = "minecraft:snowy_slopes";
    public static final String FROZEN_PEAKS = "minecraft:frozen_peaks";
    public static final String JAGGED_PEAKS = "minecraft:jagged_peaks";
    public static final String STONY_PEAKS = "minecraft:stony_peaks";
    public static final String RIVER = "minecraft:river";
    public static final String FROZEN_RIVER = "minecraft:frozen_river";
    public static final String BEACH = "minecraft:beach";
    public static final String SNOWY_BEACH = "minecraft:snowy_beach";
    public static final String STONY_SHORE = "minecraft:stony_shore";
    public static final String WARM_OCEAN = "minecraft:warm_ocean";
    public static final String LUKEWARM_OCEAN = "minecraft:lukewarm_ocean";
    public static final String DEEP_LUKEWARM_OCEAN = "minecraft:deep_lukewarm_ocean";
    public static final String OCEAN = "minecraft:ocean";
    public static final String DEEP_OCEAN = "minecraft:deep_ocean";
    public static final String COLD_OCEAN = "minecraft:cold_ocean";
    public static final String DEEP_COLD_OCEAN = "minecraft:deep_cold_ocean";
    public static final String FROZEN_OCEAN = "minecraft:frozen_ocean";
    public static final String DEEP_FROZEN_OCEAN = "minecraft:deep_frozen_ocean";
    public static final String MUSHROOM_FIELDS = "minecraft:mushroom_fields";
    public static final String DRIPSTONE_CAVES = "minecraft:dripstone_caves";
    public static final String LUSH_CAVES = "minecraft:lush_caves";
    public static final String DEEP_DARK = "minecraft:deep_dark";
    public static final String SULFUR_CAVES = "minecraft:sulfur_caves";
    public static final String NETHER_WASTES = "minecraft:nether_wastes";
    public static final String WARPED_FOREST = "minecraft:warped_forest";
    public static final String CRIMSON_FOREST = "minecraft:crimson_forest";
    public static final String SOUL_SAND_VALLEY = "minecraft:soul_sand_valley";
    public static final String BASALT_DELTAS = "minecraft:basalt_deltas";
    public static final String THE_END = "minecraft:the_end";
    public static final String END_HIGHLANDS = "minecraft:end_highlands";
    public static final String END_MIDLANDS = "minecraft:end_midlands";
    public static final String SMALL_END_ISLANDS = "minecraft:small_end_islands";
    public static final String END_BARRENS = "minecraft:end_barrens";

    /** Every id above, in the vanilla declaration order. */
    public static final List<String> ALL = List.of(
        THE_VOID, PLAINS, SUNFLOWER_PLAINS, SNOWY_PLAINS, ICE_SPIKES, DESERT, SWAMP, MANGROVE_SWAMP, FOREST,
        FLOWER_FOREST, BIRCH_FOREST, DAPPLED_FOREST, DARK_FOREST, PALE_GARDEN, OLD_GROWTH_BIRCH_FOREST,
        OLD_GROWTH_PINE_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, TAIGA, SNOWY_TAIGA, SAVANNA, SAVANNA_PLATEAU,
        WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_FOREST, WINDSWEPT_SAVANNA, JUNGLE, SPARSE_JUNGLE,
        BAMBOO_JUNGLE, BADLANDS, ERODED_BADLANDS, WOODED_BADLANDS, MEADOW, CHERRY_GROVE, GROVE, SNOWY_SLOPES,
        FROZEN_PEAKS, JAGGED_PEAKS, STONY_PEAKS, RIVER, FROZEN_RIVER, BEACH, SNOWY_BEACH, STONY_SHORE, WARM_OCEAN,
        LUKEWARM_OCEAN, DEEP_LUKEWARM_OCEAN, OCEAN, DEEP_OCEAN, COLD_OCEAN, DEEP_COLD_OCEAN, FROZEN_OCEAN,
        DEEP_FROZEN_OCEAN, MUSHROOM_FIELDS, DRIPSTONE_CAVES, LUSH_CAVES, DEEP_DARK, SULFUR_CAVES, NETHER_WASTES,
        WARPED_FOREST, CRIMSON_FOREST, SOUL_SAND_VALLEY, BASALT_DELTAS, THE_END, END_HIGHLANDS, END_MIDLANDS,
        SMALL_END_ISLANDS, END_BARRENS);

    private Biomes() {
    }
}
