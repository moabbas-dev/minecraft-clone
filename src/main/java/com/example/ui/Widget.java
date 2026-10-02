package com.example.ui;

/** A clickable/draggable element positioned in logical GUI pixels. */
public abstract class Widget {
    public int x, y, w, h;
    public boolean active = true;
    public boolean visible = true;

    protected Widget(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(float mx, float my) {
        return visible && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public abstract void render(GuiContext g);

    /** Returns true if the click was consumed. */
    public boolean mouseClicked(float mx, float my) {
        return false;
    }

    public void mouseDragged(float mx, float my) {
    }

    public void mouseReleased() {
    }

    /** Typed text (only meaningful for the focused widget). */
    public void charsTyped(String s) {
    }

    public void keyPressed(int glfwKey) {
    }

    public boolean isFocusable() {
        return false;
    }

    public void setFocused(boolean focused) {
    }
}
