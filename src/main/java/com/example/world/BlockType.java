package com.example.world;

import lombok.Getter;

/**
 * Block palette. Originally matched Minecraft Java Edition 1.8's (pre-flattening) numeric
 * block IDs; the project now targets modern Minecraft, which dropped numeric IDs entirely in
 * the 1.13 "Flattening" in favor of namespaced string names (e.g. minecraft:grass_block) - so
 * {@link #id} below is purely this project's own internal save-format byte, not a claim about
 * any real vanilla identifier.
 */
public enum BlockType {
    AIR(0, "Air", false, false, 0, 0f),
    STONE(1, "Stone", true, false, 1.5f, 0f),
    GRASS_BLOCK(2, "Grass Block", true, false, 0.6f, 0f),
    DIRT(3, "Dirt", true, false, 0.5f, 0f),
    COBBLESTONE(4, "Cobblestone", true, false, 2.0f, 0f),
    OAK_PLANKS(5, "Oak Planks", true, false, 2.0f, 0f),
    BEDROCK(7, "Bedrock", true, false, -1f, 0f),
    WATER(9, "Water", false, true, -1f, 0f),
    SAND(12, "Sand", true, false, 0.5f, 0f),
    GRAVEL(13, "Gravel", true, false, 0.6f, 0f),
    GOLD_ORE(14, "Gold Ore", true, false, 3.0f, 0f),
    IRON_ORE(15, "Iron Ore", true, false, 3.0f, 0f),
    COAL_ORE(16, "Coal Ore", true, false, 3.0f, 0f),
    OAK_LOG(17, "Oak Log", true, false, 2.0f, 0f),
    OAK_LEAVES(18, "Oak Leaves", true, false, 0.2f, 0f),
    GLASS(20, "Glass", true, true, 0.3f, 0f),
    SANDSTONE(24, "Sandstone", true, false, 0.8f, 0f),
    GOLD_BLOCK(41, "Block of Gold", true, false, 3.0f, 0f),
    IRON_BLOCK(42, "Block of Iron", true, false, 5.0f, 0f),
    BRICKS(45, "Bricks", true, false, 2.0f, 0f),
    TNT(46, "TNT", true, false, 0f, 0f),
    BOOKSHELF(47, "Bookshelf", true, false, 1.5f, 0f),
    MOSSY_COBBLESTONE(48, "Mossy Cobblestone", true, false, 2.0f, 0f),
    OBSIDIAN(49, "Obsidian", true, false, 50.0f, 0f),
    CHEST(54, "Chest", true, false, 2.5f, 0f),
    DIAMOND_ORE(56, "Diamond Ore", true, false, 3.0f, 0f),
    DIAMOND_BLOCK(57, "Block of Diamond", true, false, 5.0f, 0f),
    CRAFTING_TABLE(58, "Crafting Table", true, false, 2.5f, 0f),
    FURNACE(61, "Furnace", true, false, 3.5f, 0f),
    ICE(79, "Ice", true, true, 0.5f, 0f),
    SNOW_BLOCK(80, "Snow Block", true, false, 0.2f, 0f),
    CLAY(82, "Clay", true, false, 0.6f, 0f),
    PUMPKIN(86, "Pumpkin", true, false, 1.0f, 0f),
    NETHERRACK(87, "Netherrack", true, false, 0.4f, 0f),
    GLOWSTONE(89, "Glowstone", true, false, 0.3f, 15f),
    TERRACOTTA(90, "Terracotta", true, false, 1.25f, 0f),
    RED_SAND(91, "Red Sand", true, false, 0.5f, 0f),
    BIRCH_LOG(92, "Birch Log", true, false, 2.0f, 0f),
    BIRCH_LEAVES(93, "Birch Leaves", true, false, 0.2f, 0f),
    SPRUCE_LOG(94, "Spruce Log", true, false, 2.0f, 0f),
    SPRUCE_LEAVES(95, "Spruce Leaves", true, false, 0.2f, 0f),
    JUNGLE_LOG(96, "Jungle Log", true, false, 2.0f, 0f),
    JUNGLE_LEAVES(97, "Jungle Leaves", true, false, 0.2f, 0f),
    ACACIA_LOG(98, "Acacia Log", true, false, 2.0f, 0f),
    ACACIA_LEAVES(99, "Acacia Leaves", true, false, 0.2f, 0f),
    MANGROVE_LOG(100, "Mangrove Log", true, false, 2.0f, 0f),
    MANGROVE_LEAVES(101, "Mangrove Leaves", true, false, 0.2f, 0f),
    DARK_OAK_LOG(102, "Dark Oak Log", true, false, 2.0f, 0f),
    DARK_OAK_LEAVES(103, "Dark Oak Leaves", true, false, 0.2f, 0f),
    CHERRY_LOG(104, "Cherry Log", true, false, 2.0f, 0f),
    CHERRY_LEAVES(105, "Cherry Leaves", true, false, 0.2f, 0f),
    MUD(106, "Mud", true, false, 0.5f, 0f),
    MANGROVE_ROOTS(107, "Mangrove Roots", true, false, 0.7f, 0f),
    // Real vanilla saplings are a small cross/plant model with no solid collision - rendered by
    // ChunkMeshBuilder as two crossing diagonal quads (not a cube), same idea as real vanilla's
    // model, see its emitCrossPlant. transparentRender=true is required for that (not just a
    // rendering-style choice): it's what keeps isOpaqueCube() false, so a sapling never culls
    // the face of the block underneath it (it previously did, when this was marked opaque - a
    // real bug: the grass block's own top face was being skipped because the sapling above it
    // was treated as fully solid) and light still passes through it correctly (LightEngine also
    // keys off isOpaqueCube()). All 4 growth stages share one texture, matching real vanilla
    // (the stage is an internal simulation detail, not a visual one there either). Only STAGE_0
    // is player-placeable (see BlockType.PLACEABLE) - stages 1-3 are SaplingGrowth's own
    // internal states, reached only by growing from stage 0.
    OAK_SAPLING_STAGE_0(108, "Oak Sapling", false, true, 0f, 0f),
    OAK_SAPLING_STAGE_1(109, "Oak Sapling", false, true, 0f, 0f),
    OAK_SAPLING_STAGE_2(110, "Oak Sapling", false, true, 0f, 0f),
    OAK_SAPLING_STAGE_3(111, "Oak Sapling", false, true, 0f, 0f),
    // Tools and their crafting ingredient (STICK). None of these are ever placed in the world -
    // solidCollision/transparentRender=(false,true) matches the sapling convention (never an
    // opaque cube), and hardness=-1 (matches BEDROCK/WATER's "not something you break normally")
    // means a tool is treated as unbreakable/no-drop by BlockDrops.dropsFor if one is ever
    // targeted anyway (only reachable through the test controller's setBlock, never real play -
    // see ToolEffectiveness's class docs). Not listed in PLACEABLE, so BlockPlacer already
    // refuses to place any of them without needing its own special case.
    STICK(112, "Stick", false, true, -1f, 0f),
    WOODEN_PICKAXE(113, "Wooden Pickaxe", false, true, -1f, 0f),
    STONE_PICKAXE(114, "Stone Pickaxe", false, true, -1f, 0f),
    IRON_PICKAXE(115, "Iron Pickaxe", false, true, -1f, 0f),
    WOODEN_AXE(116, "Wooden Axe", false, true, -1f, 0f),
    STONE_AXE(117, "Stone Axe", false, true, -1f, 0f),
    IRON_AXE(118, "Iron Axe", false, true, -1f, 0f),
    WOODEN_SHOVEL(119, "Wooden Shovel", false, true, -1f, 0f),
    STONE_SHOVEL(120, "Stone Shovel", false, true, -1f, 0f),
    IRON_SHOVEL(121, "Iron Shovel", false, true, -1f, 0f),
    // Food items (see FoodRegistry) - same non-placeable-item convention as
    // the tools above. No farming or cooking exists yet, so none of these have a crafting
    // recipe; obtainable only via the test controller for now, the same "the real item exists,
    // nothing legitimately produces it yet" situation already accepted for IRON_PICKAXE.
    APPLE(122, "Apple", false, true, -1f, 0f),
    BREAD(123, "Bread", false, true, -1f, 0f),
    COOKED_BEEF(124, "Cooked Beef", false, true, -1f, 0f),

