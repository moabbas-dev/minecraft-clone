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
    public record Drop(BlockType item, int min, int max, float chance) {
        public Drop(BlockType item, int min, int max) {
            this(item, min, max, 1f);
        }

        public Drop {
            if (chance < 0f || chance > 1f) throw new IllegalArgumentException("invalid drop chance: " + chance);
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

        // Leaves roll their drops independently (vanilla): sapling 5% (jungle 2.5%), 1-2 sticks 2%, and an apple
        // 0.5% from oak and dark oak only. Only the oak sapling item exists, so every species drops that one.
        for (BlockType leaf : new BlockType[]{
                BlockType.OAK_LEAVES, BlockType.BIRCH_LEAVES, BlockType.SPRUCE_LEAVES, BlockType.JUNGLE_LEAVES,
                BlockType.ACACIA_LEAVES, BlockType.MANGROVE_LEAVES, BlockType.DARK_OAK_LEAVES, BlockType.CHERRY_LEAVES}) {
            float sapling = leaf == BlockType.JUNGLE_LEAVES ? 0.025f : 0.05f;
            boolean apples = leaf == BlockType.OAK_LEAVES || leaf == BlockType.DARK_OAK_LEAVES;
            List<Drop> rolls = new java.util.ArrayList<>();
            rolls.add(new Drop(BlockType.OAK_SAPLING_STAGE_0, 1, 1, sapling));
            rolls.add(new Drop(BlockType.STICK, 1, 2, 0.02f));
            if (apples) rolls.add(new Drop(BlockType.APPLE, 1, 1, 0.005f));
            OVERRIDES.put(leaf, List.copyOf(rolls));
        }
        // Other vanilla item drops.
        OVERRIDES.put(BlockType.CLAY, List.of(new Drop(BlockType.CLAY_BALL, 4, 4)));
        OVERRIDES.put(BlockType.SNOW_BLOCK, List.of(new Drop(BlockType.SNOWBALL, 4, 4))); // needs a shovel (ToolEffectiveness)
        OVERRIDES.put(BlockType.GLOWSTONE, List.of(new Drop(BlockType.GLOWSTONE_DUST, 2, 4)));
        OVERRIDES.put(BlockType.BOOKSHELF, List.of(new Drop(BlockType.BOOK, 3, 3)));
        OVERRIDES.put(BlockType.GRAVEL, List.of(new Drop(BlockType.FLINT, 1, 1, 0.1f))); // else gravel itself, see resolve

        // All four sapling growth stages are one collectible item: only stage 0 is placeable
        // (BlockType.PLACEABLE), stages 1-3 are SaplingGrowth's internal states.
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            OVERRIDES.put(stage, List.of(Drop.of(BlockType.OAK_SAPLING_STAGE_0)));
        }

        // Fragile blocks that shatter without yielding anything.
        OVERRIDES.put(BlockType.GLASS, NOTHING);
        OVERRIDES.put(BlockType.ICE, NOTHING);
        // Iron/gold/copper/diamond ore still drop themselves: this project has no ore-block-
        // drops-raw-material mechanic (see BlockType's own docs on RAW_IRON/RAW_GOLD/RAW_COPPER),
        // so the ore block itself is the collectible, furnace-smeltable form.
        // Coal ore is the one exception - real vanilla's own "Coal" item (see BlockType) needed a
        // legitimate source for it to be usable as furnace fuel at all (this milestone's own
        // requirement), matching real vanilla's actual behavior exactly.
        OVERRIDES.put(BlockType.COAL_ORE, List.of(Drop.of(BlockType.COAL)));
        // Diamond ore yields the diamond item (needed for the diamond tier); an iron-or-better pickaxe is
        // still required by ToolEffectiveness.
        OVERRIDES.put(BlockType.DIAMOND_ORE, List.of(Drop.of(BlockType.DIAMOND)));
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
        if (broken == BlockType.GRAVEL) { // vanilla: 10% flint, otherwise the gravel block itself (never both)
            return List.of(new Resolved(random.nextFloat() < 0.1f ? BlockType.FLINT : BlockType.GRAVEL, 1));
        }
        for (Drop d : drops) {
            if (d.chance() < 1f && random.nextFloat() >= d.chance()) continue;
            int count = d.min() == d.max() ? d.min() : d.min() + random.nextInt(d.max() - d.min() + 1);
            if (count > 0) result.add(new Resolved(d.item(), count));
        }
        return result;
    }
}
