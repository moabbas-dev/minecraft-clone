package com.example.ui;

import com.example.engine.Font;
import com.example.engine.UiRenderer;

/**
 * Draws the container-screen furniture (panel, slot frames, hover highlight, arrow, flame, tooltip) as flat coloured
 * quads at a GUI scale, in the classic look: a light-grey bevelled panel, inset slots, a white 50% hover square and a
 * dark-purple tooltip. Original art - there is no texture involved. All positions and sizes are framebuffer pixels;
 * {@code s} is the GUI scale (framebuffer pixels per GUI pixel).
 */
public final class GuiPainter {
    private GuiPainter() {
    }

    private static void quad(UiRenderer ui, float x, float y, float w, float h, int rgb, float a) {
        ui.drawColorQuad(x, y, w, h, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a);
    }

    /** Screen dimmer behind a container screen. */
    public static void dim(UiRenderer ui, float w, float h) {
        quad(ui, 0, 0, w, h, 0x101010, 0.72f);
    }

    /** The grey container background: black edge, white top/left bevel, dark-grey bottom/right bevel. */
    public static void panel(UiRenderer ui, float x, float y, float w, float h, float s) {
        quad(ui, x, y, w, h, 0x000000, 1f);
        quad(ui, x + s, y + s, w - 2 * s, h - 2 * s, 0xC6C6C6, 1f);
        quad(ui, x + s, y + s, w - 3 * s, 2 * s, 0xFFFFFF, 1f);
        quad(ui, x + s, y + s, 2 * s, h - 3 * s, 0xFFFFFF, 1f);
        quad(ui, x + 3 * s, y + h - 3 * s, w - 4 * s, 2 * s, 0x555555, 1f);
        quad(ui, x + w - 3 * s, y + 3 * s, 2 * s, h - 4 * s, 0x555555, 1f);
        // the two corners where the bevels meet read as a single mid-grey pixel
        quad(ui, x + w - 3 * s, y + s, 2 * s, 2 * s, 0xC6C6C6, 1f);
        quad(ui, x + s, y + h - 3 * s, 2 * s, 2 * s, 0xC6C6C6, 1f);
    }

    /** An inset 18 x 18 slot frame around the 16 x 16 item area whose top-left is (itemX, itemY). */
    public static void slot(UiRenderer ui, float itemX, float itemY, float s) {
        float x = itemX - s, y = itemY - s;
        quad(ui, x, y, 18 * s, 18 * s, 0x8B8B8B, 1f);
        quad(ui, x, y, 18 * s, s, 0x373737, 1f);
        quad(ui, x, y, s, 18 * s, 0x373737, 1f);
        quad(ui, x + s, y + 17 * s, 17 * s, s, 0xFFFFFF, 1f);
        quad(ui, x + 17 * s, y + s, s, 17 * s, 0xFFFFFF, 1f);
    }

    /** A larger inset box (the player-preview area). */
    public static void inset(UiRenderer ui, float x, float y, float w, float h, float s) {
        quad(ui, x, y, w, h, 0x8B8B8B, 1f);
        quad(ui, x, y, w, s, 0x373737, 1f);
        quad(ui, x, y, s, h, 0x373737, 1f);
        quad(ui, x + s, y + h - s, w - s, s, 0xFFFFFF, 1f);
        quad(ui, x + w - s, y + s, s, h - s, 0xFFFFFF, 1f);
    }

    /** The white half-transparent square drawn over the hovered slot's item area. */
    public static void highlight(UiRenderer ui, InventoryLayout.Rect itemArea) {
        quad(ui, itemArea.x(), itemArea.y(), itemArea.w(), itemArea.h(), 0xFFFFFF, 0.5f);
    }

    /**
     * A right-pointing arrow of {@code w x h} GUI pixels, grey, with the left {@code progress} fraction filled white.
     */
    public static void arrow(UiRenderer ui, InventoryLayout.Rect r, float s, float progress) {
        int w = Math.round(r.w() / s), h = Math.round(r.h() / s);
        int headLen = Math.max(4, h / 2 + 1);
        int shaftEnd = w - headLen;
        float mid = (h - 1) / 2f;
        for (int row = 0; row < h; row++) {
            float dy = Math.abs(row - mid);
            int shaftHalf = Math.max(1, h / 6);
            int end = dy <= shaftHalf ? w : shaftEnd + Math.round((1 - dy / (mid + 0.5f)) * headLen);
            int x0 = 0;
            while (x0 < end) {
                float filledUntil = progress * w;
                boolean filled = x0 + 0.5f < filledUntil;
                int x1 = x0;
                while (x1 < end && ((x1 + 0.5f < filledUntil) == filled)) x1++;
                quad(ui, r.x() + x0 * s, r.y() + row * s, (x1 - x0) * s, s, filled ? 0xFFFFFF : 0x8B8B8B, 1f);
                x0 = x1;
            }
        }
    }

    private static final String[] FLAME = {
            "......##......",
            ".....#yy#.....",
            ".....#yy#.#...",
            "....#yyyy##...",
            "...#yyooyyy#..",
            "...#yooooyy#..",
            "..#yyooooooy#.",
            "..#yooorroooy#",
            "..#yoorrrroo#.",
            "..#yoorrrroo#.",
            "..#yooorrroo#.",
            "...#yooooooy#.",
            "....#yooooy#..",
            ".....######...",
    };

    /** The furnace flame; only the bottom {@code fraction} of it is lit. */
    public static void flame(UiRenderer ui, InventoryLayout.Rect r, float s, float fraction) {
        int h = FLAME.length;
        int litFrom = Math.round(h * (1f - Math.max(0f, Math.min(1f, fraction))));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < FLAME[y].length(); x++) {
                char c = FLAME[y].charAt(x);
                if (c == '.') continue;
                boolean lit = y >= litFrom;
                int rgb = switch (c) {
                    case '#' -> lit ? 0x5A2400 : 0x3C3C3C;
                    case 'y' -> lit ? 0xFFD23A : 0x6E6E6E;
                    case 'o' -> lit ? 0xF58A1E : 0x5A5A5A;
                    default -> lit ? 0xD84A12 : 0x505050;
                };
                quad(ui, r.x() + x * s, r.y() + y * s, s, s, rgb, 1f);
            }
        }
    }

    /**
     * A vanilla-style tooltip near the mouse: dark purple box with a thin violet border, white text with a shadow.
     * Kept on screen by flipping to the left of the cursor or sliding up when it would overflow.
     */
    public static void tooltip(UiRenderer ui, Font font, String text, float mouseX, float mouseY, float s, float screenW, float screenH) {
        float textW = font.width(text, s);
        float padX = 3 * s, textH = 8 * s;
        float w = textW + 2 * padX, h = textH + 6 * s;
        float x = mouseX + 12 * s, y = mouseY - 12 * s;
        if (x + w > screenW) x = mouseX - 12 * s - w;
        if (y + h > screenH) y = screenH - h;
        if (y < 0) y = 0;
        quad(ui, x, y, w, h, 0x100010, 0.94f);
        quad(ui, x, y + s, w, h - 2 * s, 0x100010, 0.94f);
        quad(ui, x + s, y, w - 2 * s, s, 0x5000FF, 0.35f);
        quad(ui, x + s, y + h - s, w - 2 * s, s, 0x28007F, 0.35f);
        quad(ui, x, y + s, s, h - 2 * s, 0x5000FF, 0.35f);
        quad(ui, x + w - s, y + s, s, h - 2 * s, 0x28007F, 0.35f);
        font.draw(ui, text, x + padX, y + 3 * s, s, 0xFFFFFF, true);
    }
}
