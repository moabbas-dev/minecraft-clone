package com.example.cooking;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recipe table every station queries - a station never branches on "am I a Furnace/Blast
 * Furnace/Smoker" to decide what it can process; it only ever calls {@link #recipeFor} with its
 * own {@link CookingStationType} and lets the recipe's own {@link CookingRecipe#stations} decide
 * (see {@code CookingStationState#canProcess}).
 *
 * Recipes only use items this project's registry actually has (see each block's own comment) -
 * no armor/nether/farming recipes were added since none of those systems exist yet (Ancient
 * Debris/Netherite Scrap, Nether Gold Ore, golden/chainmail equipment recycling, and
 * Netherrack→Nether Brick are all explicitly deferred - documented in the final report, not
 * silently dropped).
 *
 * One input maps to at most one recipe overall (not per-station) - real vanilla's own rule, and
 * simpler than needing a tie-break when two recipes could both claim the same input.
 */
public final class CookingRecipeRegistry {
    private CookingRecipeRegistry() {
    }

    private static final Set<CookingStationType> FURNACE_ONLY = EnumSet.of(CookingStationType.FURNACE);
    private static final Set<CookingStationType> FURNACE_AND_BLAST = EnumSet.of(CookingStationType.FURNACE, CookingStationType.BLAST_FURNACE);
    private static final Set<CookingStationType> FURNACE_AND_SMOKER = EnumSet.of(CookingStationType.FURNACE, CookingStationType.SMOKER);
    private static final Set<CookingStationType> BLAST_ONLY = EnumSet.of(CookingStationType.BLAST_FURNACE);

    private static CookingRecipe r(BlockType in, BlockType out, float xp, Set<CookingStationType> stations) {
        return new CookingRecipe(in, out, 1, xp, stations);
    }

    private static final List<CookingRecipe> ALL = List.of(
            // ---- Furnace-only: stone/sand/wood-adjacent building materials ----
            r(BlockType.SAND, BlockType.GLASS, 0.1f, FURNACE_ONLY),
            r(BlockType.COBBLESTONE, BlockType.STONE, 0.1f, FURNACE_ONLY),
            r(BlockType.STONE, BlockType.SMOOTH_STONE, 0.1f, FURNACE_ONLY),
            r(BlockType.CLAY, BlockType.BRICK, 0.1f, FURNACE_ONLY),
            r(BlockType.OAK_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.BIRCH_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.SPRUCE_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.JUNGLE_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.ACACIA_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.MANGROVE_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.DARK_OAK_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),
            r(BlockType.CHERRY_LOG, BlockType.CHARCOAL, 0.15f, FURNACE_ONLY),

            // ---- Furnace + Blast Furnace: ores and raw materials -> ingots ----
            r(BlockType.IRON_ORE, BlockType.IRON_INGOT, 0.7f, FURNACE_AND_BLAST),
            r(BlockType.RAW_IRON, BlockType.IRON_INGOT, 0.7f, FURNACE_AND_BLAST),
            r(BlockType.GOLD_ORE, BlockType.GOLD_INGOT, 1.0f, FURNACE_AND_BLAST),
            r(BlockType.RAW_GOLD, BlockType.GOLD_INGOT, 1.0f, FURNACE_AND_BLAST),
            r(BlockType.COPPER_ORE, BlockType.COPPER_INGOT, 0.7f, FURNACE_AND_BLAST),
            r(BlockType.RAW_COPPER, BlockType.COPPER_INGOT, 0.7f, FURNACE_AND_BLAST),

            // ---- Blast Furnace only: metal equipment recycling. Only the iron tier tools exist
            // in this project (no golden tools/armor, no chainmail) - see the class docs. No
            // durability system exists either, so this is always exactly 1 nugget per tool
            // (a documented simplification of real vanilla's durability-proportional nugget
            // count), matching the request's "iron tools -> iron nuggets" example. ----
            r(BlockType.IRON_PICKAXE, BlockType.IRON_NUGGET, 0.1f, BLAST_ONLY),
            r(BlockType.IRON_AXE, BlockType.IRON_NUGGET, 0.1f, BLAST_ONLY),
            r(BlockType.IRON_SHOVEL, BlockType.IRON_NUGGET, 0.1f, BLAST_ONLY),

            // ---- Furnace + Smoker: food ----
            r(BlockType.RAW_BEEF, BlockType.COOKED_BEEF, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.RAW_PORKCHOP, BlockType.COOKED_PORKCHOP, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.RAW_CHICKEN, BlockType.COOKED_CHICKEN, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.RAW_MUTTON, BlockType.COOKED_MUTTON, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.RAW_RABBIT, BlockType.COOKED_RABBIT, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.COD, BlockType.COOKED_COD, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.SALMON, BlockType.COOKED_SALMON, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.POTATO, BlockType.BAKED_POTATO, 0.35f, FURNACE_AND_SMOKER),
            r(BlockType.KELP, BlockType.DRIED_KELP, 0.1f, FURNACE_AND_SMOKER)
    );

    private static final Map<BlockType, CookingRecipe> BY_INPUT = new EnumMap<>(BlockType.class);

    static {
        for (CookingRecipe recipe : ALL) {
            if (BY_INPUT.put(recipe.input(), recipe) != null) {
                throw new IllegalStateException("duplicate cooking recipe input: " + recipe.input());
            }
        }
    }

    /** The recipe for this input, regardless of station - null if nothing cooks/smelts it at all. */
    public static CookingRecipe recipeFor(BlockType input) {
        return BY_INPUT.get(input);
    }

    /** The recipe for this input AT this station - null if nothing cooks it, or the recipe that
     *  does exists but doesn't accept this station (see {@link CookingRecipe#supportsStation}). */
    public static CookingRecipe recipeFor(BlockType input, CookingStationType station) {
        CookingRecipe recipe = BY_INPUT.get(input);
        return recipe != null && recipe.supportsStation(station) ? recipe : null;
    }

    public static boolean accepts(BlockType input, CookingStationType station) {
        return recipeFor(input, station) != null;
    }

    public static List<CookingRecipe> all() {
        return ALL;
    }
}
