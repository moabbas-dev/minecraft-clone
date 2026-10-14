package com.example.engine;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * One mob skin as its own GL texture (the vanilla 64x32 / 64x64 entity layout - the box UVs in
 * {@code MobModelBuilder} index straight into it). Loads {@code textures/entity/...} when present (the user's
 * own gitignored copy, like every other real texture here) and otherwise falls back to a flat procedural
 * colour of the same size so the model still renders.
 */
@Slf4j
public final class EntityTexture {
    private final int id;
    private final int width, height;

    private EntityTexture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * The first path in {@code candidates} that is an existing file, or the first candidate when none exists (so the
     * "not found" warning names the preferred path). Lets one skin be found under both its current and older file name,
     * e.g. Minecraft 1.21.5 moved the pig, cow and chicken skins to {@code temperate_*.png}.
     */
    public static String firstExisting(String... candidates) {
        for (String c : candidates) if (new File(c).isFile()) return c;
        return candidates[0];
    }

    /** {@link #load(String, int, int, int)} for a skin that has several possible file names, newest first. */
    public static EntityTexture loadFirst(String[] candidates, int fallbackW, int fallbackH, int fallbackRgb) {
        return load(firstExisting(candidates), fallbackW, fallbackH, fallbackRgb);
    }

    public static EntityTexture load(String path, int fallbackW, int fallbackH, int fallbackRgb) {
        BufferedImage img = null;
        File file = new File(path);
        if (file.isFile()) {
            try {
                img = ImageIO.read(file);
            } catch (Exception e) {
                log.warn("Could not read mob skin {}: {}", path, e.toString());
            }
        } else {
            log.warn("Mob skin {} not found - using a flat placeholder colour", path);
        }
        if (img == null) img = placeholder(fallbackW, fallbackH, fallbackRgb);
        return upload(img);
    }

    static BufferedImage placeholder(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(rgb);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int d = r.nextInt(17) - 8;
                int rr = clamp(((rgb >> 16) & 255) + d), gg = clamp(((rgb >> 8) & 255) + d), bb = clamp((rgb & 255) + d);
                img.setRGB(x, y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
            }
        }
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static EntityTexture upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer buf = memAlloc(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
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
        return new EntityTexture(id, w, h);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void destroy() {
        glDeleteTextures(id);
    }
}
