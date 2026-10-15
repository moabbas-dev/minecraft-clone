package com.example.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobModelPoseTest {
    @Test
    void aJointLeavesItsPivotWhereItIs() {
        MobModelBuilder.Writer w = MobModelBuilder.writer(new float[16], 0, 0f, 1f, new float[]{1, 1, 1}, 64, 32);
        Matrix4f m = w.joint(-3f, 18f, 7f, 0.8f, 0.3f, 0.2f);
        Vector3f pivot = new Vector3f(3f / 16f, 6f / 16f, 7f / 16f); // model (-3, 18, 7) in our frame: x flipped, y up from the ground
        Vector3f moved = m.transformPosition(new Vector3f(pivot));
        assertEquals(pivot.x, moved.x, 1e-5f);
        assertEquals(pivot.y, moved.y, 1e-5f);
        assertEquals(pivot.z, moved.z, 1e-5f);
    }

    @Test
    void aPositiveLegAngleSwingsTheFootBackwards() {
        MobModelBuilder.Writer w = MobModelBuilder.writer(new float[16], 0, 0f, 1f, new float[]{1, 1, 1}, 64, 32);
        Matrix4f m = w.joint(-3f, 18f, 7f, 0.5f, 0f, 0f);
        Vector3f foot = m.transformPosition(new Vector3f(3f / 16f, 0f, 7f / 16f)); // the foot, straight below the hip
        assertTrue(foot.z > 7f / 16f + 0.1f, "vanilla's positive xRot moves the foot towards +Z (the back): " + foot.z);
        assertTrue(foot.y > 0.0f, "and lifts it");
    }

    @Test
    void aHeadTurnedClockwiseLooksTowardsPlusX() {
        MobModelBuilder.Writer w = MobModelBuilder.writer(new float[16], 0, 0f, 1f, new float[]{1, 1, 1}, 64, 32);
        Matrix4f m = w.joint(0f, 12f, -6f, 0f, 0.5f, 0f);
        Vector3f nose = m.transformPosition(new Vector3f(0f, 0.75f, -14f / 16f)); // a point in front of the head
        assertTrue(nose.x > 0.1f, "a positive head yaw (clockwise from above) turns the face towards +X: " + nose.x);
    }

    @Test
    void theLegsOfAQuadrupedSwingInDiagonalPairs() {
        float[] leg = MobModelBuilder.quadLegAngles(2f, 1f);
        assertEquals(leg[0], leg[3], 1e-6f, "right hind and left front go together");
        assertEquals(leg[1], leg[2], 1e-6f, "left hind and right front go together");
        assertEquals(-leg[0], leg[1], 1e-5f, "and the pairs are opposite");
        assertEquals(1.4f, Math.abs(MobModelBuilder.quadLegAngles(0f, 1f)[0]), 1e-6f, "the swing reaches 1.4 radians at full speed");
        assertEquals(0f, MobModelBuilder.quadLegAngles(3f, 0f)[0], 0f, "no speed, no swing");
    }

    @Test
    void aWalkingZombieRaisesItsArmsEightyDegreesOrAHundredTwentyWhenAttacking() {
        assertEquals(-80f, (float) Math.toDegrees(MobModelBuilder.zombieArmRaise(false)), 0.01f);
        assertEquals(-120f, (float) Math.toDegrees(MobModelBuilder.zombieArmRaise(true)), 0.01f);
    }

    @Test
    void everyEmitterFitsTheBoxBudget() {
        float[] buf = new float[MobModelBuilder.FLOATS_PER_MOB];
        float[] tint = {1, 1, 1};
        MobModelBuilder.Pose pose = new MobModelBuilder.Pose(3f, 0.7f, 0.4f, 0.2f, 50f, true, 1.2f, 0.8f);
        assertTrue(MobModelBuilder.emitPig(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitCow(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitChicken(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitBabyChicken(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitZombie(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitSkeleton(buf, 0, 0, 0, 0, 0, 0, pose, 1, 0, tint) <= buf.length);
        assertTrue(MobModelBuilder.emitCreeper(buf, 0, 0, 0, 0, 0, 0, pose, 0.5f, 1, 0, tint) <= buf.length);
    }
}
