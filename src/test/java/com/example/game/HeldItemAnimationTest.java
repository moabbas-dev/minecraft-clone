package com.example.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The equip animation, the real-skin arm and the dropped-item sprite placement. */
class HeldItemAnimationTest {
    private static Vector3f origin(Matrix4f m) {
        return m.transformPosition(new Vector3f());
    }

    @Test
    void equipDropIsFullyLoweredAtZeroAndGoneAtOneAndEasesOut() {
        assertEquals(1f, HeldItemModel.equipDrop(0f), 1e-6f);
        assertEquals(0f, HeldItemModel.equipDrop(1f), 1e-6f);
        float previous = 1f;
        for (int i = 1; i <= 10; i++) {
            float d = HeldItemModel.equipDrop(i / 10f);
            assertTrue(d < previous, "the hand only ever rises while the progress grows");
            previous = d;
        }
        // ease-out: more than half of the way up after the first third of the time
        assertTrue(HeldItemModel.equipDrop(1f / 3f) < 0.5f);
    }

    @Test
    void aLoweredHandSitsSixTenthsOfABlockLowerForEveryHeldKind() {
        float flat = origin(HeldItemModel.flatItem(0f)).y - origin(HeldItemModel.flatItem(0f, 1f)).y;
        float block = origin(HeldItemModel.block(0f)).y - origin(HeldItemModel.block(0f, 1f)).y;
        float arm = origin(HeldItemModel.emptyArm(0f)).y - origin(HeldItemModel.emptyArm(0f, 1f)).y;
        assertEquals(0.6f, flat, 1e-4f);
        assertEquals(0.6f, block, 1e-4f);
        assertEquals(0.6f, arm, 1e-4f);
    }

    @Test
    void theArmIsTextureMappedOntoTheRightArmRectangleOfTheSkin() {
        float[] v = HeldItemModel.armBox();
        assertEquals(36 * HeldItemModel.FLOATS_PER_VERTEX, v.length);
        for (int i = 0; i < v.length; i += HeldItemModel.FLOATS_PER_VERTEX) {
            float u = v[i + 3], t = v[i + 4];
            assertTrue(u >= 40f / 64f - 1e-6f && u <= 56f / 64f + 1e-6f, "u " + u);
            assertTrue(t >= 16f / 64f - 1e-6f && t <= 32f / 64f + 1e-6f, "v " + t);
        }
    }

    @Test
    void aDroppedSpriteIsScaledTurnedAndCentredOnItsPosition() {
        boolean[] mask = new boolean[256];
        java.util.Arrays.fill(mask, true);
        float[] local = HeldItemModel.extrudedSprite(mask, new float[]{0f, 0f, 1f, 1f});
        float[] out = new float[local.length];
        int n = ItemModelBuilder.emitSprite(out, 0, local, 10f, 5f, 20f, 0.5f, 1f, 0f, 1f, 0.25f);
        assertEquals(local.length, n);
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
        for (int i = 0; i < n; i += ItemModelBuilder.FLOATS_PER_VERTEX) {
            minX = Math.min(minX, out[i]);
            maxX = Math.max(maxX, out[i]);
            minY = Math.min(minY, out[i + 1]);
            maxY = Math.max(maxY, out[i + 1]);
            assertEquals(0.25f, out[i + 6], 1e-6f, "block light is the world's, not the baked face shade");
            assertTrue(out[i + 5] <= 1f && out[i + 5] > 0f, "sky light is dimmed by the face's own shade");
        }
        assertEquals(10f - 0.25f, minX, 1e-4f);
        assertEquals(10f + 0.25f, maxX, 1e-4f);
        assertEquals(5f - 0.25f, minY, 1e-4f);
        assertEquals(5f + 0.25f, maxY, 1e-4f);
    }
}
