package com.example.world;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * What a tool does for mining: how much faster the right tool breaks a block, and which blocks
 * refuse to drop anything without at least the right tool tier. This is the "tool or
 * enchantment-like context parameter" {@link BlockDrops}'s own class docs already anticipated -
 * neither existed before this round.
 *
 * Scope (deliberate, documented): only the three mining tools (pickaxe/axe/shovel) across three
 * tiers (wood/stone/iron) exist. No sword - this project has no combat system for one to matter
 * in yet. No gold/diamond tier - iron is the highest tier this project's block set can produce
 * without a furnace (see the class docs on WOODEN_PICKAXE's family in {@link BlockType} and
 * {@code Recipes} for why IRON_* tools are real, fully-functional items with no crafting recipe
 * yet: obtaining iron requires smelting, which doesn't exist yet either - the same "real item,
 * no legitimate recipe yet" situation this project already accepts for IRON_BLOCK/DIAMOND_BLOCK).
 *
 * Harvest-level gating (a tool tier too low drops nothing, not just "slower") is deliberately
 * limited to the four ore blocks - real vanilla gates plain stone/cobblestone/etc. behind
 * "any pickaxe at all" too, but doing that here would make every existing bare-hand-mining test
 * and the current live game balance regress; ore gating is the one part of "vanilla's tool
 * rule" the request explicitly calls out, so that's what's implemented.
 */
public final class ToolEffectiveness {
    private ToolEffectiveness() {
    }

    public enum Kind {PICKAXE, AXE, SHOVEL, SWORD}

    /** speedMultiplier applies only when a tool's Kind matches {@link #correctKindFor}; harvestLevel
     *  is compared against {@link #requiredHarvestLevel} for the 4 gated ores. */
    public enum Tier {
        WOOD(2f, 1), STONE(4f, 2), IRON(6f, 3);

        public final float speedMultiplier;
        public final int harvestLevel;

        Tier(float speedMultiplier, int harvestLevel) {
            this.speedMultiplier = speedMultiplier;
            this.harvestLevel = harvestLevel;
        }
    }

    private record ToolInfo(Kind kind, Tier tier) {
    }

    private static final Map<BlockType, ToolInfo> TOOLS = new EnumMap<>(BlockType.class);

    static {
        TOOLS.put(BlockType.WOODEN_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.WOOD));
        TOOLS.put(BlockType.STONE_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.STONE));
        TOOLS.put(BlockType.IRON_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.IRON));
        TOOLS.put(BlockType.WOODEN_AXE, new ToolInfo(Kind.AXE, Tier.WOOD));
        TOOLS.put(BlockType.STONE_AXE, new ToolInfo(Kind.AXE, Tier.STONE));
        TOOLS.put(BlockType.IRON_AXE, new ToolInfo(Kind.AXE, Tier.IRON));
        TOOLS.put(BlockType.WOODEN_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.WOOD));
        TOOLS.put(BlockType.STONE_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.STONE));
        TOOLS.put(BlockType.IRON_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.IRON));
        // Swords: real items with their own combat stats (see com.example.combat); correctKindFor
        // never returns SWORD, so they mine at bare-hand speed - a sword is not a mining tool here.
        TOOLS.put(BlockType.WOODEN_SWORD, new ToolInfo(Kind.SWORD, Tier.WOOD));
        TOOLS.put(BlockType.STONE_SWORD, new ToolInfo(Kind.SWORD, Tier.STONE));
        TOOLS.put(BlockType.IRON_SWORD, new ToolInfo(Kind.SWORD, Tier.IRON));
    }

    /** Blocks a pickaxe speeds up. Ore/stone/stone-derived blocks - real vanilla's own pickaxe list,
     *  trimmed to what this project actually has. */
    private static final Set<BlockType> PICKAXE_BLOCKS = EnumSet.of(
            BlockType.STONE, BlockType.COBBLESTONE, BlockType.MOSSY_COBBLESTONE, BlockType.SANDSTONE,
            BlockType.BRICKS, BlockType.OBSIDIAN, BlockType.COAL_ORE, BlockType.IRON_ORE,
            BlockType.GOLD_ORE, BlockType.DIAMOND_ORE, BlockType.GOLD_BLOCK, BlockType.IRON_BLOCK,
            BlockType.DIAMOND_BLOCK, BlockType.NETHERRACK, BlockType.TERRACOTTA, BlockType.FURNACE,
            BlockType.COPPER_ORE, BlockType.SMOOTH_STONE, BlockType.BLAST_FURNACE, BlockType.SMOKER);

    /** Blocks an axe speeds up: every log species, planks, and the wood-family furniture blocks. */
    private static final Set<BlockType> AXE_BLOCKS = EnumSet.of(
            BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG, BlockType.JUNGLE_LOG,
            BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG, BlockType.CHERRY_LOG,
            BlockType.OAK_PLANKS, BlockType.BOOKSHELF, BlockType.CRAFTING_TABLE, BlockType.CHEST);

    /** Blocks a shovel speeds up: loose/soft ground blocks. */
    private static final Set<BlockType> SHOVEL_BLOCKS = EnumSet.of(
            BlockType.DIRT, BlockType.GRASS_BLOCK, BlockType.SAND, BlockType.RED_SAND,
            BlockType.GRAVEL, BlockType.CLAY, BlockType.SNOW_BLOCK, BlockType.MUD);

    /** Ore blocks that need at least this harvest level to drop anything (0 = no minimum -
     *  breakable, and its normal drop obtainable, by hand or any tool). */
    private static final Map<BlockType, Integer> REQUIRED_HARVEST_LEVEL = Map.of(
            BlockType.COAL_ORE, Tier.WOOD.harvestLevel,
            BlockType.IRON_ORE, Tier.STONE.harvestLevel,
            // Real vanilla actually requires an iron pickaxe for gold ore; simplified to stone
            // here since this project has no gold-tier tool and gold is otherwise unobtainable
            // survival-wise anyway (documented simplification, not a claim about real vanilla).
            BlockType.GOLD_ORE, Tier.STONE.harvestLevel,
            BlockType.COPPER_ORE, Tier.STONE.harvestLevel,
            BlockType.DIAMOND_ORE, Tier.IRON.harvestLevel);

    public static boolean isTool(BlockType type) {
        return TOOLS.containsKey(type);
    }

    public static Kind kindOf(BlockType tool) {
        ToolInfo info = TOOLS.get(tool);
        return info == null ? null : info.kind();
    }

    public static Tier tierOf(BlockType tool) {
        ToolInfo info = TOOLS.get(tool);
        return info == null ? null : info.tier();
    }

    /** Which tool Kind (if any) speeds up breaking this block - null means no tool helps. */
    public static Kind correctKindFor(BlockType block) {
        if (PICKAXE_BLOCKS.contains(block)) return Kind.PICKAXE;
        if (AXE_BLOCKS.contains(block)) return Kind.AXE;
        if (SHOVEL_BLOCKS.contains(block)) return Kind.SHOVEL;
        return null;
    }

    /** How much faster {@code block} breaks when {@code tool} is held - 1f (no change) for an
     *  empty hand, a non-tool item, or a tool of the wrong kind for this block. */
    public static float speedMultiplier(BlockType tool, BlockType block) {
        if (block == null) return 1f;
        Kind toolKind = kindOf(tool);
        if (toolKind == null || toolKind != correctKindFor(block)) return 1f;
        return tierOf(tool).speedMultiplier;
    }

    /** False only for the handful of ores that need at least a given tier - see
     *  {@link #REQUIRED_HARVEST_LEVEL}. Every other block can always be harvested, tool or not,
     *  matching this project's existing (pre-tools) behavior exactly. */
    public static boolean canHarvest(BlockType tool, BlockType block) {
        if (block == null) return true;
        Integer required = REQUIRED_HARVEST_LEVEL.get(block);
        if (required == null || required <= 0) return true;
        Kind toolKind = kindOf(tool);
        if (toolKind != correctKindFor(block)) return false; // wrong tool, no tool, or non-tool item
        return tierOf(tool).harvestLevel >= required;
    }
}
