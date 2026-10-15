package com.example.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadAndWalkTest {
    private static final float DEG = (float) Math.PI / 180f;

    private static void run(HeadControl h, float seconds, float yaw, float moved, boolean lookRequest, float tx, float ty, float tz) {
        for (float t = 0; t < seconds; t += 0.05f) {
            if (lookRequest) h.lookAt(tx, ty, tz);
            h.advance(0.05f, yaw, moved, false, 0f, 1f, 0f);
        }
    }

    /** Reads the state at the end of the current tick (partial tick ~1) instead of the previous tick. */
    private static void settle(HeadControl h) {
        h.advance(0.0499f, 0f, 0f, false, 0f, 1f, 0f);
    }

    @Test
    void theHeadTurnsAtMostTenDegreesPerTickTowardsWhatItLooksAt() {
        HeadControl h = new HeadControl();
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f); // start facing -Z
        // a point to the east (+X): the target yaw is +90 degrees
        h.lookAt(5f, 1f, 0f);
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        settle(h);
        assertEquals(10f * DEG, h.netHeadYaw(), 1e-3f, "one tick = 10 degrees");
        run(h, 0.5f, 0f, 0f, true, 5f, 1f, 0f);
        assertEquals(90f * DEG, h.netHeadYaw() + 0f, 0.35f, "after 0.5 s it is nearly there (the body follows past 75)");
    }

    @Test
    void theBodyStaysPutUntilTheHeadIsMoreThan75DegreesAway() {
        HeadControl h = new HeadControl();
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        run(h, 0.3f, 0f, 0f, true, 5f, 1f, 0f); // 6 ticks = 60 degrees
        assertEquals(0f, h.bodyYaw(0f), 1e-3f);
        run(h, 0.4f, 0f, 0f, true, 5f, 1f, 0f);
        assertTrue(h.bodyYaw(0f) > 0.05f, "the body now follows the head: " + h.bodyYaw(0f));
        settle(h);
        assertTrue(Math.abs(h.netHeadYaw()) <= 75f * DEG + 0.2f, "the head never gets past 75 degrees of the body");
    }

    @Test
    void theHeadReturnsToTheBodyWhenNothingIsLookedAt() {
        HeadControl h = new HeadControl();
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        run(h, 0.3f, 0f, 0f, true, 5f, 1f, 0f);
        run(h, 1.5f, 0f, 0f, false, 0f, 0f, 0f);
        assertEquals(0f, h.netHeadYaw(), 0.1f);
    }

    @Test
    void whileWalkingTheBodyTakesTheMovementHeading() {
        HeadControl h = new HeadControl();
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        run(h, 0.2f, 1f, 0.1f, false, 0f, 0f, 0f);
        assertEquals(1f, h.bodyYaw(0f), 1e-3f);
    }

    @Test
    void theHeadPitchesTowardsALowerOrHigherTarget() {
        HeadControl h = new HeadControl();
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        h.lookAt(0f, 0f, -2f); // 1 block below the eye, 2 ahead
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        settle(h);
        assertEquals((float) Math.atan2(1.0, 2.0), h.headPitch(), 1e-3f, "positive = looking down");
        h.lookAt(0f, 3f, -2f);
        h.advance(0.05f, 0f, 0f, false, 0f, 1f, 0f);
        settle(h);
        assertTrue(h.headPitch() < 0f, "looking up is negative");
    }

    @Test
    void theWalkAnimationEasesTowardsFourTimesTheDistancePerTick() {
        WalkAnimation w = new WalkAnimation();
        for (int i = 0; i < 40; i++) w.advance(0.05f, 0.1f, false); // 0.1 blocks per tick -> target 0.4
        assertEquals(0.4f, w.speed(), 0.02f);
        float pos = w.position();
        for (int i = 0; i < 10; i++) w.advance(0.05f, 0.1f, false);
        assertEquals(pos + 10 * 0.4f, w.position(), 0.2f, "the position advances by the speed every tick");
    }

    @Test
    void babiesAdvanceTheirLimbsThreeTimesAsFast() {
        WalkAnimation adult = new WalkAnimation(), baby = new WalkAnimation();
        for (int i = 0; i < 20; i++) {
            adult.advance(0.05f, 0.1f, false);
            baby.advance(0.05f, 0.1f, true);
        }
        assertEquals(adult.position() * 3f, baby.position(), 1e-3f);
    }

    @Test
    void aStoppedWalkerHasNoSpeed() {
        WalkAnimation w = new WalkAnimation();
        for (int i = 0; i < 10; i++) w.advance(0.05f, 0.2f, false);
        w.stop();
        assertEquals(0f, w.speed(), 0f);
        assertEquals(0f, w.position(), 0f);
    }
}
