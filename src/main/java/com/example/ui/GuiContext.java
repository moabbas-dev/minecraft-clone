package com.example.ui;

import com.example.engine.Font;
import com.example.engine.UiRenderer;

/** Per-frame drawing helpers in logical GUI pixels (1 unit = 1 vanilla GUI pixel). */
public final class GuiContext {
    public final UiRenderer ui;
    public final Font font;
    public final GuiEnv env;
    public final int w, h;
    public final float mx, my;
    public final float time;

    public GuiContext(UiRenderer ui, Font font, GuiEnv env, int w, int h, float mx, float my, float time) {
        this.ui = ui;
        this.font = font;
        this.env = env;
        this.w = w;
        this.h = h;
        this.mx = mx;
        this.my = my;
        this.time = time;
    }

    public void fill(float x, float y, float fw, float fh, int rgb, float alpha) {
        ui.drawColorQuad(x, y, fw, fh, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    public void fill(float x, float y, float fw, float fh, int rgb) {
        fill(x, y, fw, fh, rgb, 1f);
    }

    /** Vertical gradient (top color to bottom color, with alpha). */
    public void gradient(float x, float y, float fw, float fh, int topRgb, float topA, int botRgb, float botA) {
        int steps = Math.max(1, Math.min(64, (int) fh));
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            int r = lerp((topRgb >> 16) & 255, (botRgb >> 16) & 255, t);
            int g = lerp((topRgb >> 8) & 255, (botRgb >> 8) & 255, t);
            int b = lerp(topRgb & 255, botRgb & 255, t);
            float a = topA + (botA - topA) * t;
            fill(x, y + fh * i / steps, fw, fh / steps + 0.01f, (r << 16) | (g << 8) | b, a);
        }
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    /** Text with the vanilla shadow, left edge at x, top at y. */
    public float text(String s, float x, float y, int rgb) {
        return font.draw(ui, s, x, y, 1f, rgb, true);
    }

    public float textNoShadow(String s, float x, float y, int rgb) {
        return font.draw(ui, s, x, y, 1f, rgb, false);
    }

    public void textCentered(String s, float cx, float y, int rgb) {
        font.drawCentered(ui, s, cx, y, 1f, rgb, true);
    }

    public int textWidth(String s) {
        return font.width(s);
    }

    /** Tiled dirt menu background, darkened like vanilla's 0x404040 tint. */
    public void dirtBackground() {
        ui.drawTiled(env.dirtTexture, env.dirtUv, 0, 0, w, h, 32f, 0.25f, 0.25f, 0.25f, 1f);
    }

    /** Dim overlay behind in-game menus (vanilla's dark translucent gradient). */
    public void ingameOverlay() {
        gradient(0, 0, w, h, 0x101010, 0.75f, 0x101010, 0.82f);
    }

    /** Dirt strip, for list-screen headers and footers. */
    public void dirtStrip(float x, float y, float sw, float sh) {
        ui.drawTiled(env.dirtTexture, env.dirtUv, x, y, sw, sh, 32f, 0.25f, 0.25f, 0.25f, 1f);
    }
}
