package com.example.engine;

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
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
