package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Base for every menu screen. Lays out in logical GUI pixels (see {@link GuiScale}) and renders
 * through a logical-size UI projection, so the same layout code is correct at any window size or
 * GUI-scale setting and re-layouts automatically when either changes. A screen can open a child
 * screen on top of itself (Options -> Video Settings, Select World -> Edit World ...); Escape in the
 * child closes it and returns to the parent, which is vanilla's screen-stack behavior.
 */
public abstract class McScreen implements Screen {
    protected final GuiEnv env;
    protected int guiW, guiH, scale = 1;
    protected float mx, my;
    protected final List<Widget> widgets = new ArrayList<>();
    protected McScreen parent, child;

    private int lastW = -1, lastH = -1, lastScale = -1;
    private boolean dirty = true;
    private float time;

    protected McScreen(GuiEnv env) {
        this.env = env;
    }

    /** Builds the widgets for the current {@link #guiW}/{@link #guiH}. Called on open, resize, GUI-scale change, and {@link #invalidate()}. */
    protected abstract void init();

    protected void tick(float dt, Input input) {
    }

    protected void drawBackground(GuiContext g) {
        g.dirtBackground();
    }

    /** Titles and labels: drawn above the background, below the widgets. */
    protected void drawContent(GuiContext g) {
    }

    /** Overlays drawn after the widgets (tooltips). */
    protected void drawForeground(GuiContext g) {
    }

    protected void onEscape() {
        if (parent != null) parent.closeChild();
    }

    protected void onEnter() {
    }

    protected void onScroll(double delta) {
    }

    /** Called each time this screen becomes visible again after a child closed. */
    protected void onResume() {
    }

    public void invalidate() {
        dirty = true;
    }

    public void openChild(McScreen c) {
        c.parent = this;
        child = c;
        c.invalidate();
    }

    public void closeChild() {
        child = null;
        invalidate();
        onResume();
    }

    /** Frontmost screen of this stack (for tests/debug). */
    public McScreen top() {
        return child == null ? this : child.top();
    }

    private void layout(int fbW, int fbH) {
        int s = GuiScale.compute(fbW, fbH, env.options.guiScale);
        if (dirty || s != lastScale || fbW != lastW || fbH != lastH) {
            scale = s;
            guiW = GuiScale.logical(fbW, s);
            guiH = GuiScale.logical(fbH, s);
            widgets.clear();
            init();
            lastW = fbW;
            lastH = fbH;
            lastScale = s;
            dirty = false;
        }
    }

    @Override
    public void update(float dt, Input input, int fbW, int fbH) {
        if (child != null) {
            child.update(dt, input, fbW, fbH);
            return;
        }
        time += dt;
        layout(fbW, fbH);
        mx = (float) (input.getMouseX() / scale);
        my = (float) (input.getMouseY() / scale);
        tick(dt, input);

        String typed = input.consumeTypedChars();
        boolean backspace = input.consumeKeyPressed(GLFW_KEY_BACKSPACE);
        for (Widget w : widgets) {
            if (!typed.isEmpty()) w.charsTyped(typed);
            if (backspace) w.keyPressed(GLFW_KEY_BACKSPACE);
        }
        if (input.consumeKeyPressed(GLFW_KEY_ENTER)) onEnter();
        if (input.consumeKeyPressed(GLFW_KEY_ESCAPE)) {
            onEscape();
            return;
        }
        double scroll = input.getScrollDelta();
        if (scroll != 0) onScroll(scroll);

        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            Widget hit = null;
            for (int i = widgets.size() - 1; i >= 0 && hit == null; i--) {
                Widget w = widgets.get(i);
                if (w.visible && w.mouseClicked(mx, my)) hit = w;
            }
            for (Widget w : widgets) if (w.isFocusable() && w != hit) w.setFocused(false);
            if (child != null || dirty) return; // the click navigated away or rebuilt the layout
        }
        if (input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) {
            for (Widget w : widgets) w.mouseDragged(mx, my);
        } else {
            for (Widget w : widgets) w.mouseReleased();
        }
    }

    @Override
    public void render(UiRenderer ui, Font font, int fbW, int fbH) {
        if (child != null) {
            child.render(ui, font, fbW, fbH);
            return;
        }
        layout(fbW, fbH);
        ui.begin(fbW / (float) scale, fbH / (float) scale);
        GuiContext g = new GuiContext(ui, font, env, guiW, guiH, mx, my, time);
        drawBackground(g);
        drawContent(g);
        for (Widget w : widgets) if (w.visible) w.render(g);
        drawForeground(g);
        ui.begin(fbW, fbH);
    }
}
