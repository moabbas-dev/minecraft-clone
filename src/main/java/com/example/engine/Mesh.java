package com.example.engine;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Generic VAO/VBO wrapper. Supports an arbitrary list of float attribute sizes
 * (e.g. [3,2,1] for position/uv/shade, or [2,2] for position/uv in UI space).
 */
public class Mesh {
    private final int vaoId;
    private final int vboId;
    private final int[] attribSizes;
    private int vertexCount;
    /** Reused staging buffer for {@link #upload(float[], int)} - grown on demand, freed in destroy(). */
    private FloatBuffer staging;

    public Mesh(int[] attribSizes) {
        this.attribSizes = attribSizes;
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        int stride = 0;
        for (int s : attribSizes) stride += s;
        int offset = 0;
        for (int i = 0; i < attribSizes.length; i++) {
            glVertexAttribPointer(i, attribSizes[i], GL_FLOAT, false, stride * Float.BYTES, (long) offset * Float.BYTES);
            glEnableVertexAttribArray(i);
            offset += attribSizes[i];
        }
        glBindVertexArray(0);
    }

    public void upload(float[] data) {
        int stride = 0;
        for (int s : attribSizes) stride += s;
        vertexCount = stride == 0 ? 0 : data.length / stride;
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
    }

    /**
     * Uploads only the first {@code floatCount} floats of {@code data}, so callers can keep one
     * big reusable scratch array for per-frame dynamic geometry (dropped items, particles, crack
     * overlay) instead of allocating an exactly-sized array every frame.
     */
    public void upload(float[] data, int floatCount) {
        int stride = 0;
        for (int s : attribSizes) stride += s;
        vertexCount = stride == 0 ? 0 : floatCount / stride;
        if (vertexCount == 0) return;
        if (staging == null || staging.capacity() < floatCount) {
            if (staging != null) MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAllocFloat(Math.max(floatCount, 4096));
        }
        staging.clear();
        staging.put(data, 0, vertexCount * stride).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, staging, GL_DYNAMIC_DRAW);
    }

    public void draw() {
        if (vertexCount == 0) return;
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public boolean isEmpty() {
        return vertexCount == 0;
    }

    public void destroy() {
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
