package com.example.ui;

/** Vanilla in-game "Game Menu" (1.8 layout, rows 24px apart, 98px halves 4px apart). */
public class PauseScreen extends McScreen {
    public interface Listener {
        void onBackToGame();

        void onOptions();

        void onQuitToTitle();
    }

    private final Listener listener;

    public PauseScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
    }

    @Override
    protected void init() {
        int cx = guiW / 2, j = -16, h4 = guiH / 4;
        widgets.add(new Button(cx - 100, h4 + 24 + j, 200, 20, "Back to Game", listener::onBackToGame));
        Button adv = new Button(cx - 100, h4 + 48 + j, 98, 20, "Advancements", () -> {
        });
        adv.active = false;
        widgets.add(adv);
        Button stats = new Button(cx + 2, h4 + 48 + j, 98, 20, "Statistics", () -> {
        });
        stats.active = false;
        widgets.add(stats);
        widgets.add(new Button(cx - 100, h4 + 96 + j, 98, 20, "Options...", listener::onOptions));
        Button lan = new Button(cx + 2, h4 + 96 + j, 98, 20, "Open to LAN", () -> {
        });
        lan.active = false;
        widgets.add(lan);
        widgets.add(new Button(cx - 100, h4 + 120 + j, 200, 20, "Save and Quit to Title", listener::onQuitToTitle));
    }

    @Override
    protected void onEscape() {
        listener.onBackToGame();
    }

    @Override
    protected void drawBackground(GuiContext g) {
        g.ingameOverlay();
    }

    @Override
    protected void drawContent(GuiContext g) {
        g.textCentered("Game Menu", g.w / 2f, 40, 0xFFFFFF);
    }
}
