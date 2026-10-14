package com.example.ui;

/**
 * Shared, long-lived inputs every menu screen needs: the user options and the real GUI sprites. {@code sprites} is null
 * in headless tests, where the screens fall back to plain flat drawing.
 */
public final class GuiEnv {
    public final GameOptions options;
    public final UiSprites sprites;

    public GuiEnv(GameOptions options, UiSprites sprites) {
        this.options = options;
        this.sprites = sprites;
    }

    /** Headless (no sprites). */
    public GuiEnv(GameOptions options) {
        this(options, null);
    }
}
