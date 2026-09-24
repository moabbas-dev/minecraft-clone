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
    OAK_PLANKS(5, "Oak Wood Planks", true, false, 2.0f, 0f),
    BEDROCK(7, "Bedrock", true, false, -1f, 0f),
    WATER(9, "Water", false, true, -1f, 0f),
    SAND(12, "Sand", true, false, 0.5f, 0f),
    GRAVEL(13, "Gravel", true, false, 0.6f, 0f),
    GOLD_ORE(14, "Gold Ore", true, false, 3.0f, 0f),
    IRON_ORE(15, "Iron Ore", true, false, 3.0f, 0f),
    COAL_ORE(16, "Coal Ore", true, false, 3.0f, 0f),
    OAK_LOG(17, "Oak Wood", true, false, 2.0f, 0f),
    OAK_LEAVES(18, "Oak Leaves", true, false, 0.2f, 0f),
    GLASS(20, "Glass", false, true, 0.3f, 0f),
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
    BIRCH_LOG(92, "Birch Wood", true, false, 2.0f, 0f),
    BIRCH_LEAVES(93, "Birch Leaves", true, false, 0.2f, 0f),
    SPRUCE_LOG(94, "Spruce Wood", true, false, 2.0f, 0f),
    SPRUCE_LEAVES(95, "Spruce Leaves", true, false, 0.2f, 0f),
    JUNGLE_LOG(96, "Jungle Wood", true, false, 2.0f, 0f),
    JUNGLE_LEAVES(97, "Jungle Leaves", true, false, 0.2f, 0f),
    ACACIA_LOG(98, "Acacia Wood", true, false, 2.0f, 0f),
    ACACIA_LEAVES(99, "Acacia Leaves", true, false, 0.2f, 0f),
    MANGROVE_LOG(100, "Mangrove Wood", true, false, 2.0f, 0f),
    MANGROVE_LEAVES(101, "Mangrove Leaves", true, false, 0.2f, 0f),
    DARK_OAK_LOG(102, "Dark Oak Wood", true, false, 2.0f, 0f),
    DARK_OAK_LEAVES(103, "Dark Oak Leaves", true, false, 0.2f, 0f),
    CHERRY_LOG(104, "Cherry Wood", true, false, 2.0f, 0f),
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
    OAK_SAPLING_STAGE_3(111, "Oak Sapling", false, true, 0f, 0f);

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
            OAK_SAPLING_STAGE_0,
            SAND, GRAVEL, SANDSTONE, GLASS, BRICKS, MOSSY_COBBLESTONE, OBSIDIAN,
            COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK,
            BOOKSHELF, CRAFTING_TABLE, FURNACE, CHEST, TNT, ICE, SNOW_BLOCK, CLAY,
            PUMPKIN, NETHERRACK, GLOWSTONE, TERRACOTTA, RED_SAND
    };
}
