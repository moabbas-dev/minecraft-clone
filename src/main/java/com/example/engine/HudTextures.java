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
 * Real Minecraft HUD sprites (hearts, hunger icons, hotbar and its selection frame), loaded from
 * {@code textures/gui/sprites/hud/} - gitignored like every other real texture here. Loading is
 * all-or-nothing: if any sprite is missing {@link #loadOrNull()} returns null and the HUD keeps its
 * plain colored-quad look, so a fresh clone without the textures still runs.
 */
@Slf4j
public final class HudTextures {
    public record Sprite(int id, int w, int h) {
    }

    private static final String DIR = "textures/gui/sprites/hud/";
    private static final float[] FULL_UV = {0f, 0f, 1f, 1f};

    /** Optional: null when the armor sprites are missing (the armor bar is then simply not drawn). */
    public Sprite armorEmpty, armorHalf, armorFull;
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
            return t;
        } catch (IOException | RuntimeException e) {
            log.info("Real HUD sprites not available ({}) - using the plain HUD", e.getMessage());
            return null;
        }
    }

    private static Sprite load(String name) throws IOException {
        File f = new File(DIR + name);
        BufferedImage img = ImageIO.read(f);
        if (img == null) throw new IOException("not an image: " + f);
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

    public void destroy() {
        for (Sprite s : new Sprite[]{hotbar, hotbarSelection, heartContainer, heartFull, heartHalf, foodEmpty, foodFull, foodHalf}) {
            glDeleteTextures(s.id());
        }
    }
}
