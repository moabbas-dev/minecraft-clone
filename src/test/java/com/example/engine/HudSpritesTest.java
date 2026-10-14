package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sizes follow docs/vanilla-rules.md section 5: 9x9 icons, 182x22 hotbar, 24x23 selection frame, 182x5 XP bar. */
class HudSpritesTest {
    private static BufferedImage img(String name) {
        BufferedImage i = HudSprites.image(name);
        assertNotNull(i, name);
        return i;
    }

    private static int opaque(BufferedImage i) {
        int n = 0;
        for (int y = 0; y < i.getHeight(); y++) for (int x = 0; x < i.getWidth(); x++) if ((i.getRGB(x, y) >>> 24) != 0) n++;
        return n;
    }

    private static boolean visible(BufferedImage i, int x, int y) {
        return (i.getRGB(x, y) >>> 24) != 0;
    }

    @Test
    void iconsAreNineByNineAndDrawnNotBlank() {
        for (String n : List.of("heart/container.png", "heart/full.png", "heart/half.png", "heart/container_blinking.png",
                "heart/full_blinking.png", "heart/half_blinking.png", "food_empty.png", "food_full.png", "food_half.png",
                "armor_empty.png", "armor_half.png", "armor_full.png", "air.png", "air_bursting.png")) {
            BufferedImage i = img(n);
            assertEquals(9, i.getWidth(), n);
            assertEquals(9, i.getHeight(), n);
            assertTrue(opaque(i) >= 20, n + " should have a real shape");
        }
    }

    @Test
    void barsAndFramesHaveVanillaSizes() {
        assertEquals(182, img("hotbar.png").getWidth());
        assertEquals(22, img("hotbar.png").getHeight());
        assertEquals(24, img("hotbar_selection.png").getWidth());
        assertEquals(23, img("hotbar_selection.png").getHeight());
        for (String n : List.of("experience_bar_background.png", "experience_bar_progress.png")) {
            assertEquals(182, img(n).getWidth(), n);
            assertEquals(5, img(n).getHeight(), n);
        }
    }

    @Test
    void halfIconsOnlyCoverTheLeftSideSoTheEmptyIconShowsThroughOnTheRight() {
        for (String[] pair : new String[][]{{"heart/full.png", "heart/half.png"}, {"food_full.png", "food_half.png"}, {"armor_full.png", "armor_half.png"}}) {
            BufferedImage full = img(pair[0]), half = img(pair[1]);
            assertTrue(opaque(half) < opaque(full), pair[1] + " has fewer pixels than " + pair[0]);
            for (int y = 0; y < 9; y++) for (int x = 5; x < 9; x++) assertTrue(!visible(half, x, y), pair[1] + " right side must be clear");
        }
    }

    @Test
    void containerAndFillAgreeOnTheHeartSilhouette() {
        BufferedImage container = img("heart/container.png"), full = img("heart/full.png");
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) assertEquals(visible(container, x, y), visible(full, x, y), "pixel " + x + "," + y);
    }

    @Test
    void hotbarHasNineTwentyPixelSlotsWithDarkWells() {
        BufferedImage bar = img("hotbar.png");
        for (int slot = 0; slot < 9; slot++) {
            int cx = 1 + slot * 20 + 10, cy = 11;
            int argb = bar.getRGB(cx, cy);
            assertTrue((argb >>> 24) > 100 && ((argb >> 16) & 0xFF) < 60, "slot " + slot + " well should be dark");
        }
        assertTrue(((bar.getRGB(0, 5) >> 16) & 0xFF) < 30, "outer border is near black");
    }

    @Test
    void selectionFrameIsHollowSoTheItemShowsThrough() {
        BufferedImage sel = img("hotbar_selection.png");
        assertTrue(visible(sel, 0, 0) && visible(sel, 12, 1) && visible(sel, 12, 2));
        assertTrue(!visible(sel, 12, 11), "centre must be transparent");
    }

    @Test
    void unknownNamesReturnNull() {
        assertNull(HudSprites.image("nope.png"));
    }
}
