package com.example.world;

import java.io.File;

/**
 * Per-biome grass/foliage color, and the weighted blend of those colors across neighboring
 * biomes at one world column. Deliberately separate from {@link Biome}'s climate-*selection*
 * space: color here is a function of each biome's own {@link Biome#defTemperature}/
 * {@link Biome#defDownfall} definition, not of the raw climate noise that decides *where*
 * biomes are placed - matching vanilla, where a biome's colormap sample point does not move
 * when its multi-noise placement region does.
 *
 * Blending happens in sRGB: the grass/foliage colormap PNGs and the block textures they tint
 * are already stored in sRGB, and the fragment shader multiplies tint straight into the sampled
 * texture color with no gamma/linear conversion anywhere in the pipeline - so blending here in
 * sRGB keeps this step consistent with the renderer rather than introducing a mismatched color
 * space that the shader would then have to undo.
 */
public class BiomeColors {

    /**
     * Exponent applied to each biome's IDW weight (from {@link Biome#weights}) before
     * renormalizing, for color blending specifically. 1.0 = the exact same blend already used
     * for terrain height and tree density. Raising this sharpens the transition (biases toward
     * the single nearest biome's own color); this is this project's configurable analog of
     * vanilla's spatial "Biome Blend" radius option - a climate-space sharpness knob rather than
     * a block-radius box blur, appropriate here since color is already a pure, seam-free
     * function of world (x, z) with no neighboring-column sampling involved.
     */
    private static final float BLEND_SHARPNESS = 1.0f;

    // Swamp grass varies by position between two fixed colors - a real, long-standing vanilla
    // special case. The exact vanilla noise frequency/threshold live in server-side worldgen
    // code this project has no access to verify, so rather than fabricate a matching threshold
    // this uses a smooth lerp (at least as correct as an unverified hard cutoff) between the
    // same two documented colors, driven by this world's own seeded noise.
    private static final float[] SWAMP_GRASS_LOW = unpackHex(0x4C763C);
    private static final float[] SWAMP_GRASS_HIGH = unpackHex(0x6A7039);
    private static final float[] SWAMP_FOLIAGE = unpackHex(0x6A7039);
    private static final double SWAMP_NOISE_SCALE = 1.0 / 60.0;

    // Badlands' grass/foliage bypass the colormap entirely in vanilla, regardless of its
    // definition temperature/downfall - another long-standing special case.
    private static final float[] BADLANDS_GRASS = unpackHex(0x90814D);
    private static final float[] BADLANDS_FOLIAGE = unpackHex(0x9E814D);

    private final SimplexNoise swampNoise;
    private final float[][] grassByBiome;
    private final float[][] foliageByBiome;

    public BiomeColors(long seed, File grassColormapFile, File foliageColormapFile) {
        this.swampNoise = new SimplexNoise(seed + 6000);
        Colormap grassColormap = Colormap.load(grassColormapFile, 0x7CBD6B);
        Colormap foliageColormap = Colormap.load(foliageColormapFile, 0x59AE30);

        Biome[] all = Biome.values();
        grassByBiome = new float[all.length][];
        foliageByBiome = new float[all.length][];
        for (int i = 0; i < all.length; i++) {
            Biome b = all[i];
            grassByBiome[i] = b == Biome.BADLANDS ? BADLANDS_GRASS
                    : grassColormap.sampleDefinition(b.defTemperature, b.defDownfall);
            foliageByBiome[i] = b == Biome.BADLANDS ? BADLANDS_FOLIAGE
                    : foliageColormap.sampleDefinition(b.defTemperature, b.defDownfall);
        }
    }

    /** This biome's own grass color at this world column - fixed per biome, except Swamp. */
    private float[] grassColorFor(Biome biome, int worldX, int worldZ) {
        if (biome == Biome.SWAMP) return swampGrass(worldX, worldZ);
        return grassByBiome[biome.ordinal()];
    }

    private float[] foliageColorFor(Biome biome) {
        return biome == Biome.SWAMP ? SWAMP_FOLIAGE : foliageByBiome[biome.ordinal()];
    }

    private float[] swampGrass(int worldX, int worldZ) {
        double n = swampNoise.fbm(worldX, worldZ, 1, 0.5, 2.0, SWAMP_NOISE_SCALE);
        double t = Math.max(0.0, Math.min(1.0, (n + 1.0) / 2.0));
        return lerp(SWAMP_GRASS_LOW, SWAMP_GRASS_HIGH, t);
    }

    /** Weighted blend of every biome's own grass color at this column - see class docs. */
    public float[] blendGrass(int worldX, int worldZ, float[] weights) {
        float[] w = sharpen(weights);
        Biome[] all = Biome.values();
        float r = 0, g = 0, b = 0;
        for (int i = 0; i < all.length; i++) {
            float[] c = grassColorFor(all[i], worldX, worldZ);
            r += w[i] * c[0];
            g += w[i] * c[1];
            b += w[i] * c[2];
        }
        return new float[]{r, g, b};
    }

    /** Weighted blend of every biome's own foliage color at this column - see class docs. */
    public float[] blendFoliage(int worldX, int worldZ, float[] weights) {
        float[] w = sharpen(weights);
        Biome[] all = Biome.values();
        float r = 0, g = 0, b = 0;
        for (int i = 0; i < all.length; i++) {
            float[] c = foliageColorFor(all[i]);
            r += w[i] * c[0];
            g += w[i] * c[1];
            b += w[i] * c[2];
        }
        return new float[]{r, g, b};
    }

    private static float[] sharpen(float[] weights) {
        if (BLEND_SHARPNESS == 1f) return weights;
        float[] w = new float[weights.length];
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            w[i] = (float) Math.pow(weights[i], BLEND_SHARPNESS);
            sum += w[i];
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (w[i] / sum);
        }
        return w;
    }

    private static float[] lerp(float[] a, float[] b, double t) {
        return new float[]{
                (float) (a[0] + (b[0] - a[0]) * t),
                (float) (a[1] + (b[1] - a[1]) * t),
                (float) (a[2] + (b[2] - a[2]) * t)
        };
    }

    private static float[] unpackHex(int rgb) {
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }
}
