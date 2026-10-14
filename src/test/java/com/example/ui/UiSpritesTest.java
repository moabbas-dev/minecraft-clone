package com.example.ui;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class UiSpritesTest {
    @Test
    void aNineSliceKeepsItsCornersAndStretchesTheMiddle() {
        // a 200x20 button sprite with a 3 px border drawn at 100x20: nine cells, the middle one 94 wide
        float[][] cells = UiSprites.nineSliceCells(10, 20, 100, 20, 200, 20, 3, 3, 3, 3);
        assertEquals(9, cells.length);
        float[] topLeft = cells[0], topMiddle = cells[1], topRight = cells[2];
        assertEquals(3, topLeft[2] - topLeft[0], 1e-4f, "the corner is 3 px");
        assertEquals(94, topMiddle[2] - topMiddle[0], 1e-4f, "the middle takes the rest");
        assertEquals(3, topRight[2] - topRight[0], 1e-4f);
        assertEquals(0f, topLeft[4], 1e-6f);
        assertEquals(3f / 200f, topLeft[6], 1e-6f, "the corner samples the first 3 texels");
        assertEquals(1f, topRight[6], 1e-6f);
        assertEquals(110f, topRight[2], 1e-4f);
    }

    @Test
    void aWidgetSmallerThanItsBordersSharesThemInsteadOfOverlapping() {
        float[][] cells = UiSprites.nineSliceCells(0, 0, 4, 20, 200, 20, 3, 3, 3, 3);
        float total = 0;
        int inFirstRow = 0;
        for (float[] c : cells) {
            if (c[1] != cells[0][1]) continue; // only the cells of the top row
            total += c[2] - c[0];
            inFirstRow++;
        }
        assertEquals(2, inFirstRow, "no room for a middle column");
        assertEquals(4f, total, 1e-4f, "the cells of a row add up to the widget width");
        assertTrue(cells[0][2] - cells[0][0] <= 2f + 1e-4f);
    }

    @Test
    void theSliderHandleUsesDifferentBorders() {
        float[][] cells = UiSprites.nineSliceCells(0, 0, 8, 20, 8, 20, 2, 2, 2, 3);
        assertEquals(9, cells.length);
        assertEquals(4f, cells[1][2] - cells[1][0], 1e-4f, "the 8 px handle keeps a 4 px middle between its 2 px sides");
        float[] bottomLeft = cells[cells.length - 3];
        assertEquals(3f, bottomLeft[3] - bottomLeft[1], 1e-4f, "the bottom border is 3 px");
    }

    @Test
    void splashesAreReadFromTheListAndOnlyPlainAscii(@org.junit.jupiter.api.io.TempDir File dir) throws Exception {
        File f = new File(dir, "splashes.txt");
        Files.writeString(f.toPath(), "Awesome!\n\n  Best in class!  \nnon-ascii é\n");
        List<String> all = Splashes.load(f);
        assertEquals(List.of("Awesome!", "Best in class!"), all);
        assertTrue(all.contains(Splashes.pick(all, new Random(1))));
        assertEquals("", Splashes.pick(List.of(), new Random(1)));
        assertTrue(Splashes.load(new File(dir, "missing.txt")).isEmpty());
    }
}
