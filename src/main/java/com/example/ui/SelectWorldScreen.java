package com.example.ui;

import com.example.world.WorldIO;
import com.example.world.WorldSettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla "Select World": a scrolling list (36px rows: name, "folder (date)", game mode) between a
 * 32px header and 64px footer, with Play Selected World / Create New World on the first button row
 * and Edit / Delete / Re-Create / Cancel on the second. Edit and Delete open child screens.
 */
public class SelectWorldScreen extends McScreen {
    public interface Listener {
        void onLoadWorld(WorldIO.SavedWorld world);

        void onCreateNew();

        void onBack();
    }

    static final int ROW_H = 36, LIST_W = 220, HEADER_H = 32, FOOTER_H = 64;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final Listener listener;
    private List<WorldIO.SavedWorld> worlds = new ArrayList<>();
    private int selected = -1;
    private float scroll;
    private long lastClickNanos;
    private int lastClickIndex = -1;

    public SelectWorldScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
    }

    /** Re-scans saves/ - call whenever this screen is entered. */
    public void refresh() {
        worlds = WorldIO.listSavedWorlds();
        selected = worlds.isEmpty() ? -1 : Math.min(Math.max(selected, -1), worlds.size() - 1);
        child = null;
        invalidate();
    }

    WorldIO.SavedWorld selectedWorld() {
        return selected >= 0 && selected < worlds.size() ? worlds.get(selected) : null;
    }

    @Override
    protected void init() {
        widgets.add(new WorldList());
        int cx = guiW / 2;
        int y1 = guiH - 52, y2 = guiH - 28;
        Button play = new Button(cx - 154, y1, 150, 20, "Play Selected World", () -> {
            if (selectedWorld() != null) listener.onLoadWorld(selectedWorld());
        });
        play.active = selectedWorld() != null;
        widgets.add(play);
        widgets.add(new Button(cx + 4, y1, 150, 20, "Create New World", listener::onCreateNew));
        Button edit = new Button(cx - 154, y2, 72, 20, "Edit", () -> {
            WorldIO.SavedWorld w = selectedWorld();
            if (w != null) openChild(new EditWorldScreen(env, w.displayName(), newName -> {
                WorldIO.renameWorld(w.folderName(), newName);
                refresh();
            }));
        });
        edit.active = selectedWorld() != null;
        widgets.add(edit);
        Button delete = new Button(cx - 76, y2, 72, 20, "Delete", () -> {
            WorldIO.SavedWorld w = selectedWorld();
            if (w != null) openChild(new ConfirmScreen(env, "Are you sure you want to delete this world?",
                    "'" + w.displayName() + "' will be lost forever! (A long time!)", "Delete", () -> {
                WorldIO.deleteWorld(w.folderName());
                selected = -1;
                refresh();
            }));
        });
        delete.active = selectedWorld() != null;
        widgets.add(delete);
        Button recreate = new Button(cx + 4, y2, 72, 20, "Re-Create", () -> {
        });
        recreate.active = false;
        widgets.add(recreate);
        widgets.add(new Button(cx + 82, y2, 72, 20, "Cancel", listener::onBack));
    }

    @Override
    protected void onEscape() {
        listener.onBack();
    }

    @Override
    protected void onEnter() {
        if (selectedWorld() != null) listener.onLoadWorld(selectedWorld());
    }

    @Override
    protected void onScroll(double delta) {
        scroll = clampScroll(scroll - (float) delta * 18f);
    }

    private float viewH() {
        return guiH - HEADER_H - FOOTER_H;
    }

    private float clampScroll(float s) {
        return Math.max(0f, Math.min(s, Math.max(0f, worlds.size() * ROW_H - viewH())));
    }

    @Override
    protected void drawForeground(GuiContext g) {
        g.textCentered("Select World", g.w / 2f, 12, 0xFFFFFF);
        if (worlds.isEmpty()) g.textCentered("No saved worlds yet - create one below.", g.w / 2f, HEADER_H + 20, 0xA0A0A0);
    }

    private final class WorldList extends Widget {
        WorldList() {
            super(0, HEADER_H, guiW, guiH - HEADER_H - FOOTER_H);
        }

        @Override
        public boolean mouseClicked(float mx, float my) {
            if (!contains(mx, my) || mx < (guiW - LIST_W) / 2f || mx > (guiW + LIST_W) / 2f) return false;
            int idx = (int) ((my - HEADER_H + scroll) / ROW_H);
            if (idx < 0 || idx >= worlds.size()) return false;
            long now = System.nanoTime();
            boolean dbl = idx == lastClickIndex && now - lastClickNanos < 400_000_000L;
            lastClickIndex = idx;
            lastClickNanos = now;
            selected = idx;
            if (dbl) listener.onLoadWorld(worlds.get(idx));
            else invalidate();
            return true;
        }

        @Override
        public void render(GuiContext g) {
            scroll = clampScroll(scroll);
            float left = (g.w - LIST_W) / 2f;
            g.listBackground(HEADER_H, g.h - FOOTER_H);
            g.clip(0, HEADER_H, g.w, g.h - HEADER_H - FOOTER_H);
            for (int i = 0; i < worlds.size(); i++) {
                float y = HEADER_H - scroll + i * ROW_H;
                if (y + ROW_H < HEADER_H || y > g.h - FOOTER_H) continue;
                if (i == selected) {
                    g.fill(left - 2, y - 2, LIST_W + 4, ROW_H, 0x808080);
                    g.fill(left - 1, y - 1, LIST_W + 2, ROW_H - 2, 0x000000);
                }
                WorldIO.SavedWorld w = worlds.get(i);
                g.text(w.displayName(), left + 2, y + 1, 0xFFFFFF);
                g.text(w.folderName() + " (" + DATE.format(Instant.ofEpochMilli(w.lastPlayed())) + ")", left + 2, y + 12, 0x808080);
                String mode = w.type() == WorldSettings.WorldType.FLAT ? "Survival Mode - Superflat" : "Survival Mode";
                g.text(mode, left + 2, y + 23, 0x808080);
            }
            g.unclip();
            float total = worlds.size() * ROW_H;
            if (total > viewH()) {
                float barH = Math.max(16, viewH() * viewH() / total);
                float barY = HEADER_H + (viewH() - barH) * (scroll / (total - viewH()));
                g.scrollbar(g.w - 8, HEADER_H, viewH(), barY, barH);
            }
        }
    }
}
