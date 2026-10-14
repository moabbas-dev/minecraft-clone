package com.example.ui;

import com.example.engine.HudTextures;
import com.example.engine.UiRenderer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The real GUI textures the menus are built from ({@code textures/gui/sprites/...} and the plain files under
 * {@code textures/gui/}), loaded on first use and drawn the way vanilla draws them: 9-slice sprites keep their border and
 * stretch the middle, backgrounds tile. Every draw method returns false when the file is missing, so a screen can fall back
 * to something plain instead of crashing. Needs a GL context.
 */
public final class UiSprites {
    private static final String ROOT = "textures/gui/";
    private final Map<String, HudTextures.Sprite> cache = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    /** A sprite by its name under {@code textures/gui/sprites/}, e.g. {@code widget/button}; null when absent. */
    public HudTextures.Sprite sprite(String name) {
        return load(ROOT + "sprites/" + name + ".png");
    }

    /** A plain GUI texture by name under {@code textures/gui/}, e.g. {@code menu_background}; null when absent. */
    public HudTextures.Sprite texture(String name) {
        return load(ROOT + name + ".png");
    }

    private HudTextures.Sprite load(String path) {
        if (missing.contains(path)) return null;
        HudTextures.Sprite s = cache.get(path);
        if (s == null) {
            s = HudTextures.loadFile(path);
            if (s == null) {
                missing.add(path);
                return null;
            }
            cache.put(path, s);
        }
        return s;
    }

    /** The patch rectangles of a 9-slice: {destination x0,y0,x1,y1, uv u0,v0,u1,v1} for each non-empty cell. */
    static float[][] nineSliceCells(float x, float y, float w, float h, float sw, float sh, float l, float t, float r, float b) {
        // borders never exceed half of the destination, so a small widget keeps both of its edges
        l = Math.min(l, w / 2f);
        r = Math.min(r, w / 2f);
        t = Math.min(t, h / 2f);
        b = Math.min(b, h / 2f);
        float[] xs = {x, x + l, x + w - r, x + w}, ys = {y, y + t, y + h - b, y + h};
        float[] us = {0f, l / sw, 1f - r / sw, 1f}, vs = {0f, t / sh, 1f - b / sh, 1f};
        java.util.List<float[]> cells = new java.util.ArrayList<>(9);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                if (xs[i + 1] - xs[i] <= 0f || ys[j + 1] - ys[j] <= 0f) continue;
                cells.add(new float[]{xs[i], ys[j], xs[i + 1], ys[j + 1], us[i], vs[j], us[i + 1], vs[j + 1]});
            }
        }
        return cells.toArray(new float[0][]);
    }

    /** Draws a 9-slice sprite with the given border widths (left, top, right, bottom, in sprite pixels = GUI pixels). */
    public boolean nineSlice(UiRenderer ui, String name, float x, float y, float w, float h, float l, float t, float r, float b) {
        HudTextures.Sprite s = sprite(name);
        if (s == null) return false;
        for (float[] c : nineSliceCells(x, y, w, h, s.w(), s.h(), l, t, r, b)) {
            ui.drawTexturedQuad(c[0], c[1], c[2] - c[0], c[3] - c[1], new float[]{c[4], c[5], c[6], c[7]}, s.id(), 1f, 1f, 1f, 1f);
        }
        return true;
    }

    public boolean nineSlice(UiRenderer ui, String name, float x, float y, float w, float h, float border) {
        return nineSlice(ui, name, x, y, w, h, border, border, border, border);
    }

    /** Fills a rectangle by repeating a GUI texture every {@code cell} units (partial edge tiles are cropped). */
    public boolean tile(UiRenderer ui, String textureName, float x, float y, float w, float h, float cell) {
        HudTextures.Sprite s = texture(textureName);
        if (s == null) return false;
        ui.drawTiled(s.id(), new float[]{0f, 0f, 1f, 1f}, x, y, w, h, cell, 1f, 1f, 1f, 1f);
        return true;
    }

    /** Draws the rectangle {@code u0,v0..u1,v1} (0..1) of a GUI texture into {@code x,y,w,h}. */
    public boolean blit(UiRenderer ui, String textureName, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        HudTextures.Sprite s = texture(textureName);
        if (s == null) return false;
        ui.drawTexturedQuad(x, y, w, h, new float[]{u0, v0, u1, v1}, s.id(), 1f, 1f, 1f, 1f);
        return true;
    }
}
