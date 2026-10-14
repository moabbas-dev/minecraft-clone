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
 * Block texture atlas. Tiles that have a real texture copied into {@code textures/block/}
 * (the user's own local Minecraft assets - see {@link #REAL_TEXTURE_PATHS}) load that PNG;
 * everything else (celestial bodies, rain, and chest - modern Minecraft renders chests as an
 * entity model, not a simple block texture) falls back to the original procedural drawing, as
 * does any tile whose file is missing, unreadable, or an unexpected size - a missing texture
 * file should never crash the game.
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
            Tile.GUNPOWDER, Tile.BONE, Tile.ARROW, Tile.STRING, Tile.SPIDER_EYE);

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
        // Real item icons (textures/item/<name>.png, gitignored like the block textures);
        // any that is missing falls back to the procedural icon drawn in drawTile().
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
        int[][] grassSplit = GrassSideSplit.split(toArgb(loadOrDrawTile(Tile.GRASS_SIDE)), TILE_PX,
                GrassSideSplit.meanLuma(toArgb(loadOrDrawTile(Tile.GRASS_TOP))));
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
                int[] px = LeafEdgeDilate.dilate(toArgb(loadOrDrawTile(tile)), TILE_PX);
                for (int y = 0; y < TILE_PX; y++)
                    for (int x = 0; x < TILE_PX; x++) atlas.setRGB(origin[0] + x, origin[1] + y, px[y * TILE_PX + x]);
                continue;
            }
            atlasG.drawImage(loadOrDrawTile(tile), origin[0], origin[1], null);
        }
        atlasG.dispose();

        for (Tile tile : Tile.values()) {
            extrudeBorder(atlas, tile);
        }

        textureId = upload(atlas);
    }

    private static int[] toArgb(BufferedImage img) {
        return img.getRGB(0, 0, TILE_PX, TILE_PX, null, 0, TILE_PX);
    }

    private BufferedImage loadOrDrawTile(Tile tile) {
        String path = REAL_TEXTURE_PATHS.get(tile);
        if (path != null) {
            File file = new File(path);
            try {
                BufferedImage real = ImageIO.read(file);
                if (real == null) {
                    log.warn("Texture file {} isn't a decodable image - using procedural fallback for {}", path, tile);
                } else if (tile == Tile.WATER) {
                    // water_still.png is a vertical strip of animation frames; this engine has
                    // no texture animation, so just take the first 16x16 frame as a static look.
                    int w = Math.min(TILE_PX, real.getWidth());
                    int h = Math.min(TILE_PX, real.getHeight());
                    return real.getSubimage(0, 0, w, h);
                } else if (real.getWidth() == TILE_PX && real.getHeight() == TILE_PX) {
                    return real;
                } else {
                    log.warn("Texture {} is {}x{}, expected {}x{} - using procedural fallback for {}",
                            path, real.getWidth(), real.getHeight(), TILE_PX, TILE_PX, tile);
                }
            } catch (IOException e) {
                log.warn("Couldn't load texture {} for {} ({}) - using procedural fallback", path, tile, e.getMessage());
            }
        }
        return drawTile(tile);
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
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buffer);
        return id;
    }

    // ---------- procedural drawing ----------

    private BufferedImage drawTile(Tile tile) {
        BufferedImage icon = ItemIcons.draw(tile); // original pixel-art item icons
        if (icon != null) return icon;
        BufferedImage img = new BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(tile.ordinal() * 7919L + 13);
        Graphics2D g = img.createGraphics();
        switch (tile) {
            case STONE -> noisyFill(img, rnd, new Color(128, 128, 128), 18);
            case DIRT -> noisyFill(img, rnd, new Color(134, 96, 67), 20);
            case GRASS_TOP -> noisyFill(img, rnd, new Color(90, 158, 64), 22);
            case GRASS_SIDE -> grassSide(img, rnd);
            case COBBLESTONE -> cobble(img, rnd, new Color(122, 122, 122));
            case PLANKS -> planks(img, rnd, new Color(176, 143, 92));
            case BEDROCK -> cobble(img, rnd, new Color(70, 70, 70));
            case WATER -> waterLike(img, rnd, new Color(50, 90, 200));
            case SAND -> noisyFill(img, rnd, new Color(219, 208, 154), 12);
            case GRAVEL -> speckledNoise(img, rnd, new Color(120, 118, 116), new Color[]{new Color(90, 88, 86), new Color(150, 148, 146)}, 40);
            case GOLD_ORE -> oreTile(img, rnd, new Color(250, 220, 60));
            case IRON_ORE -> oreTile(img, rnd, new Color(216, 175, 148));
            case COAL_ORE -> oreTile(img, rnd, new Color(30, 30, 30));
            case DIAMOND_ORE -> oreTile(img, rnd, new Color(110, 235, 220));
            case LOG_SIDE -> logSide(img, rnd);
            case LOG_TOP -> logTop(img, rnd);
            case LEAVES -> speckledNoise(img, rnd, new Color(46, 110, 34), new Color[]{new Color(34, 90, 24), new Color(70, 140, 50)}, 60);
            case GLASS -> glass(img, rnd);
            case SANDSTONE_TOP, SANDSTONE_BOTTOM -> noisyFill(img, rnd, new Color(224, 214, 168), 8);
            case SANDSTONE_SIDE -> bandedHorizontal(img, rnd, new Color(224, 214, 168), new Color(200, 190, 148));
            case GOLD_BLOCK -> noisyFill(img, rnd, new Color(247, 220, 79), 10);
            case IRON_BLOCK -> noisyFill(img, rnd, new Color(216, 216, 216), 10);
            case DIAMOND_BLOCK -> noisyFill(img, rnd, new Color(120, 230, 219), 10);
            case BRICKS -> bricks(img, rnd);
            case TNT_SIDE -> tntSide(img, rnd);
            case TNT_TOP, TNT_BOTTOM -> noisyFill(img, rnd, new Color(180, 60, 40), 10);
            case BOOKSHELF -> bookshelf(img, rnd);
            case MOSSY_COBBLESTONE -> mossyCobble(img, rnd);
            case OBSIDIAN -> speckledNoise(img, rnd, new Color(20, 14, 30), new Color[]{new Color(45, 25, 70), new Color(10, 8, 15)}, 25);
            case CHEST -> chest(img, rnd);
            case CRAFTING_TABLE_TOP -> craftingTop(img, rnd);
            case CRAFTING_TABLE_SIDE -> planks(img, rnd, new Color(150, 110, 66));
            case FURNACE_SIDE -> furnaceSide(img, rnd);
            case FURNACE_TOP -> noisyFill(img, rnd, new Color(110, 110, 110), 14);
            case ICE -> ice(img, rnd);
            case SNOW_BLOCK -> noisyFill(img, rnd, new Color(240, 245, 250), 10);
            case CLAY -> noisyFill(img, rnd, new Color(160, 168, 180), 10);
            case PUMPKIN_SIDE -> pumpkinSide(img, rnd);
            case PUMPKIN_TOP -> pumpkinTop(img, rnd);
            case NETHERRACK -> speckledNoise(img, rnd, new Color(105, 47, 47), new Color[]{new Color(80, 32, 32), new Color(130, 60, 60)}, 55);
            case GLOWSTONE -> speckledNoise(img, rnd, new Color(180, 150, 100), new Color[]{new Color(255, 230, 150), new Color(140, 110, 70)}, 45);
            case SUN -> disc(img, new Color(255, 252, 230), new Color(255, 210, 110));
            case MOON -> disc(img, new Color(240, 240, 248), new Color(195, 195, 210));
            case TERRACOTTA -> bandedHorizontal(img, rnd, new Color(180, 95, 65), new Color(160, 80, 55));
            case RED_SAND -> noisyFill(img, rnd, new Color(191, 108, 57), 14);
            case RAIN -> rainStreak(img, rnd);
            case NIGHT_MOTE -> nightMote(img);
            // New species fallbacks (only reached if the copied PNG is missing/unreadable -
            // verified present, see REAL_TEXTURE_PATHS): simple tinted variants of the existing
            // oak log/leaf procedural style rather than dedicated bark patterns per species.
            case BIRCH_LOG_SIDE, BIRCH_LOG_TOP -> noisyFill(img, rnd, new Color(224, 216, 195), 14);
            case BIRCH_LEAVES -> speckledNoise(img, rnd, new Color(128, 167, 85), new Color[]{new Color(110, 150, 70), new Color(150, 185, 105)}, 60);
            case SPRUCE_LOG_SIDE, SPRUCE_LOG_TOP -> noisyFill(img, rnd, new Color(84, 60, 40), 10);
            case SPRUCE_LEAVES -> speckledNoise(img, rnd, new Color(56, 84, 56), new Color[]{new Color(42, 68, 42), new Color(70, 100, 68)}, 60);
            case JUNGLE_LOG_SIDE, JUNGLE_LOG_TOP -> noisyFill(img, rnd, new Color(90, 60, 40), 12);
            case JUNGLE_LEAVES -> speckledNoise(img, rnd, new Color(40, 120, 30), new Color[]{new Color(30, 100, 22), new Color(60, 145, 45)}, 60);
            case ACACIA_LOG_SIDE, ACACIA_LOG_TOP -> noisyFill(img, rnd, new Color(120, 105, 95), 12);
            case ACACIA_LEAVES -> speckledNoise(img, rnd, new Color(150, 130, 60), new Color[]{new Color(130, 112, 48), new Color(170, 150, 75)}, 60);
            case MANGROVE_LOG_SIDE, MANGROVE_LOG_TOP -> noisyFill(img, rnd, new Color(100, 55, 45), 12);
            case MANGROVE_LEAVES -> speckledNoise(img, rnd, new Color(45, 110, 55), new Color[]{new Color(35, 92, 45), new Color(60, 130, 68)}, 60);
            case DARK_OAK_LOG_SIDE, DARK_OAK_LOG_TOP -> noisyFill(img, rnd, new Color(50, 35, 22), 10);
            case DARK_OAK_LEAVES -> speckledNoise(img, rnd, new Color(35, 60, 25), new Color[]{new Color(28, 50, 20), new Color(42, 70, 30)}, 55);
            case CHERRY_LOG_SIDE, CHERRY_LOG_TOP -> noisyFill(img, rnd, new Color(92, 60, 60), 12);
            case CHERRY_LEAVES -> speckledNoise(img, rnd, new Color(247, 190, 213), new Color[]{new Color(240, 160, 195), new Color(252, 210, 225)}, 50);
            case MUD -> noisyFill(img, rnd, new Color(60, 48, 44), 10);
            case MANGROVE_ROOTS_SIDE, MANGROVE_ROOTS_TOP -> noisyFill(img, rnd, new Color(78, 52, 40), 14);
            case SKIN -> noisyFill(img, rnd, new Color(208, 158, 124), 6);
            case OAK_SAPLING -> speckledNoise(img, rnd, new Color(60, 120, 40), new Color[]{new Color(50, 100, 32), new Color(75, 140, 50)}, 65);
            // Tools/stick: no real texture exists for these (no item-texture dump has been
            // copied into textures/, unlike every block above), so they're always procedural -
            // a diagonal wood handle (real vanilla's handles are wood at every tier) plus a head
            // shape distinguishing pickaxe/axe/shovel, tinted by tier (wood/stone/iron).
            case STICK -> stick(img, rnd);
            case WOODEN_PICKAXE -> pickaxeIcon(img, rnd, TOOL_HEAD_WOOD);
            case STONE_PICKAXE -> pickaxeIcon(img, rnd, TOOL_HEAD_STONE);
            case IRON_PICKAXE -> pickaxeIcon(img, rnd, TOOL_HEAD_IRON);
            case WOODEN_AXE -> axeIcon(img, rnd, TOOL_HEAD_WOOD);
            case STONE_AXE -> axeIcon(img, rnd, TOOL_HEAD_STONE);
            case IRON_AXE -> axeIcon(img, rnd, TOOL_HEAD_IRON);
            case WOODEN_SHOVEL -> shovelIcon(img, rnd, TOOL_HEAD_WOOD);
            case STONE_SHOVEL -> shovelIcon(img, rnd, TOOL_HEAD_STONE);
            case IRON_SHOVEL -> shovelIcon(img, rnd, TOOL_HEAD_IRON);
            // Food: also no real texture dump exists for these (item textures, not block
            // textures) - simple flat-icon shapes, same procedural-fallback convention as tools.
            case APPLE -> appleIcon(img, rnd);
            case BREAD -> breadIcon(img, rnd);
            case COOKED_BEEF -> cookedBeefIcon(img, rnd);

            // ---- Cooking/smelting milestone ----
            case BLAST_FURNACE_TOP -> speckledNoise(img, rnd, new Color(70, 78, 85), new Color[]{new Color(90, 40, 20), new Color(50, 55, 60)}, 20);
            case BLAST_FURNACE_SIDE -> noisyFill(img, rnd, new Color(95, 100, 105), 12);
            case SMOKER_TOP -> speckledNoise(img, rnd, new Color(70, 55, 45), new Color[]{new Color(30, 25, 22), new Color(90, 70, 55)}, 30);
            case SMOKER_SIDE -> noisyFill(img, rnd, new Color(100, 78, 60), 12);
            case COPPER_ORE -> oreTile(img, rnd, new Color(196, 120, 80));
            case SMOOTH_STONE -> noisyFill(img, rnd, new Color(168, 168, 168), 6);

            case COAL -> blockyIcon(img, rnd, new Color(25, 25, 25));
            case CHARCOAL -> blockyIcon(img, rnd, new Color(45, 40, 38));
            case BRICK -> blockyIcon(img, rnd, new Color(150, 80, 65));
            case IRON_INGOT -> ingotIcon(img, rnd, new Color(216, 216, 216), new Color(240, 240, 240));
            case GOLD_INGOT -> ingotIcon(img, rnd, new Color(247, 220, 79), new Color(255, 240, 150));
            case COPPER_INGOT -> ingotIcon(img, rnd, new Color(196, 120, 80), new Color(220, 150, 110));
            case IRON_NUGGET -> nuggetIcon(img, rnd, new Color(226, 226, 226));
            case RAW_IRON -> rawOreIcon(img, rnd, new Color(176, 128, 104));
            case RAW_GOLD -> rawOreIcon(img, rnd, new Color(222, 190, 80));
            case RAW_COPPER -> rawOreIcon(img, rnd, new Color(180, 105, 70));

            case RAW_BEEF -> meatIcon(img, rnd, new Color(196, 90, 90), null);
            case RAW_PORKCHOP -> meatIcon(img, rnd, new Color(216, 130, 130), null);
            case COOKED_PORKCHOP -> meatIcon(img, rnd, new Color(160, 100, 70), new Color(100, 55, 35));
            case RAW_CHICKEN -> meatIcon(img, rnd, new Color(232, 200, 180), null);
            case COOKED_CHICKEN -> meatIcon(img, rnd, new Color(190, 148, 90), new Color(130, 90, 50));
            case RAW_MUTTON -> meatIcon(img, rnd, new Color(180, 100, 110), null);
            case COOKED_MUTTON -> meatIcon(img, rnd, new Color(145, 92, 68), new Color(95, 55, 35));
            case RAW_RABBIT -> meatIcon(img, rnd, new Color(190, 120, 120), null);
            case COOKED_RABBIT -> meatIcon(img, rnd, new Color(155, 98, 72), new Color(100, 58, 36));
            case COD -> fishIcon(img, rnd, new Color(190, 175, 140), null);
            case COOKED_COD -> fishIcon(img, rnd, new Color(215, 195, 150), new Color(180, 150, 100));
            case SALMON -> fishIcon(img, rnd, new Color(210, 120, 100), null);
            case COOKED_SALMON -> fishIcon(img, rnd, new Color(195, 110, 70), new Color(140, 75, 45));
            case POTATO -> potatoIcon(img, rnd, new Color(200, 170, 110));
            case BAKED_POTATO -> potatoIcon(img, rnd, new Color(150, 105, 60));
            case KELP -> kelpIcon(img, rnd, new Color(50, 110, 50));
            case DRIED_KELP -> kelpIcon(img, rnd, new Color(70, 90, 45));

            // ---- Mob-system milestone ----
            case ROTTEN_FLESH -> meatIcon(img, rnd, new Color(120, 110, 70), new Color(80, 95, 55));
            case ZOMBIE_SKIN -> noisyFill(img, rnd, new Color(94, 135, 76), 12);
            case WOODEN_SWORD -> swordIcon(img, rnd, TOOL_HEAD_WOOD);
            case STONE_SWORD -> swordIcon(img, rnd, TOOL_HEAD_STONE);
            case IRON_SWORD -> swordIcon(img, rnd, TOOL_HEAD_IRON);
            case LEATHER -> blockyIcon(img, rnd, new Color(150, 90, 50));
            case FEATHER -> blockyIcon(img, rnd, new Color(235, 235, 235));
            case EGG -> potatoIcon(img, rnd, new Color(235, 225, 200));
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS -> blockyIcon(img, rnd, new Color(150, 90, 50));
            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS -> blockyIcon(img, rnd, new Color(210, 210, 210));
            case GOLD_HELMET, GOLD_CHESTPLATE, GOLD_LEGGINGS, GOLD_BOOTS -> blockyIcon(img, rnd, new Color(240, 210, 70));
            case DIAMOND -> blockyIcon(img, rnd, new Color(110, 235, 220));
            case DIAMOND_SWORD -> swordIcon(img, rnd, new Color(110, 235, 220));
            case DIAMOND_AXE -> axeIcon(img, rnd, new Color(110, 235, 220));
            case DIAMOND_PICKAXE -> pickaxeIcon(img, rnd, new Color(110, 235, 220));
            case DIAMOND_SHOVEL -> shovelIcon(img, rnd, new Color(110, 235, 220));
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> blockyIcon(img, rnd, new Color(110, 235, 220));
            case FLINT -> blockyIcon(img, rnd, new Color(70, 70, 75));
            case CLAY_BALL -> potatoIcon(img, rnd, new Color(160, 166, 180));
            case SNOWBALL -> potatoIcon(img, rnd, new Color(245, 250, 250));
            case GLOWSTONE_DUST -> blockyIcon(img, rnd, new Color(255, 210, 90));
            case BOOK -> blockyIcon(img, rnd, new Color(140, 85, 45));
            case GUNPOWDER -> blockyIcon(img, rnd, new Color(110, 110, 110));
            case BONE -> blockyIcon(img, rnd, new Color(235, 232, 215));
            case ARROW -> swordIcon(img, rnd, new Color(200, 200, 200));
            case STRING -> blockyIcon(img, rnd, new Color(235, 235, 235));
            case SPIDER_EYE -> blockyIcon(img, rnd, new Color(120, 30, 40));
            case PIG_SKIN -> noisyFill(img, rnd, new Color(236, 160, 160), 10);
            case COW_SKIN -> speckledNoise(img, rnd, new Color(98, 66, 40), new Color[]{new Color(235, 235, 235), new Color(60, 40, 25)}, 55);
            case CHICKEN_SKIN -> noisyFill(img, rnd, new Color(240, 240, 240), 8);
            case CHICKEN_BEAK -> noisyFill(img, rnd, new Color(235, 150, 40), 8);
        }
        g.dispose();
        return img;
    }

    private void setPixel(BufferedImage img, int x, int y, Color c) {
        if (x < 0 || y < 0 || x >= TILE_PX || y >= TILE_PX) return;
        img.setRGB(x, y, c.getRGB());
    }

    private Color jitter(Color base, Random rnd, int variance) {
        int r = clamp(base.getRed() + rnd.nextInt(variance * 2 + 1) - variance);
        int gr = clamp(base.getGreen() + rnd.nextInt(variance * 2 + 1) - variance);
        int b = clamp(base.getBlue() + rnd.nextInt(variance * 2 + 1) - variance);
        return new Color(r, gr, b);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void noisyFill(BufferedImage img, Random rnd, Color base, int variance) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++)
                setPixel(img, x, y, jitter(base, rnd, variance));
    }

    private void speckledNoise(BufferedImage img, Random rnd, Color base, Color[] speckleColors, int chancePercent) {
        noisyFill(img, rnd, base, 10);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++)
                if (rnd.nextInt(100) < chancePercent)
                    setPixel(img, x, y, speckleColors[rnd.nextInt(speckleColors.length)]);
    }

    private void oreTile(BufferedImage img, Random rnd, Color oreColor) {
        noisyFill(img, rnd, new Color(128, 128, 128), 14);
        for (int i = 0; i < 6; i++) {
            int cx = rnd.nextInt(TILE_PX);
            int cy = rnd.nextInt(TILE_PX);
            setPixel(img, cx, cy, oreColor);
            setPixel(img, cx + 1, cy, oreColor);
            setPixel(img, cx, cy + 1, oreColor);
        }
    }

    private void cobble(BufferedImage img, Random rnd, Color base) {
        noisyFill(img, rnd, base, 14);
        for (int i = 0; i < 8; i++) {
            int x = rnd.nextInt(TILE_PX);
            int y = rnd.nextInt(TILE_PX);
            int len = 3 + rnd.nextInt(4);
            Color line = jitter(base, rnd, 40);
            for (int j = 0; j < len; j++) {
                if (rnd.nextBoolean()) x += rnd.nextInt(2);
                else y += rnd.nextInt(2);
                setPixel(img, x, y, new Color(
                        clamp(line.getRed() - 30), clamp(line.getGreen() - 30), clamp(line.getBlue() - 30)));
            }
        }
    }

    private void mossyCobble(BufferedImage img, Random rnd) {
        cobble(img, rnd, new Color(122, 122, 122));
        for (int i = 0; i < 20; i++) {
            int x = rnd.nextInt(TILE_PX);
            int y = rnd.nextInt(TILE_PX);
            setPixel(img, x, y, new Color(80, 130, 60));
        }
    }

    private void planks(BufferedImage img, Random rnd, Color base) {
        for (int y = 0; y < TILE_PX; y++) {
            Color rowBase = jitter(base, rnd, 8);
            for (int x = 0; x < TILE_PX; x++) {
                Color c = jitter(rowBase, rnd, 10);
                if (x % 4 == 0) c = new Color(clamp(c.getRed() - 25), clamp(c.getGreen() - 20), clamp(c.getBlue() - 15));
                setPixel(img, x, y, c);
            }
        }
    }

    private void grassSide(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                int grassLine = 3 + (int) (Math.sin(x * 0.9) * 1.2);
                if (y < grassLine) setPixel(img, x, y, jitter(new Color(90, 158, 64), rnd, 18));
                else setPixel(img, x, y, jitter(new Color(134, 96, 67), rnd, 18));
            }
        }
    }

    private void logSide(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean stripe = ((x + (y / 3)) % 4) == 0;
                Color base = stripe ? new Color(90, 65, 40) : new Color(120, 90, 58);
                setPixel(img, x, y, jitter(base, rnd, 10));
            }
    }

    private void logTop(BufferedImage img, Random rnd) {
        double cx = TILE_PX / 2.0, cy = TILE_PX / 2.0;
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - cx, y - cy);
                boolean ring = ((int) (d * 1.6)) % 3 == 0;
                Color base = ring ? new Color(150, 118, 78) : new Color(180, 150, 105);
                setPixel(img, x, y, jitter(base, rnd, 8));
            }
    }

    private void glass(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean edge = x == 0 || y == 0 || x == TILE_PX - 1 || y == TILE_PX - 1;
                int alpha = edge ? 160 : 60;
                Color c = jitter(new Color(210, 235, 240), rnd, 10);
                img.setRGB(x, y, new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB());
            }
    }

    private void waterLike(BufferedImage img, Random rnd, Color base) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                Color c = jitter(base, rnd, 20);
                img.setRGB(x, y, new Color(c.getRed(), c.getGreen(), c.getBlue(), 170).getRGB());
            }
    }

    private void bandedHorizontal(BufferedImage img, Random rnd, Color a, Color b) {
        for (int y = 0; y < TILE_PX; y++) {
            Color rowColor = (y / 3) % 2 == 0 ? a : b;
            for (int x = 0; x < TILE_PX; x++) setPixel(img, x, y, jitter(rowColor, rnd, 8));
        }
    }

    private void bricks(BufferedImage img, Random rnd) {
        Color brick = new Color(150, 80, 65);
        Color mortar = new Color(190, 180, 170);
        for (int y = 0; y < TILE_PX; y++) {
            int rowInBand = y % 4;
            int offset = ((y / 4) % 2 == 0) ? 0 : 4;
            for (int x = 0; x < TILE_PX; x++) {
                boolean mortarLine = rowInBand == 0 || ((x + offset) % 8 == 0);
                setPixel(img, x, y, jitter(mortarLine ? mortar : brick, rnd, 10));
            }
        }
    }

    private void tntSide(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                Color base = (y >= 6 && y <= 10) ? new Color(235, 235, 225) : new Color(196, 60, 40);
                setPixel(img, x, y, jitter(base, rnd, 8));
            }
    }

    private void bookshelf(BufferedImage img, Random rnd) {
        Color[] spineColors = {new Color(150, 40, 40), new Color(40, 70, 140), new Color(150, 140, 40), new Color(60, 110, 60)};
        for (int y = 0; y < TILE_PX; y++) {
            boolean frame = y < 1 || y > TILE_PX - 2;
            for (int x = 0; x < TILE_PX; x++) {
                if (frame || x < 1 || x > TILE_PX - 2) {
                    setPixel(img, x, y, jitter(new Color(150, 110, 66), rnd, 10));
                } else {
                    Color spine = spineColors[(x / 2) % spineColors.length];
                    setPixel(img, x, y, jitter(spine, rnd, 12));
                }
            }
        }
    }

    private void chest(BufferedImage img, Random rnd) {
        planks(img, rnd, new Color(140, 100, 55));
        for (int x = 0; x < TILE_PX; x++) {
            setPixel(img, x, 7, new Color(60, 45, 25));
            setPixel(img, x, 8, new Color(60, 45, 25));
        }
        for (int y = 0; y < TILE_PX; y++) {
            setPixel(img, 0, y, new Color(60, 45, 25));
            setPixel(img, TILE_PX - 1, y, new Color(60, 45, 25));
        }
        setPixel(img, 7, 6, new Color(40, 35, 20));
        setPixel(img, 8, 6, new Color(40, 35, 20));
        setPixel(img, 7, 9, new Color(210, 190, 60));
        setPixel(img, 8, 9, new Color(210, 190, 60));
    }

    private void craftingTop(BufferedImage img, Random rnd) {
        planks(img, rnd, new Color(150, 110, 66));
        for (int i = 0; i < TILE_PX; i++) {
            setPixel(img, i, i, new Color(90, 65, 35));
            if (i < TILE_PX) setPixel(img, TILE_PX - 1 - i, i, new Color(200, 170, 90));
        }
    }

    private void furnaceSide(BufferedImage img, Random rnd) {
        noisyFill(img, rnd, new Color(110, 110, 110), 12);
        for (int y = 5; y <= 11; y++)
            for (int x = 4; x <= 11; x++)
                setPixel(img, x, y, new Color(35, 35, 35));
        for (int y = 6; y <= 10; y++)
            for (int x = 5; x <= 10; x++)
                setPixel(img, x, y, new Color(60, 40, 20));
    }

    private void ice(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                Color c = jitter(new Color(175, 210, 235), rnd, 12);
                img.setRGB(x, y, new Color(c.getRed(), c.getGreen(), c.getBlue(), 210).getRGB());
            }
        for (int i = 0; i < 4; i++) {
            int x = rnd.nextInt(TILE_PX), y = rnd.nextInt(TILE_PX);
            int len = 4 + rnd.nextInt(5);
            for (int j = 0; j < len; j++) {
                setPixel(img, Math.min(TILE_PX - 1, x + j), Math.min(TILE_PX - 1, y + j / 2), new Color(220, 240, 250, 230));
            }
        }
    }

    private void pumpkinSide(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean ridge = (x % 4) == 0;
                Color base = ridge ? new Color(180, 90, 20) : new Color(220, 120, 30);
                setPixel(img, x, y, jitter(base, rnd, 10));
            }
    }

    private void disc(BufferedImage img, Color core, Color edge) {
        double cx = TILE_PX / 2.0, cy = TILE_PX / 2.0;
        double r = TILE_PX / 2.0 - 0.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d > r) {
                    img.setRGB(x, y, 0); // transparent outside the circle
                    continue;
                }
                double t = Math.max(0, Math.min(1, d / r));
                int cr = (int) (core.getRed() + (edge.getRed() - core.getRed()) * t);
                int cg = (int) (core.getGreen() + (edge.getGreen() - core.getGreen()) * t);
                int cb = (int) (core.getBlue() + (edge.getBlue() - core.getBlue()) * t);
                img.setRGB(x, y, new Color(cr, cg, cb, 255).getRGB());
            }
        }
    }

    /** A translucent vertical streak, centered, fading out toward the edges - a simple raindrop quad texture. */
    private void rainStreak(BufferedImage img, Random rnd) {
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double distFromCenter = Math.abs(x + 0.5 - TILE_PX / 2.0) / (TILE_PX / 2.0);
                int alpha = (int) (200 * Math.max(0, 1 - distFromCenter * distFromCenter));
                // Vanilla rain reads as a light, slightly saturated blue streak (not gray-white).
                img.setRGB(x, y, new Color(100, 140, 255, alpha).getRGB());
            }
        }
    }

    /** A soft round pale yellow-green glow (firefly-like), fully transparent at the edge. */
    private void nightMote(BufferedImage img) {
        double c = (TILE_PX - 1) / 2.0;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x - c, y - c) / (TILE_PX / 2.0);
                double a = Math.max(0, 1 - d);
                img.setRGB(x, y, new Color(230, 255, 150, (int) (255 * a * a)).getRGB());
            }
        }
    }

    private static final Color TOOL_HANDLE = new Color(121, 85, 58);
    private static final Color TOOL_HEAD_WOOD = new Color(163, 122, 80);
    private static final Color TOOL_HEAD_STONE = new Color(132, 132, 132);
    private static final Color TOOL_HEAD_IRON = new Color(216, 216, 216);

    /** A lone diagonal handle, no head - the crafting ingredient/half-finished-looking stick. */
    private void stick(BufferedImage img, Random rnd) {
        for (int i = 0; i < 12; i++) {
            setPixel(img, 2 + i, 13 - i, jitter(TOOL_HANDLE, rnd, 10));
        }
    }

    /** The shared diagonal wood handle every tool icon is built on top of. */
    private void toolHandle(BufferedImage img, Random rnd) {
        for (int i = 0; i < 9; i++) {
            int x = 2 + i, y = 13 - i;
            setPixel(img, x, y, jitter(TOOL_HANDLE, rnd, 8));
            setPixel(img, x, y + 1, jitter(TOOL_HANDLE, rnd, 8));
        }
    }

    private void pickaxeIcon(BufferedImage img, Random rnd, Color head) {
        toolHandle(img, rnd);
        for (int[] c : new int[][]{{8, 3}, {9, 2}, {10, 1}, {11, 1}, {12, 2}, {13, 3}, {9, 4}, {12, 4}}) {
            setPixel(img, c[0], c[1], jitter(head, rnd, 10));
        }
    }

    private void axeIcon(BufferedImage img, Random rnd, Color head) {
        toolHandle(img, rnd);
        for (int[] c : new int[][]{{9, 1}, {10, 1}, {11, 1}, {9, 2}, {10, 2}, {11, 2}, {12, 2},
                {10, 3}, {11, 3}, {12, 3}, {13, 3}, {11, 4}, {12, 4}}) {
            setPixel(img, c[0], c[1], jitter(head, rnd, 10));
        }
    }

    private void shovelIcon(BufferedImage img, Random rnd, Color head) {
        toolHandle(img, rnd);
        for (int x = 9; x <= 12; x++) {
            for (int y = 1; y <= 4; y++) setPixel(img, x, y, jitter(head, rnd, 10));
        }
    }

    /** A small red disc with a brown stem and a green leaf - all transparent outside the disc,
     *  matching the flat-icon rendering these items use (see ChunkMeshBuilder.isFlatIconItem). */
    private void appleIcon(BufferedImage img, Random rnd) {
        double cx = 8, cy = 9, r = 5.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d <= r) img.setRGB(x, y, jitter(new Color(196, 40, 40), rnd, 14).getRGB());
                else img.setRGB(x, y, 0);
            }
        }
        setPixel(img, 8, 3, new Color(90, 60, 30));
        setPixel(img, 8, 2, new Color(90, 60, 30));
        setPixel(img, 9, 2, new Color(60, 130, 40));
        setPixel(img, 10, 2, new Color(60, 130, 40));
    }

    /** An oval tan loaf with darker crust lines - transparent outside the oval. */
    private void breadIcon(BufferedImage img, Random rnd) {
        double cx = 8, cy = 8, rx = 6.5, ry = 4.5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                if (nx * nx + ny * ny <= 1.0) {
                    boolean crustLine = ((x + 2) % 4) == 0;
                    Color base = crustLine ? new Color(160, 110, 55) : new Color(206, 158, 92);
                    img.setRGB(x, y, jitter(base, rnd, 10).getRGB());
                } else {
                    img.setRGB(x, y, 0);
                }
            }
        }
    }

    /** A brown rounded steak slab with a couple of darker grill-mark stripes. */
    private void cookedBeefIcon(BufferedImage img, Random rnd) {
        meatIcon(img, rnd, new Color(150, 88, 60), new Color(90, 45, 30));
    }

    /** A diagonal blade with a short handle (fallback when no real sword texture exists). */
    private void swordIcon(BufferedImage img, Random rnd, Color blade) {
        for (int i = 0; i < 9; i++) {
            setPixel(img, 5 + i, 10 - i, jitter(blade, rnd, 8));
            setPixel(img, 6 + i, 10 - i, jitter(blade, rnd, 8));
        }
        setPixel(img, 4, 11, jitter(TOOL_HANDLE, rnd, 8));
        setPixel(img, 3, 12, jitter(TOOL_HANDLE, rnd, 8));
        setPixel(img, 2, 13, jitter(TOOL_HANDLE, rnd, 8));
    }

    /** A rounded meat-slab shape, either raw (a plain flesh-tone base with no grill marks) or
     *  cooked (the same shape with darker grill-mark speckling) - every raw/cooked meat and fish
     *  pair reuses this one shape with different colors, rather than each getting its own. */
    private void meatIcon(BufferedImage img, Random rnd, Color base, Color grillMark) {
        double cx = 8, cy = 8, rx = 6, ry = 5;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                if (nx * nx + ny * ny <= 1.0) {
                    boolean mark = grillMark != null && (x + y) % 5 == 0;
                    img.setRGB(x, y, jitter(mark ? grillMark : base, rnd, 10).getRGB());
                } else {
                    img.setRGB(x, y, 0);
                }
            }
        }
    }

    /** A flat teardrop-ish fish shape (a smaller ellipse than the meat slab, with a small tail
     *  triangle) - reused for both fish species, raw and cooked. */
    private void fishIcon(BufferedImage img, Random rnd, Color base, Color mark) {
        double cx = 7, cy = 8, rx = 5, ry = 3.2;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                boolean body = nx * nx + ny * ny <= 1.0;
                boolean tail = x >= 12 && x <= 14 && Math.abs(y - 8) <= (14 - x);
                if (body || tail) {
                    boolean marked = mark != null && (x + y) % 4 == 0;
                    img.setRGB(x, y, jitter(marked ? mark : base, rnd, 8).getRGB());
                } else {
                    img.setRGB(x, y, 0);
                }
            }
        }
    }

    /** A small rounded potato/tuber shape. */
    private void potatoIcon(BufferedImage img, Random rnd, Color base) {
        double cx = 8, cy = 9, rx = 4.5, ry = 4;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry;
                img.setRGB(x, y, nx * nx + ny * ny <= 1.0 ? jitter(base, rnd, 14).getRGB() : 0);
            }
        }
    }

    /** A thin vertical wavy strand, for kelp/dried kelp - everywhere the strand doesn't reach
     *  stays transparent (a fresh BufferedImage already starts fully zeroed/transparent). */
    private void kelpIcon(BufferedImage img, Random rnd, Color base) {
        for (int y = 0; y < TILE_PX; y++) {
            int sway = (int) Math.round(Math.sin(y * 0.6) * 1.8);
            int cx = 8 + sway;
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx;
                if (x >= 0 && x < TILE_PX) img.setRGB(x, y, jitter(base, rnd, 10).getRGB());
            }
        }
    }

    /** A simple rectangular ingot-bar shape with a lighter top-face highlight. */
    private void ingotIcon(BufferedImage img, Random rnd, Color base, Color highlight) {
        for (int y = 5; y <= 11; y++) {
            for (int x = 3; x <= 12; x++) {
                boolean topFace = y <= 6;
                setPixel(img, x, y, jitter(topFace ? highlight : base, rnd, 10));
            }
        }
    }

    /** A small rounded nugget/pebble shape - a scaled-down version of the ingot's rounded end. */
    private void nuggetIcon(BufferedImage img, Random rnd, Color base) {
        double cx = 8, cy = 9, r = 3.2;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                img.setRGB(x, y, d <= r ? jitter(base, rnd, 12).getRGB() : 0);
            }
        }
    }

    /** A rough, irregular raw-ore lump - larger and lumpier than the smooth ingot bar. */
    private void rawOreIcon(BufferedImage img, Random rnd, Color base) {
        double cx = 8, cy = 8;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                double wobble = Math.sin(x * 1.3 + y * 0.7) * 1.2;
                double r = 5.2 + wobble;
                img.setRGB(x, y, Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r ? jitter(base, rnd, 16).getRGB() : 0);
            }
        }
    }

    /** A small dark rectangular block - brick/coal/charcoal all reuse this with different colors. */
    private void blockyIcon(BufferedImage img, Random rnd, Color base) {
        for (int y = 4; y <= 12; y++) {
            for (int x = 4; x <= 11; x++) setPixel(img, x, y, jitter(base, rnd, 14));
        }
    }

    private void pumpkinTop(BufferedImage img, Random rnd) {
        noisyFill(img, rnd, new Color(220, 120, 30), 12);
        for (int y = 6; y <= 9; y++)
            for (int x = 6; x <= 9; x++)
                setPixel(img, x, y, new Color(70, 110, 40));
    }
}
