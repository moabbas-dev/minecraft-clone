package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextureIoTest {
    @Test
    void grayscaleTexelsKeepTheirStoredValueInsteadOfBeingLightened() {
        BufferedImage gray = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        gray.getRaster().setSample(0, 0, 0, 198);
        gray.getRaster().setSample(1, 0, 0, 255);
        BufferedImage out = TextureIo.exact(gray);
        assertEquals(0xFFC6C6C6, out.getRGB(0, 0), "198 stays 198 (Java's own getRGB would give about 228)");
        assertEquals(0xFFFFFFFF, out.getRGB(1, 0));
    }

    @Test
    void colourImagesPassThroughUntouched() {
        BufferedImage rgb = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        assertSame(rgb, TextureIo.exact(rgb));
    }
}
