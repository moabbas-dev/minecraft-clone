package com.example.engine;

import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;

public class Input implements InputState {
    private final long window;
    private final boolean[] keysDown = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keysPressedEdge = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] mouseDown = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mousePressedEdge = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final StringBuilder typedChars = new StringBuilder();

    private double lastMouseX, lastMouseY;
    private double mouseDeltaX, mouseDeltaY;
    private boolean firstMouse = true;
    private double scrollDelta;

    // GLFW reports cursor position in "screen coordinates", but window.getWidth()/getHeight()
    // (and therefore all UI layout/hit-testing) is in framebuffer pixels; the two differ under
    // OS display scaling (e.g. Windows set to 125%). Without this, button hit-testing silently
    // used the wrong coordinate space, making menus feel unresponsive on scaled displays.
    private double cursorScaleX = 1.0, cursorScaleY = 1.0;

    public Input(long window) {
        this.window = window;
        refreshCursorScale();

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key < 0 || key > GLFW_KEY_LAST) return;
            if (action == GLFW_PRESS) {
                keysDown[key] = true;
                keysPressedEdge[key] = true;
            } else if (action == GLFW_RELEASE) {
                keysDown[key] = false;
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double sx = xpos * cursorScaleX;
            double sy = ypos * cursorScaleY;
            if (firstMouse) {
                lastMouseX = sx;
                lastMouseY = sy;
                firstMouse = false;
            }
            mouseDeltaX += sx - lastMouseX;
            mouseDeltaY += sy - lastMouseY;
            lastMouseX = sx;
            lastMouseY = sy;
        });

        glfwSetWindowSizeCallback(window, (win, w, h) -> refreshCursorScale());

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
            if (action == GLFW_PRESS) {
                mouseDown[button] = true;
                mousePressedEdge[button] = true;
            } else if (action == GLFW_RELEASE) {
                mouseDown[button] = false;
            }
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> scrollDelta += yoffset);

        glfwSetCharCallback(window, (win, codepoint) -> typedChars.appendCodePoint(codepoint));
    }

    @Override
    public boolean isKeyDown(int key) {
        return keysDown[key];
    }

    @Override
    public boolean consumeKeyPressed(int key) {
        boolean v = keysPressedEdge[key];
        keysPressedEdge[key] = false;
        return v;
    }

    public boolean isMouseDown(int button) {
        return mouseDown[button];
    }

    public boolean consumeMousePressed(int button) {
        boolean v = mousePressedEdge[button];
        mousePressedEdge[button] = false;
        return v;
    }

    public double getMouseDeltaX() {
        return mouseDeltaX;
    }

    public double getMouseDeltaY() {
        return mouseDeltaY;
    }

    public double getScrollDelta() {
        return scrollDelta;
    }

    public double getMouseX() {
        return lastMouseX;
    }

    public double getMouseY() {
        return lastMouseY;
    }

    /** Returns and clears the Unicode text typed since the last call (for text fields). */
    public String consumeTypedChars() {
        String s = typedChars.toString();
        typedChars.setLength(0);
        return s;
    }

    /**
     * Re-reads the OS cursor position directly. Needed right after toggling cursor
     * capture mode, since the stored position otherwise stays stale (an unbounded
     * virtual coordinate accumulated while captured) until the next mouse-move event.
     */
    public void resyncMousePosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xBuf = stack.mallocDouble(1);
            DoubleBuffer yBuf = stack.mallocDouble(1);
            glfwGetCursorPos(window, xBuf, yBuf);
            lastMouseX = xBuf.get(0) * cursorScaleX;
            lastMouseY = yBuf.get(0) * cursorScaleY;
        }
    }

    /** Recomputes the screen-coordinates-to-pixels ratio from GLFW's own reported sizes. */
    private void refreshCursorScale() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            glfwGetWindowSize(window, winW, winH);
            IntBuffer fbW = stack.mallocInt(1), fbH = stack.mallocInt(1);
            glfwGetFramebufferSize(window, fbW, fbH);
            if (winW.get(0) > 0) cursorScaleX = (double) fbW.get(0) / winW.get(0);
            if (winH.get(0) > 0) cursorScaleY = (double) fbH.get(0) / winH.get(0);
        }
    }

    /** Call once per frame after consuming deltas for the frame. */
    public void endFrame() {
        mouseDeltaX = 0;
        mouseDeltaY = 0;
        scrollDelta = 0;
    }
}
