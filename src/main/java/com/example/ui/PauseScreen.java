package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class PauseScreen implements Screen {
    public interface Listener {
        void onBackToGame();

        void onQuitToTitle();

        void onQuitGame();
    }

    private final Listener listener;
    private Button resumeButton, quitToTitleButton, quitGameButton;

    public PauseScreen(Listener listener) {
        this.listener = listener;
    }

    private void layout(int w, int h) {
        float bw = 240, bh = 44, gap = 14;
        float cx = w / 2f - bw / 2f;
        float startY = h / 2f - (bh * 3 + gap * 2) / 2f;
        resumeButton = new Button(cx, startY, bw, bh, "Back to Game");
        quitToTitleButton = new Button(cx, startY + bh + gap, bw, bh, "Save & Quit to Title");
        quitGameButton = new Button(cx, startY + (bh + gap) * 2, bw, bh, "Quit Game");
    }

    @Override
    public void update(float dt, Input input, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        double mx = input.getMouseX(), my = input.getMouseY();
        resumeButton.updateHover(mx, my);
        quitToTitleButton.updateHover(mx, my);
        quitGameButton.updateHover(mx, my);
        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (resumeButton.contains(mx, my)) listener.onBackToGame();
            else if (quitToTitleButton.contains(mx, my)) listener.onQuitToTitle();
            else if (quitGameButton.contains(mx, my)) listener.onQuitGame();
        }
    }

    @Override
    public void render(UiRenderer ui, Font font, int screenWidth, int screenHeight) {
        // See TitleScreen.render() - a mid-frame screen switch can call render() before
        // this screen's own update() has run, so layout() must be safe to call from here too.
        layout(screenWidth, screenHeight);

        ui.drawColorQuad(0, 0, screenWidth, screenHeight, 0, 0, 0, 0.55f);

        String title = "Game Paused";
        float scale = 2f;
        float tw = font.measureText(title, scale);
        font.drawText(ui, title, screenWidth / 2f - tw / 2f, screenHeight * 0.25f, scale, 1f, 1f, 1f, 1f);

        resumeButton.render(ui, font, false);
        quitToTitleButton.render(ui, font, false);
        quitGameButton.render(ui, font, false);
    }
}
