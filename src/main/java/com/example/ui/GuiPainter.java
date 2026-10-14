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
     * Pixel extent of row {@code row} of the arrow, as {x start, x end exclusive}: a 3-px shaft in the middle and a
     * triangular head whose base is a full column {@code h} tall and which narrows by 1 px per row to a tip
     * {@code (h+1)/2} px long. Measured from the real furnace / crafting-table / inventory screens: 22x15 (shaft 14),
     * 22x15, and 16x13 (shaft 9).
     */
    static int[] arrowRow(int w, int h, int row) {
        int headW = (h + 1) / 2;
        int shaftLen = w - headW;
        int mid = (h - 1) / 2;
        int dy = Math.abs(row - mid);
        int end = shaftLen + headW - dy;
        int start = dy <= 1 ? 0 : shaftLen;
        return new int[]{start, end};
    }

    /**
     * A right-pointing arrow filling the rect (see {@link #arrowRow}), grey, with the left {@code progress} fraction white.
     */
    public static void arrow(UiRenderer ui, InventoryLayout.Rect r, float s, float progress) {
        int w = Math.round(r.w() / s), h = Math.round(r.h() / s);
        float filledUntil = Math.max(0f, Math.min(1f, progress)) * w;
        for (int row = 0; row < h; row++) {
            int[] ext = arrowRow(w, h, row);
            int x0 = ext[0];
            while (x0 < ext[1]) {
                boolean filled = x0 + 0.5f < filledUntil;
                int x1 = x0;
                while (x1 < ext[1] && ((x1 + 0.5f < filledUntil) == filled)) x1++;
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
                if (y < litFrom) continue; // vanilla draws nothing for the unlit part of the flame
                int rgb = switch (c) {
                    case '#' -> 0x5A2400;
                    case 'y' -> 0xFFD23A;
                    case 'o' -> 0xF58A1E;
                    default -> 0xD84A12;
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
