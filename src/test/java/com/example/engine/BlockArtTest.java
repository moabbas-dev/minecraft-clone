package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated block textures are 16x16, deterministic, structured (several tones) and distinct from each other. */
class BlockArtTest {
    /** Tiles whose texture is intentionally see-through. */
    private static final Set<Tile> TRANSLUCENT = Set.of(Tile.GLASS, Tile.ICE, Tile.WATER);

    private static int[] pixels(BufferedImage i) {
        return i.getRGB(0, 0, 16, 16, null, 0, 16);
    }

    @Test
    void everyHandledTileIs16x16AndNotFlat() {
        int handled = 0;
        for (Tile t : Tile.values()) {
            BufferedImage img = BlockArt.draw(t);
            if (img == null) continue;
            handled++;
            assertEquals(16, img.getWidth(), t.name());
            assertEquals(16, img.getHeight(), t.name());
            int[] px = pixels(img);
            Set<Integer> colours = new HashSet<>();
            for (int p : px) colours.add(p);
            assertTrue(colours.size() >= 4, t + " should use several tones, had " + colours.size());
            if (!TRANSLUCENT.contains(t)) {
                for (int p : px) assertEquals(0xFF, p >>> 24, t + " must be fully opaque");
            }
        }
        assertTrue(handled >= 70, "expected the full set of block tiles, got " + handled);
    }

    @Test
    void texturesAreDeterministicAndDistinct() {
        Map<String, Tile> seen = new HashMap<>();
        for (Tile t : Tile.values()) {
            BufferedImage a = BlockArt.draw(t), b = BlockArt.draw(t);
            if (a == null) continue;
            assertTrue(Arrays.equals(pixels(a), pixels(b)), t + " must be the same every time");
            Tile other = seen.put(Arrays.toString(pixels(a)), t);
            assertNull(other, t + " has the same pixels as " + other);
        }
    }

    @Test
    void grassSideHasAGreenFringeOverDirt() {
        BufferedImage side = BlockArt.draw(Tile.GRASS_SIDE);
        assertNotNull(side);
        int greenTop = 0, brownBottom = 0;
        for (int x = 0; x < 16; x++) {
            int top = side.getRGB(x, 0), bottom = side.getRGB(x, 15);
            if (((top >> 8) & 0xFF) > ((top >> 16) & 0xFF) + 20) greenTop++;
            if (((bottom >> 16) & 0xFF) > ((bottom >> 8) & 0xFF) + 20) brownBottom++;
        }
        assertEquals(16, greenTop, "the whole top row is grass");
        assertEquals(16, brownBottom, "the whole bottom row is dirt");
    }

    @Test
    void tntSideCarriesALightBandBetweenRedHalves() {
        BufferedImage tnt = BlockArt.draw(Tile.TNT_SIDE);
        assertNotNull(tnt);
        int red = tnt.getRGB(1, 1), band = tnt.getRGB(1, 8);
        assertTrue(((red >> 16) & 0xFF) > 150 && ((red >> 8) & 0xFF) < 90, "red above the band");
        assertTrue(((band >> 8) & 0xFF) > 190, "light band");
    }

    @Test
    void unhandledTilesFallThrough() {
        for (Tile t : new Tile[]{Tile.SUN, Tile.MOON, Tile.RAIN, Tile.NIGHT_MOTE, Tile.SKIN, Tile.STICK, Tile.OAK_SAPLING}) {
            assertNull(BlockArt.draw(t), t.name());
        }
    }
}
