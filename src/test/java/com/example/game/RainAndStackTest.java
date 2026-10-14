package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainAndStackTest {
    @Test
    void rainIsFullStrengthAtTheCameraAndHalfAtTheEdge() {
        assertEquals(1f, Game.rainColumnAlpha(0f, 1f), 1e-6f);
        assertEquals(0.5f, Game.rainColumnAlpha(1f, 1f), 1e-6f);
        assertEquals(0.5f, Game.rainColumnAlpha(0f, 0.5f), 1e-6f, "scaled by the rain level");
        assertTrue(Game.rainColumnAlpha(0.3f, 1f) > Game.rainColumnAlpha(0.7f, 1f));
    }

    @Test
    void droppedStacksAreDrawnWithOneToFiveCopies() {
        assertEquals(1, Game.renderCopies(1));
        assertEquals(2, Game.renderCopies(2));
        assertEquals(2, Game.renderCopies(16));
        assertEquals(3, Game.renderCopies(17));
        assertEquals(3, Game.renderCopies(32));
        assertEquals(4, Game.renderCopies(33));
        assertEquals(4, Game.renderCopies(48));
        assertEquals(5, Game.renderCopies(49));
        assertEquals(5, Game.renderCopies(64));
    }
}
