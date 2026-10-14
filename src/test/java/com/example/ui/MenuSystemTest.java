package com.example.ui;

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
        assertEquals(GameOptions.MAX_RENDER_DISTANCE, o.renderDistance);
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

    @Test
    void optionsAreWrittenInTheVanillaKeyColonValueFormat(@TempDir File dir) throws Exception {
        GameOptions o = new GameOptions();
        o.fov = 90;
        o.gamma = 1f;
        o.maxFps = GameOptions.UNLIMITED_FPS;
        o.toggleSprint = true;
        o.hostileVolume = 0.25f;
        o.keys.set(com.example.ui.KeyBindings.Bind.DROP, org.lwjgl.glfw.GLFW.GLFW_KEY_G);
        File f = new File(dir, "options.txt");
        o.save(f);
        String text = java.nio.file.Files.readString(f.toPath());
        assertTrue(text.contains("fov:0.5\n"), "fov is stored as (degrees - 70) / 40");
        assertTrue(text.contains("gamma:1.0\n"));
        assertTrue(text.contains("maxFps:260\n"));
        assertTrue(text.contains("soundCategory_hostile:0.25\n"));
        assertTrue(text.contains("key_key.attack:key.mouse.left\n"));
        assertTrue(text.contains("key_key.drop:key.keyboard.g\n"));
        assertFalse(text.contains("="), "no java-properties syntax");
        GameOptions back = GameOptions.load(f);
        assertEquals(90, back.fov);
        assertEquals(1f, back.gamma);
        assertEquals(GameOptions.UNLIMITED_FPS, back.maxFps);
        assertTrue(back.toggleSprint);
        assertEquals(0.25f, back.hostileVolume);
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_KEY_G, back.keys.get(com.example.ui.KeyBindings.Bind.DROP));
    }

    @Test
    void aLegacyKeyEqualsValueOptionsFileIsStillRead(@TempDir File dir) throws Exception {
        File f = new File(dir, "options.txt");
        java.nio.file.Files.writeString(f.toPath(), "fov=100\nmasterVolume=0.3\nviewBobbing=false\nrenderDistance=8\n");
        GameOptions o = GameOptions.load(f);
        assertEquals(100, o.fov);
        assertEquals(0.3f, o.masterVolume, 1e-6f);
        assertFalse(o.viewBobbing);
        assertEquals(8, o.renderDistance);
    }

    @Test
    void keyBindingsDetectConflictsAndRoundTripIds() {
        com.example.ui.KeyBindings k = new com.example.ui.KeyBindings();
        assertFalse(k.conflicts(com.example.ui.KeyBindings.Bind.JUMP));
        k.set(com.example.ui.KeyBindings.Bind.DROP, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
        assertTrue(k.conflicts(com.example.ui.KeyBindings.Bind.JUMP));
        assertTrue(k.conflicts(com.example.ui.KeyBindings.Bind.DROP));
        assertEquals("key.keyboard.left.shift", com.example.ui.KeyBindings.idOf(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT));
        assertEquals(com.example.ui.KeyBindings.MOUSE_BASE + 2, com.example.ui.KeyBindings.codeOf("key.mouse.middle"));
        assertEquals(com.example.ui.KeyBindings.UNBOUND, com.example.ui.KeyBindings.codeOf("key.keyboard.unknown"));
        k.reset();
        assertFalse(k.conflicts(com.example.ui.KeyBindings.Bind.JUMP));
    }
}
