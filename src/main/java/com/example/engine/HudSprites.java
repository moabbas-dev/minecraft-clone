package com.example.engine;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Original pixel-art HUD sprites, drawn from small ASCII maps and flat colour rows - nothing here is copied or
 * extracted from Mojang's assets. Sizes and the way the pieces fit together follow the vanilla layout in
 * docs/vanilla-rules.md section 5 (9x9 heart/food/armor/air icons on an 8 px pitch, a 182x22 hotbar with 20 px slots,
 * a 24x23 selection frame, a 182x5 XP bar); the look is the classic one: a dark outline, three tones of shading and
 * a light pixel at the top left.
 *
 * {@link #image} is keyed by the same names {@link HudTextures} uses for its optional texture files, so a file in
 * {@code textures/gui/sprites/hud/} still takes precedence for anyone who supplies their own.
 */
public final class HudSprites {
    private HudSprites() {
    }

    private static final int CLEAR = 0;

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- 9x9 icon shapes ('.' = transparent) ----

    /** Heart: '#' outline, 'r' fill, 'w' highlight, 'd' shade. */
    private static final String[] HEART = {
            "..##.##..",
            ".#ww#rr#.",
            "#wwrrrrr#",
            "#wrrrrrr#",
            "#rrrrrrd#",
            ".#rrrrd#.",
            "..#rrd#..",
            "...#d#...",
            "....#....",
    };

    /** Drumstick: '#' outline, 'b' meat, 'l' highlight, 'd' shade, 'w' bone. */
    private static final String[] FOOD = {
            ".....###.",
            "...##bbb#",
            "..#bllbb#",
            "..#blbbd#",
            ".#bbbbbd#",
            ".#bbbdd#.",
            "#wbbd#...",
            "#ww##....",
            ".##......",
    };

    /** Chestplate: '#' outline, 'w' plate, 'f' highlight, 'd' shade. */
    private static final String[] ARMOR = {
            ".#.....#.",
            "#f#...#w#",
            "#fw###ww#",
            "#fwwwwwd#",
            "#wwwwwwd#",
            ".#wwwwd#.",
            ".#wwwwd#.",
            ".#wdddd#.",
            "..#####..",
    };

    /** Air bubble: '#' outline, 'b' water, 'w' highlight. */
    private static final String[] BUBBLE = {
            "..#####..",
            ".#bwbbb#.",
            "#bwbbbbb#",
            "#bwbbbbb#",
            "#bbbbbbb#",
            "#bbbbbbb#",
            "#bbbbbbb#",
            ".#bbbbb#.",
            "..#####..",
    };

    /** The pop that replaces a bubble as it bursts. */
    private static final String[] BURST = {
            "b...b...b",
            ".b..b..b.",
            "..b.b.b..",
            "...bbb...",
            "bbb.b.bbb",
            "...bbb...",
            "..b.b.b..",
            ".b..b..b.",
            "b...b...b",
    };

    private static BufferedImage paint(String[] shape, Map<Character, Integer> palette) {
        BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                Integer c = palette.get(shape[y].charAt(x));
                img.setRGB(x, y, c == null ? CLEAR : c);
            }
        }
        return img;
    }

    /** Silhouette of a shape (every non-'.' cell) painted in one fill with a separate outline colour for '#'. */
    private static BufferedImage silhouette(String[] shape, int outline, int fill) {
        BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                char ch = shape[y].charAt(x);
                img.setRGB(x, y, ch == '.' ? CLEAR : ch == '#' ? outline : fill);
            }
        }
        return img;
    }

    /** The left five columns of {@code full}; the right side stays transparent so the empty icon underneath shows. */
    private static BufferedImage leftHalf(BufferedImage full) {
        BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 5; x++) img.setRGB(x, y, full.getRGB(x, y));
        return img;
    }

    private static Map<Character, Integer> heartPalette(boolean blinking) {
        return blinking
                ? Map.of('#', rgb(0, 0, 0), 'r', rgb(255, 150, 150), 'w', rgb(255, 255, 255), 'd', rgb(220, 110, 110))
                : Map.of('#', rgb(30, 0, 0), 'r', rgb(224, 24, 24), 'w', rgb(255, 150, 140), 'd', rgb(160, 8, 12));
    }

    // ---- bars and frames ----

    private static BufferedImage hotbar() {
        BufferedImage img = new BufferedImage(182, 22, BufferedImage.TYPE_INT_ARGB);
        int border = rgb(16, 16, 16), frame = rgb(139, 139, 139), shadow = rgb(90, 90, 90), well = rgba(24, 24, 24, 200);
        for (int y = 0; y < 22; y++) {
            for (int x = 0; x < 182; x++) {
                boolean outer = x == 0 || x == 181 || y == 0 || y == 21;
                img.setRGB(x, y, outer ? border : frame);
            }
        }
        for (int i = 0; i < 9; i++) {
            int x0 = 1 + i * 20;
            for (int y = 0; y < 20; y++) {
                for (int x = 0; x < 20; x++) {
                    int px = x0 + x, py = 1 + y;
                    boolean inWell = x >= 2 && x < 18 && y >= 2 && y < 18;
                    if (inWell) img.setRGB(px, py, well);
                    else if (x >= 18 || y >= 18) img.setRGB(px, py, shadow); // bevel: light top-left, dark bottom-right
                }
            }
        }
        return img;
    }

    private static BufferedImage selection() {
        BufferedImage img = new BufferedImage(24, 23, BufferedImage.TYPE_INT_ARGB);
        int black = rgb(0, 0, 0), white = rgb(255, 255, 255), grey = rgb(198, 198, 198);
        for (int y = 0; y < 23; y++) {
            for (int x = 0; x < 24; x++) {
                int edge = Math.min(Math.min(x, 23 - x), Math.min(y, 22 - y));
                if (edge == 0) img.setRGB(x, y, black);
                else if (edge == 1) img.setRGB(x, y, white);
                else if (edge == 2) img.setRGB(x, y, grey);
                else img.setRGB(x, y, CLEAR);
            }
        }
        return img;
    }

    private static final int[] XP_BG_ROWS = {rgb(16, 16, 16), rgb(72, 72, 72), rgb(58, 58, 58), rgb(44, 44, 44), rgb(16, 16, 16)};
    private static final int[] XP_FILL_ROWS = {rgb(190, 255, 130), rgb(128, 232, 32), rgb(108, 200, 24), rgb(78, 150, 16), rgb(36, 88, 8)};

    private static BufferedImage xp(int[] rows, boolean capped) {
        BufferedImage img = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 182; x++) {
                boolean cap = capped && (x == 0 || x == 181);
                img.setRGB(x, y, cap ? rgb(16, 16, 16) : rows[y]);
            }
        }
        return img;
    }

    private static BufferedImage solid(int w, int h, int border, int fill) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                img.setRGB(x, y, edge ? border : fill);
            }
        }
        return img;
    }

    /** A small upward sword shown when the attack is fully charged and an enemy is in reach. */
    private static BufferedImage crosshairFull() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int blade = rgb(240, 240, 240), edge = rgb(40, 40, 40), grip = rgb(130, 90, 55);
        for (int y = 2; y < 10; y++) {
            img.setRGB(8, y, blade);
            img.setRGB(7, y, edge);
            img.setRGB(9, y, edge);
        }
        img.setRGB(8, 1, edge);
        for (int x = 5; x <= 11; x++) img.setRGB(x, 10, edge);
        for (int x = 6; x <= 10; x++) img.setRGB(x, 11, grip);
        img.setRGB(8, 12, grip);
        img.setRGB(8, 13, edge);
        return img;
    }

    /**
     * The sprite for a texture-file name used by {@link HudTextures}, or null for an unknown name.
     */
    public static BufferedImage image(String name) {
        return switch (name) {
            case "hotbar.png" -> hotbar();
            case "hotbar_selection.png" -> selection();
            case "heart/container.png" -> silhouette(HEART, rgb(0, 0, 0), rgb(54, 22, 22));
            case "heart/container_blinking.png" -> silhouette(HEART, rgb(255, 255, 255), rgb(255, 255, 255));
            case "heart/full.png" -> paint(HEART, heartPalette(false));
            case "heart/half.png" -> leftHalf(paint(HEART, heartPalette(false)));
            case "heart/full_blinking.png" -> paint(HEART, heartPalette(true));
            case "heart/half_blinking.png" -> leftHalf(paint(HEART, heartPalette(true)));
            case "food_empty.png" -> silhouette(FOOD, rgb(0, 0, 0), rgb(56, 44, 38));
            case "food_full.png" -> paint(FOOD, Map.of('#', rgb(40, 14, 0), 'b', rgb(196, 112, 52), 'l', rgb(236, 170, 100),
                    'd', rgb(138, 70, 28), 'w', rgb(236, 232, 214)));
            case "food_half.png" -> leftHalf(paint(FOOD, Map.of('#', rgb(40, 14, 0), 'b', rgb(196, 112, 52), 'l', rgb(236, 170, 100),
                    'd', rgb(138, 70, 28), 'w', rgb(236, 232, 214))));
            case "armor_empty.png" -> silhouette(ARMOR, rgb(70, 70, 76), rgba(20, 20, 24, 150));
            case "armor_full.png" -> paint(ARMOR, Map.of('#', rgb(26, 26, 32), 'w', rgb(178, 184, 192), 'f', rgb(238, 242, 248),
                    'd', rgb(110, 116, 126)));
            case "armor_half.png" -> leftHalf(paint(ARMOR, Map.of('#', rgb(26, 26, 32), 'w', rgb(178, 184, 192), 'f', rgb(238, 242, 248),
                    'd', rgb(110, 116, 126))));
            case "air.png" -> paint(BUBBLE, Map.of('#', rgb(20, 60, 110), 'b', rgb(120, 190, 255), 'w', rgb(235, 248, 255)));
            case "air_bursting.png" -> paint(BURST, Map.of('b', rgb(215, 240, 255)));
            case "experience_bar_background.png" -> xp(XP_BG_ROWS, true);
            case "experience_bar_progress.png" -> xp(XP_FILL_ROWS, false);
            case "crosshair_attack_indicator_background.png" -> solid(16, 4, rgb(16, 16, 16), rgb(64, 64, 64));
            case "crosshair_attack_indicator_progress.png" -> solid(16, 4, rgb(255, 255, 255), rgb(255, 255, 255));
            case "crosshair_attack_indicator_full.png" -> crosshairFull();
            case "hotbar_attack_indicator_background.png" -> solid(6, 18, rgb(16, 16, 16), rgb(64, 64, 64));
            case "hotbar_attack_indicator_progress.png" -> solid(6, 18, rgb(255, 255, 255), rgb(255, 255, 255));
            default -> null;
        };
    }
}
