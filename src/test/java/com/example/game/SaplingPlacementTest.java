package com.example.game;

import com.example.world.Raycast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link Game#isDirectlyAboveGround}, the pure geometric half of sapling placement
 * validation, directly - no Player/GL context needed. The player-intersection half
 * ({@code blockIntersectsPlayer}) is untested here, matching this codebase's existing precedent:
 * no block type's placement path has ever unit-tested that check.
 */
class SaplingPlacementTest {

    @Test
    void directlyOnTopOfTheTargetedBlockIsAllowed() {
        Raycast.Hit hit = new Raycast.Hit(5, 10, 5, 5, 11, 5, com.example.world.BlockType.GRASS_BLOCK);
        assertTrue(Game.isDirectlyAboveGround(hit));
    }

    @Test
    void placingAgainstASideFaceIsRejected() {
        // placeX differs from the targeted block's x - placing against a side face, not on top.
        Raycast.Hit hit = new Raycast.Hit(5, 10, 5, 6, 10, 5, com.example.world.BlockType.GRASS_BLOCK);
        assertFalse(Game.isDirectlyAboveGround(hit));
    }

    @Test
    void placingUnderneathIsRejected() {
        Raycast.Hit hit = new Raycast.Hit(5, 10, 5, 5, 9, 5, com.example.world.BlockType.GRASS_BLOCK);
        assertFalse(Game.isDirectlyAboveGround(hit));
    }

    @Test
    void placingDiagonallyIsRejected() {
        Raycast.Hit hit = new Raycast.Hit(5, 10, 5, 6, 11, 6, com.example.world.BlockType.GRASS_BLOCK);
        assertFalse(Game.isDirectlyAboveGround(hit));
    }
}
