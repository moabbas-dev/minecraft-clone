package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class TitleScreen implements Screen {
    public interface Listener {
        void onPlay();

        void onQuit();
    }

    private final Listener listener;
    private Button playButton;
    private Button quitButton;

    public TitleScreen(Listener listener) {
        this.listener = listener;
    }

    private void layout(int w, int h) {
        float bw = 220, bh = 44;
        float cx = w / 2f - bw / 2f;
        playButton = new Button(cx, h / 2f - 10, bw, bh, "Play");
        quitButton = new Button(cx, h / 2f + 46, bw, bh, "Quit");
    }

    @Override
    public void update(float dt, Input input, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        double mx = input.getMouseX(), my = input.getMouseY();
        playButton.updateHover(mx, my);
        quitButton.updateHover(mx, my);
        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (playButton.contains(mx, my)) listener.onPlay();
            else if (quitButton.contains(mx, my)) listener.onQuit();
        }
    }

    @Override
    public void render(UiRenderer ui, Font font, int screenWidth, int screenHeight) {
        // A screen transition can land here before this screen's own update() has run
        // this frame (the switch happens mid-frame inside the previous screen's update),
        // so layout() must be idempotent and safe to call from render() too.
        layout(screenWidth, screenHeight);

        ui.drawColorQuad(0, 0, screenWidth, screenHeight * 0.55f, 0.4f, 0.62f, 0.88f, 1f);
        ui.drawColorQuad(0, screenHeight * 0.55f, screenWidth, screenHeight * 0.45f, 0.5f, 0.7f, 0.38f, 1f);

        String title = "MINECRAFT 1.8 CLONE";
        float scale = 3f;
        float tw = font.measureText(title, scale);
        font.drawText(ui, title, screenWidth / 2f - tw / 2f, screenHeight * 0.18f, scale, 1f, 1f, 1f, 1f);

        String subtitle = "A from-scratch voxel engine";
        float subScale = 1.1f;
        float sw = font.measureText(subtitle, subScale);
        font.drawText(ui, subtitle, screenWidth / 2f - sw / 2f, screenHeight * 0.18f + font.lineHeight(scale) + 8, subScale, 0.9f, 0.95f, 1f, 1f);

        playButton.render(ui, font, false);
        quitButton.render(ui, font, false);
    }
}
