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
        int type = img.getType();
        if (type != BufferedImage.TYPE_BYTE_GRAY && type != BufferedImage.TYPE_USHORT_GRAY) return img;
        int shift = type == BufferedImage.TYPE_USHORT_GRAY ? 8 : 0;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int v = img.getRaster().getSample(x, y, 0) >> shift;
                out.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }
}
