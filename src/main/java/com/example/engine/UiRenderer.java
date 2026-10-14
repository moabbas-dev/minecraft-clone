package com.example.engine;

import static org.lwjgl.opengl.GL33.*;

/**
 * Shared 2D overlay drawing (screen-space quads, textured or solid color) used by every
 * menu screen and the in-game HUD, so they all go through one shader/mesh instead of
 * each hand-rolling its own.
 */
public class UiRenderer {
    private final Shader shader;
    private final Mesh mesh;
    private float logicalW = 1, logicalH = 1;

    public UiRenderer() {
        shader = new Shader(Shaders.UI_VERTEX, Shaders.UI_FRAGMENT);
        mesh = new Mesh(new int[]{2, 2});
    }

    public void begin(int screenWidth, int screenHeight) {
        begin((float) screenWidth, (float) screenHeight);
    }

    /** Sets the logical coordinate space (e.g. framebuffer size / GUI scale) quads are drawn in. */
    public void begin(float logicalWidth, float logicalHeight) {
        shader.bind();
        shader.setUniform("uScreenSize", logicalWidth, logicalHeight);
        logicalW = logicalWidth;
        logicalH = logicalHeight;
    }

    /** Limits drawing to a rectangle given in the current logical units (scrolling lists hide what is past their edges). */
    public void setClip(float x, float y, float w, float h) {
        int[] vp = new int[4];
        glGetIntegerv(GL_VIEWPORT, vp);
        float sx = vp[2] / logicalW, sy = vp[3] / logicalH;
        int px = Math.round(x * sx), py = Math.round(y * sy), pw = Math.round(w * sx), ph = Math.round(h * sy);
        glEnable(GL_SCISSOR_TEST);
        glScissor(vp[0] + px, vp[1] + vp[3] - py - ph, Math.max(0, pw), Math.max(0, ph));
    }

    public void clearClip() {
        glDisable(GL_SCISSOR_TEST);
    }

    public void drawColorQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        drawQuad(x, y, w, h, null, r, g, b, a);
    }

    public void drawTexturedQuad(float x, float y, float w, float h, float[] uv, int textureId, float r, float g, float b, float a) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setUniform("uTexture", 0);
        drawQuad(x, y, w, h, uv, r, g, b, a);
    }

    /** Draws an arbitrary textured quad from 4 corner points (winding: p0,p1,p2,p3 around the shape). */
    public void drawTexturedQuadPoints(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                                        float[] uv, int textureId, float r, float g, float b, float a) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setUniform("uTexture", 0);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float[] verts = {
                x0, y0, u0, v0,
                x1, y1, u1, v0,
                x2, y2, u1, v1,
                x0, y0, u0, v0,
                x2, y2, u1, v1,
                x3, y3, u0, v1
        };
        shader.setUniform("uColor", r, g, b, a);
        shader.setUniform("uUseTexture", 1);
        mesh.upload(verts);
        mesh.draw();
    }

    /** Fills a rectangle by repeating one texture rect every {@code tile} units, as a single batch
     *  (partial edge tiles are cropped, not stretched) - the menu dirt background. */
    public void drawTiled(int textureId, float[] uv, float x, float y, float w, float h, float tile,
                          float r, float g, float b, float a) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setUniform("uTexture", 0);
        int cols = (int) Math.ceil(w / tile), rows = (int) Math.ceil(h / tile);
        float[] verts = new float[cols * rows * 24];
        int n = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float x0 = x + col * tile, y0 = y + row * tile;
                float fw = Math.min(tile, x + w - x0) / tile, fh = Math.min(tile, y + h - y0) / tile;
                float x1 = x0 + fw * tile, y1 = y0 + fh * tile;
                float u0 = uv[0], v0 = uv[1], u1 = uv[0] + (uv[2] - uv[0]) * fw, v1 = uv[1] + (uv[3] - uv[1]) * fh;
                float[] q = {x0, y0, u0, v0, x0, y1, u0, v1, x1, y1, u1, v1, x0, y0, u0, v0, x1, y1, u1, v1, x1, y0, u1, v0};
                System.arraycopy(q, 0, verts, n, 24);
                n += 24;
            }
        }
        shader.setUniform("uColor", r, g, b, a);
        shader.setUniform("uUseTexture", 1);
        mesh.upload(verts);
        mesh.draw();
    }

    private void drawQuad(float x, float y, float w, float h, float[] uv, float r, float g, float b, float a) {
        float u0 = 0, v0 = 0, u1 = 0, v1 = 0;
        boolean textured = uv != null;
        if (textured) {
            u0 = uv[0];
            v0 = uv[1];
            u1 = uv[2];
            v1 = uv[3];
        }
        float[] verts = {
                x, y, u0, v0,
                x, y + h, u0, v1,
                x + w, y + h, u1, v1,
                x, y, u0, v0,
                x + w, y + h, u1, v1,
                x + w, y, u1, v0
        };
        shader.setUniform("uColor", r, g, b, a);
        shader.setUniform("uUseTexture", textured ? 1 : 0);
        mesh.upload(verts);
        mesh.draw();
    }

    public void end() {
        shader.unbind();
    }

    public void destroy() {
        shader.destroy();
        mesh.destroy();
    }
}
