package com.example.world;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Samples Minecraft's real grass/foliage colormap images with vanilla's actual formula, so
 * grass-top and leaf tint colors match the game's real climate-driven look. Grass and leaf
 * base textures ship grayscale precisely so this tint can be multiplied in at render time.
 *
 * Sampled directly from each column's own continuous (temperature, humidity) noise rather than
 * from a "nearest biome" color - since that noise is already smooth, sampling it directly
 * already produces smooth, seamless transitions with no separate blending step needed, and no
 * approximation of the real algorithm either.
 */
@Slf4j
public class Colormap {
    private final BufferedImage image;
    private final float[] fallback;

    private Colormap(BufferedImage image, int fallbackRgb) {
        this.image = image;
        this.fallback = unpack(fallbackRgb);
    }

    public static Colormap load(File file, int fallbackRgb) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                log.warn("Colormap file exists but couldn't be decoded: {} - falling back to a flat color", file);
            }
            return new Colormap(img, fallbackRgb);
        } catch (IOException e) {
            log.warn("Colormap not found at {} - falling back to a flat color ({})", file, e.getMessage());
            return new Colormap(null, fallbackRgb);
        }
    }

    /**
     * temperature/humidity are raw noise values, roughly in [-1, 1]; remapped internally to
     * vanilla's own [0, 1] convention and vanilla's real sampling formula (adjusted humidity =
     * humidity * temperature, sampled at pixel ((1-temperature)*255, (1-adjHumidity)*255)).
     */
    public float[] sample(double temperature, double humidity) {
        return sampleDefinition(clamp01((temperature + 1.0) / 2.0), clamp01((humidity + 1.0) / 2.0));
    }

    /**
     * Same vanilla sampling formula as {@link #sample}, but for values already in vanilla's own
     * [0, 1] convention - used by {@link BiomeColors} with a biome's real temperature/downfall
     * definition (temp01 = clamp(temperature/2, 0, 1), downfall01 = downfall) rather than with
     * raw climate-selection noise.
     */
    public float[] sampleDefinition(double temp01, double downfall01) {
        if (image == null) return fallback;
        double t = clamp01(temp01);
        double h = clamp01(downfall01);
        double adjHumidity = h * t;
        int x = (int) clamp((1.0 - t) * 255.0, 0, image.getWidth() - 1);
        int y = (int) clamp((1.0 - adjHumidity) * 255.0, 0, image.getHeight() - 1);
        return unpack(image.getRGB(x, y));
    }

    private static float[] unpack(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
        };
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
