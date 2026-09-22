package com.example.engine;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

public class Shader {
    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public Shader(String vertexSource, String fragmentSource) {
        int vertexId = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentId = compile(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();
        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(programId));
        }
        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int compile(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(id));
        }
        return id;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    private int location(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setUniform(String name, int value) {
        glUniform1i(location(name), value);
    }

    public void setUniform(String name, float value) {
        glUniform1f(location(name), value);
    }

    public void setUniform(String name, float x, float y) {
        glUniform2f(location(name), x, y);
    }

    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(location(name), x, y, z);
    }

    public void setUniform(String name, float x, float y, float z, float w) {
        glUniform4f(location(name), x, y, z, w);
    }

    public void setUniform(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            glUniformMatrix4fv(location(name), false, buffer);
        }
    }

    public void destroy() {
        glDeleteProgram(programId);
    }
}
