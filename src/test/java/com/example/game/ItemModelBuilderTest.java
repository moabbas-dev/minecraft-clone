package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemModelBuilderTest {
    private static final float[] UV = {0.25f, 0.5f, 0.375f, 0.625f};
    private static final float[] TINT = {1f, 1f, 1f};
    private static final ItemModelBuilder.Model CUBE = new ItemModelBuilder.Model(UV, UV, UV, TINT, TINT, false);
    private static final ItemModelBuilder.Model CROSS = new ItemModelBuilder.Model(UV, UV, UV, TINT, TINT, true);

    @Test
    void aCubeWritesThirtySixVerticesAndNeverMoreThanTheDocumentedMaximum() {
        float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        int n = ItemModelBuilder.emitCube(out, 0, 5f, 6f, 7f, 0.125f, 1f, 0f, CUBE, 1f, 0f);
        assertEquals(ItemModelBuilder.CUBE_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX, n);
    }

    @Test
    void aGrassLikeModelAddsATintedOverlayQuadOnEachOfTheFourSides() {
        float[] overlayUv = {0.5f, 0.5f, 0.625f, 0.625f};
        float[] grassTint = {0.4f, 0.8f, 0.3f};
        ItemModelBuilder.Model grass = new ItemModelBuilder.Model(UV, UV, UV, TINT, TINT, false, overlayUv, grassTint);
        float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        int n = ItemModelBuilder.emitCube(out, 0, 0, 0, 0, 0.125f, 1f, 0f, grass, 1f, 0f);
        assertEquals(ItemModelBuilder.CUBE_WITH_OVERLAY_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX, n);
        int base = ItemModelBuilder.CUBE_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX;
        assertEquals(grassTint[1], out[base + 8], 1e-6f, "overlay vertices carry the grass tint");
        assertTrue(out[base + 3] >= overlayUv[0] && out[base + 3] <= overlayUv[2], "overlay vertices sample the overlay tile");
    }

    @Test
    void anArmBoxWritesThirtySixVerticesWithinItsHalfExtents() {
        float[] out = new float[ItemModelBuilder.BOX_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX];
        int n = ItemModelBuilder.emitBox(out, 0, 0.14f, 0.2f, 0.6f, UV, TINT);
        assertEquals(ItemModelBuilder.BOX_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX, n);
        float maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < n; i += ItemModelBuilder.FLOATS_PER_VERTEX) {
            maxX = Math.max(maxX, Math.abs(out[i]));
            maxY = Math.max(maxY, Math.abs(out[i + 1]));
            maxZ = Math.max(maxZ, Math.abs(out[i + 2]));
            assertTrue(out[i + 3] >= UV[0] && out[i + 3] <= UV[2]);
        }
        assertEquals(0.14f, maxX, 1e-6f);
        assertEquals(0.2f, maxY, 1e-6f);
        assertEquals(0.6f, maxZ, 1e-6f);
    }

    @Test
    void aCrossWritesTwelveVertices() {
        float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        int n = ItemModelBuilder.emitCross(out, 0, 0, 0, 0, 0.15f, 1f, 0f, CROSS, 1f, 0f);
        assertEquals(ItemModelBuilder.CROSS_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX, n);
    }

    @Test
    void everyVertexStaysWithinTheItemsSmallBoundingSphereAtAnyRotation() {
        float half = 0.125f;
        float radius = half * (float) Math.sqrt(3) + 1e-4f;
        for (int deg = 0; deg < 360; deg += 30) {
            float cos = (float) Math.cos(Math.toRadians(deg)), sin = (float) Math.sin(Math.toRadians(deg));
            float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
            int n = ItemModelBuilder.emitCube(out, 0, 10f, 20f, 30f, half, cos, sin, CUBE, 1f, 0f);
            for (int i = 0; i < n; i += ItemModelBuilder.FLOATS_PER_VERTEX) {
                float dx = out[i] - 10f, dy = out[i + 1] - 20f, dz = out[i + 2] - 30f;
                assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius, "vertex too far at " + deg + " degrees");
            }
        }
    }

    @Test
    void uvCoordinatesStayInsideTheSuppliedRect() {
        float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        int n = ItemModelBuilder.emitCube(out, 0, 0, 0, 0, 0.125f, 1f, 0f, CUBE, 1f, 0f);
        for (int i = 0; i < n; i += ItemModelBuilder.FLOATS_PER_VERTEX) {
            assertTrue(out[i + 3] >= UV[0] && out[i + 3] <= UV[2]);
            assertTrue(out[i + 4] >= UV[1] && out[i + 4] <= UV[3]);
        }
    }

    @Test
    void writingAdvancesFromTheGivenIndexSoSeveralItemsCanShareOneArray() {
        float[] out = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM * 2];
        int n = ItemModelBuilder.emitCube(out, 0, 0, 0, 0, 0.125f, 1f, 0f, CUBE, 1f, 0f);
        int m = ItemModelBuilder.emitCube(out, n, 5, 0, 0, 0.125f, 1f, 0f, CUBE, 1f, 0f);
        assertEquals(ItemModelBuilder.CUBE_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX * 2, m);
    }

    @Test
    void wholeItemsRotateAroundTheirOwnVerticalAxisWithoutChangingHeight() {
        float[] a = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        float[] b = new float[ItemModelBuilder.MAX_FLOATS_PER_ITEM];
        ItemModelBuilder.emitCube(a, 0, 0, 0, 0, 0.125f, 1f, 0f, CUBE, 1f, 0f);
        ItemModelBuilder.emitCube(b, 0, 0, 0, 0, 0.125f, 0f, 1f, CUBE, 1f, 0f); // 90 degrees
        for (int i = 0; i < a.length; i += ItemModelBuilder.FLOATS_PER_VERTEX) {
            assertEquals(a[i + 1], b[i + 1], 1e-5f, "rotation must not change Y");
        }
    }
}
