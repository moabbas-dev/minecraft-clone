package com.example.engine;

import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private final long handle;
    private int width;
    private int height;
    private boolean resized;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            this.width = w;
            this.height = h;
            this.resized = true;
        });

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        glfwShowWindow(handle);

        GL.createCapabilities();
        glEnable(GL_MULTISAMPLE);
        glViewport(0, 0, width, height);
    }

    public void captureMouse() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }
    }

    /** Releases the FPS-style captured cursor so menus can click normally. */
    public void releaseMouse() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void close() {
        glfwSetWindowShouldClose(handle, true);
    }

    public void update() {
        if (resized) {
            glViewport(0, 0, width, height);
            resized = false;
        }
        glfwSwapBuffers(handle);
        glfwPollEvents();
    }

    public void destroy() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    public long getHandle() {
        return handle;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float aspectRatio() {
        return height == 0 ? 1f : (float) width / (float) height;
    }
}
