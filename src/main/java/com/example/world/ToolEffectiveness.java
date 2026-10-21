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

    public enum Kind {PICKAXE, AXE, SHOVEL, SWORD, HOE, SPEAR}

    /** speedMultiplier applies only when a tool's Kind matches {@link #correctKindFor}; harvestLevel
     *  is compared against {@link #requiredHarvestLevel} for the 4 gated ores. */
    public enum Tier {
        WOOD(2f, 1), STONE(4f, 2), IRON(6f, 3), DIAMOND(8f, 4),
        /** Copper mines at 5 and harvests as well as stone (ToolMaterial.COPPER; incorrect_for_copper_tool = needs iron or diamond). */
        COPPER(5f, 2),
        /** Gold mines fastest (x12) but harvests no better than wood (vanilla mining level 0). */
        GOLD(12f, 1);

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
        TOOLS.put(BlockType.DIAMOND_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.DIAMOND));
        TOOLS.put(BlockType.DIAMOND_AXE, new ToolInfo(Kind.AXE, Tier.DIAMOND));
        TOOLS.put(BlockType.DIAMOND_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.DIAMOND));
        TOOLS.put(BlockType.DIAMOND_SWORD, new ToolInfo(Kind.SWORD, Tier.DIAMOND));
        TOOLS.put(BlockType.GOLD_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.GOLD));
        TOOLS.put(BlockType.GOLD_AXE, new ToolInfo(Kind.AXE, Tier.GOLD));
        TOOLS.put(BlockType.GOLD_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.GOLD));
        TOOLS.put(BlockType.GOLD_SWORD, new ToolInfo(Kind.SWORD, Tier.GOLD));
        // Hoes: items with durability and attack stats; they speed up no block that exists here (hay, leaves and sculk are not in this game).
        TOOLS.put(BlockType.WOODEN_HOE, new ToolInfo(Kind.HOE, Tier.WOOD));
        TOOLS.put(BlockType.STONE_HOE, new ToolInfo(Kind.HOE, Tier.STONE));
        TOOLS.put(BlockType.IRON_HOE, new ToolInfo(Kind.HOE, Tier.IRON));
        TOOLS.put(BlockType.GOLD_HOE, new ToolInfo(Kind.HOE, Tier.GOLD));
        TOOLS.put(BlockType.DIAMOND_HOE, new ToolInfo(Kind.HOE, Tier.DIAMOND));
        TOOLS.put(BlockType.COPPER_SWORD, new ToolInfo(Kind.SWORD, Tier.COPPER));
        TOOLS.put(BlockType.COPPER_AXE, new ToolInfo(Kind.AXE, Tier.COPPER));
        TOOLS.put(BlockType.COPPER_PICKAXE, new ToolInfo(Kind.PICKAXE, Tier.COPPER));
        TOOLS.put(BlockType.COPPER_SHOVEL, new ToolInfo(Kind.SHOVEL, Tier.COPPER));
        TOOLS.put(BlockType.COPPER_HOE, new ToolInfo(Kind.HOE, Tier.COPPER));
        // Spears: weapons with no mining speed (like swords here), one per material.
        TOOLS.put(BlockType.WOODEN_SPEAR, new ToolInfo(Kind.SPEAR, Tier.WOOD));
        TOOLS.put(BlockType.STONE_SPEAR, new ToolInfo(Kind.SPEAR, Tier.STONE));
        TOOLS.put(BlockType.COPPER_SPEAR, new ToolInfo(Kind.SPEAR, Tier.COPPER));
        TOOLS.put(BlockType.IRON_SPEAR, new ToolInfo(Kind.SPEAR, Tier.IRON));
        TOOLS.put(BlockType.GOLD_SPEAR, new ToolInfo(Kind.SPEAR, Tier.GOLD));
        TOOLS.put(BlockType.DIAMOND_SPEAR, new ToolInfo(Kind.SPEAR, Tier.DIAMOND));
    }

    /** Blocks a pickaxe speeds up. Ore/stone/stone-derived blocks - real vanilla's own pickaxe list,
     *  trimmed to what this project actually has. */
    private static final Set<BlockType> PICKAXE_BLOCKS = EnumSet.of(
            BlockType.STONE, BlockType.COBBLESTONE, BlockType.MOSSY_COBBLESTONE, BlockType.SANDSTONE,
            BlockType.BRICKS, BlockType.OBSIDIAN, BlockType.COAL_ORE, BlockType.IRON_ORE,
            BlockType.GOLD_ORE, BlockType.DIAMOND_ORE, BlockType.GOLD_BLOCK, BlockType.IRON_BLOCK,
            BlockType.DIAMOND_BLOCK, BlockType.NETHERRACK, BlockType.TERRACOTTA, BlockType.FURNACE,
            BlockType.COPPER_ORE, BlockType.SMOOTH_STONE, BlockType.BLAST_FURNACE, BlockType.SMOKER,
            // G28: tags/block/mineable/pickaxe.json in the 26.3 jar.
            BlockType.CALCITE, BlockType.CINNABAR, BlockType.DEEPSLATE, BlockType.COBBLED_DEEPSLATE, BlockType.DEEPSLATE_IRON_ORE,
            BlockType.GRANITE, BlockType.PACKED_ICE, BlockType.RAW_COPPER_BLOCK, BlockType.RAW_IRON_BLOCK, BlockType.RED_SANDSTONE,
            BlockType.SULFUR, BlockType.TUFF, BlockType.WHITE_TERRACOTTA, BlockType.ORANGE_TERRACOTTA, BlockType.YELLOW_TERRACOTTA,
            BlockType.BROWN_TERRACOTTA, BlockType.RED_TERRACOTTA, BlockType.LIGHT_GRAY_TERRACOTTA);

    /** Blocks an axe speeds up: every log species, planks, and the wood-family furniture blocks. */
    private static final Set<BlockType> AXE_BLOCKS = EnumSet.of(
            BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG, BlockType.JUNGLE_LOG,
            BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG, BlockType.CHERRY_LOG,
            BlockType.OAK_PLANKS, BlockType.BOOKSHELF, BlockType.CRAFTING_TABLE, BlockType.CHEST, BlockType.PUMPKIN);

    /** Blocks a shovel speeds up: loose/soft ground blocks. */
    private static final Set<BlockType> SHOVEL_BLOCKS = EnumSet.of(
            BlockType.DIRT, BlockType.GRASS_BLOCK, BlockType.SAND, BlockType.RED_SAND,
            BlockType.GRAVEL, BlockType.CLAY, BlockType.SNOW_BLOCK, BlockType.MUD,
            // G28: tags/block/mineable/shovel.json in the 26.3 jar.
            BlockType.COARSE_DIRT, BlockType.MYCELIUM, BlockType.PODZOL);

    /** Ore blocks that need at least this harvest level to drop anything (0 = no minimum -
     *  breakable, and its normal drop obtainable, by hand or any tool). */
    private static final Map<BlockType, Integer> REQUIRED_HARVEST_LEVEL = buildRequiredLevels();

    private static Map<BlockType, Integer> buildRequiredLevels() {
        Map<BlockType, Integer> m = new java.util.EnumMap<>(BlockType.class);
        // Vanilla: every block a pickaxe is "correct" for drops nothing without one (stone by hand yields no cobblestone)...
        for (BlockType b : PICKAXE_BLOCKS) m.put(b, Tier.WOOD.harvestLevel);
        // ...and these need a better tier (vanilla: gold, diamond need iron; iron needs stone; obsidian needs diamond).
        m.put(BlockType.IRON_ORE, Tier.STONE.harvestLevel);
        m.put(BlockType.COPPER_ORE, Tier.STONE.harvestLevel);
        m.put(BlockType.IRON_BLOCK, Tier.STONE.harvestLevel);
        m.put(BlockType.GOLD_ORE, Tier.IRON.harvestLevel);
        m.put(BlockType.DIAMOND_ORE, Tier.IRON.harvestLevel);
        m.put(BlockType.GOLD_BLOCK, Tier.IRON.harvestLevel);
        m.put(BlockType.DIAMOND_BLOCK, Tier.IRON.harvestLevel);
        m.put(BlockType.OBSIDIAN, Tier.DIAMOND.harvestLevel);
        m.put(BlockType.SNOW_BLOCK, Tier.WOOD.harvestLevel); // needs a shovel for its snowballs
        // G28: tags/block/needs_stone_tool.json; packed ice is pickaxe-mineable but its Properties have no
        // requiresCorrectToolForDrops, so any hand breaks it at the harvestable speed.
        m.put(BlockType.DEEPSLATE_IRON_ORE, Tier.STONE.harvestLevel);
        m.put(BlockType.RAW_COPPER_BLOCK, Tier.STONE.harvestLevel);
        m.put(BlockType.RAW_IRON_BLOCK, Tier.STONE.harvestLevel);
        m.remove(BlockType.PACKED_ICE);
        return m;
    }

    /**
     * Vanilla break time in seconds with {@code tool} (null = bare hand): {@code hardness * 1.5 / speed} when the
     * item can harvest the block, {@code hardness * 5 / speed} when it cannot, where speed is the tier multiplier
     * for a correct tool and 1 otherwise. Unbreakable blocks return -1.
     */
    public static float breakSeconds(BlockType tool, BlockType block) {
        float raw = rawBreakSeconds(tool, block);
        return raw < 0f ? raw : wholeTicks(raw);
    }

    /** {@link #breakSeconds} before the whole-tick rounding, so airborne/underwater penalties can be applied first. */
    public static float rawBreakSeconds(BlockType tool, BlockType block) {
        if (block == null || block.hardness < 0f) return -1f;
        if (block == BlockType.TNT) return block.handBreakSeconds();
        float base = canHarvest(tool, block) ? block.hardness * 1.5f : block.hardness * 5f;
        return base / speedMultiplier(tool, block);
    }

    /** Vanilla breaks blocks in whole game ticks (any remainder rounds up) and instantly once the per-tick damage
     *  reaches 1, i.e. when the time is 0.05 s or less (docs/vanilla-rules.md 1.3). The epsilon absorbs float noise. */
    public static float wholeTicks(float seconds) {
        if (seconds <= 0.05f + 1e-4f) return 0f;
        return (float) Math.ceil(seconds * 20f - 1e-3f) / 20f;
    }

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
