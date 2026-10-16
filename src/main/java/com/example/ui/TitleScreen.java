package com.example.ui;

import java.util.Random;

/**
 * Vanilla main menu layout: the real logo, a rotating yellow splash, Singleplayer /
 * Multiplayer / Minecraft Realms stacked 24px apart (200x20) starting at h/4+48, then Options... and
 * Quit Game as 98px halves 4px apart. Multiplayer and Realms exist in the layout but are inactive -
 * this clone has no networking.
 */
public class TitleScreen extends McScreen {
    public interface Listener {
        void onPlay();

        void onOptions();

        void onQuit();
    }

    private static final java.util.List<String> SPLASHES = Splashes.load(new java.io.File("texts/splashes.txt"));

    private final Listener listener;
    private final String splash;

    public TitleScreen(GuiEnv env, Listener listener) {
        super(env);
        this.listener = listener;
        this.splash = Splashes.pick(SPLASHES, new Random());
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
        // Minecraft's panorama images are not in the client jar any more (they are 1x1 placeholders), so the title screen
        // sits on the real menu background.
        g.dirtBackground();
    }

    @Override
    protected void drawContent(GuiContext g) {
        // The real logo (256x44 GUI pixels at y = 30) and the "Java Edition" plate under it, as LogoRenderer draws them.
        float lx = (g.w - 256) / 2f, ly = 30;
        boolean logo = env.sprites != null && env.sprites.blit(g.ui, "title/minecraft", lx, ly, 256, 44, 0f, 0f, 1f, 44f / 64f);
        if (logo) env.sprites.blit(g.ui, "title/edition", lx + 88, ly + 37, 128, 14, 0f, 0f, 1f, 14f / 16f);
        else g.textCentered("MINECRAFT", g.w / 2f, ly, 0xFFFFFF);

        // Splash: yellow, rotated -20 degrees, pulsing (vanilla's scale formula), from Minecraft's own splash list.
        if (!splash.isEmpty()) {
            float pulse = 1.8f - Math.abs((float) Math.sin((g.time * 1000f % 1000f) / 1000f * Math.PI * 2)) * 0.1f;
            float sp = pulse * 100f / (g.font.width(splash) + 32);
            g.font.drawRotated(g.ui, splash, g.w / 2f + 123, 69, (float) Math.toRadians(-20), Math.max(1f, sp), 1f, 1f, 0f, true);
        }

        g.text("Minecraft 26.3 Clone", 2, g.h - 10, 0xFFFFFF);
        String right = "Not affiliated with Mojang AB";
        g.text(right, g.w - g.textWidth(right) - 2, g.h - 10, 0xFFFFFF);
    }
}
