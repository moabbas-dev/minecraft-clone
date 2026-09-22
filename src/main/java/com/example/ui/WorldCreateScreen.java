package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;
import com.example.world.WorldSettings;

import static org.lwjgl.glfw.GLFW.*;

public class WorldCreateScreen implements Screen {
    public interface Listener {
        void onCreateWorld(WorldSettings settings);

        void onBack();
    }

    private final Listener listener;
    private final StringBuilder worldName = new StringBuilder("New World");
    private WorldSettings.WorldType selectedType = null;

    private Button survivalCard, flatCard, createButton, backButton;

    public WorldCreateScreen(Listener listener) {
        this.listener = listener;
    }

    /** Reset to defaults each time the screen is entered fresh. */
    public void reset() {
        worldName.setLength(0);
        worldName.append("New World");
        selectedType = null;
    }

    private void layout(int w, int h) {
        float cardW = 220, cardH = 140, gap = 24;
        float totalW = cardW * 2 + gap;
        float startX = w / 2f - totalW / 2f;
        float cardY = h / 2f - cardH / 2f;
        survivalCard = new Button(startX, cardY, cardW, cardH, "Survival");
        flatCard = new Button(startX + cardW + gap, cardY, cardW, cardH, "Flat");
        createButton = new Button(w / 2f - 110, cardY + cardH + 50, 220, 44, "Create World");
        backButton = new Button(20, h - 56, 100, 36, "Back");
    }

    @Override
    public void update(float dt, Input input, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);

        String typed = input.consumeTypedChars();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (worldName.length() < 24 && c >= 32 && c < 127) worldName.append(c);
        }
        if (input.consumeKeyPressed(GLFW_KEY_BACKSPACE) && worldName.length() > 0) {
            worldName.deleteCharAt(worldName.length() - 1);
        }

        double mx = input.getMouseX(), my = input.getMouseY();
        survivalCard.updateHover(mx, my);
        flatCard.updateHover(mx, my);
        createButton.updateHover(mx, my);
        backButton.updateHover(mx, my);

        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (survivalCard.contains(mx, my)) selectedType = WorldSettings.WorldType.SURVIVAL;
            else if (flatCard.contains(mx, my)) selectedType = WorldSettings.WorldType.FLAT;
            else if (backButton.contains(mx, my)) listener.onBack();
            else if (createButton.contains(mx, my) && selectedType != null) {
                String name = worldName.length() == 0 ? "New World" : worldName.toString();
                listener.onCreateWorld(new WorldSettings(name, selectedType));
            }
        }
    }

    @Override
    public void render(UiRenderer ui, Font font, int screenWidth, int screenHeight) {
        // See TitleScreen.render() - a mid-frame screen switch can call render() before
        // this screen's own update() has run, so layout() must be safe to call from here too.
        layout(screenWidth, screenHeight);

        ui.drawColorQuad(0, 0, screenWidth, screenHeight, 0.15f, 0.15f, 0.19f, 1f);

        String title = "Create New World";
        float titleScale = 2.2f;
        float tw = font.measureText(title, titleScale);
        font.drawText(ui, title, screenWidth / 2f - tw / 2f, screenHeight * 0.1f, titleScale, 1f, 1f, 1f, 1f);

        String nameLabel = "Name: " + worldName + "_";
        float nameScale = 1.3f;
        float nw = font.measureText(nameLabel, nameScale);
        font.drawText(ui, nameLabel, screenWidth / 2f - nw / 2f, screenHeight * 0.26f, nameScale, 0.9f, 0.9f, 0.9f, 1f);

        survivalCard.render(ui, font, selectedType == WorldSettings.WorldType.SURVIVAL);
        flatCard.render(ui, font, selectedType == WorldSettings.WorldType.FLAT);

        float descScale = 0.95f;
        font.drawText(ui, "Rolling terrain,", survivalCard.x + 12, survivalCard.y + survivalCard.height - 46, descScale, 0.85f, 0.85f, 0.85f, 1f);
        font.drawText(ui, "oceans and ores", survivalCard.x + 12, survivalCard.y + survivalCard.height - 26, descScale, 0.85f, 0.85f, 0.85f, 1f);
        font.drawText(ui, "Superflat", flatCard.x + 12, flatCard.y + flatCard.height - 46, descScale, 0.85f, 0.85f, 0.85f, 1f);
        font.drawText(ui, "starter world", flatCard.x + 12, flatCard.y + flatCard.height - 26, descScale, 0.85f, 0.85f, 0.85f, 1f);

        boolean canCreate = selectedType != null;
        createButton.render(ui, font, false);
        if (!canCreate) {
            ui.drawColorQuad(createButton.x, createButton.y, createButton.width, createButton.height, 0, 0, 0, 0.55f);
        }
        backButton.render(ui, font, false);
    }
}