    // ---- Cooking/smelting milestone (see com.example.cooking) ----

    // New processing-station blocks - real placeable/minable blocks like the existing FURNACE.
    BLAST_FURNACE(125, "Blast Furnace", true, false, 3.5f, 0f),
    SMOKER(126, "Smoker", true, false, 3.5f, 0f),

    // New real ore/decorative blocks - same convention as the existing ore blocks.
    COPPER_ORE(127, "Copper Ore", true, false, 3.0f, 0f),
    SMOOTH_STONE(128, "Smooth Stone", true, false, 2.0f, 0f),

    // Smelting/cooking outputs and fuel that are items, not blocks - same non-placeable
    // convention as the tools/food above. COAL is the real drop from mining COAL_ORE (see
    // BlockDrops) - the one existing-item-drop change this milestone needed, since fuel has to
    // be legitimately obtainable for the whole feature to be more than creative-only.
    COAL(129, "Coal", false, true, -1f, 0f),
    CHARCOAL(130, "Charcoal", false, true, -1f, 0f),
    IRON_INGOT(131, "Iron Ingot", false, true, -1f, 0f),
    GOLD_INGOT(132, "Gold Ingot", false, true, -1f, 0f),
    COPPER_INGOT(133, "Copper Ingot", false, true, -1f, 0f),
    IRON_NUGGET(134, "Iron Nugget", false, true, -1f, 0f),
    BRICK(135, "Brick", false, true, -1f, 0f),
    // Raw ore materials (real modern-vanilla items) - kept as real items with full smelting
    // logic, but this project has no ore-block-drops-raw-material mechanic, so - like
    // IRON_PICKAXE before them - they're obtainable only through the test controller for now.
    RAW_IRON(136, "Raw Iron", false, true, -1f, 0f),
    RAW_GOLD(137, "Raw Gold", false, true, -1f, 0f),
    RAW_COPPER(138, "Raw Copper", false, true, -1f, 0f),

