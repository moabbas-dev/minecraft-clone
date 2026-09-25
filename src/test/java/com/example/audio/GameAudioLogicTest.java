package com.example.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises GameAudio's pure decision predicates directly - never touches AudioEngine/OpenAL
 *  (see the class docs on GameAudio for why these are extracted as static methods). */
class GameAudioLogicTest {

    @Test
    void footstepDoesNotTriggerBelowThreshold() {
        assertFalse(GameAudio.shouldTriggerFootstep(0.5f, GameAudio.STEP_DISTANCE));
    }

    @Test
    void footstepTriggersAtOrAboveThreshold() {
        assertTrue(GameAudio.shouldTriggerFootstep(GameAudio.STEP_DISTANCE, GameAudio.STEP_DISTANCE));
        assertTrue(GameAudio.shouldTriggerFootstep(GameAudio.STEP_DISTANCE + 0.5f, GameAudio.STEP_DISTANCE));
    }

    @Test
    void sneakingUsesALongerThresholdThanWalking() {
        assertTrue(GameAudio.footstepThreshold(true) > GameAudio.footstepThreshold(false));
    }

    @Test
    void lightningStrikeDetectedOnlyWhenFlashRisesFromPreviousFrame() {
        // A fresh strike: flash jumps from a low decaying value up to 1f.
        assertTrue(GameAudio.isLightningStrikeThisFrame(1f, 0.2f));
        // Still decaying, no new strike.
        assertFalse(GameAudio.isLightningStrikeThisFrame(0.3f, 0.5f));
        // Unchanged (e.g. already at 0).
        assertFalse(GameAudio.isLightningStrikeThisFrame(0f, 0f));
    }

    @Test
    void undergroundDetectedAtOrBelowSkyLightThreshold() {
        assertTrue(GameAudio.isUnderground(0));
        assertTrue(GameAudio.isUnderground(GameAudio.CAVE_SKYLIGHT_THRESHOLD));
        assertFalse(GameAudio.isUnderground(GameAudio.CAVE_SKYLIGHT_THRESHOLD + 1));
        assertFalse(GameAudio.isUnderground(15));
    }
}
