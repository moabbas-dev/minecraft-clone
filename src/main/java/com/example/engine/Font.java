package com.example.engine;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Minecraft-style bitmap font renderer (see {@link PixelFont} for the glyph data and metrics):
 * proportional 8x8-cell glyphs, line height 9, nearest-neighbour sampling, and the vanilla
 * drop shadow (one font pixel down-right at a quarter of the text color).
 *
 * <p>Two ways to size text. {@link #draw} takes the size of one font pixel directly - menu screens
 * pass 1 and let the GUI scale (a uniform logical-to-framebuffer scale, see {@code GuiScale}) do the
 * rest, exactly like vanilla. The legacy {@link #drawText}/{@link #measureText}/{@link #lineHeight}
 * trio used by the HUD takes a multiplier and rounds it to a whole number of framebuffer pixels per
 * font pixel (scale 1.0 = 3px) so text stays crisp at any size.
 */
public class Font {
    /** Font pixels per legacy "scale 1.0" unit. */
    public static final float LEGACY_PX_PER_SCALE = 3f;

    private final int textureId;

    public Font() {
        textureId = upload(PixelFont.buildSheet());
    }

    private static int upload(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        ByteBuffer buffer = memAlloc(w * h * 4);
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
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        memFree(buffer);
        return id;
    }

    // ---- primary API: px = size of one font pixel in the current coordinate space ----

    /** Draws text (optionally with the vanilla shadow) and returns the advance width drawn. */
    public float draw(UiRenderer ui, String text, float x, float y, float px,
                      float r, float g, float b, float a, boolean shadow) {
        if (shadow) glyphs(ui, text, x + px, y + px, px, r * 0.25f, g * 0.25f, b * 0.25f, a);
        return glyphs(ui, text, x, y, px, r, g, b, a);
    }

    /** Packed 0xRRGGBB convenience overload. */
    public float draw(UiRenderer ui, String text, float x, float y, float px, int rgb, boolean shadow) {
        return draw(ui, text, x, y, px, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f, shadow);
    }

    public float drawCentered(UiRenderer ui, String text, float cx, float y, float px, int rgb, boolean shadow) {
        float w = width(text, px);
        return draw(ui, text, cx - w / 2f, y, px, rgb, shadow);
    }

    /** Text width in font pixels * px (includes the trailing 1px gap, as vanilla does). */
    public float width(String text, float px) {
        return PixelFont.textWidth(text) * px;
    }

    public int width(String text) {
        return PixelFont.textWidth(text);
    }

    private float glyphs(UiRenderer ui, String text, float x, float y, float px,
                         float r, float g, float b, float a) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int gw = PixelFont.glyphWidth(c);
            if (gw > 0) {
                int col = c % 16, row = c / 16;
                float u0 = (col * PixelFont.CELL) / (float) PixelFont.SHEET;
                float v0 = (row * PixelFont.CELL) / (float) PixelFont.SHEET;
                float u1 = (col * PixelFont.CELL + gw) / (float) PixelFont.SHEET;
                float v1 = (row * PixelFont.CELL + PixelFont.CELL) / (float) PixelFont.SHEET;
                ui.drawTexturedQuad(cursor, y, gw * px, PixelFont.CELL * px, new float[]{u0, v0, u1, v1}, textureId, r, g, b, a);
            }
            cursor += PixelFont.advance(c) * px;
        }
        return cursor - x;
    }

    /** Text rotated about its own center (title-screen splash), same shadow rule as {@link #draw}. */
    public void drawRotated(UiRenderer ui, String text, float cx, float cy, float angleRad, float px,
                            float r, float g, float b, boolean shadow) {
        if (shadow) rotatedGlyphs(ui, text, cx + px, cy + px, angleRad, px, r * 0.25f, g * 0.25f, b * 0.25f);
        rotatedGlyphs(ui, text, cx, cy, angleRad, px, r, g, b);
    }

    private void rotatedGlyphs(UiRenderer ui, String text, float cx, float cy, float angle, float px,
                               float r, float g, float b) {
        float total = PixelFont.textWidth(text) * px;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float cursor = -total / 2f;
        float top = -PixelFont.CELL * px / 2f, bottom = PixelFont.CELL * px / 2f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int gw = PixelFont.glyphWidth(c);
            if (gw > 0) {
                int col = c % 16, row = c / 16;
                float u0 = (col * PixelFont.CELL) / (float) PixelFont.SHEET;
                float v0 = (row * PixelFont.CELL) / (float) PixelFont.SHEET;
                float u1 = (col * PixelFont.CELL + gw) / (float) PixelFont.SHEET;
                float v1 = (row * PixelFont.CELL + PixelFont.CELL) / (float) PixelFont.SHEET;
                float l = cursor, rr = cursor + gw * px;
                ui.drawTexturedQuadPoints(
                        cx + l * cos - top * sin, cy + l * sin + top * cos,
                        cx + rr * cos - top * sin, cy + rr * sin + top * cos,
                        cx + rr * cos - bottom * sin, cy + rr * sin + bottom * cos,
                        cx + l * cos - bottom * sin, cy + l * sin + bottom * cos,
                        new float[]{u0, v0, u1, v1}, textureId, r, g, b, 1f);
            }
            cursor += PixelFont.advance(c) * px;
        }
    }

    // ---- legacy API (HUD, inventory screens): multiplier -> whole framebuffer px per font px ----

    /** Whole framebuffer pixels per font pixel for a legacy scale multiplier. */
    public static float legacyPx(float scale) {
        return Math.max(1, Math.round(scale * LEGACY_PX_PER_SCALE));
    }

    /** Draws text and returns the width drawn. Bright text gets the vanilla shadow; dark panel
     *  labels (vanilla draws those unshadowed) do not. */
    public float drawText(UiRenderer ui, String text, float x, float y, float scale, float r, float g, float b, float a) {
        boolean shadow = (r + g + b) / 3f > 0.5f;
        return draw(ui, text, x, y, legacyPx(scale), r, g, b, a, shadow);
    }

    public float measureText(String text, float scale) {
        return width(text, legacyPx(scale));
    }

    public float lineHeight(float scale) {
        return PixelFont.LINE_HEIGHT * legacyPx(scale);
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }
}
