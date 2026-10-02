package com.example.ui;

import com.example.engine.PixelFont;
import com.example.world.Difficulty;
import com.example.world.WorldIO;
import com.example.world.WorldSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class MenuSystemTest {
    // ---- GUI scale (vanilla ScaledResolution rule) ----

    @Test
    void autoGuiScaleIsTheLargestIntegerKeepingAtLeast320x240() {
        assertEquals(1, GuiScale.compute(320, 240, 0));
        assertEquals(1, GuiScale.compute(639, 479, 0));
        assertEquals(2, GuiScale.compute(640, 480, 0));
        assertEquals(2, GuiScale.compute(1279, 719, 0));
        assertEquals(3, GuiScale.compute(1280, 720, 0));
        assertEquals(4, GuiScale.compute(1920, 1080, 0));
        assertEquals(1, GuiScale.compute(100, 100, 0), "tiny windows floor at 1");
    }

    @Test
    void aManualScaleOnlyCapsTheAutoScale() {
        assertEquals(2, GuiScale.compute(1920, 1080, 2));
        assertEquals(4, GuiScale.compute(1920, 1080, 4));
        assertEquals(2, GuiScale.compute(640, 480, 4), "cannot exceed what fits");
    }

    @Test
    void logicalSizeRoundsUp() {
        assertEquals(480, GuiScale.logical(1920, 4));
        assertEquals(427, GuiScale.logical(1280, 3));
    }

    // ---- font metrics (Minecraft-style proportional bitmap font) ----

    @Test
    void everyPrintableAsciiGlyphIsDefinedAndNarrowerThanItsCell() {
        for (char c = 33; c <= 126; c++) {
            assertTrue(PixelFont.glyphWidth(c) >= 1 && PixelFont.glyphWidth(c) <= 5, "glyph " + c);
            boolean lit = false;
            for (int y = 0; y < 8; y++) for (int x = 0; x < PixelFont.glyphWidth(c); x++) lit |= PixelFont.pixel(c, x, y);
            assertTrue(lit, "glyph " + c + " has pixels");
        }
    }

    @Test
    void advanceIsWidthPlusOneAndSpaceIsFour() {
        assertEquals(4, PixelFont.advance(' '));
        assertEquals(2, PixelFont.advance('.'), "a period is 1 wide + 1 gap");
        assertEquals(2, PixelFont.advance('!'));
        assertEquals(6, PixelFont.advance('W'));
        assertEquals(PixelFont.advance('H') + PixelFont.advance('i'), PixelFont.textWidth("Hi"));
        assertEquals(9, PixelFont.LINE_HEIGHT);
    }

    @Test
    void glyphSheetIsTheVanilla128x128With8x8Cells() {
        var sheet = PixelFont.buildSheet();
        assertEquals(128, sheet.getWidth());
        assertEquals(128, sheet.getHeight());
        // 'A' (65) lives in cell column 1, row 4.
        boolean any = false;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) any |= (sheet.getRGB(8 + x, 32 + y) >>> 24) != 0;
        assertTrue(any);
    }

    // ---- options ----

    @Test
    void optionsRoundTripAndClamp(@TempDir File dir) {
        File f = new File(dir, "options.txt");
        GameOptions o = new GameOptions();
        o.fov = 90;
        o.renderDistance = 9;
        o.guiScale = 3;
        o.invertMouse = true;
        o.masterVolume = 0.25f;
        o.save(f);
        GameOptions r = GameOptions.load(f);
        assertEquals(90, r.fov);
        assertEquals(9, r.renderDistance);
        assertEquals(3, r.guiScale);
        assertTrue(r.invertMouse);
        assertEquals(0.25f, r.masterVolume, 1e-6f);
    }

    @Test
    void missingCorruptOrOutOfRangeOptionsFallBackSafely(@TempDir File dir) throws Exception {
        assertEquals(70, GameOptions.load(new File(dir, "none.txt")).fov);
        File bad = new File(dir, "bad.txt");
        java.nio.file.Files.writeString(bad.toPath(), "fov=notanumber\nrenderDistance=999\nmasterVolume=7\nguiScale=-4\n");
        GameOptions o = GameOptions.load(bad);
        assertEquals(70, o.fov);
        assertEquals(12, o.renderDistance);
        assertEquals(1f, o.masterVolume);
        assertEquals(0, o.guiScale);
    }

    @Test
    void defaultMouseSensitivityIsTheOriginalSpeed() {
        assertEquals(1f, new GameOptions().mouseSpeedMultiplier(), 1e-5f);
    }

    // ---- widgets (logic only; drawing needs GL) ----

    @Test
    void buttonOnlyFiresWhenActiveAndInside() {
        int[] n = {0};
        Button b = new Button(10, 10, 100, 20, "x", () -> n[0]++);
        assertFalse(b.mouseClicked(5, 15));
        assertTrue(b.mouseClicked(50, 15));
        b.active = false;
        assertFalse(b.mouseClicked(50, 15));
        assertEquals(1, n[0]);
    }

    @Test
    void sliderMapsTheMouseXToZeroToOneAndDragsOnlyAfterAClick() {
        float[] v = {-1};
        Slider s = new Slider(0, 0, 100, 20, 0.5f, f -> "", f -> v[0] = f);
        s.mouseDragged(50, 5);
        assertEquals(-1f, v[0], "no change before a click");
        assertTrue(s.mouseClicked(4, 5));
        assertEquals(0f, v[0], 1e-6f);
        s.mouseDragged(96, 5);
        assertEquals(1f, v[0], 1e-6f);
        s.mouseDragged(500, 5);
        assertEquals(1f, s.value(), "clamped");
        s.mouseReleased();
        s.mouseDragged(10, 5);
        assertEquals(1f, s.value(), "released: no more dragging");
    }

    @Test
    void textFieldTypesFilteredCharsAndBackspacesOnlyWhenFocused() {
        StringBuilder sb = new StringBuilder("ab");
        TextField f = new TextField(0, 0, 100, 20, sb, 4);
        f.charsTyped("zz");
        assertEquals("ab", sb.toString(), "unfocused ignores typing");
        f.mouseClicked(5, 5);
        f.charsTyped("cdée");
        assertEquals("abcd", sb.toString(), "max length and ASCII-only");
        f.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
        assertEquals("abc", sb.toString());
        f.mouseClicked(500, 500);
        f.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
        assertEquals("abc", sb.toString(), "click outside unfocuses");
    }

    // ---- world creation / editing ----

    @Test
    void seedTextFollowsVanillaRules() {
        assertNull(WorldCreateScreen.parseSeed("   "));
        assertEquals(12345L, WorldCreateScreen.parseSeed(" 12345 "));
        assertEquals(-7L, WorldCreateScreen.parseSeed("-7"));
        assertEquals((long) "hello".hashCode(), WorldCreateScreen.parseSeed("hello"));
    }

    @Test
    void renamingAWorldChangesOnlyItsNameAndKeepsEverythingElse(@TempDir File dir) {
        WorldSettings s = new WorldSettings("Old Name", WorldSettings.WorldType.FLAT, Difficulty.HARD);
        WorldIO.saveMeta(dir, s, 99L, 1.5f, 2.5f, 3.5f, 0.4f, 3, new com.example.inventory.Inventory(),
                new WorldIO.SurvivalSnapshot(11f, 12f, 3f, 0.5f));
        assertTrue(WorldIO.renameWorld(dir, "  Brand New  "));
        WorldIO.LoadedMeta m = WorldIO.loadMeta(dir);
        assertEquals("Brand New", m.settings().name());
        assertEquals(WorldSettings.WorldType.FLAT, m.settings().type());
        assertEquals(Difficulty.HARD, m.settings().difficulty());
        assertEquals(99L, m.seed());
        assertEquals(2.5f, m.playerPos().y);
        assertEquals(3, m.selectedIndex());
        assertEquals(11f, m.survival().health());
        assertFalse(WorldIO.renameWorld(dir, "   "), "blank names are rejected");
        assertFalse(WorldIO.renameWorld(new File(dir, "missing"), "x"));
    }
}
