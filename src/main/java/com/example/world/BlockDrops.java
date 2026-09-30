package com.example.world;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What breaking a block yields. Table-driven: {@link #OVERRIDES} lists every block that does
 * NOT simply drop itself; anything absent drops one of itself. Unbreakable blocks (hardness &lt; 0:
 * bedrock, water) and AIR never drop anything regardless of the table.
 *
 * Deliberately shaped so future rules slot in without touching callers: a {@link Drop} has a
 * min/max count (random quantities), and {@link #resolve} is the single choke point where a
 * tool or enchantment-like context parameter would be added later. Neither exists yet.
 *
 * Mappings marked "vanilla-like" follow the real game's convention (cobblestone from stone,
 * dirt from grass, leaves dropping nothing); they are this project's own table, not extracted
 * from any game data.
 */
public final class BlockDrops {
    private BlockDrops() {
    }

    /** One possible outcome: {@code item} in a quantity between min and max inclusive. */
    public record Drop(BlockType item, int min, int max) {
        public Drop {
            if (item == null || item == BlockType.AIR) throw new IllegalArgumentException("invalid drop item: " + item);
            if (min < 0 || max < min) throw new IllegalArgumentException("invalid drop quantity: " + min + ".." + max);
        }

        public static Drop of(BlockType item) {
            return new Drop(item, 1, 1);
        }
    }

    /** A drop actually rolled for one specific break. */
    public record Resolved(BlockType item, int count) {
    }

    private static final List<Drop> NOTHING = List.of();
    private static final Map<BlockType, List<Drop>> OVERRIDES = new EnumMap<>(BlockType.class);

    static {
        // Vanilla-like conversions.
        OVERRIDES.put(BlockType.STONE, List.of(Drop.of(BlockType.COBBLESTONE)));
        OVERRIDES.put(BlockType.GRASS_BLOCK, List.of(Drop.of(BlockType.DIRT)));

        // Leaves drop nothing (real vanilla's sapling/apple chances are not modeled yet).
        for (BlockType leaf : new BlockType[]{
                BlockType.OAK_LEAVES, BlockType.BIRCH_LEAVES, BlockType.SPRUCE_LEAVES, BlockType.JUNGLE_LEAVES,
                BlockType.ACACIA_LEAVES, BlockType.MANGROVE_LEAVES, BlockType.DARK_OAK_LEAVES, BlockType.CHERRY_LEAVES}) {
            OVERRIDES.put(leaf, NOTHING);
        }

        // All four sapling growth stages are one collectible item: only stage 0 is placeable
        // (BlockType.PLACEABLE), stages 1-3 are SaplingGrowth's internal states.
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            OVERRIDES.put(stage, List.of(Drop.of(BlockType.OAK_SAPLING_STAGE_0)));
        }

        // Fragile blocks that shatter without yielding anything.
        OVERRIDES.put(BlockType.GLASS, NOTHING);
        OVERRIDES.put(BlockType.ICE, NOTHING);
        // Ores drop themselves: no coal/diamond/etc. item exists in this project yet, so the
        // ore block is the only collectible form (default rule, listed here as a reminder).
    }

    /** The drop definitions for a block (empty = drops nothing). Never null. */
    public static List<Drop> dropsFor(BlockType broken) {
        if (broken == null || broken == BlockType.AIR || broken.hardness < 0) return NOTHING;
        List<Drop> override = OVERRIDES.get(broken);
        return override != null ? override : List.of(Drop.of(broken));
    }

    /**
     * Rolls the actual quantities for one break. Call exactly once per successful break - this
     * consumes randomness, so calling it twice would both duplicate and desync the result.
     * Equivalent to {@code resolve(broken, random, null)} - a bare hand.
     */
    public static List<Resolved> resolve(BlockType broken, Random random) {
        return resolve(broken, random, null);
    }

    /**
     * Same as {@link #resolve(BlockType, Random)}, but {@code heldTool} (the BlockType of
     * whatever's in the player's selected hotbar slot, or null for an empty hand) can withhold
     * the drop entirely - see {@link ToolEffectiveness#canHarvest}. Nothing about the roll
     * itself (counts, randomness) changes; a disallowed harvest simply never rolls at all.
     */
    public static List<Resolved> resolve(BlockType broken, Random random, BlockType heldTool) {
        if (!ToolEffectiveness.canHarvest(heldTool, broken)) return List.of();
        List<Drop> drops = dropsFor(broken);
        if (drops.isEmpty()) return List.of();
        List<Resolved> result = new java.util.ArrayList<>(drops.size());
        for (Drop d : drops) {
            int count = d.min() == d.max() ? d.min() : d.min() + random.nextInt(d.max() - d.min() + 1);
            if (count > 0) result.add(new Resolved(d.item(), count));
        }
        return result;
    }
}
