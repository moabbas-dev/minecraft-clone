package com.example.ui;

/** Vanilla "You died!" screen: red-tinted overlay, big title, score line, Respawn and Title Screen. */
public class DeathScreen extends McScreen {
    public interface Listener {
        void onRespawn();

        void onTitleScreen();
    }

    private final Listener listener;

    public DeathScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
    }

    @Override
    protected void init() {
        int cx = guiW / 2, h4 = guiH / 4;
        widgets.add(new Button(cx - 100, h4 + 72, 200, 20, "Respawn", listener::onRespawn));
        widgets.add(new Button(cx - 100, h4 + 96, 200, 20, "Title Screen", listener::onTitleScreen));
    }

    @Override
    protected void onEscape() {
        // vanilla: Escape does nothing here - you must choose
    }

    @Override
    protected void drawBackground(GuiContext g) {
        g.gradient(0, 0, g.w, g.h, 0x500000, 0.38f, 0x803030, 0.63f);
    }

    @Override
    protected void drawContent(GuiContext g) {
        g.font.draw(g.ui, "You died!", g.w / 2f - g.font.width("You died!", 2f) / 2f, 30, 2f, 0xFFFFFF, true);
        String score = "Score: ";
        String value = "0";
        float total = g.textWidth(score) + g.textWidth(value);
        g.text(score, g.w / 2f - total / 2f, 100, 0xFFFFFF);
        g.text(value, g.w / 2f - total / 2f + g.textWidth(score), 100, 0xFFFF55);
    }
}
