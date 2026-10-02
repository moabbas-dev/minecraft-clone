package com.example.ui;

import java.util.function.Supplier;

/** Vanilla-style push button (200x20 wide; 150/98/72-wide variants are used by the screens). */
public class Button extends Widget {
    public static final int NORMAL_FILL = 0x707070, HOVER_FILL = 0x6F7DC8, DISABLED_FILL = 0x4A4A4A;
    public static final int BEVEL_LIGHT = 0xA0A0A0, BEVEL_DARK = 0x3C3C3C;

    private final Supplier<String> label;
    private final Runnable action;

    public Button(int x, int y, int w, int h, String label, Runnable action) {
        this(x, y, w, h, () -> label, action);
    }

    public Button(int x, int y, int w, int h, Supplier<String> label, Runnable action) {
        super(x, y, w, h);
        this.label = label;
        this.action = action;
    }

    public String label() {
        return label.get();
    }

    @Override
    public boolean mouseClicked(float mx, float my) {
        if (!active || !contains(mx, my)) return false;
        action.run();
        return true;
    }

    @Override
    public void render(GuiContext g) {
        boolean hovered = active && contains(g.mx, g.my);
        drawFrame(g, x, y, w, h, active ? (hovered ? HOVER_FILL : NORMAL_FILL) : DISABLED_FILL, active);
        int color = !active ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0;
        g.textCentered(label.get(), x + w / 2f, y + (h - 8) / 2f, color);
    }

    /** Black outline, light top/left bevel, dark bottom bevel - the widgets.png button look. */
    static void drawFrame(GuiContext g, float x, float y, float w, float h, int fill, boolean bevel) {
        g.fill(x, y, w, h, 0x000000);
        g.fill(x + 1, y + 1, w - 2, h - 2, fill);
        if (bevel) {
            g.fill(x + 1, y + 1, w - 2, 1, BEVEL_LIGHT, 0.55f);
            g.fill(x + 1, y + 1, 1, h - 2, BEVEL_LIGHT, 0.35f);
            g.fill(x + 1, y + h - 3, w - 2, 2, BEVEL_DARK, 0.7f);
        }
    }
}