    // Smoker/food raw+cooked pairs - no mob-drop or farming system exists, so (like APPLE/BREAD)
    // these are real, fully cookable items obtainable only through the test controller for now.
    RAW_BEEF(139, "Raw Beef", false, true, -1f, 0f), // cooks into the existing COOKED_BEEF
    RAW_PORKCHOP(140, "Raw Porkchop", false, true, -1f, 0f),
    COOKED_PORKCHOP(141, "Cooked Porkchop", false, true, -1f, 0f),
    RAW_CHICKEN(142, "Raw Chicken", false, true, -1f, 0f),
    COOKED_CHICKEN(143, "Cooked Chicken", false, true, -1f, 0f),
    RAW_MUTTON(144, "Raw Mutton", false, true, -1f, 0f),
    COOKED_MUTTON(145, "Cooked Mutton", false, true, -1f, 0f),
    RAW_RABBIT(146, "Raw Rabbit", false, true, -1f, 0f),
    COOKED_RABBIT(147, "Cooked Rabbit", false, true, -1f, 0f),
    COD(148, "Raw Cod", false, true, -1f, 0f),
    COOKED_COD(149, "Cooked Cod", false, true, -1f, 0f),
    SALMON(150, "Raw Salmon", false, true, -1f, 0f),
    COOKED_SALMON(151, "Cooked Salmon", false, true, -1f, 0f),
    POTATO(152, "Potato", false, true, -1f, 0f),
    BAKED_POTATO(153, "Baked Potato", false, true, -1f, 0f),
    // Real vanilla Kelp is an underwater plant block that grows - this project has no
    // underwater-plant/farming system, so it's simplified to a plain item, like the foods above.
    KELP(154, "Kelp", false, true, -1f, 0f),
    DRIED_KELP(155, "Dried Kelp", false, true, -1f, 0f),
    // Mob-system milestone: the one zombie drop. Real vanilla's rotten flesh also has a chance
    // to inflict Hunger on eating - not modeled here (documented simplification, matching this
    // file's own per-item confidence-level convention).
    ROTTEN_FLESH(156, "Rotten Flesh", false, true, -1f, 0f),
    // Combat milestone: swords, passive-mob drops and armor (real items, never placed as blocks).
    WOODEN_SWORD(157, "Wooden Sword", false, true, -1f, 0f),
    STONE_SWORD(158, "Stone Sword", false, true, -1f, 0f),
    IRON_SWORD(159, "Iron Sword", false, true, -1f, 0f),
    LEATHER(160, "Leather", false, true, -1f, 0f),
    FEATHER(161, "Feather", false, true, -1f, 0f),
    EGG(162, "Egg", false, true, -1f, 0f),
    LEATHER_HELMET(163, "Leather Cap", false, true, -1f, 0f),
    LEATHER_CHESTPLATE(164, "Leather Tunic", false, true, -1f, 0f),
    LEATHER_LEGGINGS(165, "Leather Pants", false, true, -1f, 0f),
    LEATHER_BOOTS(166, "Leather Boots", false, true, -1f, 0f),
    IRON_HELMET(167, "Iron Helmet", false, true, -1f, 0f),
    IRON_CHESTPLATE(168, "Iron Chestplate", false, true, -1f, 0f),
    IRON_LEGGINGS(169, "Iron Leggings", false, true, -1f, 0f),
    IRON_BOOTS(170, "Iron Boots", false, true, -1f, 0f),
    GOLD_HELMET(171, "Golden Helmet", false, true, -1f, 0f),
    GOLD_CHESTPLATE(172, "Golden Chestplate", false, true, -1f, 0f),
    GOLD_LEGGINGS(173, "Golden Leggings", false, true, -1f, 0f),
    GOLD_BOOTS(174, "Golden Boots", false, true, -1f, 0f),
    DIAMOND(175, "Diamond", false, true, -1f, 0f),
    DIAMOND_SWORD(176, "Diamond Sword", false, true, -1f, 0f),
    DIAMOND_AXE(177, "Diamond Axe", false, true, -1f, 0f),
    DIAMOND_PICKAXE(178, "Diamond Pickaxe", false, true, -1f, 0f),
    DIAMOND_SHOVEL(179, "Diamond Shovel", false, true, -1f, 0f),
    DIAMOND_HELMET(180, "Diamond Helmet", false, true, -1f, 0f),
    DIAMOND_CHESTPLATE(181, "Diamond Chestplate", false, true, -1f, 0f),
    DIAMOND_LEGGINGS(182, "Diamond Leggings", false, true, -1f, 0f),
    DIAMOND_BOOTS(183, "Diamond Boots", false, true, -1f, 0f),
    FLINT(184, "Flint", false, true, -1f, 0f),
    CLAY_BALL(185, "Clay Ball", false, true, -1f, 0f),
    SNOWBALL(186, "Snowball", false, true, -1f, 0f),
    GLOWSTONE_DUST(187, "Glowstone Dust", false, true, -1f, 0f),
    BOOK(188, "Book", false, true, -1f, 0f),
    GUNPOWDER(189, "Gunpowder", false, true, -1f, 0f),
    BONE(190, "Bone", false, true, -1f, 0f),
    ARROW(191, "Arrow", false, true, -1f, 0f),
    STRING(192, "String", false, true, -1f, 0f),
    SPIDER_EYE(193, "Spider Eye", false, true, -1f, 0f),
    // Gold tools and the hoes (the tools themselves; hoes have no tilling because there is no farmland yet).
    GOLD_SWORD(194, "Golden Sword", false, true, -1f, 0f),
    GOLD_AXE(195, "Golden Axe", false, true, -1f, 0f),
    GOLD_PICKAXE(196, "Golden Pickaxe", false, true, -1f, 0f),
    GOLD_SHOVEL(197, "Golden Shovel", false, true, -1f, 0f),
    WOODEN_HOE(198, "Wooden Hoe", false, true, -1f, 0f),
    STONE_HOE(199, "Stone Hoe", false, true, -1f, 0f),
    IRON_HOE(200, "Iron Hoe", false, true, -1f, 0f),
    GOLD_HOE(201, "Golden Hoe", false, true, -1f, 0f),
    DIAMOND_HOE(202, "Diamond Hoe", false, true, -1f, 0f),
    // Copper tools (same stats as stone, tier #minecraft:copper_tool_materials = copper ingot) and the six spears (jab attack only).
    COPPER_SWORD(203, "Copper Sword", false, true, -1f, 0f),
    COPPER_AXE(204, "Copper Axe", false, true, -1f, 0f),
    COPPER_PICKAXE(205, "Copper Pickaxe", false, true, -1f, 0f),
    COPPER_SHOVEL(206, "Copper Shovel", false, true, -1f, 0f),
    COPPER_HOE(207, "Copper Hoe", false, true, -1f, 0f),
    WOODEN_SPEAR(208, "Wooden Spear", false, true, -1f, 0f),
    STONE_SPEAR(209, "Stone Spear", false, true, -1f, 0f),
    COPPER_SPEAR(210, "Copper Spear", false, true, -1f, 0f),
    IRON_SPEAR(211, "Iron Spear", false, true, -1f, 0f),
    GOLD_SPEAR(212, "Golden Spear", false, true, -1f, 0f),
    DIAMOND_SPEAR(213, "Diamond Spear", false, true, -1f, 0f),
    // Torches (light 14): a floor torch, and a wall torch per facing (the direction its flame leans, away from the wall); only TORCH is an
    // item. Farmland and wet farmland: a full collision block here (vanilla's is 15/16 high), non-occluding like vanilla's.
    TORCH(214, "Torch", false, true, 0f, 14f),
    WALL_TORCH_EAST(215, "Torch", false, true, 0f, 14f),
    WALL_TORCH_WEST(216, "Torch", false, true, 0f, 14f),
    WALL_TORCH_NORTH(217, "Torch", false, true, 0f, 14f),
    WALL_TORCH_SOUTH(218, "Torch", false, true, 0f, 14f),
    FARMLAND(219, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_MOIST(220, "Farmland", true, true, 0.6f, 0f),
    GOLDEN_APPLE(221, "Golden Apple", false, true, -1f, 0f),
    BOW(222, "Bow", false, true, -1f, 0f),
    // The bow while it is drawn: only ever shown in the hand, never an item in a slot.
    BOW_PULLING_0(223, "Bow", false, true, -1f, 0f),
    BOW_PULLING_1(224, "Bow", false, true, -1f, 0f),
    BOW_PULLING_2(225, "Bow", false, true, -1f, 0f);

    public final int id;
    public final String displayName;
    @Getter
    private final boolean solidCollision;
    @Getter
    private final boolean transparentRender;
    /** Hardness in seconds; -1 means unbreakable by hand. */
    public final float hardness;
    public final float lightEmission;

    BlockType(int id, String displayName, boolean solidCollision, boolean transparentRender, float hardness, float lightEmission) {
        this.id = id;
        this.displayName = displayName;
        this.solidCollision = solidCollision;
        this.transparentRender = transparentRender;
        this.hardness = hardness;
        this.lightEmission = lightEmission;
    }

    /**
     * Full cubes that fully occlude neighboring faces. Derived directly from transparentRender
     * so any block flagged transparent (water, glass, ice, ...) is automatically non-opaque -
     * ice used to be left out of this and stayed in the opaque mesh pass despite having a
     * partially-transparent texture, which produced blend-order artifacts against whatever
     * happened to be drawn after it in the same batch.
     */
    public boolean isOpaqueCube() {
        return this != AIR && !transparentRender;
    }

    public boolean isAir() {
        return this == AIR;
    }

    /** Approximate vanilla "hand break time" = hardness * 1.5 seconds; unbreakable blocks return -1. */
    public float handBreakSeconds() {
        if (hardness < 0) return -1f;
        if (this == TNT) return 0.05f;
        return hardness * 1.5f;
    }

    private static final BlockType[] BY_ID = new BlockType[256];
    static {
        for (BlockType b : values()) {
            BY_ID[b.id] = b;
        }
    }

    public static BlockType byId(int id) {
        if (id < 0 || id >= BY_ID.length) return AIR;
        BlockType b = BY_ID[id];
        return b == null ? AIR : b;
    }

    /** Block types the player can place, in a sensible hotbar order. */
    public static final BlockType[] PLACEABLE = {
            STONE, COBBLESTONE, DIRT, GRASS_BLOCK, OAK_PLANKS, OAK_LOG, OAK_LEAVES,
            BIRCH_LOG, BIRCH_LEAVES, SPRUCE_LOG, SPRUCE_LEAVES, JUNGLE_LOG, JUNGLE_LEAVES,
            ACACIA_LOG, ACACIA_LEAVES, MANGROVE_LOG, MANGROVE_LEAVES,
            DARK_OAK_LOG, DARK_OAK_LEAVES, CHERRY_LOG, CHERRY_LEAVES, MUD, MANGROVE_ROOTS,
            OAK_SAPLING_STAGE_0, TORCH,
            SAND, GRAVEL, SANDSTONE, GLASS, BRICKS, MOSSY_COBBLESTONE, OBSIDIAN,
            COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, COPPER_ORE, IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK,
            SMOOTH_STONE, BOOKSHELF, CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER, CHEST, TNT, ICE, SNOW_BLOCK, CLAY,
            PUMPKIN, NETHERRACK, GLOWSTONE, TERRACOTTA, RED_SAND
    };
}
