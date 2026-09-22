package com.example.ui;

import com.example.engine.Font;
import com.example.engine.UiRenderer;

/** A clickable rectangle with a label, shared by every menu screen. */
public class Button {
    public final float x, y, width, height;
    public final String label;
    private boolean hovered;

    public Button(float x, float y, float width, float height, String label) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
    }

    public void updateHover(double mouseX, double mouseY) {
        hovered = contains(mouseX, mouseY);
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render(UiRenderer ui, Font font, boolean selected) {
        float shade = (hovered || selected) ? 0.55f : 0.32f;
        ui.drawColorQuad(x, y, width, height, shade, shade, shade, 0.88f);
        float borderShade = selected ? 1f : 0.7f;
        ui.drawColorQuad(x, y, width, 2, borderShade, borderShade, borderShade, 0.9f);
        ui.drawColorQuad(x, y + height - 2, width, 2, 0.05f, 0.05f, 0.05f, 0.6f);

        float scale = 1.4f;
        float textW = font.measureText(label, scale);
        float textX = x + (width - textW) / 2f;
        float textY = y + (height - font.lineHeight(scale)) / 2f;
        font.drawText(ui, label, textX, textY, scale, 1f, 1f, 1f, 1f);
    }
}
