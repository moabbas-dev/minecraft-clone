package com.example.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The arrow's shape was measured from the real furnace, crafting-table and inventory screens (reference images from
 * minecraft.wiki read pixel by pixel): a 3 px shaft and a triangular head whose base is a full column.
 */
class GuiPainterTest {
    @Test
    void furnaceAndCraftingArrowIs22x15WithA14PixelShaftAndAnEightPixelHead() {
        // row 0 is just the top pixel of the head's base column
        assertArrayEquals(new int[]{14, 15}, GuiPainter.arrowRow(22, 15, 0));
        // one row below the tip row the arrow is 21 wide, the tip row is the full 22
        assertArrayEquals(new int[]{0, 21}, GuiPainter.arrowRow(22, 15, 6));
        assertArrayEquals(new int[]{0, 22}, GuiPainter.arrowRow(22, 15, 7));
        assertArrayEquals(new int[]{0, 21}, GuiPainter.arrowRow(22, 15, 8));
        // rows outside the shaft only contain the head
        assertArrayEquals(new int[]{14, 19}, GuiPainter.arrowRow(22, 15, 4));
        assertArrayEquals(new int[]{14, 15}, GuiPainter.arrowRow(22, 15, 14));
    }

    @Test
    void theHeadGrowsByOnePixelPerRowTowardsTheTip() {
        int previous = 0;
        for (int row = 0; row <= 5; row++) {
            int[] e = GuiPainter.arrowRow(22, 15, row);
            assertEquals(14, e[0]);
            assertEquals(row + 1, e[1] - e[0]);
            previous = e[1] - e[0];
        }
        assertEquals(6, previous);
    }

    @Test
    void theInventoryArrowIsSmaller16x13() {
        assertArrayEquals(new int[]{9, 10}, GuiPainter.arrowRow(16, 13, 0));
        assertArrayEquals(new int[]{0, 16}, GuiPainter.arrowRow(16, 13, 6));
        assertArrayEquals(new int[]{0, 15}, GuiPainter.arrowRow(16, 13, 5));
    }

    @Test
    void theArrowIsSymmetricAboveAndBelowItsCentreRow() {
        for (int[] size : new int[][]{{22, 15}, {16, 13}}) {
            int h = size[1];
            for (int row = 0; row < h; row++) {
                assertArrayEquals(GuiPainter.arrowRow(size[0], h, row), GuiPainter.arrowRow(size[0], h, h - 1 - row));
            }
        }
    }
}
