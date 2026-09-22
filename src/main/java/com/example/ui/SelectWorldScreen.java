package com.example.ui;

import com.example.engine.Font;
import com.example.engine.Input;
import com.example.engine.UiRenderer;
import com.example.world.WorldIO;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class SelectWorldScreen implements Screen {
    public interface Listener {
        void onLoadWorld(WorldIO.SavedWorld world);

        void onCreateNew();

        void onBack();
    }

    private final Listener listener;
    private List<WorldIO.SavedWorld> worlds = new ArrayList<>();
    private final List<Button> rowPlayButtons = new ArrayList<>();
    private final List<Button> rowDeleteButtons = new ArrayList<>();
    private Button createButton, backButton;
    private String pendingDelete = null;

    public SelectWorldScreen(Listener listener) {
        this.listener = listener;
    }

    /** Re-scans saves/ - call whenever this screen is entered. */
    public void refresh() {
        worlds = WorldIO.listSavedWorlds();
        pendingDelete = null;
    }

    private void layout(int w, int h) {
        rowPlayButtons.clear();
        rowDeleteButtons.clear();
        float rowW = 480, rowH = 44, gap = 8;
        float startX = w / 2f - rowW / 2f;
        float startY = h * 0.18f;
        for (int i = 0; i < worlds.size(); i++) {
            float y = startY + i * (rowH + gap);
            rowPlayButtons.add(new Button(startX, y, rowW - 90, rowH, worlds.get(i).displayName()));
            rowDeleteButtons.add(new Button(startX + rowW - 84, y, 84, rowH, "Delete"));
        }
        createButton = new Button(w / 2f - 110, h - 110, 220, 44, "Create New World");
        backButton = new Button(20, h - 56, 100, 36, "Back");
    }

    @Override
    public void update(float dt, Input input, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        double mx = input.getMouseX(), my = input.getMouseY();
        for (Button b : rowPlayButtons) b.updateHover(mx, my);
        for (Button b : rowDeleteButtons) b.updateHover(mx, my);
        createButton.updateHover(mx, my);
        backButton.updateHover(mx, my);

        if (input.consumeMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (createButton.contains(mx, my)) {
                listener.onCreateNew();
                return;
            }
            if (backButton.contains(mx, my)) {
                listener.onBack();
                return;
            }
            for (int i = 0; i < rowDeleteButtons.size(); i++) {
                if (rowDeleteButtons.get(i).contains(mx, my)) {
                    String folder = worlds.get(i).folderName();
                    if (folder.equals(pendingDelete)) {
                        WorldIO.deleteWorld(folder);
                        refresh();
                    } else {
                        pendingDelete = folder; // first click arms it, second click confirms
                    }
                    return;
                }
            }
            for (int i = 0; i < rowPlayButtons.size(); i++) {
                if (rowPlayButtons.get(i).contains(mx, my)) {
                    listener.onLoadWorld(worlds.get(i));
                    return;
                }
            }
        }
    }

    @Override
    public void render(UiRenderer ui, Font font, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);

        ui.drawColorQuad(0, 0, screenWidth, screenHeight, 0.15f, 0.15f, 0.19f, 1f);

        String title = "Select World";
        float titleScale = 2.2f;
        float tw = font.measureText(title, titleScale);
        font.drawText(ui, title, screenWidth / 2f - tw / 2f, screenHeight * 0.06f, titleScale, 1f, 1f, 1f, 1f);

        if (worlds.isEmpty()) {
            String msg = "No saved worlds yet - create one below.";
            float ms = 1.1f;
            float mw = font.measureText(msg, ms);
            font.drawText(ui, msg, screenWidth / 2f - mw / 2f, screenHeight * 0.18f, ms, 0.8f, 0.8f, 0.8f, 1f);
        }

        for (int i = 0; i < worlds.size(); i++) {
            WorldIO.SavedWorld w = worlds.get(i);
            rowPlayButtons.get(i).render(ui, font, false);
            Button del = rowDeleteButtons.get(i);
            boolean armed = w.folderName().equals(pendingDelete);
            ui.drawColorQuad(del.x, del.y, del.width, del.height, armed ? 0.7f : 0.4f, 0.15f, 0.15f, 0.9f);
            float ds = 1.1f;
            float dw = font.measureText(armed ? "Confirm?" : "Delete", ds);
            font.drawText(ui, armed ? "Confirm?" : "Delete", del.x + (del.width - dw) / 2f, del.y + (del.height - font.lineHeight(ds)) / 2f, ds, 1, 1, 1, 1);

            String typeLabel = w.type().name();
            font.drawText(ui, typeLabel, rowPlayButtons.get(i).x + 12, rowPlayButtons.get(i).y + rowPlayButtons.get(i).height - 16, 0.8f, 0.75f, 0.85f, 1f, 1f);
        }

        createButton.render(ui, font, false);
        backButton.render(ui, font, false);
    }
}
