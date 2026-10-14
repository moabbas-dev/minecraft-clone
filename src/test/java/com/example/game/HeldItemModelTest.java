package com.example.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldItemModelTest {
    private static final float[] UV = {0f, 0f, 1f, 1f};

    private static Vector3f at(Matrix4f m, float x, float y, float z) {
        return m.transformPosition(new Vector3f(x, y, z));
    }

    @Test
    void eatingStartsInPlaceBobsAndEndsAtTheMouth() {
        // at the start (remaining = full use time) the hand has not moved: progress^27 is ~1 so f3 is ~0
        Vector3f start = at(HeldItemModel.eat(32f, 32f, 1), 0f, 0f, 0f);
        assertEquals(0f, start.x, 1e-4f);
        assertEquals(0f, start.y, 1e-4f);
        // partway through the first moments the item is travelling (progress^27 falls fast)
        Vector3f travelling = at(HeldItemModel.eat(31f, 32f, 1), 0f, 0f, 0f);
        assertTrue(travelling.x > 0.05f && travelling.x < 0.6f, "on its way: " + travelling.x);
        // once 20% of the time has passed the item is at the mouth and bobs by |cos(f/4*pi)|*0.1
        Vector3f mid = at(HeldItemModel.eat(22.4f, 32f, 1), 0f, 0f, 0f);
        assertEquals(0.6f, mid.x, 1e-3f);
        float bob = Math.abs((float) Math.cos(22.4f / 4f * Math.PI) * 0.1f);
        assertEquals(-0.5f + bob, mid.y, 1e-3f);
        // at the very end the item is 0.6 across and 0.5 down from the anchor
        Vector3f end = at(HeldItemModel.eat(0.0001f, 32f, 1), 0f, 0f, 0f);
        assertEquals(0.6f, end.x, 1e-3f);
        assertEquals(-0.5f + 0.1f, end.y, 1e-3f, "0.5 down, plus the bob (cos(0) * 0.1)");
    }

    @Test
    void theLeftHandEatsMirrored() {
        Vector3f right = at(HeldItemModel.eat(3f, 32f, 1), 0.2f, 0.3f, 0.1f);
        Vector3f left = at(HeldItemModel.eat(3f, 32f, -1), -0.2f, 0.3f, 0.1f);
        assertEquals(-right.x, left.x, 1e-4f);
        assertEquals(right.y, left.y, 1e-4f);
        assertEquals(right.z, left.z, 1e-4f);
    }

    @Test
    void anIsolatedPixelGetsFourSidesPlusFrontAndBack() {
        boolean[] mask = new boolean[256];
        mask[5 * 16 + 5] = true;
        assertEquals((2 + 4) * 6 * HeldItemModel.FLOATS_PER_VERTEX, HeldItemModel.extrudedSprite(mask, UV).length);
    }

    @Test
    void aFullSpriteOnlyHasSidesOnItsOutline() {
        boolean[] mask = new boolean[256];
        Arrays.fill(mask, true);
        assertEquals((2 + 64) * 6 * HeldItemModel.FLOATS_PER_VERTEX, HeldItemModel.extrudedSprite(mask, UV).length);
    }

    @Test
    void anEmptySpriteStillHasFrontAndBack() {
        assertEquals(12 * HeldItemModel.FLOATS_PER_VERTEX, HeldItemModel.extrudedSprite(new boolean[256], UV).length);
    }

    @Test
    void theSpriteIsOneSixteenthThickAndCentred() {
        boolean[] mask = new boolean[256];
        Arrays.fill(mask, true);
        float[] v = HeldItemModel.extrudedSprite(mask, UV);
        float minZ = 9, maxZ = -9;
        for (int i = 0; i < v.length; i += HeldItemModel.FLOATS_PER_VERTEX) {
            minZ = Math.min(minZ, v[i + 2]);
            maxZ = Math.max(maxZ, v[i + 2]);
            assertTrue(Math.abs(v[i]) <= 0.5001f && Math.abs(v[i + 1]) <= 0.5001f);
        }
        assertEquals(1f / 16f, maxZ - minZ, 1e-6f);
        assertEquals(0f, (maxZ + minZ) / 2f, 1e-6f);
    }

    @Test
    void atRestAFlatItemSitsLowerRightAndForwardOfTheCamera() {
        Vector3f origin = at(HeldItemModel.flatItem(0f), 0, 0, 0);
        assertEquals(0.56f + 1.13f / 16f, origin.x, 1e-4f);
        assertEquals(-0.52f + 3.2f / 16f, origin.y, 1e-4f);
        assertEquals(-0.72f + 1.13f / 16f, origin.z, 1e-4f);
    }

    @Test
    void aHeldBlockIsAnchoredAtVanillasRightHandPointAndScaledToPointFour() {
        Matrix4f m = HeldItemModel.block(0f);
        Vector3f origin = at(m, 0, 0, 0);
        assertEquals(0.56f, origin.x, 1e-4f);
        assertEquals(-0.52f, origin.y, 1e-4f);
        assertEquals(-0.72f, origin.z, 1e-4f);
        assertEquals(0.4f, at(m, 0, 1, 0).sub(origin).length(), 1e-4f, "one block of model space is 0.4 on screen");
    }

    @Test
    void theSwingMovesTheItemTowardsTheCentreAndDownThenBack() {
        Vector3f rest = at(HeldItemModel.flatItem(0f), 0, 0, 0);
        Vector3f mid = at(HeldItemModel.flatItem(0.5f), 0, 0, 0);
        Vector3f end = at(HeldItemModel.flatItem(1f), 0, 0, 0);
        assertTrue(mid.x < rest.x, "swings in toward the middle of the screen");
        assertEquals(rest.x, end.x, 1e-3f);
        assertEquals(rest.y, end.y, 1e-3f);
        assertEquals(rest.z, end.z, 1e-3f);
    }

    @Test
    void theBareArmComesInFromTheLowerRightCorner() {
        Matrix4f m = HeldItemModel.emptyArm(0f);
        // middle of the arm cube in its model space
        Vector3f centre = at(m, -6f / 16f, 6f / 16f, 0f);
        assertTrue(centre.x > 0.1f, "right of centre, was " + centre);
        assertTrue(centre.y < -0.1f, "below centre, was " + centre);
        assertTrue(centre.z < -0.2f, "in front of the camera, was " + centre);
    }
}
