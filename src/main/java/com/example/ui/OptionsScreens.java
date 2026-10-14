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
        /** Difficulty lock (vanilla padlock next to the Difficulty button); null when no world is open. */
        public java.util.function.BooleanSupplier isLocked;
        public Runnable lock;

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
                boolean locked = ctx.isLocked != null && ctx.isLocked.getAsBoolean();
                Button diff = new Button(right(), row(0), 130, 20,
                        () -> "Difficulty: " + ctx.getDifficulty.get().label(), () -> {
                    ctx.setDifficulty.accept(ctx.getDifficulty.get().next());
                });
                diff.active = !locked;
                widgets.add(diff);
                // The padlock: locking is permanent for the world and asks first, like vanilla.
                Button lockButton = new Button(right() + 130, row(0), 20, 20, locked ? "L" : "U", () -> {
                    Difficulty d = ctx.getDifficulty.get();
                    openChild(new ConfirmScreen(ctx.env, "Lock Difficulty?",
                            "Lock this world to " + d.label() + "? You will never be able to change it again!", "Lock", () -> {
                        if (ctx.lock != null) ctx.lock.run();
                        invalidate();
                    }));
                });
                lockButton.active = !locked && ctx.lock != null;
                widgets.add(lockButton);
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
    }

    private static final class Video extends Page {
        Video(Context ctx) {
            super(ctx, "Video Settings");
        }

        @Override
        void build() {
            inactive(left(), row(0), "Graphics: Fancy");
            widgets.add(new Slider(right(), row(0), 150, 20, (o.renderDistance - 2) / (float) (GameOptions.MAX_RENDER_DISTANCE - 2),
                    v -> "Render Distance: " + (2 + Math.round(v * (GameOptions.MAX_RENDER_DISTANCE - 2))) + " chunks", v -> {
                o.renderDistance = 2 + Math.round(v * (GameOptions.MAX_RENDER_DISTANCE - 2));
                ctx.onChanged.run();
            }));
            inactive(left(), row(1), "Smooth Lighting: ON");
            widgets.add(new Slider(right(), row(1), 150, 20, (o.maxFps - 10) / 250f,
                    v -> "Max Framerate: " + GameOptions.fpsLabel(10 + Math.round(v * 25) * 10), v -> {
                o.maxFps = 10 + Math.round(v * 25) * 10;
                ctx.onChanged.run();
            }));
            toggle(left(), row(2), "Use VSync", () -> o.vsync, b -> o.vsync = b);
            toggle(right(), row(2), "View Bobbing", () -> o.viewBobbing, b -> o.viewBobbing = b);
            widgets.add(new Button(left(), row(3), 150, 20, () -> "GUI Scale: " + guiScaleName(o.guiScale), () -> {
                o.guiScale = (o.guiScale + 1) % 5;
                ctx.onChanged.run();
            }));
            widgets.add(new Button(right(), row(3), 150, 20, () -> "Attack Indicator: " + attackName(o.attackIndicator), () -> {
                o.attackIndicator = (o.attackIndicator + 1) % 3;
                ctx.onChanged.run();
            }));
            widgets.add(new Slider(left(), row(4), 150, 20, o.gamma, v -> "Brightness: " + GameOptions.gammaLabel(v), v -> {
                o.gamma = v;
                ctx.onChanged.run();
            }));
            toggle(right(), row(4), "Fullscreen", () -> o.fullscreen, b -> o.fullscreen = b);
            widgets.add(new Slider(left(), row(5), 150, 20, o.fovEffectScale,
                    v -> "FOV Effects: " + (v == 0f ? "Off" : Math.round(v * 100) + "%"), v -> {
                o.fovEffectScale = v;
                ctx.onChanged.run();
            }));
            inactive(right(), row(5), "Clouds: Fancy");
        }

        static String attackName(int v) {
            return switch (v) {
                case 0 -> "Off";
                case 1 -> "Crosshair";
                default -> "Hotbar";
            };
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
            widgets.add(new Button(left(), row(1), 150, 20, () -> "Sneak: " + (o.toggleCrouch ? "Toggle" : "Hold"), () -> {
                o.toggleCrouch = !o.toggleCrouch;
                ctx.onChanged.run();
            }));
            widgets.add(new Button(right(), row(1), 150, 20, () -> "Sprint: " + (o.toggleSprint ? "Toggle" : "Hold"), () -> {
                o.toggleSprint = !o.toggleSprint;
                ctx.onChanged.run();
            }));
            toggle(left(), row(2), "Auto-Jump", () -> o.autoJump, b -> o.autoJump = b);
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

        /** A sound category nothing plays yet: shown in its vanilla slot, but inactive. */
        private void unused(int x, int y, String name, float value) {
            inactive(x, y, name + ": " + Math.round(value * 100) + "%");
        }

        @Override
        void build() {
            volume(left(), row(0), 310, "Master Volume", () -> o.masterVolume, v -> o.masterVolume = v);
            unused(left(), row(1), "Music", o.musicVolume);
            unused(right(), row(1), "Jukebox/Noteblocks", o.recordVolume);
            volume(left(), row(2), 150, "Weather", () -> o.weatherVolume, v -> o.weatherVolume = v);
            volume(right(), row(2), 150, "Blocks", () -> o.blocksVolume, v -> o.blocksVolume = v);
            volume(left(), row(3), 150, "Hostile Creatures", () -> o.hostileVolume, v -> o.hostileVolume = v);
            volume(right(), row(3), 150, "Friendly Creatures", () -> o.neutralVolume, v -> o.neutralVolume = v);
            volume(left(), row(4), 150, "Players", () -> o.playersVolume, v -> o.playersVolume = v);
            volume(right(), row(4), 150, "Ambient/Environment", () -> o.ambientVolume, v -> o.ambientVolume = v);
            unused(left(), row(5), "Voice/Speech", o.voiceVolume);
        }
    }

    /** Vanilla key rebinding: click a binding, press a key or mouse button (Esc unbinds); duplicates show red. */
    private static final class KeyBinds extends Page {
        private float scroll;
        private KeyBindings.Bind waiting;

        KeyBinds(Context ctx) {
            super(ctx, "Key Binds");
        }

        @Override
        void build() {
        }

        private int maxScroll() {
            return Math.max(0, KeyBindings.Bind.values().length * 24 - (guiH - 80));
        }

        @Override
        protected void init() {
            KeyBindings.Bind[] binds = KeyBindings.Bind.values();
            for (int i = 0; i < binds.length; i++) {
                KeyBindings.Bind b = binds[i];
                int y = 36 + i * 24 - Math.round(scroll);
                if (y < 30 || y > guiH - 56) continue;
                widgets.add(new Button(guiW / 2 - 5, y, 95, 20, () -> keyLabel(b), () -> waiting = b));
                widgets.add(new Button(guiW / 2 + 95, y, 50, 20, "Reset", () -> {
                    o.keys.set(b, b.defaultCode);
                    ctx.onChanged.run();
                }));
            }
            widgets.add(new Button(guiW / 2 - 155, guiH - 28, 150, 20, "Reset Keys", () -> {
                o.keys.reset();
                ctx.onChanged.run();
            }));
            widgets.add(new Button(guiW / 2 + 5, guiH - 28, 150, 20, "Done", this::done));
        }

        private String keyLabel(KeyBindings.Bind b) {
            String n = KeyBindings.displayName(o.keys.get(b));
            if (waiting == b) return "> " + n + " <";
            return o.keys.conflicts(b) ? "[ " + n + " ]" : n;
        }

        @Override
        protected void tick(float dt, com.example.engine.Input input) {
            if (waiting == null) return;
            int key = input.consumeAnyKeyPressed();
            int mouse = input.consumeAnyMousePressed();
            int code = Integer.MIN_VALUE;
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) code = KeyBindings.UNBOUND;
            else if (key >= 0 && KeyBindings.isBindable(key)) code = key;
            else if (mouse >= 0 && KeyBindings.isBindable(KeyBindings.MOUSE_BASE + mouse)) code = KeyBindings.MOUSE_BASE + mouse;
            if (code != Integer.MIN_VALUE) {
                o.keys.set(waiting, code);
                waiting = null;
                ctx.onChanged.run();
            }
        }

        @Override
        protected void onEscape() {
            if (waiting == null) super.onEscape();
        }

        @Override
        protected void onScroll(double delta) {
            scroll = Math.max(0, Math.min(scroll - (float) delta * 20f, maxScroll()));
            invalidate();
        }

        @Override
        protected void drawContent(GuiContext g) {
            KeyBindings.Bind[] binds = KeyBindings.Bind.values();
            g.listBackground(30, g.h - 40);
            for (int i = 0; i < binds.length; i++) {
                float y = 36 + i * 24 - Math.round(scroll);
                if (y < 30 || y > g.h - 56) continue;
                g.text(binds[i].label, g.w / 2f - 155, y + 6, o.keys.conflicts(binds[i]) ? 0xFF5555 : 0xFFFFFF);
            }
            g.textCentered(title, g.w / 2f, 15, 0xFFFFFF);
        }
    }
}
