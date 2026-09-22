package com.example.engine;

/**
 * The subset of {@link Input} that gameplay logic actually reads. Extracted so systems like
 * {@link com.example.physics.Player} can be driven by a real GLFW-backed {@link Input} at
 * runtime or by a plain test double in a headless JUnit test - {@link Input} itself still
 * requires a live GLFW window handle to construct, so it can't be used directly in tests.
 */
public interface InputState {
    boolean isKeyDown(int key);

    /** True only on the frame the key transitioned to pressed; consumes the edge. */
    boolean consumeKeyPressed(int key);
}
