package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every flat item has its own 16x16 original icon with a transparent background and a dark outline. */
class ItemIconsTest {
    private static final List<Tile> ITEM_TILES = new ArrayList<>();

    static {
        String[] tiers = {"WOODEN", "STONE", "IRON", "DIAMOND"};
        for (String t : tiers) for (String k : new String[]{"PICKAXE", "AXE", "SHOVEL", "SWORD"}) ITEM_TILES.add(Tile.valueOf(t + "_" + k));
        for (String m : new String[]{"LEATHER", "IRON", "GOLD", "DIAMOND"}) {
            for (String k : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"}) ITEM_TILES.add(Tile.valueOf(m + "_" + k));
        }
        for (String n : ("STICK COAL CHARCOAL IRON_INGOT GOLD_INGOT COPPER_INGOT IRON_NUGGET BRICK RAW_IRON RAW_GOLD RAW_COPPER "
                + "APPLE BREAD COOKED_BEEF RAW_BEEF RAW_PORKCHOP COOKED_PORKCHOP RAW_CHICKEN COOKED_CHICKEN RAW_MUTTON COOKED_MUTTON "
                + "RAW_RABBIT COOKED_RABBIT COD COOKED_COD SALMON COOKED_SALMON POTATO BAKED_POTATO KELP DRIED_KELP ROTTEN_FLESH "
                + "LEATHER FEATHER EGG DIAMOND FLINT CLAY_BALL SNOWBALL GLOWSTONE_DUST BOOK GUNPOWDER BONE ARROW STRING SPIDER_EYE").split(" ")) {
            ITEM_TILES.add(Tile.valueOf(n));
        }
    }

    private static boolean visible(BufferedImage i, int x, int y) {
        return (i.getRGB(x, y) >>> 24) != 0;
    }

    @Test
    void everyItemTileHasA16x16IconWithAShapeAndAnOutline() {
        for (Tile t : ITEM_TILES) {
            BufferedImage icon = ItemIcons.draw(t);
            assertNotNull(icon, t.name());
            assertEquals(16, icon.getWidth(), t.name());
            assertEquals(16, icon.getHeight(), t.name());
            int opaque = 0, dark = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (!visible(icon, x, y)) continue;
                    opaque++;
                    int argb = icon.getRGB(x, y);
                    if (((argb >> 16) & 0xFF) < 70 && ((argb >> 8) & 0xFF) < 70 && (argb & 0xFF) < 70) dark++;
                }
            }
            assertTrue(opaque >= 24, t + " should be a real shape, had " + opaque + " pixels");
            assertTrue(opaque <= 200, t + " should leave a transparent background");
            assertTrue(dark >= 6 || t == Tile.COAL || t == Tile.CHARCOAL, t + " should be outlined");
            assertTrue(!visible(icon, 0, 0), t + " top-left corner should be clear");
        }
    }

    @Test
    void iconsAreAllDistinct() {
        Map<String, Tile> seen = new HashMap<>();
        for (Tile t : ITEM_TILES) {
            BufferedImage icon = ItemIcons.draw(t);
            int[] px = icon.getRGB(0, 0, 16, 16, null, 0, 16);
            String key = Arrays.toString(px);
            Tile other = seen.put(key, t);
            assertNull(other, t + " has the same pixels as " + other);
        }
    }

    @Test
    void nonItemTilesAreNotHandled() {
        for (Tile t : new Tile[]{Tile.STONE, Tile.GRASS_TOP, Tile.WATER, Tile.SUN, Tile.SKIN, Tile.ZOMBIE_SKIN, Tile.CHEST}) {
            assertNull(ItemIcons.draw(t), t.name());
        }
    }

    @Test
    void theSaplingIsACutoutSpriteNotAFilledSquare() {
        BufferedImage s = ItemIcons.draw(Tile.OAK_SAPLING);
        assertNotNull(s);
        assertTrue(!visible(s, 0, 0) && !visible(s, 15, 0) && !visible(s, 0, 15) && !visible(s, 15, 15));
        assertTrue(visible(s, 8, 14), "the trunk reaches the bottom");
    }
}
