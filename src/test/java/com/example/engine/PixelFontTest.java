package com.example.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/** The font metrics are measured from the glyph sheet like vanilla's bitmap provider does; these tests use a made-up sheet. */
class PixelFontTest {
    private boolean wasLoaded;
    private BufferedImage original;

    @BeforeEach
    void remember() {
        wasLoaded = PixelFont.loaded();
        original = PixelFont.buildSheet();
    }

    @AfterEach
    void restore() {
        PixelFont.useSheet(wasLoaded ? original : null);
    }

    /** A sheet where each listed character has a lit pixel at the given column (so its width is column + 1). */
    private static BufferedImage sheet(Object... charAndColumn) {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < charAndColumn.length; i += 2) {
            char c = (Character) charAndColumn[i];
            int col = (Integer) charAndColumn[i + 1];
            img.setRGB((c % 16) * 8 + col, (c / 16) * 8 + 3, 0xFFFFFFFF);
        }
        return img;
    }

    @Test
    void aGlyphIsAsWideAsItsRightmostLitColumnAndAdvancesOneMore() {
        PixelFont.useSheet(sheet('.', 0, '!', 0, 'W', 4, 'H', 4, 'i', 0));
        assertEquals(1, PixelFont.glyphWidth('.'));
        assertEquals(2, PixelFont.advance('.'), "a period is 1 wide + 1 gap");
        assertEquals(2, PixelFont.advance('!'));
        assertEquals(6, PixelFont.advance('W'));
        assertEquals(PixelFont.advance('H') + PixelFont.advance('i'), PixelFont.textWidth("Hi"));
    }

    @Test
    void aSpaceIsFourWideAndHasNoGlyph() {
        PixelFont.useSheet(sheet(' ', 3, 'a', 3));
        assertEquals(4, PixelFont.advance(' '));
        assertEquals(0, PixelFont.glyphWidth(' '));
        assertEquals(9, PixelFont.LINE_HEIGHT);
    }

    @Test
    void aLitPixelIsReportedWhereTheSheetHasOne() {
        PixelFont.useSheet(sheet('A', 2));
        assertTrue(PixelFont.pixel('A', 2, 3));
        assertFalse(PixelFont.pixel('A', 1, 3));
        assertFalse(PixelFont.pixel('B', 0, 0));
    }

    @Test
    void theSheetHandedToTheGpuIs128x128() {
        PixelFont.useSheet(sheet('A', 0));
        assertEquals(128, PixelFont.buildSheet().getWidth());
        assertEquals(128, PixelFont.buildSheet().getHeight());
    }

    @Test
    void withoutAFontEveryCharacterStillAdvancesSoLayoutsWork() {
        PixelFont.useSheet(null);
        assertFalse(PixelFont.loaded());
        assertEquals(PixelFont.FALLBACK_ADVANCE, PixelFont.advance('A'));
        assertEquals(4, PixelFont.advance(' '));
        assertEquals(0, PixelFont.glyphWidth('A'), "nothing is drawn");
        assertEquals(128, PixelFont.buildSheet().getWidth());
    }

    @Test
    void aSheetOfTheWrongSizeIsRejected() {
        PixelFont.useSheet(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
        assertFalse(PixelFont.loaded());
    }
}
