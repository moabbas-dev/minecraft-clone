package com.example.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Block and item texture atlas. Every tile is a real Minecraft texture read from {@code textures/} (the user's own local
 * copy, restored by {@code scripts/restore-assets.py}; see {@link #REAL_TEXTURE_PATHS}). Nothing is drawn by the program: a
 * tile whose file is missing, unreadable or an unexpected size shows vanilla's magenta-and-black "missing texture" square
 * and is logged, so a missing file never crashes the game.
 */
@Slf4j
public class TextureAtlas {
    public static final int TILE_PX = 16;
    // Each tile gets a wrapped (tile-repeating) border so mipmap downsampling blends a tile with
    // itself instead of bleeding in neighboring tiles' colors. 1px only protected mip level 0-1;
    // at distance (mip 2+) a 1px gutter let e.g. the water tile next to sand tint far-away sand
    // blocks with blue grid lines. 8px keeps every mip level up to MAX_MIP_LEVEL clean.
    public static final int BORDER_PX = 8;
    /** A 16px tile is a single texel at level 4; deeper levels only blend the whole atlas. */
    private static final int MAX_MIP_LEVEL = 4;
    public static final int CELL_PX = TILE_PX + BORDER_PX * 2;
    public static final int COLS = 8;
    public static final int ROWS = (Tile.values().length + COLS - 1) / COLS;

    private static final Map<Tile, String> REAL_TEXTURE_PATHS = new EnumMap<>(Tile.class);

    /**
     * The animated tiles and which 16 x 16 part of a frame each shows: the still sprites and the burning overlay one whole frame; the
     * flowing sprites are 32 x 32 frames, whose top-left quarter is what the sides of a fluid use (u 0 to 0.5, v 0 to 0.5 of the sprite) and
     * whose middle (8, 8) is what a moving surface uses (FluidRenderer samples 0.25 to 0.75 around the centre, turned to the flow).
     */
    private static final Map<Tile, int[]> ANIMATED = new EnumMap<>(Tile.class);

    static {
        ANIMATED.put(Tile.WATER, new int[]{0, 0});
        ANIMATED.put(Tile.WATER_FLOW, new int[]{0, 0});
        ANIMATED.put(Tile.WATER_FLOW_TOP, new int[]{8, 8});
        ANIMATED.put(Tile.LAVA, new int[]{0, 0});
        ANIMATED.put(Tile.LAVA_FLOW, new int[]{0, 0});
        ANIMATED.put(Tile.LAVA_FLOW_TOP, new int[]{8, 8});
        ANIMATED.put(Tile.FIRE_1, new int[]{0, 0});
    }

    private final Map<Tile, TextureAnimation> animations = new EnumMap<>(Tile.class);

    /** Cutout tiles (binary alpha - fully opaque leaf/sprout pixels, fully transparent holes)
     *  whose transparent pixels get {@link LeafEdgeDilate} applied before upload, so mipmapping
     *  never blends the black behind a hole into a leaf edge (the "pale blue line" glitch). Every
     *  real leaf species plus the sapling sprite - anything else with a real texture (e.g. GLASS)
     *  is a deliberate partial-alpha look, not a cutout, and is left as-is. */
    private static final java.util.Set<Tile> CUTOUT_FOLIAGE = java.util.EnumSet.of(
            Tile.LEAVES, Tile.BIRCH_LEAVES, Tile.SPRUCE_LEAVES, Tile.JUNGLE_LEAVES, Tile.ACACIA_LEAVES,
            Tile.MANGROVE_LEAVES, Tile.DARK_OAK_LEAVES, Tile.CHERRY_LEAVES, Tile.OAK_SAPLING,
            // Every other flat-icon-only item (tools/stick, food - see ChunkMeshBuilder.isFlatIconItem)
            // is the same kind of cutout tile rendered through the same mipmapped flat-icon quad,
            // so it gets the same edge-halo fix, not just the species this was originally reported for.
            Tile.STICK, Tile.WOODEN_PICKAXE, Tile.STONE_PICKAXE, Tile.IRON_PICKAXE,
            Tile.WOODEN_AXE, Tile.STONE_AXE, Tile.IRON_AXE, Tile.WOODEN_SHOVEL, Tile.STONE_SHOVEL, Tile.IRON_SHOVEL,
            Tile.APPLE, Tile.BREAD, Tile.COOKED_BEEF,
            // Cooking-milestone flat-icon items (not the 4 real block tiles - those are normal
            // opaque cube textures, not cutouts).
            Tile.COAL, Tile.CHARCOAL, Tile.IRON_INGOT, Tile.GOLD_INGOT, Tile.COPPER_INGOT,
            Tile.IRON_NUGGET, Tile.BRICK, Tile.RAW_IRON, Tile.RAW_GOLD, Tile.RAW_COPPER,
            Tile.RAW_BEEF, Tile.RAW_PORKCHOP, Tile.COOKED_PORKCHOP, Tile.RAW_CHICKEN, Tile.COOKED_CHICKEN,
            Tile.RAW_MUTTON, Tile.COOKED_MUTTON, Tile.RAW_RABBIT, Tile.COOKED_RABBIT,
            Tile.COD, Tile.COOKED_COD, Tile.SALMON, Tile.COOKED_SALMON,
            Tile.POTATO, Tile.BAKED_POTATO, Tile.KELP, Tile.DRIED_KELP,
            Tile.ROTTEN_FLESH,
            Tile.WOODEN_SWORD, Tile.STONE_SWORD, Tile.IRON_SWORD, Tile.LEATHER, Tile.FEATHER, Tile.EGG, Tile.LEATHER_HELMET, Tile.LEATHER_CHESTPLATE, Tile.LEATHER_LEGGINGS, Tile.LEATHER_BOOTS, Tile.IRON_HELMET, Tile.IRON_CHESTPLATE, Tile.IRON_LEGGINGS, Tile.IRON_BOOTS, Tile.GOLD_HELMET, Tile.GOLD_CHESTPLATE, Tile.GOLD_LEGGINGS, Tile.GOLD_BOOTS,
            Tile.DIAMOND, Tile.DIAMOND_SWORD, Tile.DIAMOND_AXE, Tile.DIAMOND_PICKAXE, Tile.DIAMOND_SHOVEL, Tile.DIAMOND_HELMET, Tile.DIAMOND_CHESTPLATE, Tile.DIAMOND_LEGGINGS, Tile.DIAMOND_BOOTS,
            Tile.FLINT, Tile.CLAY_BALL, Tile.SNOWBALL, Tile.GLOWSTONE_DUST, Tile.BOOK,
            Tile.GUNPOWDER, Tile.BONE, Tile.ARROW, Tile.STRING, Tile.SPIDER_EYE,
            Tile.GOLD_SWORD, Tile.GOLD_AXE, Tile.GOLD_PICKAXE, Tile.GOLD_SHOVEL, Tile.WOODEN_HOE, Tile.STONE_HOE, Tile.IRON_HOE, Tile.GOLD_HOE, Tile.DIAMOND_HOE,
            Tile.TORCH, Tile.GOLDEN_APPLE, Tile.BOW, Tile.BOW_PULLING_0, Tile.BOW_PULLING_1, Tile.BOW_PULLING_2, Tile.WHEAT, Tile.BUCKET, Tile.WATER_BUCKET, Tile.LAVA_BUCKET, Tile.WHEAT_SEEDS, Tile.CARROT, Tile.BEETROOT, Tile.BEETROOT_SEEDS, Tile.COPPER_SWORD, Tile.COPPER_AXE, Tile.COPPER_PICKAXE, Tile.COPPER_SHOVEL, Tile.COPPER_HOE, Tile.WOODEN_SPEAR, Tile.STONE_SPEAR, Tile.COPPER_SPEAR, Tile.IRON_SPEAR, Tile.GOLD_SPEAR, Tile.DIAMOND_SPEAR);

    static {
        REAL_TEXTURE_PATHS.put(Tile.STONE, "textures/block/stone.png");
        REAL_TEXTURE_PATHS.put(Tile.DIRT, "textures/block/dirt.png");
        REAL_TEXTURE_PATHS.put(Tile.GRASS_TOP, "textures/block/grass_block_top.png");
        REAL_TEXTURE_PATHS.put(Tile.GRASS_SIDE, "textures/block/grass_block_side.png");
        REAL_TEXTURE_PATHS.put(Tile.COBBLESTONE, "textures/block/cobblestone.png");
        REAL_TEXTURE_PATHS.put(Tile.PLANKS, "textures/block/oak_planks.png");
        REAL_TEXTURE_PATHS.put(Tile.BEDROCK, "textures/block/bedrock.png");
        REAL_TEXTURE_PATHS.put(Tile.WATER, "textures/block/water_still.png"); // animated strip - first frame only
        REAL_TEXTURE_PATHS.put(Tile.SAND, "textures/block/sand.png");
        REAL_TEXTURE_PATHS.put(Tile.GRAVEL, "textures/block/gravel.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_ORE, "textures/block/gold_ore.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_ORE, "textures/block/iron_ore.png");
        REAL_TEXTURE_PATHS.put(Tile.COAL_ORE, "textures/block/coal_ore.png");
        REAL_TEXTURE_PATHS.put(Tile.LOG_SIDE, "textures/block/oak_log.png");
        REAL_TEXTURE_PATHS.put(Tile.LOG_TOP, "textures/block/oak_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.LEAVES, "textures/block/oak_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.GLASS, "textures/block/glass.png");
        REAL_TEXTURE_PATHS.put(Tile.SANDSTONE_TOP, "textures/block/sandstone_top.png");
        REAL_TEXTURE_PATHS.put(Tile.SANDSTONE_SIDE, "textures/block/sandstone.png");
        REAL_TEXTURE_PATHS.put(Tile.SANDSTONE_BOTTOM, "textures/block/sandstone_bottom.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_BLOCK, "textures/block/gold_block.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_BLOCK, "textures/block/iron_block.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_ORE, "textures/block/diamond_ore.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_BLOCK, "textures/block/diamond_block.png");
        REAL_TEXTURE_PATHS.put(Tile.BRICKS, "textures/block/bricks.png");
        REAL_TEXTURE_PATHS.put(Tile.TNT_SIDE, "textures/block/tnt_side.png");
        REAL_TEXTURE_PATHS.put(Tile.TNT_TOP, "textures/block/tnt_top.png");
        REAL_TEXTURE_PATHS.put(Tile.TNT_BOTTOM, "textures/block/tnt_bottom.png");
        REAL_TEXTURE_PATHS.put(Tile.BOOKSHELF, "textures/block/bookshelf.png");
        REAL_TEXTURE_PATHS.put(Tile.MOSSY_COBBLESTONE, "textures/block/mossy_cobblestone.png");
        REAL_TEXTURE_PATHS.put(Tile.OBSIDIAN, "textures/block/obsidian.png");
        REAL_TEXTURE_PATHS.put(Tile.CRAFTING_TABLE_TOP, "textures/block/crafting_table_top.png");
        REAL_TEXTURE_PATHS.put(Tile.CRAFTING_TABLE_SIDE, "textures/block/crafting_table_side.png");
        REAL_TEXTURE_PATHS.put(Tile.FURNACE_SIDE, "textures/block/furnace_side.png");
        REAL_TEXTURE_PATHS.put(Tile.FURNACE_TOP, "textures/block/furnace_top.png");
        REAL_TEXTURE_PATHS.put(Tile.ICE, "textures/block/ice.png");
        REAL_TEXTURE_PATHS.put(Tile.SNOW_BLOCK, "textures/block/snow.png"); // modern MC reuses the thin-layer texture
        REAL_TEXTURE_PATHS.put(Tile.CLAY, "textures/block/clay.png");
        REAL_TEXTURE_PATHS.put(Tile.PUMPKIN_SIDE, "textures/block/pumpkin_side.png");
        REAL_TEXTURE_PATHS.put(Tile.PUMPKIN_TOP, "textures/block/pumpkin_top.png");
        REAL_TEXTURE_PATHS.put(Tile.NETHERRACK, "textures/block/netherrack.png");
        REAL_TEXTURE_PATHS.put(Tile.GLOWSTONE, "textures/block/glowstone.png");
        REAL_TEXTURE_PATHS.put(Tile.TERRACOTTA, "textures/block/terracotta.png");
        REAL_TEXTURE_PATHS.put(Tile.RED_SAND, "textures/block/red_sand.png");
        REAL_TEXTURE_PATHS.put(Tile.BIRCH_LOG_SIDE, "textures/block/birch_log.png");
        REAL_TEXTURE_PATHS.put(Tile.BIRCH_LOG_TOP, "textures/block/birch_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.BIRCH_LEAVES, "textures/block/birch_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.SPRUCE_LOG_SIDE, "textures/block/spruce_log.png");
        REAL_TEXTURE_PATHS.put(Tile.SPRUCE_LOG_TOP, "textures/block/spruce_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.SPRUCE_LEAVES, "textures/block/spruce_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.JUNGLE_LOG_SIDE, "textures/block/jungle_log.png");
        REAL_TEXTURE_PATHS.put(Tile.JUNGLE_LOG_TOP, "textures/block/jungle_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.JUNGLE_LEAVES, "textures/block/jungle_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.ACACIA_LOG_SIDE, "textures/block/acacia_log.png");
        REAL_TEXTURE_PATHS.put(Tile.ACACIA_LOG_TOP, "textures/block/acacia_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.ACACIA_LEAVES, "textures/block/acacia_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.MANGROVE_LOG_SIDE, "textures/block/mangrove_log.png");
        REAL_TEXTURE_PATHS.put(Tile.MANGROVE_LOG_TOP, "textures/block/mangrove_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.MANGROVE_LEAVES, "textures/block/mangrove_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.DARK_OAK_LOG_SIDE, "textures/block/dark_oak_log.png");
        REAL_TEXTURE_PATHS.put(Tile.DARK_OAK_LOG_TOP, "textures/block/dark_oak_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.DARK_OAK_LEAVES, "textures/block/dark_oak_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.CHERRY_LOG_SIDE, "textures/block/cherry_log.png");
        REAL_TEXTURE_PATHS.put(Tile.CHERRY_LOG_TOP, "textures/block/cherry_log_top.png");
        REAL_TEXTURE_PATHS.put(Tile.CHERRY_LEAVES, "textures/block/cherry_leaves.png");
        REAL_TEXTURE_PATHS.put(Tile.MUD, "textures/block/mud.png");
        REAL_TEXTURE_PATHS.put(Tile.MANGROVE_ROOTS_SIDE, "textures/block/mangrove_roots_side.png");
        REAL_TEXTURE_PATHS.put(Tile.MANGROVE_ROOTS_TOP, "textures/block/mangrove_roots_top.png");
        REAL_TEXTURE_PATHS.put(Tile.OAK_SAPLING, "textures/block/oak_sapling.png");
        REAL_TEXTURE_PATHS.put(Tile.BLAST_FURNACE_TOP, "textures/block/blast_furnace_top.png");
        REAL_TEXTURE_PATHS.put(Tile.BLAST_FURNACE_SIDE, "textures/block/blast_furnace_side.png");
        REAL_TEXTURE_PATHS.put(Tile.SMOKER_TOP, "textures/block/smoker_top.png");
        REAL_TEXTURE_PATHS.put(Tile.SMOKER_SIDE, "textures/block/smoker_side.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_ORE, "textures/block/copper_ore.png");
        REAL_TEXTURE_PATHS.put(Tile.SMOOTH_STONE, "textures/block/smooth_stone.png");
        // CHEST, SUN, MOON, RAIN: no real block-texture equivalent, always procedural.
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_SWORD, "textures/item/wooden_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_SWORD, "textures/item/stone_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_SWORD, "textures/item/iron_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.LEATHER, "textures/item/leather.png");
        REAL_TEXTURE_PATHS.put(Tile.FEATHER, "textures/item/feather.png");
        REAL_TEXTURE_PATHS.put(Tile.EGG, "textures/item/egg.png");
        REAL_TEXTURE_PATHS.put(Tile.LEATHER_HELMET, "textures/item/leather_helmet.png");
        REAL_TEXTURE_PATHS.put(Tile.LEATHER_CHESTPLATE, "textures/item/leather_chestplate.png");
        REAL_TEXTURE_PATHS.put(Tile.LEATHER_LEGGINGS, "textures/item/leather_leggings.png");
        REAL_TEXTURE_PATHS.put(Tile.LEATHER_BOOTS, "textures/item/leather_boots.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_HELMET, "textures/item/iron_helmet.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_CHESTPLATE, "textures/item/iron_chestplate.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_LEGGINGS, "textures/item/iron_leggings.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_BOOTS, "textures/item/iron_boots.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_HELMET, "textures/item/golden_helmet.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_CHESTPLATE, "textures/item/golden_chestplate.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_LEGGINGS, "textures/item/golden_leggings.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_BOOTS, "textures/item/golden_boots.png");
        REAL_TEXTURE_PATHS.put(Tile.GUNPOWDER, "textures/item/gunpowder.png");
        REAL_TEXTURE_PATHS.put(Tile.BONE, "textures/item/bone.png");
        REAL_TEXTURE_PATHS.put(Tile.ARROW, "textures/item/arrow.png");
        REAL_TEXTURE_PATHS.put(Tile.STRING, "textures/item/string.png");
        REAL_TEXTURE_PATHS.put(Tile.SPIDER_EYE, "textures/item/spider_eye.png");
        REAL_TEXTURE_PATHS.put(Tile.FLINT, "textures/item/flint.png");
        REAL_TEXTURE_PATHS.put(Tile.CLAY_BALL, "textures/item/clay_ball.png");
        REAL_TEXTURE_PATHS.put(Tile.SNOWBALL, "textures/item/snowball.png");
        REAL_TEXTURE_PATHS.put(Tile.GLOWSTONE_DUST, "textures/item/glowstone_dust.png");
        REAL_TEXTURE_PATHS.put(Tile.BOOK, "textures/item/book.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND, "textures/item/diamond.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_SWORD, "textures/item/diamond_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_AXE, "textures/item/diamond_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_PICKAXE, "textures/item/diamond_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_SHOVEL, "textures/item/diamond_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_HELMET, "textures/item/diamond_helmet.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_CHESTPLATE, "textures/item/diamond_chestplate.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_LEGGINGS, "textures/item/diamond_leggings.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_BOOTS, "textures/item/diamond_boots.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_SWORD, "textures/item/golden_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_AXE, "textures/item/golden_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_PICKAXE, "textures/item/golden_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_SHOVEL, "textures/item/golden_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_HOE, "textures/item/wooden_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_HOE, "textures/item/stone_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_HOE, "textures/item/iron_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_HOE, "textures/item/golden_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_HOE, "textures/item/diamond_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_SWORD, "textures/item/copper_sword.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_AXE, "textures/item/copper_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_PICKAXE, "textures/item/copper_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_SHOVEL, "textures/item/copper_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_HOE, "textures/item/copper_hoe.png");
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_SPEAR, "textures/item/wooden_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_SPEAR, "textures/item/stone_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_SPEAR, "textures/item/copper_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_SPEAR, "textures/item/iron_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_SPEAR, "textures/item/golden_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.DIAMOND_SPEAR, "textures/item/diamond_spear.png");
        REAL_TEXTURE_PATHS.put(Tile.TORCH, "textures/block/torch.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLDEN_APPLE, "textures/item/golden_apple.png");
        REAL_TEXTURE_PATHS.put(Tile.WHEAT, "textures/item/wheat.png");
        REAL_TEXTURE_PATHS.put(Tile.WHEAT_SEEDS, "textures/item/wheat_seeds.png");
        REAL_TEXTURE_PATHS.put(Tile.CARROT, "textures/item/carrot.png");
        REAL_TEXTURE_PATHS.put(Tile.BEETROOT, "textures/item/beetroot.png");
        REAL_TEXTURE_PATHS.put(Tile.BEETROOT_SEEDS, "textures/item/beetroot_seeds.png");
        REAL_TEXTURE_PATHS.put(Tile.WATER_FLOW, "textures/block/water_flow.png"); // 32 wide animation strip - first frame, top-left 16x16
        REAL_TEXTURE_PATHS.put(Tile.LAVA, "textures/block/lava_still.png");
        REAL_TEXTURE_PATHS.put(Tile.LAVA_FLOW, "textures/block/lava_flow.png");
        REAL_TEXTURE_PATHS.put(Tile.BUCKET, "textures/item/bucket.png");
        REAL_TEXTURE_PATHS.put(Tile.WATER_BUCKET, "textures/item/water_bucket.png");
        REAL_TEXTURE_PATHS.put(Tile.LAVA_BUCKET, "textures/item/lava_bucket.png");
        REAL_TEXTURE_PATHS.put(Tile.WATER_FLOW_TOP, "textures/block/water_flow.png");
        REAL_TEXTURE_PATHS.put(Tile.LAVA_FLOW_TOP, "textures/block/lava_flow.png");
        REAL_TEXTURE_PATHS.put(Tile.FIRE_1, "textures/block/fire_1.png");
        REAL_TEXTURE_PATHS.put(Tile.BOW, "textures/item/bow.png");
        REAL_TEXTURE_PATHS.put(Tile.BOW_PULLING_0, "textures/item/bow_pulling_0.png");
        REAL_TEXTURE_PATHS.put(Tile.BOW_PULLING_1, "textures/item/bow_pulling_1.png");
        REAL_TEXTURE_PATHS.put(Tile.BOW_PULLING_2, "textures/item/bow_pulling_2.png");
        REAL_TEXTURE_PATHS.put(Tile.FARMLAND, "textures/block/farmland.png");
        REAL_TEXTURE_PATHS.put(Tile.FARMLAND_MOIST, "textures/block/farmland_moist.png");
        // Item icons: textures/item/<name>.png.
        REAL_TEXTURE_PATHS.put(Tile.STICK, "textures/item/stick.png");
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_PICKAXE, "textures/item/wooden_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_PICKAXE, "textures/item/stone_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_PICKAXE, "textures/item/iron_pickaxe.png");
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_AXE, "textures/item/wooden_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_AXE, "textures/item/stone_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_AXE, "textures/item/iron_axe.png");
        REAL_TEXTURE_PATHS.put(Tile.WOODEN_SHOVEL, "textures/item/wooden_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.STONE_SHOVEL, "textures/item/stone_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_SHOVEL, "textures/item/iron_shovel.png");
        REAL_TEXTURE_PATHS.put(Tile.APPLE, "textures/item/apple.png");
        REAL_TEXTURE_PATHS.put(Tile.BREAD, "textures/item/bread.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_BEEF, "textures/item/cooked_beef.png");
        REAL_TEXTURE_PATHS.put(Tile.COAL, "textures/item/coal.png");
        REAL_TEXTURE_PATHS.put(Tile.CHARCOAL, "textures/item/charcoal.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_INGOT, "textures/item/iron_ingot.png");
        REAL_TEXTURE_PATHS.put(Tile.GOLD_INGOT, "textures/item/gold_ingot.png");
        REAL_TEXTURE_PATHS.put(Tile.COPPER_INGOT, "textures/item/copper_ingot.png");
        REAL_TEXTURE_PATHS.put(Tile.IRON_NUGGET, "textures/item/iron_nugget.png");
        REAL_TEXTURE_PATHS.put(Tile.BRICK, "textures/item/brick.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_IRON, "textures/item/raw_iron.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_GOLD, "textures/item/raw_gold.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_COPPER, "textures/item/raw_copper.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_BEEF, "textures/item/beef.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_PORKCHOP, "textures/item/porkchop.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_PORKCHOP, "textures/item/cooked_porkchop.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_CHICKEN, "textures/item/chicken.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_CHICKEN, "textures/item/cooked_chicken.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_MUTTON, "textures/item/mutton.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_MUTTON, "textures/item/cooked_mutton.png");
        REAL_TEXTURE_PATHS.put(Tile.RAW_RABBIT, "textures/item/rabbit.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_RABBIT, "textures/item/cooked_rabbit.png");
        REAL_TEXTURE_PATHS.put(Tile.COD, "textures/item/cod.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_COD, "textures/item/cooked_cod.png");
        REAL_TEXTURE_PATHS.put(Tile.SALMON, "textures/item/salmon.png");
        REAL_TEXTURE_PATHS.put(Tile.COOKED_SALMON, "textures/item/cooked_salmon.png");
        REAL_TEXTURE_PATHS.put(Tile.POTATO, "textures/item/potato.png");
        REAL_TEXTURE_PATHS.put(Tile.BAKED_POTATO, "textures/item/baked_potato.png");
        REAL_TEXTURE_PATHS.put(Tile.KELP, "textures/item/kelp.png");
        REAL_TEXTURE_PATHS.put(Tile.DRIED_KELP, "textures/item/dried_kelp.png");
        REAL_TEXTURE_PATHS.put(Tile.ROTTEN_FLESH, "textures/item/rotten_flesh.png");
    }

    @Getter
    private final int textureId;

    public TextureAtlas() {
        BufferedImage atlas = new BufferedImage(COLS * CELL_PX, ROWS * CELL_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D atlasG = atlas.createGraphics();

        // The grass side is split into a dirt base and a tintable grayscale grass overlay (see
        // GrassSideSplit) so the fringe follows biome color like the top face does. Those two
        // tiles are written pixel-by-pixel (setRGB) rather than drawImage'd, because their
        // transparent pixels carry a deliberate RGB that drawImage would discard.
        int[][] grassSplit = GrassSideSplit.split(toArgb(loadTile(Tile.GRASS_SIDE)), TILE_PX,
                GrassSideSplit.meanLuma(toArgb(loadTile(Tile.GRASS_TOP))));
        for (Tile tile : Tile.values()) {
            int[] origin = cellOrigin(tile);
            if (tile == Tile.GRASS_SIDE || tile == Tile.GRASS_SIDE_OVERLAY) {
                int[] px = tile == Tile.GRASS_SIDE ? grassSplit[0] : grassSplit[1];
                for (int y = 0; y < TILE_PX; y++)
                    for (int x = 0; x < TILE_PX; x++) atlas.setRGB(origin[0] + x, origin[1] + y, px[y * TILE_PX + x]);
                continue;
            }
            if (CUTOUT_FOLIAGE.contains(tile)) {
                // Graphics2D.drawImage alpha-composites (SRC_OVER): a fully-transparent source
                // pixel would leave the atlas canvas's own blank (black, alpha 0) pixel in place,
                // discarding whatever dilated RGB we computed - so this writes pixels directly.
                int[] px = LeafEdgeDilate.dilate(toArgb(loadTile(tile)), TILE_PX);
                for (int y = 0; y < TILE_PX; y++)
                    for (int x = 0; x < TILE_PX; x++) atlas.setRGB(origin[0] + x, origin[1] + y, px[y * TILE_PX + x]);
                continue;
            }
            atlasG.drawImage(loadTile(tile), origin[0], origin[1], null);
        }
        atlasG.dispose();

        for (Tile tile : Tile.values()) {
            extrudeBorder(atlas, tile);
        }

        textureId = upload(atlas);
        loadAnimations();
    }

    private void loadAnimations() {
        for (Map.Entry<Tile, int[]> e : ANIMATED.entrySet()) {
            String path = REAL_TEXTURE_PATHS.get(e.getKey());
            if (path == null) continue;
            try {
                BufferedImage strip = TextureIo.read(new File(path));
                File meta = new File(path + ".mcmeta");
                if (strip == null || !meta.isFile()) continue;
                TextureAnimation anim = TextureAnimation.parse(strip, java.nio.file.Files.readString(meta.toPath()));
                if (anim != null && anim.frameCount() > 1) animations.put(e.getKey(), anim);
            } catch (IOException | RuntimeException ex) {
                log.warn("Couldn't load the animation of {} ({}) - it stays on its first frame", e.getKey(), ex.getMessage());
            }
        }
    }

    /** How many tiles are animated (for tests and the debug overlay). */
    public int animatedTileCount() {
        return animations.size();
    }

    /**
     * One game tick of every animated tile (water, lava, the burning overlay): the frame moves on when its time is up and the changed tiles
     * are written into the atlas again, border and mip levels included, with glTexSubImage2D - nothing is re-meshed.
     */
    public void tickAnimations() {
        if (animations.isEmpty()) return;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        for (Map.Entry<Tile, TextureAnimation> e : animations.entrySet()) {
            TextureAnimation anim = e.getValue();
            anim.tick();
            if (!anim.takeChanged()) continue;
            int[] r = ANIMATED.get(e.getKey());
            uploadCell(e.getKey(), anim.region(r[0], r[1], TILE_PX));
        }
    }

    /** Writes a tile's 16 x 16 picture (and its wrapped border, and every mip level) into its atlas cell. */
    private void uploadCell(Tile tile, int[] pixels) {
        int[] cell = new int[CELL_PX * CELL_PX];
        for (int y = 0; y < CELL_PX; y++) {
            for (int x = 0; x < CELL_PX; x++) {
                cell[y * CELL_PX + x] = pixels[Math.floorMod(y - BORDER_PX, TILE_PX) * TILE_PX + Math.floorMod(x - BORDER_PX, TILE_PX)];
            }
        }
        int col = tile.ordinal() % COLS, row = tile.ordinal() / COLS;
        int[][] chain = MipmapChain.generate(cell, CELL_PX, MAX_MIP_LEVEL, MipmapChain.strategyFor(cell, false, false));
        for (int l = 0; l <= MAX_MIP_LEVEL; l++) {
            int size = CELL_PX >> l;
            ByteBuffer b = memAlloc(size * size * 4);
            for (int argb : chain[l]) {
                b.put((byte) ((argb >> 16) & 0xFF));
                b.put((byte) ((argb >> 8) & 0xFF));
                b.put((byte) (argb & 0xFF));
                b.put((byte) ((argb >> 24) & 0xFF));
            }
            b.flip();
            glTexSubImage2D(GL_TEXTURE_2D, l, col * size, row * size, size, size, GL_RGBA, GL_UNSIGNED_BYTE, b);
            memFree(b);
        }
    }

    private static int[] toArgb(BufferedImage img) {
        return img.getRGB(0, 0, TILE_PX, TILE_PX, null, 0, TILE_PX);
    }

    private BufferedImage loadTile(Tile tile) {
        if (tile == Tile.CHEST_TOP || tile == Tile.CHEST_SIDE) {
            try {
                return chestFace(tile);
            } catch (IOException | RuntimeException e) {
                log.warn("Couldn't build the chest tile {} from {} ({})", tile, CHEST_SKIN, e.getMessage());
                return missingTexture();
            }
        }
        String path = REAL_TEXTURE_PATHS.get(tile);
        if (path != null) {
            File file = new File(path);
            try {
                BufferedImage real = TextureIo.read(file);
                if (real == null) {
                    log.warn("Texture file {} isn't a decodable image - using the missing-texture placeholder for {}", path, tile);
                } else if (ANIMATED.containsKey(tile)) {
                    // an animation strip: the first frame's part of it (the animation then replaces it, see tickAnimations)
                    int[] r = ANIMATED.get(tile);
                    int w = Math.min(TILE_PX, real.getWidth() - r[0]);
                    int h = Math.min(TILE_PX, real.getHeight() - r[1]);
                    return real.getSubimage(r[0], r[1], w, h);
                } else if (real.getWidth() == TILE_PX && real.getHeight() == TILE_PX) {
                    return DYEABLE.contains(tile) ? dyeLeather(real, path) : real;
                } else {
                    log.warn("Texture {} is {}x{}, expected {}x{} - using the missing-texture placeholder for {}",
                            path, real.getWidth(), real.getHeight(), TILE_PX, TILE_PX, tile);
                }
            } catch (IOException e) {
                log.warn("Couldn't load texture {} for {} ({}) - using the missing-texture placeholder", path, tile, e.getMessage());
            }
        }
        return missingTexture();
    }

    private final Map<Tile, boolean[]> alphaMasks = new EnumMap<>(Tile.class);

    /** 16x16 row-major (top row first): true where the tile has a visible pixel. Used to extrude flat held items. */
    public boolean[] alphaMask(Tile tile) {
        return alphaMasks.computeIfAbsent(tile, t -> {
            BufferedImage img = loadTile(t);
            boolean[] m = new boolean[TILE_PX * TILE_PX];
            for (int y = 0; y < TILE_PX; y++) for (int x = 0; x < TILE_PX; x++) m[y * TILE_PX + x] = (img.getRGB(x, y) >>> 24) > 40;
            return m;
        });
    }

    private int[] cellOrigin(Tile tile) {
        int col = tile.ordinal() % COLS;
        int row = tile.ordinal() / COLS;
        return new int[]{col * CELL_PX + BORDER_PX, row * CELL_PX + BORDER_PX};
    }

    /** Copies each tile's edge pixels outward into its border ring (clamped, including corners). */
    private void extrudeBorder(BufferedImage atlas, Tile tile) {
        int[] origin = cellOrigin(tile);
        int ox = origin[0], oy = origin[1];
        for (int dy = -BORDER_PX; dy < TILE_PX + BORDER_PX; dy++) {
            for (int dx = -BORDER_PX; dx < TILE_PX + BORDER_PX; dx++) {
                boolean border = dx < 0 || dy < 0 || dx >= TILE_PX || dy >= TILE_PX;
                if (!border) continue;
                int sx = Math.floorMod(dx, TILE_PX);
                int sy = Math.floorMod(dy, TILE_PX);
                atlas.setRGB(ox + dx, oy + dy, atlas.getRGB(ox + sx, oy + sy));
            }
        }
    }

    public float[] getUv(Tile tile) {
        int[] origin = cellOrigin(tile);
        float atlasW = COLS * CELL_PX;
        float atlasH = ROWS * CELL_PX;
        float u0 = origin[0] / atlasW;
        float u1 = (origin[0] + TILE_PX) / atlasW;
        float v0 = origin[1] / atlasH;
        float v1 = (origin[1] + TILE_PX) / atlasH;
        return new float[]{u0, v0, u1, v1};
    }

    public int getTextureId() {
        return textureId;
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }


    private int upload(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        ByteBuffer buffer = memAlloc(w * h * 4);
        // No vertical flip: buffer row i must equal image row i so it matches getUv()'s
        // row-index-based V calculation. (A flip here without also flipping getUv()
        // makes every tile sample from the wrong row-band of the atlas.)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xFF));
                buffer.put((byte) ((argb >> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buffer.flip();

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        // Mipmapped minification (with border-extruded tiles above) kills the moire/checkerboard
        // aliasing that noisy 16x16 textures show at a distance under plain GL_NEAREST.
        // GL_NEAREST magnification keeps the blocky pixel-art look up close.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, MAX_MIP_LEVEL);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        memFree(buffer);
        uploadMipLevels(image);
        return id;
    }

    /** The leaf tiles: their mip levels are DARK_CUTOUT (every leaves.png.mcmeta in the client says {@code "mipmap_strategy": "dark_cutout"}). */
    private static final java.util.Set<Tile> LEAF_TILES = java.util.EnumSet.of(
            Tile.LEAVES, Tile.BIRCH_LEAVES, Tile.SPRUCE_LEAVES, Tile.JUNGLE_LEAVES, Tile.ACACIA_LEAVES,
            Tile.MANGROVE_LEAVES, Tile.DARK_OAK_LEAVES, Tile.CHERRY_LEAVES);

    /**
     * Levels 0-4 of the atlas, built cell by cell with {@link MipmapChain} (each cell is its tile plus its wrapped border, so a cell's
     * chain never reads its neighbours): vanilla builds a sprite's mips with the sprite's own strategy, not one filter over the whole atlas.
     */
    private void uploadMipLevels(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[][] levels = new int[MAX_MIP_LEVEL + 1][];
        for (int l = 0; l <= MAX_MIP_LEVEL; l++) levels[l] = new int[(w >> l) * (h >> l)];
        int[] cell = new int[CELL_PX * CELL_PX];
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                Tile tile = index < Tile.values().length ? Tile.values()[index] : null;
                for (int y = 0; y < CELL_PX; y++) for (int x = 0; x < CELL_PX; x++) cell[y * CELL_PX + x] = image.getRGB(col * CELL_PX + x, row * CELL_PX + y);
                MipmapChain.Strategy strategy = MipmapChain.strategyFor(cell, tile != null && LEAF_TILES.contains(tile), tile == Tile.GLASS);
                int[][] chain = MipmapChain.generate(cell, CELL_PX, MAX_MIP_LEVEL, strategy);
                for (int l = 0; l <= MAX_MIP_LEVEL; l++) {
                    int size = CELL_PX >> l, stride = w >> l;
                    for (int y = 0; y < size; y++) {
                        System.arraycopy(chain[l], y * size, levels[l], (row * size + y) * stride + col * size, size);
                    }
                }
            }
        }
        for (int l = 1; l <= MAX_MIP_LEVEL; l++) {
            int lw = w >> l, lh = h >> l;
            ByteBuffer b = memAlloc(lw * lh * 4);
            for (int argb : levels[l]) {
                b.put((byte) ((argb >> 16) & 0xFF));
                b.put((byte) ((argb >> 8) & 0xFF));
                b.put((byte) (argb & 0xFF));
                b.put((byte) ((argb >> 24) & 0xFF));
            }
            b.flip();
            glTexImage2D(GL_TEXTURE_2D, l, GL_RGBA8, lw, lh, 0, GL_RGBA, GL_UNSIGNED_BYTE, b);
            memFree(b);
        }
    }

    // ---------- placeholders and composed tiles ----------

    /** Vanilla's own "missing texture" look (magenta and black 8px squares) for a tile whose file is absent. */
    private static BufferedImage missingTexture() {
        BufferedImage img = new BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++)
                img.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFFF800F8 : 0xFF000000);
        return img;
    }

    /** Leather armor: the item texture is grey and vanilla tints it with the (default) dye colour, then draws the overlay sprite. */
    private static final java.util.Set<Tile> DYEABLE = java.util.EnumSet.of(
            Tile.LEATHER_HELMET, Tile.LEATHER_CHESTPLATE, Tile.LEATHER_LEGGINGS, Tile.LEATHER_BOOTS);
    /** Vanilla's undyed leather colour, 0xA06540. */
    static final int LEATHER_DYE = 0xA06540;

    /**
     * Multiplies the grey base by the dye colour and lays the matching {@code <name>_overlay.png} (untinted rivets and
     * edges) over it, as the item model's two layers do.
     */
    static BufferedImage dyeLeather(BufferedImage base, String path) {
        BufferedImage out = new BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_INT_ARGB);
        BufferedImage overlay = null;
        try {
            File f = new File(path.replace(".png", "_overlay.png"));
            if (f.isFile()) overlay = TextureIo.read(f);
        } catch (IOException e) {
            log.warn("Couldn't read the leather overlay for {}: {}", path, e.getMessage());
        }
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int argb = base.getRGB(x, y);
                int a = argb >>> 24;
                int r = ((argb >> 16) & 0xFF) * ((LEATHER_DYE >> 16) & 0xFF) / 255;
                int g = ((argb >> 8) & 0xFF) * ((LEATHER_DYE >> 8) & 0xFF) / 255;
                int b = (argb & 0xFF) * (LEATHER_DYE & 0xFF) / 255;
                if (overlay != null && overlay.getWidth() == TILE_PX && overlay.getHeight() == TILE_PX) {
                    int o = overlay.getRGB(x, y);
                    int oa = o >>> 24;
                    if (oa > 0) { // source-over: the overlay on top of the dyed base
                        float k = oa / 255f, ba = a / 255f, outA = k + (1 - k) * ba;
                        r = Math.round((((o >> 16) & 0xFF) * k + r * (1 - k) * ba) / outA);
                        g = Math.round((((o >> 8) & 0xFF) * k + g * (1 - k) * ba) / outA);
                        b = Math.round(((o & 0xFF) * k + b * (1 - k) * ba) / outA);
                        a = Math.round(255f * outA);
                    }
                }
                out.setRGB(x, y, (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b));
            }
        }
        return out;
    }

    private static final String CHEST_SKIN = "textures/entity/chest/normal.png";

    /**
     * The chest block is an entity model in Minecraft, so its faces are cut out of the real entity skin: the lid top, or a
     * side made of the lid's side over the body's side (the lid overlaps the body by one row). Both are 14x14 in the skin
     * and are stretched to the tile size.
     */
    private static BufferedImage chestFace(Tile tile) throws IOException {
        BufferedImage skin = TextureIo.read(new File(CHEST_SKIN));
        if (skin == null) throw new IOException("not an image");
        BufferedImage face = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
        if (tile == Tile.CHEST_TOP) {
            for (int y = 0; y < 14; y++) for (int x = 0; x < 14; x++) face.setRGB(x, y, skin.getRGB(14 + x, y));
        } else {
            for (int y = 0; y < 10; y++) for (int x = 0; x < 14; x++) face.setRGB(x, 4 + y, skin.getRGB(x, 33 + y));
            for (int y = 0; y < 5; y++) for (int x = 0; x < 14; x++) face.setRGB(x, y, skin.getRGB(x, 14 + y));
        }
        BufferedImage out = new BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) out.setRGB(x, y, face.getRGB(x * 14 / TILE_PX, y * 14 / TILE_PX));
        return out;
    }
}
