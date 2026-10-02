package com.example.ui;

import com.example.world.Difficulty;
import com.example.world.WorldIO;
import com.example.world.WorldSettings;

/**
 * Vanilla (1.8) "Create New World": world name + "Will be saved in", Game Mode, and a "More World
 * Options..." page with seed and world type. Only the options this game actually supports are
 * interactive (name, difficulty, seed, world type); the rest are shown inactive in their vanilla
 * positions so the layout matches without pretending to features that do not exist.
 */
public class WorldCreateScreen extends McScreen {
    public interface Listener {
        /** @param seed null = random. */
        void onCreateWorld(WorldSettings settings, Long seed);

        void onBack();
    }

    private final Listener listener;
    private final StringBuilder worldName = new StringBuilder("New World");
    private final StringBuilder seedText = new StringBuilder();
    private boolean moreOptions;
    private WorldSettings.WorldType type = WorldSettings.WorldType.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;

    public WorldCreateScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
    }

    /** Reset to defaults each time the screen is entered fresh. */
    public void reset() {
        worldName.setLength(0);
        worldName.append("New World");
        seedText.setLength(0);
        moreOptions = false;
        type = WorldSettings.WorldType.SURVIVAL;
        difficulty = Difficulty.NORMAL;
        child = null;
        invalidate();
    }

    /** Vanilla: blank = random, a number is used as-is, any other text is hashed. */
    static Long parseSeed(String text) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return (long) t.hashCode();
        }
    }

    private static String title(Enum<?> e) {
        String s = e.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void init() {
        int cx = guiW / 2;
        if (!moreOptions) {
            TextField name = new TextField(cx - 100, 60, 200, 20, worldName, 32);
            name.setFocused(true);
            widgets.add(name);
            Button mode = new Button(cx - 75, 115, 150, 20, "Game Mode: Survival", () -> {
            });
            mode.active = false;
            widgets.add(mode);
            widgets.add(new Button(cx - 75, 161, 150, 20, () -> "Difficulty: " + title(difficulty), () -> {
                difficulty = difficulty.next();
            }));
            widgets.add(new Button(cx - 75, 187, 150, 20, "More World Options...", () -> {
                moreOptions = true;
                invalidate();
            }));
        } else {
            TextField seed = new TextField(cx - 100, 60, 200, 20, seedText, 32);
            seed.setFocused(true);
            widgets.add(seed);
            Button structures = new Button(cx - 155, 100, 150, 20, "Generate Structures: OFF", () -> {
            });
            structures.active = false;
            widgets.add(structures);
            widgets.add(new Button(cx + 5, 100, 150, 20,
                    () -> "World Type: " + (type == WorldSettings.WorldType.FLAT ? "Superflat" : "Default"), () -> {
                type = type == WorldSettings.WorldType.FLAT ? WorldSettings.WorldType.SURVIVAL : WorldSettings.WorldType.FLAT;
            }));
            Button bonus = new Button(cx - 155, 151, 150, 20, "Bonus Chest: OFF", () -> {
            });
            bonus.active = false;
            widgets.add(bonus);
            Button cheats = new Button(cx + 5, 151, 150, 20, "Allow Cheats: OFF", () -> {
            });
            cheats.active = false;
            widgets.add(cheats);
            widgets.add(new Button(cx - 75, 187, 150, 20, "Done", () -> {
                moreOptions = false;
                invalidate();
            }));
        }
        widgets.add(new Button(cx - 155, guiH - 28, 150, 20, "Create New World", this::create));
        widgets.add(new Button(cx + 5, guiH - 28, 150, 20, "Cancel", listener::onBack));
    }

    private void create() {
        String n = worldName.toString().trim();
        if (n.isEmpty()) n = "New World";
        listener.onCreateWorld(new WorldSettings(n, type, difficulty), parseSeed(seedText.toString()));
    }

    @Override
    protected void onEnter() {
        create();
    }

    @Override
    protected void onEscape() {
        if (moreOptions) {
            moreOptions = false;
            invalidate();
        } else {
            listener.onBack();
        }
    }

    @Override
    protected void drawContent(GuiContext g) {
        g.textCentered("Create New World", g.w / 2f, 20, 0xFFFFFF);
        int cx = g.w / 2;
        if (!moreOptions) {
            g.text("World Name", cx - 100, 47, 0xA0A0A0);
            String n = worldName.toString().trim();
            g.text("Will be saved in: " + WorldIO.sanitizeFolderName(n.isEmpty() ? "New World" : n), cx - 100, 85, 0xA0A0A0);
            g.text("Search for resources, craft, gain", cx - 100 + 25, 137, 0xA0A0A0);
            g.text("levels, health and hunger", cx - 100 + 25, 148, 0xA0A0A0);
        } else {
            g.text("Seed for the World Generator", cx - 100, 47, 0xA0A0A0);
            g.text("Leave blank for a random seed", cx - 100, 85, 0xA0A0A0);
        }
    }
}
