package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeatherDyeTest {
    @Test
    void aGreyPixelTakesTheDefaultLeatherColourAndTransparencyStays() {
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        base.setRGB(2, 3, 0xFFFFFFFF); // white grey -> the dye colour itself
        base.setRGB(4, 4, 0xFF808080); // half grey -> half of it
        BufferedImage out = TextureAtlas.dyeLeather(base, "textures/item/no_such_item.png");
        assertEquals(0xFFA06540, out.getRGB(2, 3));
        assertEquals(0xFF000000 | (0xA0 * 128 / 255) << 16 | (0x65 * 128 / 255) << 8 | (0x40 * 128 / 255), out.getRGB(4, 4));
        assertEquals(0, out.getRGB(0, 0) >>> 24, "transparent pixels stay transparent");
    }
}
