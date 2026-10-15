package com.example.game;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * The 16 x 16 lightmap on texture unit 1: column = block light, row = sky light, filtered linearly like vanilla's, so a
 * vertex lit with a fractional level (the average of four cells) lands between two texels. See {@link LightmapColors}.
 */
final class LightmapTexture {
    static final int UNIT = 1;
    private final int id;
    private byte[] last;

    LightmapTexture() {
        id = glGenTextures();
        glActiveTexture(GL_TEXTURE0 + UNIT);
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glActiveTexture(GL_TEXTURE0);
    }

    /** Uploads new colours (16 x 16 RGBA) when they differ from the last upload, and binds the texture to its unit. */
    void update(byte[] rgba) {
        glActiveTexture(GL_TEXTURE0 + UNIT);
        glBindTexture(GL_TEXTURE_2D, id);
        if (!Arrays.equals(last, rgba)) {
            ByteBuffer buf = BufferUtils.createByteBuffer(rgba.length);
            buf.put(rgba).flip();
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 16, 16, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            last = rgba.clone();
        }
        glActiveTexture(GL_TEXTURE0);
    }
}
