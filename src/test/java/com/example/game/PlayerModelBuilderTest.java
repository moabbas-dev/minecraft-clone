package com.example.game;

import com.example.combat.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerModelBuilderTest {
    private static final PlayerModelBuilder.Pose FRONT = new PlayerModelBuilder.Pose((float) Math.PI, 0f, 0f);
    private static final int V = ItemModelBuilder.FLOATS_PER_VERTEX;

    private static float[] build(PlayerModelBuilder.Pose pose, boolean outer) {
        float[] out = new float[6 * ItemModelBuilder.BOX_VERTICES * V];
        int n = PlayerModelBuilder.emitSkin(out, 0, pose, outer, 1f);
        assertEquals(6 * ItemModelBuilder.BOX_VERTICES * V, n, "head, body, two arms and two legs");
        return out;
    }

    private static float[] range(float[] v, int comp) {
        float lo = 1e9f, hi = -1e9f;
        for (int i = 0; i < v.length; i += V) {
            lo = Math.min(lo, v[i + comp]);
            hi = Math.max(hi, v[i + comp]);
        }
        return new float[]{lo, hi};
    }

    @Test
    void thePlayerStandsOnTheOriginAndIsAboutTwoBlocksTall() {
        float[] y = range(build(FRONT, false), 1);
        assertEquals(0f, y[0], 1e-4f);
        assertEquals(2.0f * PlayerModelBuilder.MODEL_SCALE, y[1], 1e-4f);
    }

    @Test
    void theTextureCoordinatesStayInsideTheSkin() {
        float[] v = build(FRONT, true);
        for (int i = 0; i < v.length; i += V) {
            assertTrue(v[i + 3] >= 0f && v[i + 3] <= 1f);
            assertTrue(v[i + 4] >= 0f && v[i + 4] <= 1f);
        }
    }

    @Test
    void turningTheHeadMovesOnlyTheHeadVertices() {
        float[] straight = build(FRONT, false);
        float[] turned = build(new PlayerModelBuilder.Pose((float) Math.PI, 0.5f, 0f), false);
        int headVertices = ItemModelBuilder.BOX_VERTICES; // the head is the first box
        boolean headMoved = false;
        for (int i = 0; i < straight.length; i += V) {
            boolean moved = Math.abs(straight[i] - turned[i]) > 1e-5f || Math.abs(straight[i + 2] - turned[i + 2]) > 1e-5f;
            if (i / V < headVertices) headMoved |= moved;
            else assertFalse(moved, "vertex " + (i / V) + " belongs to the body, it must not move");
        }
        assertTrue(headMoved);
    }

    @Test
    void theOuterLayerSitsAHairOutsideTheBaseLayer() {
        float[] base = range(build(FRONT, false), 0), outer = range(build(FRONT, true), 0);
        assertTrue(outer[0] < base[0] && outer[1] > base[1]);
    }

    @Test
    void armorBoxesAreGrownByOnePixelAndLeggingsByHalfAPixel() {
        float[] out = new float[6 * ItemModelBuilder.BOX_VERTICES * V];
        int chest = PlayerModelBuilder.emitArmor(out, 0, FRONT, EquipmentSlot.CHEST, 1f);
        float[] bodyAndArms = java.util.Arrays.copyOf(out, chest);
        float width = range(bodyAndArms, 0)[1] - range(bodyAndArms, 0)[0];
        // arms at +-6 px, 4 px wide, +1 px each side: 6+2+1 = 9 px from the centre, scaled by 15/16 and 1/16 px per unit
        float expected = 2 * 9f / 16f * PlayerModelBuilder.MODEL_SCALE;
        assertTrue(width >= expected - 1e-4f && width < expected + 0.2f, "the idle arm sway only swings the arms slightly outward: " + width);

        int legs = PlayerModelBuilder.emitArmor(out, 0, FRONT, EquipmentSlot.LEGS, 1f);
        float[] legBoxes = java.util.Arrays.copyOf(out, legs);
        assertEquals(3 * ItemModelBuilder.BOX_VERTICES * V, legs);
        float legWidth = range(legBoxes, 0)[1] - range(legBoxes, 0)[0];
        assertEquals(2 * (4f + 0.5f) / 16f * PlayerModelBuilder.MODEL_SCALE, legWidth, 1e-4f); // the body (4 px + 0.5) is wider than the legs
        assertEquals(chest, 3 * ItemModelBuilder.BOX_VERTICES * V);
    }
}
