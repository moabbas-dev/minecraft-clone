package com.example.physics.gameplay;

import com.example.engine.InputState;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Edge-triggered like the real Input class: consumeKeyPressed fires once per press, not once
 * per tick held. Shared across the gameplay test package so every Player.update-driving test
 * uses the exact same fake rather than each file rolling its own.
 */
final class FakeInputState implements InputState {
    private final boolean[] down = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] pressedEdge = new boolean[GLFW_KEY_LAST + 1];

    void setKeyDown(int key, boolean isDown) {
        if (isDown && !down[key]) pressedEdge[key] = true;
        down[key] = isDown;
    }

    @Override
    public boolean isKeyDown(int key) {
        return down[key];
    }

    @Override
    public boolean consumeKeyPressed(int key) {
        boolean v = pressedEdge[key];
        pressedEdge[key] = false;
        return v;
    }
}
