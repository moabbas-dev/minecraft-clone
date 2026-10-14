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
    void aDroppedItemBobsBetween0_0625And0_2625AboveItsPosition() {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float age = 0f; age < 10f; age += 0.01f) {
            float h = Game.droppedItemHover(age, 1.3f);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertEquals(0.0625f, min, 1e-3f);
        assertEquals(0.2625f, max, 1e-3f);
        // one full bob takes 20 * pi ticks (sin(ticks / 10)), about 3.14 s
        assertEquals(Game.droppedItemHover(0.4f, 0.7f), Game.droppedItemHover(0.4f + (float) Math.PI, 0.7f), 1e-4f);
    }

    @Test
    void aDroppedItemSpinsOneRadianPerSecondFromItsOwnPhase() {
        assertEquals(2f, Game.droppedItemSpin(2f, 0f), 1e-6f);
        assertEquals(2.5f, Game.droppedItemSpin(2f, 0.5f), 1e-6f);
        float p = Game.droppedItemPhase(12345);
        assertTrue(p >= 0f && p < 2f * (float) Math.PI);
        assertEquals(p, Game.droppedItemPhase(12345), 0f, "the phase belongs to the entity, so it stays the same every frame");
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
