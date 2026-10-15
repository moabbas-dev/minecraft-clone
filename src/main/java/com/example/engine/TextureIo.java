package com.example.engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Reads texture files exactly as stored. Java converts grayscale images from a linear gray space to sRGB when asked for
 * their RGB values, which makes every grey texel lighter (a 198 grey comes out as 228); grayscale PNGs such as the
 * furnace progress sprites are therefore expanded from their raw samples instead.
 */
public final class TextureIo {
    private TextureIo() {
    }

    /** The image in {@code file} as an ARGB image with the stored pixel values, or null when it cannot be decoded. */
    public static BufferedImage read(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        return img == null ? null : exact(img);
    }

    /** {@code img} with grayscale samples expanded as they are (all other image types are returned unchanged). */
    static BufferedImage exact(BufferedImage img) {
        // Grey images (with or without an alpha band - the empty armor slot outlines are grey + alpha PNGs, which Java
        // reports as a custom type) are all expanded from their raw samples.
        java.awt.image.ColorModel cm = img.getColorModel();
        if (cm.getColorSpace().getType() != java.awt.color.ColorSpace.TYPE_GRAY) return img;
        int bands = img.getRaster().getNumBands();
        if (bands < 1 || bands > 2) return img;
        int shift = cm.getComponentSize(0) > 8 ? cm.getComponentSize(0) - 8 : 0;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int v = img.getRaster().getSample(x, y, 0) >> shift;
                int a = bands == 2 ? img.getRaster().getSample(x, y, 1) >> shift : 255;
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }
}
