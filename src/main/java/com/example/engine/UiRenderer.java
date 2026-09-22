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

    public UiRenderer() {
        shader = new Shader(Shaders.UI_VERTEX, Shaders.UI_FRAGMENT);
        mesh = new Mesh(new int[]{2, 2});
    }

    public void begin(int screenWidth, int screenHeight) {
        shader.bind();
        shader.setUniform("uScreenSize", (float) screenWidth, (float) screenHeight);
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
