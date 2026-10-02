package com.example.ui;

import com.example.world.Difficulty;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Options hub and its child pages (Video Settings, Controls, Mouse Settings, Key Binds,
 * Music &amp; Sounds), laid out like vanilla 1.8: two 150px columns 10px apart, 24px row pitch
 * starting at h/6-12, Done as a 200px button at h/6+168. Entries for features this game lacks
 * (skins, languages, chat, resource packs, ...) are present but inactive, in their vanilla slots.
 */
public final class OptionsScreens {
    private OptionsScreens() {
    }

    /** What the Options screens need from the game: change notification, difficulty access, how to leave. */
    public static final class Context {
        public final GuiEnv env;
        public final Runnable onChanged;
        public final Runnable onClose;
        /** null when no world is open (Options opened from the title screen). */
        public final Supplier<Difficulty> getDifficulty;
        public final Consumer<Difficulty> setDifficulty;

        public Context(GuiEnv env, Runnable onChanged, Runnable onClose, Supplier<Difficulty> getDifficulty,
                       Consumer<Difficulty> setDifficulty) {
            this.env = env;
            this.onChanged = onChanged;
            this.onClose = onClose;
            this.getDifficulty = getDifficulty;
            this.setDifficulty = setDifficulty;
        }
    }

    /** The root Options screen. */
    public static McScreen create(Context ctx) {
        return new Hub(ctx);
    }

    /** Options with a sub-page already open (test controller / screenshots): video, controls,
     *  mouse, keybinds, sounds. Unknown names return the plain hub. */
    public static McScreen create(Context ctx, String page) {
        Hub hub = new Hub(ctx);
        switch (page) {
            case "video" -> hub.openChild(new Video(ctx));
            case "sounds" -> hub.openChild(new Sounds(ctx));
            case "controls" -> hub.openChild(new Controls(ctx));
            case "mouse" -> {
                Controls c = new Controls(ctx);
                hub.openChild(c);
                c.openChild(new Mouse(ctx));
            }
            case "keybinds" -> {
                Controls c = new Controls(ctx);
                hub.openChild(c);
                c.openChild(new KeyBinds(ctx));
            }
            default -> {
            }
        }
        return hub;
    }

    private abstract static class Page extends McScreen {
        final Context ctx;
        final GameOptions o;
        final String title;

        Page(Context ctx, String title) {
            super(ctx.env);
            this.ctx = ctx;
            this.o = ctx.env.options;
            this.title = title;
        }

        abstract void build();

        @Override
        protected void init() {
            build();
            widgets.add(new Button(guiW / 2 - 100, guiH / 6 + 168, 200, 20, "Done", this::done));
        }

        void done() {
            ctx.onChanged.run();
            if (parent != null) parent.closeChild();
            else ctx.onClose.run();
        }

        @Override
        protected void onEscape() {
            done();
        }

        @Override
        protected void drawContent(GuiContext g) {
            g.textCentered(title, g.w / 2f, 15, 0xFFFFFF);
        }

        int left() {
            return guiW / 2 - 155;
        }

        int right() {
            return guiW / 2 + 5;
        }

        int row(int r) {
            return guiH / 6 + 24 * r - 12;
        }

        Button inactive(int x, int y, String label) {
            Button b = new Button(x, y, 150, 20, label, () -> {
            });
            b.active = false;
            widgets.add(b);
            return b;
        }

        void toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
            widgets.add(new Button(x, y, 150, 20, () -> name + ": " + (get.getAsBoolean() ? "ON" : "OFF"), () -> {
                set.accept(!get.getAsBoolean());
                ctx.onChanged.run();
            }));
        }

