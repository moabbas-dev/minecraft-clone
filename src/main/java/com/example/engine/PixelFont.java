package com.example.engine;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Glyph data and metrics of Minecraft's default font, read from the real {@code textures/font/ascii.png}: a 128x128 sheet
 * of 8x8 cells in character-code order (code c sits in cell (c % 16, c / 16)). Like vanilla's bitmap font provider, a
 * glyph is as wide as its rightmost lit column, the advance is that width + 1, a space is 4 and the line height is 9. Without
 * the file no glyph is drawn and every character advances 6, so layouts still work. Kept free of GL so the metrics are
 * unit-testable; tests inject a sheet with {@link #useSheet}.
 */
public final class PixelFont {
    private PixelFont() {
    }

    public static final int CELL = 8;
    public static final int SHEET = 128;
    public static final int FIRST = 32;
    public static final int LAST = 126;
    public static final int LINE_HEIGHT = 9;
    public static final int SPACE_ADVANCE = 4;
    /** Advance used for every character while the font file is missing. */
    public static final int FALLBACK_ADVANCE = 6;

    private static final int[] WIDTH = new int[LAST - FIRST + 1];
    private static BufferedImage sheet;

    static {
        File file = new File("textures/font/ascii.png");
        try {
            if (file.isFile()) useSheet(TextureIo.read(file));
        } catch (IOException | RuntimeException e) {
            sheet = null;
        }
    }

    /** Uses {@code img} (128x128, 8x8 cells) as the glyph sheet and measures every glyph; null clears it. */
    public static synchronized void useSheet(BufferedImage img) {
        if (img == null || img.getWidth() != SHEET || img.getHeight() != SHEET) {
            sheet = null;
            java.util.Arrays.fill(WIDTH, 0);
            return;
        }
        sheet = img;
        for (int c = FIRST; c <= LAST; c++) {
            int ox = (c % 16) * CELL, oy = (c / 16) * CELL, widest = 0;
            for (int y = 0; y < CELL; y++)
                for (int x = 0; x < CELL; x++)
                    if ((img.getRGB(ox + x, oy + y) >>> 24) > 0) widest = Math.max(widest, x + 1);
            WIDTH[c - FIRST] = c == ' ' ? 0 : widest;
        }
    }

    public static boolean loaded() {
        return sheet != null;
    }

    public static boolean has(char c) {
        return c >= FIRST && c <= LAST;
    }

    /** Lit-pixel width of the glyph (0 for space/unknown). */
    public static int glyphWidth(char c) {
        return has(c) ? WIDTH[c - FIRST] : 0;
    }

    /** Horizontal advance in font pixels: width + 1 gap, space and unknown chars = 4. */
    public static int advance(char c) {
        if (!has(c) || c == ' ') return SPACE_ADVANCE;
        if (sheet == null) return FALLBACK_ADVANCE;
        return WIDTH[c - FIRST] + 1;
    }

    /** Full advance sum (includes the trailing 1px gap, like vanilla's string width). */
    public static int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i));
        return w;
    }

    /** True if pixel (x, y) of the glyph is lit. */
    public static boolean pixel(char c, int x, int y) {
        if (sheet == null || !has(c) || y < 0 || y >= CELL || x < 0 || x >= WIDTH[c - FIRST]) return false;
        return (sheet.getRGB((c % 16) * CELL + x, (c / 16) * CELL + y) >>> 24) > 0;
    }

    /** The 128x128 glyph sheet (fully transparent when the font file is missing). */
    public static BufferedImage buildSheet() {
        return sheet != null ? sheet : new BufferedImage(SHEET, SHEET, BufferedImage.TYPE_INT_ARGB);
    }
}
