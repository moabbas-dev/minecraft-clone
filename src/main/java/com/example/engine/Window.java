package com.example.engine;

import lombok.Getter;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    @Getter
    private final long handle;
    @Getter
    private int width;
    @Getter
    private int height;
    private boolean resized;
    private boolean fullscreen;
    private int winX, winY, winW, winH;

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

    public void setVsync(boolean on) {
        glfwSwapInterval(on ? 1 : 0);
    }

    /** Windowed <-> fullscreen on the primary monitor, remembering the windowed rect to restore. */
    public void setFullscreen(boolean on) {
        if (on == fullscreen) return;
        if (on) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(handle, x, y);
            glfwGetWindowSize(handle, w, h);
            winX = x[0];
            winY = y[0];
            winW = w[0];
            winH = h[0];
            long monitor = glfwGetPrimaryMonitor();
            var mode = glfwGetVideoMode(monitor);
            if (mode == null) return;
            glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(handle, NULL, winX, winY, Math.max(winW, 320), Math.max(winH, 240), GLFW_DONT_CARE);
        }
        fullscreen = on;
    }

    /** Resizes the (windowed) window - used by the test controller to check responsive layouts. */
    public void resize(int w, int h) {
        glfwSetWindowSize(handle, w, h);
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

    public float aspectRatio() {
        return height == 0 ? 1f : (float) width / (float) height;
    }
}
