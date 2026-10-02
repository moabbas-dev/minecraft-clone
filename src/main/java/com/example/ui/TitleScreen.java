package com.example.ui;

import java.util.Random;

/**
 * Vanilla main menu layout (1.8): block-letter logo, rotating yellow splash, Singleplayer /
 * Multiplayer / Minecraft Realms stacked 24px apart (200x20) starting at h/4+48, then Options... and
 * Quit Game as 98px halves 4px apart. Multiplayer and Realms exist in the layout but are inactive -
 * this clone has no networking. The panorama is a procedural scrolling hill silhouette.
 */
public class TitleScreen extends McScreen {
    public interface Listener {
        void onPlay();

        void onOptions();

        void onQuit();
    }

    private static final String[] SPLASHES = {
            "Also try Terraria!", "100% pure Java!", "Made from scratch!", "Check it out!", "Voxels everywhere!",
            "Now with zombies!", "Cooking with fire!", "Hello, world!", "Mind the creeper!", "Block party!"};

    private final Listener listener;
    private final String splash;

    public TitleScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
        this.splash = SPLASHES[new Random().nextInt(SPLASHES.length)];
    }

    @Override
    protected void init() {
        int j = guiH / 4 + 48;
        int cx = guiW / 2;
        widgets.add(new Button(cx - 100, j, 200, 20, "Singleplayer", listener::onPlay));
        Button multi = new Button(cx - 100, j + 24, 200, 20, "Multiplayer", () -> {
        });
        multi.active = false;
        widgets.add(multi);
        Button realms = new Button(cx - 100, j + 48, 200, 20, "Minecraft Realms", () -> {
        });
        realms.active = false;
        widgets.add(realms);
        widgets.add(new Button(cx - 100, j + 72 + 12, 98, 20, "Options...", listener::onOptions));
        widgets.add(new Button(cx + 2, j + 72 + 12, 98, 20, "Quit Game", listener::onQuit));
    }

    @Override
    protected void drawBackground(GuiContext g) {
        float t = g.time;
        g.gradient(0, 0, g.w, g.h * 0.62f, 0x6FA0E8, 1f, 0xC4DCF6, 1f);
        // Two parallax hill layers scrolling slowly, like the rotating panorama.
        for (int layer = 0; layer < 2; layer++) {
            float speed = layer == 0 ? 2.5f : 6f;
            int base = (int) (g.h * (layer == 0 ? 0.58f : 0.68f));
            int color = layer == 0 ? 0x4C8A3A : 0x2F6B2A;
            for (int x = 0; x < g.w; x += 2) {
                double wx = (x + t * speed) * 0.045 + layer * 11.0;
                int hgt = (int) (Math.sin(wx) * 7 + Math.sin(wx * 2.3 + 1.7) * 4 + Math.sin(wx * 0.7) * 6);
                int top = base - hgt;
                g.fill(x, top, 2, g.h - top, color);
            }
        }
        g.gradient(0, 0, g.w, g.h, 0x000000, 0.15f, 0x000000, 0.55f);
    }

    @Override
    protected void drawContent(GuiContext g) {
        // Block-letter logo: black outline, darker drop, light stone fill.
        String logo = "MINECRAFT";
        float px = Math.max(3f, Math.min(5f, g.w / 70f));
        px = (float) Math.floor(px);
        float lw = g.font.width(logo, px);
        float lx = g.w / 2f - lw / 2f, ly = 30;
        int[][] outline = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};
        for (int[] o : outline) g.font.draw(g.ui, logo, lx + o[0], ly + o[1], px, 0x000000, false);
        g.font.draw(g.ui, logo, lx + 1, ly + 2, px, 0x3F3F3F, false);
        g.font.draw(g.ui, logo, lx, ly, px, 0xC2C2C2, false);

        // Splash: yellow, rotated -20 degrees, gently pulsing (vanilla scale formula).
        float pulse = 1.8f - Math.abs((float) Math.sin((g.time * 1000f % 1000f) / 1000f * Math.PI * 2)) * 0.1f;
        float sp = pulse * 100f / (g.font.width(splash) + 32);
        g.font.drawRotated(g.ui, splash, g.w / 2f + 100, 78, (float) Math.toRadians(-20), Math.max(1f, sp), 1f, 1f, 0f, true);

        g.text("Minecraft 1.8 Clone", 2, g.h - 10, 0xFFFFFF);
        String right = "Not affiliated with Mojang AB";
        g.text(right, g.w - g.textWidth(right) - 2, g.h - 10, 0xFFFFFF);
    }
}
