package com.example.engine;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Monospace bitmap font for UI text. Rasterizes ASCII 32-126 from Java's built-in
 * "Monospaced" AWT font into a glyph atlas texture (same approach as TextureAtlas),
 * then hands quads to a UiRenderer to draw. No external font/image files involved.
 */
public class Font {
    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 126;
    private static final int COLS = 16;
    private static final int ROWS = (LAST_CHAR - FIRST_CHAR + 1 + COLS - 1) / COLS;

    private final int textureId;
    private final int cellW;
    private final int cellH;
    private final int atlasW;
    private final int atlasH;

    public Font() {
        java.awt.Font awtFont = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 28);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probeG = probe.createGraphics();
        probeG.setFont(awtFont);
        FontMetrics fm = probeG.getFontMetrics();
        int advance = fm.charWidth('M');
        probeG.dispose();

        cellW = advance + 2;
        cellH = fm.getHeight() + 2;
        atlasW = COLS * cellW;
        atlasH = ROWS * cellH;

        BufferedImage atlasImg = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(awtFont);
        g.setColor(Color.WHITE);
        for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
            int idx = c - FIRST_CHAR;
            int col = idx % COLS;
            int row = idx / COLS;
            int x = col * cellW + 1;
            int y = row * cellH + fm.getAscent() + 1;
            g.drawString(String.valueOf((char) c), x, y);
        }
        g.dispose();

        textureId = upload(atlasImg);
    }

    private int upload(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        ByteBuffer buffer = memAlloc(w * h * 4);
        // No vertical flip - buffer row i must equal image row i so it matches the
        // row-index-based UV math in drawText() below (see TextureAtlas for why a
        // mismatched flip there previously scrambled block textures).
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
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        memFree(buffer);
        return id;
    }

    /** Draws text and returns the total pixel width drawn. */
    public float drawText(UiRenderer renderer, String text, float x, float y, float scale, float r, float g, float b, float a) {
        float cursorX = x;
        float drawW = cellW * scale;
        float drawH = cellH * scale;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= FIRST_CHAR && c <= LAST_CHAR) {
                int idx = c - FIRST_CHAR;
                int col = idx % COLS;
                int row = idx / COLS;
                float u0 = (float) (col * cellW) / atlasW;
                float v0 = (float) (row * cellH) / atlasH;
                float u1 = (float) ((col + 1) * cellW) / atlasW;
                float v1 = (float) ((row + 1) * cellH) / atlasH;
                renderer.drawTexturedQuad(cursorX, y, drawW, drawH, new float[]{u0, v0, u1, v1}, textureId, r, g, b, a);
            }
            cursorX += drawW;
        }
        return cursorX - x;
    }

    public float measureText(String text, float scale) {
        return text.length() * cellW * scale;
    }

    public float lineHeight(float scale) {
        return cellH * scale;
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }
}