        void link(int x, int y, String label, java.util.function.Supplier<McScreen> screen) {
            widgets.add(new Button(x, y, 150, 20, label, () -> openChild(screen.get())));
        }
    }

    private static final class Hub extends Page {
        Hub(Context ctx) {
            super(ctx, "Options");
        }

        @Override
        void build() {
            widgets.add(new Slider(left(), row(0), 150, 20, (o.fov - 30) / 80f, v -> {
                int f = Math.round(30 + v * 80);
                return "FOV: " + (f == 70 ? "Normal" : f == 110 ? "Quake Pro" : Integer.toString(f));
            }, v -> {
                o.fov = Math.round(30 + v * 80);
                ctx.onChanged.run();
            }));
            if (ctx.getDifficulty != null) {
                widgets.add(new Button(right(), row(0), 150, 20, () -> "Difficulty: " + cap(ctx.getDifficulty.get()), () -> {
                    Difficulty d = ctx.getDifficulty.get();
                    ctx.setDifficulty.accept(Difficulty.values()[(d.ordinal() + 1) % Difficulty.values().length]);
                }));
            }
            inactive(left(), row(1), "Skin Customization...");
            link(right(), row(1), "Music & Sounds...", () -> new Sounds(ctx));
            link(left(), row(2), "Video Settings...", () -> new Video(ctx));
            link(right(), row(2), "Controls...", () -> new Controls(ctx));
            inactive(left(), row(3), "Language...");
            inactive(right(), row(3), "Chat Settings...");
            inactive(left(), row(4), "Resource Packs...");
            inactive(right(), row(4), "Accessibility Settings...");
        }

        private static String cap(Enum<?> e) {
            String s = e.name().toLowerCase();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    private static final class Video extends Page {
        Video(Context ctx) {
            super(ctx, "Video Settings");
        }

        @Override
        void build() {
            inactive(left(), row(0), "Graphics: Fancy");
            widgets.add(new Slider(right(), row(0), 150, 20, (o.renderDistance - 2) / 10f,
                    v -> "Render Distance: " + (2 + Math.round(v * 10)) + " chunks", v -> {
                o.renderDistance = 2 + Math.round(v * 10);
                ctx.onChanged.run();
            }));
            inactive(left(), row(1), "Smooth Lighting: ON");
            toggle(right(), row(1), "Use VSync", () -> o.vsync, b -> o.vsync = b);
            toggle(left(), row(2), "View Bobbing", () -> o.viewBobbing, b -> o.viewBobbing = b);
            widgets.add(new Button(right(), row(2), 150, 20, () -> "GUI Scale: " + guiScaleName(o.guiScale), () -> {
                o.guiScale = (o.guiScale + 1) % 5;
                ctx.onChanged.run();
            }));
            toggle(left(), row(3), "Fullscreen", () -> o.fullscreen, b -> o.fullscreen = b);
            inactive(right(), row(3), "Clouds: Fancy");
            inactive(left(), row(4), "Particles: All");
            inactive(right(), row(4), "Mipmap Levels: 4");
        }

        static String guiScaleName(int s) {
            return switch (s) {
                case 0 -> "Auto";
                case 1 -> "Small";
                case 2 -> "Normal";
                case 3 -> "Large";
                default -> "Max";
            };
        }
    }

    private static final class Controls extends Page {
        Controls(Context ctx) {
            super(ctx, "Controls");
        }

        @Override
        void build() {
            link(left(), row(0), "Mouse Settings...", () -> new Mouse(ctx));
            link(right(), row(0), "Key Binds...", () -> new KeyBinds(ctx));
            inactive(left(), row(1), "Auto-Jump: OFF");
        }
    }

    private static final class Mouse extends Page {
        Mouse(Context ctx) {
            super(ctx, "Mouse Settings");
        }

        @Override
        void build() {
            widgets.add(new Slider(left(), row(0), 150, 20, o.mouseSensitivity, v -> {
                if (v == 0f) return "Sensitivity: *yawn*";
                if (v == 1f) return "Sensitivity: HYPERSPEED!!!";
                return "Sensitivity: " + Math.round(v * 200) + "%";
            }, v -> {
                o.mouseSensitivity = v;
                ctx.onChanged.run();
            }));
            toggle(right(), row(0), "Invert Mouse", () -> o.invertMouse, b -> o.invertMouse = b);
        }
    }

    private static final class Sounds extends Page {
        Sounds(Context ctx) {
            super(ctx, "Music & Sounds");
        }

        private void volume(int x, int y, int w, String name, java.util.function.Supplier<Float> get, Consumer<Float> set) {
            widgets.add(new Slider(x, y, w, 20, get.get(), v -> name + ": " + (v == 0f ? "OFF" : Math.round(v * 100) + "%"), v -> {
                set.accept(v);
                ctx.onChanged.run();
            }));
        }

        @Override
        void build() {
            volume(left(), row(0), 310, "Master Volume", () -> o.masterVolume, v -> o.masterVolume = v);
            volume(left(), row(1) + 4, 150, "Blocks", () -> o.blocksVolume, v -> o.blocksVolume = v);
            volume(right(), row(1) + 4, 150, "Players", () -> o.playersVolume, v -> o.playersVolume = v);
            volume(left(), row(2) + 4, 150, "Ambient/Environment", () -> o.ambientVolume, v -> o.ambientVolume = v);
            volume(right(), row(2) + 4, 150, "Weather", () -> o.weatherVolume, v -> o.weatherVolume = v);
        }
    }

    /** Read-only list of the game's fixed key bindings (rebinding is not supported). */
    private static final class KeyBinds extends Page {
        private static final String[][] BINDS = {
                {"Move Forward", "W"}, {"Move Backward", "S"}, {"Strafe Left", "A"}, {"Strafe Right", "D"},
                {"Jump / Swim Up", "Space"}, {"Sneak / Swim Down", "Left Shift"}, {"Sprint", "Left Ctrl"},
                {"Toggle Flying", "2x Space"}, {"Attack / Destroy", "Left Button"}, {"Use Item / Place Block", "Right Button"},
                {"Open Inventory", "E"}, {"Hotbar Slots 1-9", "1 - 9"}, {"Debug Overlay", "F3"}, {"Game Menu", "Escape"},
        };
        private float scroll;

        KeyBinds(Context ctx) {
            super(ctx, "Key Binds");
        }

        @Override
        void build() {
        }

        @Override
        protected void init() {
            widgets.add(new Button(guiW / 2 - 100, guiH - 28, 200, 20, "Done", this::done));
        }

        @Override
        protected void onScroll(double delta) {
            scroll = Math.max(0, Math.min(scroll - (float) delta * 20f, Math.max(0, BINDS.length * 24 - (guiH - 80))));
        }

        @Override
        protected void drawContent(GuiContext g) {
            g.textCentered(title, g.w / 2f, 15, 0xFFFFFF);
            for (int i = 0; i < BINDS.length; i++) {
                float y = 36 + i * 24 - scroll;
                if (y < 30 || y > g.h - 52) continue;
                g.text(BINDS[i][0], g.w / 2f - 155, y + 6, 0xFFFFFF);
                Button.drawFrame(g, g.w / 2f + 55, y, 100, 20, Button.DISABLED_FILL, false);
                g.textCentered(BINDS[i][1], g.w / 2f + 105, y + 6, 0xA0A0A0);
            }
            g.dirtStrip(0, g.h - 40, g.w, 40);
            g.dirtStrip(0, 0, g.w, 30);
            g.textCentered(title, g.w / 2f, 15, 0xFFFFFF);
        }
    }
}
