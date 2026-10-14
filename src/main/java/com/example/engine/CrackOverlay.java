package com.example.engine;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * The block-breaking crack overlay: a slightly enlarged cube around the target block textured
 * with one of {@link #STAGES} crack images (alpha-tested/blended, so the block underneath stays
 * visible). One reusable {@link Mesh}, one texture, created once - geometry is rebuilt only when
 * the target or stage actually changes, never per frame.
 *
 * Texture source: the ten real {@code textures/block/destroy_stage_0..9.png} files (16x16, restored by
 * {@code scripts/restore-assets.py}). If any is missing no crack is drawn (nothing is invented).
 * The stages are laid out left-to-right in one strip texture.
 */
@Slf4j
public class CrackOverlay {
    public static final int STAGES = 10;
    static final int TILE = 16;
    /** How far the overlay cube sits outside the block, in blocks - just enough to beat
     *  z-fighting together with the polygon offset applied at draw time, small enough not to
     *  visibly float. */
    static final float EXPAND = 0.003f;

    private final int textureId;
    private final Mesh mesh;
    private final float[] scratch = new float[6 * 6 * 11];

    private int builtStage = -1, builtX, builtY, builtZ;

    public CrackOverlay(int[] vertexLayout) {
        this.mesh = new Mesh(vertexLayout);
        this.textureId = uploadStrip(loadStrip());
    }

    /**
     * A supplied crack texture is a set of grey shades (with white highlights) that vanilla draws through a special
     * "crumbling" blend; drawn with plain alpha blending the highlights read as white scratches. This keeps the shape but
     * makes the crack black: the darker the source pixel, the more opaque the black, and white highlights vanish.
     */
    static int darkenCrackPixel(int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 8) return 0;
        int lum = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
        int a = Math.min(235, (int) ((255 - lum) * 1.5f));
        return a <= 0 ? 0 : (a << 24) | 0x141414;
    }

    private static int[] loadStrip() {
        int[] strip = new int[STAGES * TILE * TILE];
        int[][] custom = new int[STAGES][];
        boolean allCustom = true;
        for (int s = 0; s < STAGES && allCustom; s++) {
            try {
                File f = new File("textures/block/destroy_stage_" + s + ".png");
                BufferedImage img = f.isFile() ? ImageIO.read(f) : null;
                if (img == null || img.getWidth() != TILE || img.getHeight() != TILE) {
                    allCustom = false;
                } else {
                    custom[s] = img.getRGB(0, 0, TILE, TILE, null, 0, TILE);
                }
            } catch (Exception e) {
                allCustom = false;
            }
        }
        if (!allCustom) log.warn("textures/block/destroy_stage_0..9.png not all present - no block-breaking cracks will be drawn");
        for (int s = 0; s < STAGES && allCustom; s++) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    strip[y * (STAGES * TILE) + s * TILE + x] = darkenCrackPixel(custom[s][y * TILE + x]);
                }
            }
        }
        return strip;
    }

    private static int uploadStrip(int[] argb) {
        int w = STAGES * TILE, h = TILE;
        ByteBuffer buf = memAlloc(w * h * 4);
        try {
            for (int argbPixel : argb) {
                buf.put((byte) ((argbPixel >> 16) & 0xFF));
                buf.put((byte) ((argbPixel >> 8) & 0xFF));
                buf.put((byte) (argbPixel & 0xFF));
                buf.put((byte) ((argbPixel >> 24) & 0xFF));
            }
            buf.flip();
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            return id;
        } finally {
            memFree(buf);
        }
    }

    /** Binds the crack texture to {@code unit}. The caller must re-bind the atlas afterwards. */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /** Rebuilds the cube geometry only if the block or stage changed since the last call. */
    public void update(int x, int y, int z, int stage) {
        if (stage == builtStage && x == builtX && y == builtY && z == builtZ) return;
        builtStage = stage;
        builtX = x;
        builtY = y;
        builtZ = z;

        float u0 = (float) stage / STAGES + 0.0005f, u1 = (float) (stage + 1) / STAGES - 0.0005f;
        float v0 = 0f, v1 = 1f;
        float lo = -EXPAND, hi = 1f + EXPAND;
        float[][] faces = {
                {lo, hi, lo, lo, hi, hi, hi, hi, hi, hi, hi, lo},   // top
                {lo, lo, hi, lo, lo, lo, hi, lo, lo, hi, lo, hi},   // bottom
                {lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi, hi},   // south
                {hi, lo, lo, lo, lo, lo, lo, hi, lo, hi, hi, lo},   // north
                {hi, lo, hi, hi, lo, lo, hi, hi, lo, hi, hi, hi},   // east
                {lo, lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo},   // west
        };
        int n = 0;
        for (float[] f : faces) {
            // f holds 4 corners (x,y,z each): bottom-left, bottom-right, top-right, top-left of the face texture
            float[][] c = {
                    {f[0], f[1], f[2], u0, v1}, {f[3], f[4], f[5], u1, v1},
                    {f[6], f[7], f[8], u1, v0}, {f[9], f[10], f[11], u0, v0}};
            int[] tri = {0, 1, 2, 0, 2, 3};
            for (int t : tri) {
                scratch[n++] = x + c[t][0];
                scratch[n++] = y + c[t][1];
                scratch[n++] = z + c[t][2];
                scratch[n++] = c[t][3];
                scratch[n++] = c[t][4];
                scratch[n++] = 1f;  // sky
                scratch[n++] = 1f;  // block light: cracks stay visible in the dark; the crack pixels are dark themselves
                scratch[n++] = 1f;
                scratch[n++] = 1f;
                scratch[n++] = 1f;  // neutral tint
                scratch[n++] = 0f;  // no wind
            }
        }
        mesh.upload(scratch, n);
    }

    public void draw() {
        mesh.draw();
    }

    public void destroy() {
        mesh.destroy();
        glDeleteTextures(textureId);
    }
}
