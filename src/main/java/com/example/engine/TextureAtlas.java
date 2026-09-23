package com.example.engine;

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
    // Each tile gets a 1px extruded border so mipmap downsampling blends a tile with
    // itself instead of bleeding in neighboring tiles' colors.
    public static final int BORDER_PX = 1;
    public static final int CELL_PX = TILE_PX + BORDER_PX * 2;
    public static final int COLS = 8;
    public static final int ROWS = (Tile.values().length + COLS - 1) / COLS;

    private static final Map<Tile, String> REAL_TEXTURE_PATHS = new EnumMap<>(Tile.class);

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
        // CHEST, SUN, MOON, RAIN: no real block-texture equivalent, always procedural.
    }

    private final int textureId;

    public TextureAtlas() {
        BufferedImage atlas = new BufferedImage(COLS * CELL_PX, ROWS * CELL_PX, BufferedImage.TYPE_INT_ARGB);
        Graphics2D atlasG = atlas.createGraphics();

        for (Tile tile : Tile.values()) {
            BufferedImage img = loadOrDrawTile(tile);
            int[] origin = cellOrigin(tile);
            atlasG.drawImage(img, origin[0], origin[1], null);
        }
        atlasG.dispose();

        for (Tile tile : Tile.values()) {
            extrudeBorder(atlas, tile);
        }

        textureId = upload(atlas);
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
                int sx = Math.max(0, Math.min(TILE_PX - 1, dx));
                int sy = Math.max(0, Math.min(TILE_PX - 1, dy));
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

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public int getTextureId() {
        return textureId;
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
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buffer);
        return id;
    }

    // ---------- procedural drawing ----------

    private BufferedImage drawTile(Tile tile) {
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
                int alpha = (int) (140 * Math.max(0, 1 - distFromCenter * distFromCenter));
                img.setRGB(x, y, new Color(170, 195, 220, alpha).getRGB());
            }
        }
    }

    private void pumpkinTop(BufferedImage img, Random rnd) {
        noisyFill(img, rnd, new Color(220, 120, 30), 12);
        for (int y = 6; y <= 9; y++)
            for (int x = 6; x <= 9; x++)
                setPixel(img, x, y, new Color(70, 110, 40));
    }
}
