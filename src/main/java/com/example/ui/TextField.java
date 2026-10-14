package com.example.ui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;

/** Single-line text input: black box, white (focused) or gray border, blinking cursor. */
public class TextField extends Widget {
    private final StringBuilder text;
    private final int maxLength;
    private boolean focused;

    public TextField(int x, int y, int w, int h, StringBuilder backing, int maxLength) {
        super(x, y, w, h);
        this.text = backing;
        this.maxLength = maxLength;
    }

    public String value() {
        return text.toString();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean mouseClicked(float mx, float my) {
        boolean hit = active && contains(mx, my);
        focused = hit;
        return hit;
    }

    @Override
    public void charsTyped(String s) {
        if (!focused || !active) return;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (text.length() < maxLength && c >= 32 && c < 127) text.append(c);
        }
    }

    @Override
    public void keyPressed(int key) {
        if (focused && key == GLFW_KEY_BACKSPACE && text.length() > 0) text.deleteCharAt(text.length() - 1);
    }

    @Override
    public void render(GuiContext g) {
        if (!g.nineSlice(focused ? "widget/text_field_highlighted" : "widget/text_field", x, y, w, h, 1)) {
            g.fill(x, y, w, h, focused ? 0xFFFFFF : 0xA0A0A0);
            g.fill(x + 1, y + 1, w - 2, h - 2, 0x000000);
        }
        String shown = text.toString();
        while (shown.length() > 1 && g.textWidth(shown) > w - 10) shown = shown.substring(1);
        float tx = x + 4, ty = y + (h - 8) / 2f;
        float adv = g.text(shown, tx, ty, active ? 0xE0E0E0 : 0x707070);
        if (focused && ((int) (g.time * 2f)) % 2 == 0) g.fill(tx + adv, ty + 9, 5, 1, 0xE0E0E0);
    }
}
