package com.example.ui;

import java.util.function.Consumer;
import java.util.function.Function;

/** A button-sized slider: value in [0,1], label rebuilt from the value, 8px knob. */
public class Slider extends Widget {
    private float value;
    private final Function<Float, String> label;
    private final Consumer<Float> onChange;
    private boolean dragging;

    public Slider(int x, int y, int w, int h, float value, Function<Float, String> label, Consumer<Float> onChange) {
        super(x, y, w, h);
        this.value = Math.max(0f, Math.min(1f, value));
        this.label = label;
        this.onChange = onChange;
    }

    public float value() {
        return value;
    }

    public void setValue(float v) {
        value = Math.max(0f, Math.min(1f, v));
    }

    @Override
    public boolean mouseClicked(float mx, float my) {
        if (!active || !contains(mx, my)) return false;
        dragging = true;
        update(mx);
        return true;
    }

    @Override
    public void mouseDragged(float mx, float my) {
        if (dragging) update(mx);
    }

    @Override
    public void mouseReleased() {
        dragging = false;
    }

    private void update(float mx) {
        value = Math.max(0f, Math.min(1f, (mx - (x + 4)) / (w - 8)));
        onChange.accept(value);
    }

    @Override
    public void render(GuiContext g) {
        boolean hovered = active && (contains(g.mx, g.my) || dragging);
        int kx0 = x + Math.round(value * (w - 8));
        if (g.nineSlice(hovered ? "widget/slider_highlighted" : "widget/slider", x, y, w, h, 1)
                && g.nineSlice(hovered ? "widget/slider_handle_highlighted" : "widget/slider_handle", kx0, y, 8, h, 2, 2, 2, 3)) {
            g.textCentered(label.apply(value), x + w / 2f, y + (h - 8) / 2f, active ? 0xFFFFFF : 0xA0A0A0);
            return;
        }
        Button.drawFrame(g, x, y, w, h, 0x2B2B2B, false);
        int kx = x + Math.round(value * (w - 8));
        Button.drawFrame(g, kx, y, 8, h, active ? (hovered ? Button.HOVER_FILL : Button.NORMAL_FILL) : Button.DISABLED_FILL, active);
        g.textCentered(label.apply(value), x + w / 2f, y + (h - 8) / 2f, !active ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0);
    }
}
