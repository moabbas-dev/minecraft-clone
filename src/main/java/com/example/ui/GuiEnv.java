package com.example.ui;

/** Shared, long-lived inputs every menu screen needs: the user options and the dirt background texture. */
public final class GuiEnv {
    public final GameOptions options;
    public final int dirtTexture;
    public final float[] dirtUv;

    public GuiEnv(GameOptions options, int dirtTexture, float[] dirtUv) {
        this.options = options;
        this.dirtTexture = dirtTexture;
        this.dirtUv = dirtUv;
    }
}
