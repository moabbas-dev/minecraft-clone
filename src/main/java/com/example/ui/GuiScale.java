package com.example.ui;

/**
 * Minecraft's GUI scale rule (ScaledResolution): the largest integer scale for which the scaled
 * screen is still at least 320x240, floored at 1; a manual setting (1..) only caps that, and 0
 * means Auto. Menus lay out in the resulting "logical" pixels and are magnified uniformly.
 */
public final class GuiScale {
    private GuiScale() {
    }

    public static final int BASE_W = 320, BASE_H = 240;

    public static int maxScale(int fbW, int fbH) {
        int s = 1;
        while (s < 16 && fbW / (s + 1) >= BASE_W && fbH / (s + 1) >= BASE_H) s++;
        return s;
    }

    /** @param setting 0 = Auto, otherwise the user's chosen cap. */
    public static int compute(int fbW, int fbH, int setting) {
        int max = maxScale(fbW, fbH);
        return setting <= 0 ? max : Math.max(1, Math.min(setting, max));
    }

    /** Logical (scaled) screen size, rounded up like vanilla. */
    public static int logical(int fb, int scale) {
        return (fb + scale - 1) / scale;
    }
}
