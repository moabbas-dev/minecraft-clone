package com.example.ui;

import com.example.engine.Font;
import com.example.engine.HudTextures;
import com.example.engine.UiRenderer;

/**
 * Container-screen drawing helpers at a GUI scale: the real container background texture, the hover square (vanilla
 * fills the slot with 50% white) and the dark-purple tooltip. Panels, slot frames, arrows and flames all come from the
 * real textures; the only drawn fallbacks are a plain panel and plain slots when those files are missing. All positions
 * and sizes are framebuffer pixels; {@code s} is the GUI scale (framebuffer pixels per GUI pixel).
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

    /** The real container texture (176 x 166 GUI pixels in the top-left of a 256 x 256 file) drawn over the panel rectangle. */
    public static void background(UiRenderer ui, HudTextures.Sprite texture, InventoryLayout.Rect panel) {
        HudTextures.drawRegion(ui, texture, panel.x(), panel.y(), panel.w(), panel.h(), 0f, 0f,
                InventoryLayout.PANEL_W / (float) texture.w(), InventoryLayout.PANEL_H / (float) texture.h());
    }

    /** Plain stand-in panel used only when the container texture file is missing. */
    public static void panel(UiRenderer ui, float x, float y, float w, float h, float s) {
        quad(ui, x, y, w, h, 0xC6C6C6, 1f);
    }

    /** Plain stand-in slot square used only when the container texture file is missing. */
    public static void slot(UiRenderer ui, float itemX, float itemY, float s) {
        quad(ui, itemX - s, itemY - s, 18 * s, 18 * s, 0x8B8B8B, 1f);
    }

    /** The white half-transparent square drawn over the hovered slot's item area. */
    public static void highlight(UiRenderer ui, InventoryLayout.Rect itemArea) {
        quad(ui, itemArea.x(), itemArea.y(), itemArea.w(), itemArea.h(), 0xFFFFFF, 0.5f);
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
