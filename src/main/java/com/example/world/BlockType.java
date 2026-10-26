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
    BOW_PULLING_2(225, "Bow", false, true, -1f, 0f),
    // Breeding items and two foods (no farming exists, so these only come from the test controller for now).
    WHEAT(226, "Wheat", false, true, -1f, 0f),
    WHEAT_SEEDS(227, "Wheat Seeds", false, true, -1f, 0f),
    CARROT(228, "Carrot", false, true, -1f, 0f),
    BEETROOT(229, "Beetroot", false, true, -1f, 0f),
    BEETROOT_SEEDS(230, "Beetroot Seeds", false, true, -1f, 0f),
    // Flowing fluids. Vanilla keeps a fluid's amount (1-8) and a "falling" flag in the block state; here every combination
    // that can occur is its own block id. Water: source (WATER, 9), amounts 1-7 and falling; it loses 1 per block. Lava loses 2
    // per block in the overworld, so it only has the amounts 6, 4 and 2 (and falling). Lava is lit (15) and drawn without blending.
    WATER_FLOW_1(231, "Water", false, true, -1f, 0f),
    WATER_FLOW_2(232, "Water", false, true, -1f, 0f),
    WATER_FLOW_3(233, "Water", false, true, -1f, 0f),
    WATER_FLOW_4(234, "Water", false, true, -1f, 0f),
    WATER_FLOW_5(235, "Water", false, true, -1f, 0f),
    WATER_FLOW_6(236, "Water", false, true, -1f, 0f),
    WATER_FLOW_7(237, "Water", false, true, -1f, 0f),
    WATER_FALLING(238, "Water", false, true, -1f, 0f),
    LAVA(239, "Lava", false, true, -1f, 15f),
    LAVA_FLOW_2(240, "Lava", false, true, -1f, 15f),
    LAVA_FLOW_4(241, "Lava", false, true, -1f, 15f),
    LAVA_FLOW_6(242, "Lava", false, true, -1f, 15f),
    LAVA_FALLING(243, "Lava", false, true, -1f, 15f),
    BUCKET(244, "Bucket", false, true, -1f, 0f),
    WATER_BUCKET(245, "Water Bucket", false, true, -1f, 0f),
    LAVA_BUCKET(246, "Lava Bucket", false, true, -1f, 0f),
    // Farmland keeps its moisture 0-7 in the block state in vanilla; here FARMLAND is 0, FARMLAND_MOIST is 7 and these are 1-6.
    FARMLAND_1(247, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_2(248, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_3(249, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_4(250, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_5(251, "Farmland", true, true, 0.6f, 0f),
    FARMLAND_6(252, "Farmland", true, true, 0.6f, 0f),

    // ---- Vanilla terrain blocks (G28; docs/PARITY.md section 28) ----
    // Every hardness below is the destroyTime of the block's Properties in the 26.3 client's Blocks static initializer
    // (strength(F) sets destroyTime and explosion resistance to the same value, strength(FF) sets them separately; the
    // resistances are in Explosions.blastResistance). None of these emits light. Ids start at 256 (255 stays unused).
    CALCITE(256, "Calcite", true, false, 0.75f, 0f),
    CINNABAR(257, "Cinnabar", true, false, 1.5f, 0f),
    COARSE_DIRT(258, "Coarse Dirt", true, false, 0.5f, 0f),
    // Vanilla deepslate is a RotatedPillarBlock (axis x/y/z); only axis=y exists here (see VanillaBlocks).
    DEEPSLATE(259, "Deepslate", true, false, 3.0f, 0f),
    // What deepslate drops without Silk Touch (loot_table/blocks/deepslate.json); ofLegacyCopy(DEEPSLATE).strength(3.5, 6).
    COBBLED_DEEPSLATE(260, "Cobbled Deepslate", true, false, 3.5f, 0f),
    // ofLegacyCopy(IRON_ORE).strength(4.5, 3).
    DEEPSLATE_IRON_ORE(261, "Deepslate Iron Ore", true, false, 4.5f, 0f),
    GRANITE(262, "Granite", true, false, 1.5f, 0f),
    MYCELIUM(263, "Mycelium", true, false, 0.6f, 0f),
    PODZOL(264, "Podzol", true, false, 0.5f, 0f),
    // Vanilla: noOcclusion, an empty visual shape and a collision shape that depends on the entity (PowderSnowBlock);
    // here it is a non-occluding full-collision cube - the missing behaviour is listed in docs/PARITY.md section 28.
    POWDER_SNOW(265, "Powder Snow", true, true, 0.25f, 0f),
    PACKED_ICE(266, "Packed Ice", true, false, 0.5f, 0f),
    RAW_COPPER_BLOCK(267, "Block of Raw Copper", true, false, 5.0f, 0f),
    RAW_IRON_BLOCK(268, "Block of Raw Iron", true, false, 5.0f, 0f),
    RED_SANDSTONE(269, "Red Sandstone", true, false, 0.8f, 0f),
    SULFUR(270, "Sulfur", true, false, 1.5f, 0f),
    TUFF(271, "Tuff", true, false, 1.5f, 0f),
    // Blocks.DYED_TERRACOTTA: every colour shares one Properties lambda, strength(1.25, 4.2) like plain terracotta.
    WHITE_TERRACOTTA(272, "White Terracotta", true, false, 1.25f, 0f),
    ORANGE_TERRACOTTA(273, "Orange Terracotta", true, false, 1.25f, 0f),
    YELLOW_TERRACOTTA(274, "Yellow Terracotta", true, false, 1.25f, 0f),
    BROWN_TERRACOTTA(275, "Brown Terracotta", true, false, 1.25f, 0f),
    RED_TERRACOTTA(276, "Red Terracotta", true, false, 1.25f, 0f),
    LIGHT_GRAY_TERRACOTTA(277, "Light Gray Terracotta", true, false, 1.25f, 0f),

    // ---- Blocks the ported features write (C1; docs/PARITY.md section 31) ----
    // destroyTime from the 26.3 Blocks static initializer: the deepslate ores are ofLegacyCopy(<ore>).strength(4.5, 3); the infested
    // blocks are InfestedBlock(host), whose constructor sets destroyTime host.defaultDestroyTime() / 2 and explosionResistance 0.75;
    // magma is strength(0.5) with lightLevel 3; redstone ore is lightLevel(litBlockEmission(9)), 0 in its lit=false state (the only
    // one the engine has). Names from assets/minecraft/lang/en_us.json.
    ANDESITE(278, "Andesite", true, false, 1.5f, 0f),
    DIORITE(279, "Diorite", true, false, 1.5f, 0f),
    DEEPSLATE_COAL_ORE(280, "Deepslate Coal Ore", true, false, 4.5f, 0f),
    DEEPSLATE_COPPER_ORE(281, "Deepslate Copper Ore", true, false, 4.5f, 0f),
    DEEPSLATE_DIAMOND_ORE(282, "Deepslate Diamond Ore", true, false, 4.5f, 0f),
    DEEPSLATE_EMERALD_ORE(283, "Deepslate Emerald Ore", true, false, 4.5f, 0f),
    DEEPSLATE_GOLD_ORE(284, "Deepslate Gold Ore", true, false, 4.5f, 0f),
    DEEPSLATE_LAPIS_ORE(285, "Deepslate Lapis Lazuli Ore", true, false, 4.5f, 0f),
    DEEPSLATE_REDSTONE_ORE(286, "Deepslate Redstone Ore", true, false, 4.5f, 0f),
    EMERALD_ORE(287, "Emerald Ore", true, false, 3.0f, 0f),
    LAPIS_ORE(288, "Lapis Lazuli Ore", true, false, 3.0f, 0f),
    REDSTONE_ORE(289, "Redstone Ore", true, false, 3.0f, 0f),
    INFESTED_STONE(290, "Infested Stone", true, false, 0.75f, 0f),
    // Vanilla: InfestedRotatedPillarBlock (axis x/y/z); only axis=y exists here, like deepslate.
    INFESTED_DEEPSLATE(291, "Infested Deepslate", true, false, 1.5f, 0f),
    MAGMA_BLOCK(292, "Magma Block", true, false, 0.5f, 3f),
    // The ores' drop items (loot_table/blocks/*.json); inventory items only (redstone dust places no wire here).
    LAPIS_LAZULI(293, "Lapis Lazuli", false, true, -1f, 0f),
    REDSTONE(294, "Redstone Dust", false, true, -1f, 0f),
    EMERALD(295, "Emerald", false, true, -1f, 0f);

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

    /** Any water: the source (id 9), the seven flowing amounts and the falling column. */
    public boolean isWater() {
        return this == WATER || (id >= 231 && id <= 238);
    }

    public boolean isLava() {
        return id >= 239 && id <= 243;
    }

    public boolean isFluid() {
        return isWater() || isLava();
    }

    /** A still source block of water or lava (amount 8, not falling). */
    public boolean isFluidSource() {
        return this == WATER || this == LAVA;
    }

    /** The falling column of a fluid: full height, amount 8 (vanilla's FALLING flag). */
    public boolean isFluidFalling() {
        return this == WATER_FALLING || this == LAVA_FALLING;
    }

    /** FluidState.getAmount: 8 for a source and for a falling column, 1-7 for flowing water, 2, 4 or 6 for flowing lava, 0 for no fluid. */
    public int fluidAmount() {
        if (this == WATER || this == LAVA || isFluidFalling()) return 8;
        if (id >= 231 && id <= 237) return id - 230;
        if (id >= 240 && id <= 242) return (id - 239) * 2;
        return 0;
    }

    /** The fluid block with this amount (1-8; lava rounds down to an even amount) or the falling column. */
    public static BlockType fluidBlock(boolean lava, int amount, boolean falling) {
        if (falling) return lava ? LAVA_FALLING : WATER_FALLING;
        if (amount >= 8) return lava ? LAVA : WATER;
        if (amount <= 0) return AIR;
        if (!lava) return byId(230 + amount);
        int even = amount / 2;
        return even <= 0 ? AIR : byId(239 + even);
    }

    /** Approximate vanilla "hand break time" = hardness * 1.5 seconds; unbreakable blocks return -1. */
    public float handBreakSeconds() {
        if (hardness < 0) return -1f;
        if (this == TNT) return 0.05f;
        return hardness * 1.5f;
    }

    private static final BlockType[] BY_ID;
    static {
        int highest = 0;
        for (BlockType b : values()) highest = Math.max(highest, b.id);
        BY_ID = new BlockType[highest + 1];
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
            PUMPKIN, NETHERRACK, GLOWSTONE, TERRACOTTA, RED_SAND,
            // G28 terrain blocks. Powder snow has no block item in vanilla (it is placed from a powder snow bucket), so it is not here.
            CALCITE, CINNABAR, COARSE_DIRT, DEEPSLATE, COBBLED_DEEPSLATE, DEEPSLATE_IRON_ORE, GRANITE, MYCELIUM, PODZOL,
            PACKED_ICE, RAW_COPPER_BLOCK, RAW_IRON_BLOCK, RED_SANDSTONE, SULFUR, TUFF,
            WHITE_TERRACOTTA, ORANGE_TERRACOTTA, YELLOW_TERRACOTTA, BROWN_TERRACOTTA, RED_TERRACOTTA, LIGHT_GRAY_TERRACOTTA,
            // C1 feature blocks (all have block items in vanilla).
            ANDESITE, DIORITE, DEEPSLATE_COAL_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE, DEEPSLATE_GOLD_ORE,
            DEEPSLATE_LAPIS_ORE, DEEPSLATE_REDSTONE_ORE, EMERALD_ORE, LAPIS_ORE, REDSTONE_ORE, INFESTED_STONE, INFESTED_DEEPSLATE, MAGMA_BLOCK
    };
}
