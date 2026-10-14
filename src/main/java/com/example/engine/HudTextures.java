package com.example.engine;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * HUD sprites (hearts, hunger icons, armor, air, XP bar, hotbar and its selection frame). Each sprite comes from a
 * texture file in {@code textures/gui/sprites/hud/} when the player supplied one (gitignored), and otherwise from the
 * built-in original art in {@link HudSprites}, so the HUD looks right on a fresh clone with no assets.
 */
@Slf4j
public final class HudTextures {
    public record Sprite(int id, int w, int h) {
    }

    private static final String DIR = "textures/gui/sprites/hud/";
    private static final float[] FULL_UV = {0f, 0f, 1f, 1f};

    /** Optional: null when the armor sprites are missing (the armor bar is then simply not drawn). */
    public Sprite armorEmpty, armorHalf, armorFull;
    /** Optional: air bubbles, XP bar and attack-indicator sprites (each group simply skipped when missing). */
    public Sprite air, airBursting, xpBackground, xpProgress;
    public Sprite crosshairIndicatorBg, crosshairIndicatorProgress, crosshairIndicatorFull;
    public Sprite hotbarIndicatorBg, hotbarIndicatorProgress;
    /** The white-flash variants shown for a moment after the player takes (or regains) health. */
    public Sprite heartContainerBlink, heartFullBlink, heartHalfBlink;
    public final Sprite hotbar, hotbarSelection, heartContainer, heartFull, heartHalf, foodEmpty, foodFull, foodHalf;

    private HudTextures(Sprite hotbar, Sprite hotbarSelection, Sprite heartContainer, Sprite heartFull, Sprite heartHalf,
                        Sprite foodEmpty, Sprite foodFull, Sprite foodHalf) {
        this.hotbar = hotbar;
        this.hotbarSelection = hotbarSelection;
        this.heartContainer = heartContainer;
        this.heartFull = heartFull;
        this.heartHalf = heartHalf;
        this.foodEmpty = foodEmpty;
        this.foodFull = foodFull;
        this.foodHalf = foodHalf;
    }

    public static HudTextures loadOrNull() {
        try {
            HudTextures t = new HudTextures(
                    load("hotbar.png"), load("hotbar_selection.png"),
                    load("heart/container.png"), load("heart/full.png"), load("heart/half.png"),
                    load("food_empty.png"), load("food_full.png"), load("food_half.png"));
            try {
                t.armorEmpty = load("armor_empty.png");
                t.armorHalf = load("armor_half.png");
                t.armorFull = load("armor_full.png");
            } catch (IOException | RuntimeException e) {
                t.armorEmpty = t.armorHalf = t.armorFull = null;
            }
            t.air = optional("air.png");
            t.airBursting = optional("air_bursting.png");
            t.xpBackground = optional("experience_bar_background.png");
            t.xpProgress = optional("experience_bar_progress.png");
            t.crosshairIndicatorBg = optional("crosshair_attack_indicator_background.png");
            t.crosshairIndicatorProgress = optional("crosshair_attack_indicator_progress.png");
            t.crosshairIndicatorFull = optional("crosshair_attack_indicator_full.png");
            t.hotbarIndicatorBg = optional("hotbar_attack_indicator_background.png");
            t.hotbarIndicatorProgress = optional("hotbar_attack_indicator_progress.png");
            t.heartContainerBlink = optional("heart/container_blinking.png");
            t.heartFullBlink = optional("heart/full_blinking.png");
            t.heartHalfBlink = optional("heart/half_blinking.png");
            return t;
        } catch (IOException | RuntimeException e) {
            log.info("Real HUD sprites not available ({}) - using the plain HUD", e.getMessage());
            return null;
        }
    }

    private static Sprite optional(String name) {
        try {
            return load(name);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A texture file in {@link #DIR} if the player supplied one, otherwise the built-in original art. */
    private static Sprite load(String name) throws IOException {
        BufferedImage img = readFile(name);
        if (img == null) img = HudSprites.image(name);
        if (img == null) throw new IOException("no sprite named " + name);
        return upload(img);
    }

    private static BufferedImage readFile(String name) {
        try {
            File f = new File(DIR + name);
            return f.isFile() ? ImageIO.read(f) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Sprite upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer buf = memAlloc(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                buf.put((byte) ((argb >> 16) & 0xFF)).put((byte) ((argb >> 8) & 0xFF)).put((byte) (argb & 0xFF))
                        .put((byte) ((argb >>> 24) & 0xFF));
            }
        }
        buf.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        memFree(buf);
        return new Sprite(id, w, h);
    }

    /** Draws a whole sprite with its top-left at (x, y), each sprite pixel {@code scale} screen pixels. */
    public static void draw(UiRenderer ui, Sprite s, float x, float y, float scale) {
        ui.drawTexturedQuad(x, y, s.w() * scale, s.h() * scale, FULL_UV, s.id(), 1f, 1f, 1f, 1f);
    }

    /** Draws the left {@code fraction} of a sprite (a progress bar), full height. */
    public static void drawFractionX(UiRenderer ui, Sprite s, float x, float y, float scale, float fraction) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (fraction <= 0f) return;
        ui.drawTexturedQuad(x, y, s.w() * scale * fraction, s.h() * scale, new float[]{0f, 0f, fraction, 1f}, s.id(), 1f, 1f, 1f, 1f);
    }

    /** Draws the bottom {@code fraction} of a sprite (a vertical progress bar). */
    public static void drawFractionY(UiRenderer ui, Sprite s, float x, float y, float scale, float fraction) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        if (fraction <= 0f) return;
        float h = s.h() * scale * fraction;
        ui.drawTexturedQuad(x, y + s.h() * scale - h, s.w() * scale, h, new float[]{0f, 1f - fraction, 1f, 1f}, s.id(), 1f, 1f, 1f, 1f);
    }

    public void destroy() {
        for (Sprite s : new Sprite[]{hotbar, hotbarSelection, heartContainer, heartFull, heartHalf, foodEmpty, foodFull, foodHalf}) {
            glDeleteTextures(s.id());
        }
    }
}
