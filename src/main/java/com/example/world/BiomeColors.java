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

    // --- Water / fog / underwater-fog: fixed per-biome constants, not colormap-sampled (real
    // vanilla water color isn't climate-noise-driven either - it's a flat per-biome value in the
    // biome registry). Sourcing honesty, biome by biome:
    //   - DEFAULT_WATER (0x3F76E4) and SWAMP's own water (0x617B64) are long-standing,
    //     well-documented vanilla biome-registry constants, used directly - same confidence
    //     tier as the Badlands/Swamp grass colors above. Most vanilla biomes (including every
    //     other one in this project's roster) simply use the default water color, which is
    //     exactly what's applied to them here too - not a gap, that IS the verified behavior.
    //   - SNOWY_TUNDRA's cooler water tint is ADAPTED for this project (this roster has no
    //     "frozen ocean" biome to draw a verified value from) - explicitly not a vanilla claim.
    //   - Every fog and underwater-fog value below is ADAPTED/artistic, not extracted from any
    //     verified source: vanilla's overworld surface fog is essentially just the sky color
    //     with no real per-biome variation, so biome-tinted surface fog is this project's own
    //     visual choice inspired by the request, not a reproduction of a vanilla mechanic. The
    //     underwater fog values take the request's reference hexes only as a starting point
    //     (as instructed) and are re-picked per this project's own biome roster.
    //   - Ocean/Frozen Ocean/Mangrove (added alongside sky tint in this round): Ocean reuses the
    //     verified default water color (real vanilla ocean is *not* a special color, it's the
    //     default - not a gap). Frozen Ocean reuses Snowy Tundra's already-adapted cold water/
    //     underwater-fog values directly (one consistent "cold water" identity, not a second
    //     invented number). Mangrove gets its own distinct adapted values - explicitly not
    //     identical to Swamp's or Ocean's, per the request's own color-consistency requirement.
    // Every number below is read from the biome files and the overworld dimension type of the 26.3 client jar
    // (worldgen/biome/*.json: water_color, visual/sky_color, visual/water_fog_color; dimension_type/overworld.json:
    // visual/fog_color #c0d8ff; EnvironmentAttributes: the default water fog colour #050533).
    private static final int DEFAULT_WATER = 0x3F76E4;
    private static final int DEFAULT_UNDERWATER_FOG = 0x050533;
    private static final int OVERWORLD_FOG = 0xC0D8FF;

    private static final int[] WATER_BY_ORDINAL = {
            DEFAULT_WATER,   // PLAINS
            DEFAULT_WATER,   // FOREST
            DEFAULT_WATER,   // DESERT
            DEFAULT_WATER,   // SAVANNA
            DEFAULT_WATER,   // TAIGA
            DEFAULT_WATER,   // SNOWY_TUNDRA (snowy_plains)
            DEFAULT_WATER,   // JUNGLE
            0x617B64,        // SWAMP
            DEFAULT_WATER,   // MOUNTAINS (windswept_hills)
            DEFAULT_WATER,   // BADLANDS
            DEFAULT_WATER,   // OCEAN
            0x3938C9,        // FROZEN_OCEAN
            0x3A7A6A,        // MANGROVE (mangrove_swamp)
            DEFAULT_WATER,   // BIRCH_FOREST
            DEFAULT_WATER,   // DARK_FOREST
            0x5DB7EF,        // CHERRY_GROVE
    };

    /** The surface fog colour: no overworld biome here overrides the dimension's {@code #c0d8ff}. */
    private static final int[] FOG_BY_ORDINAL = {
            OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG,
            OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG, OVERWORLD_FOG,
    };

    private static final int[] UNDERWATER_FOG_BY_ORDINAL = {
            DEFAULT_UNDERWATER_FOG, // PLAINS
            DEFAULT_UNDERWATER_FOG, // FOREST
            DEFAULT_UNDERWATER_FOG, // DESERT
            DEFAULT_UNDERWATER_FOG, // SAVANNA
            DEFAULT_UNDERWATER_FOG, // TAIGA
            DEFAULT_UNDERWATER_FOG, // SNOWY_TUNDRA
            DEFAULT_UNDERWATER_FOG, // JUNGLE
            0x232317,               // SWAMP
            DEFAULT_UNDERWATER_FOG, // MOUNTAINS
            DEFAULT_UNDERWATER_FOG, // BADLANDS
            DEFAULT_UNDERWATER_FOG, // OCEAN
            DEFAULT_UNDERWATER_FOG, // FROZEN_OCEAN
            0x4D7A60,               // MANGROVE
            DEFAULT_UNDERWATER_FOG, // BIRCH_FOREST
            DEFAULT_UNDERWATER_FOG, // DARK_FOREST
            0x5DB7EF,               // CHERRY_GROVE
    };

    /** The biome's {@code visual/sky_color} (the dimension's {@code #78a7ff} where the biome gives none). */
    private static final int[] SKY_TINT_BY_ORDINAL = {
            0x78A7FF,        // PLAINS
            0x79A6FF,        // FOREST
            0x6EB1FF,        // DESERT
            0x6EB1FF,        // SAVANNA
            0x7DA3FF,        // TAIGA
            0x7FA1FF,        // SNOWY_TUNDRA (snowy_plains)
            0x77A8FF,        // JUNGLE
            0x78A7FF,        // SWAMP
            0x7DA2FF,        // MOUNTAINS (windswept_hills)
            0x6EB1FF,        // BADLANDS
            0x7BA4FF,        // OCEAN
            0x7FA1FF,        // FROZEN_OCEAN
            0x78A7FF,        // MANGROVE
            0x7AA5FF,        // BIRCH_FOREST
            0x79A6FF,        // DARK_FOREST
            0x7BA4FF,        // CHERRY_GROVE
    };

    private final SimplexNoise swampNoise;
    private final float[][] grassByBiome;
    private final float[][] foliageByBiome;
    private final float[][] waterByBiome;
    private final float[][] fogByBiome;
    private final float[][] underwaterFogByBiome;
    private final float[][] skyTintByBiome;

    public BiomeColors(long seed, File grassColormapFile, File foliageColormapFile) {
        this.swampNoise = new SimplexNoise(seed + 6000);
        Colormap grassColormap = Colormap.load(grassColormapFile, 0x7CBD6B);
        Colormap foliageColormap = Colormap.load(foliageColormapFile, 0x59AE30);

        Biome[] all = Biome.values();
        grassByBiome = new float[all.length][];
        foliageByBiome = new float[all.length][];
        waterByBiome = new float[all.length][];
        fogByBiome = new float[all.length][];
        underwaterFogByBiome = new float[all.length][];
        skyTintByBiome = new float[all.length][];
        for (int i = 0; i < all.length; i++) {
            Biome b = all[i];
            grassByBiome[i] = b == Biome.BADLANDS ? BADLANDS_GRASS
                    : grassColormap.sampleDefinition(b.defTemperature, b.defDownfall);
            foliageByBiome[i] = b == Biome.BADLANDS ? BADLANDS_FOLIAGE
                    : foliageColormap.sampleDefinition(b.defTemperature, b.defDownfall);
            waterByBiome[i] = unpackHex(WATER_BY_ORDINAL[i]);
            fogByBiome[i] = unpackHex(FOG_BY_ORDINAL[i]);
            underwaterFogByBiome[i] = unpackHex(UNDERWATER_FOG_BY_ORDINAL[i]);
            skyTintByBiome[i] = unpackHex(SKY_TINT_BY_ORDINAL[i]);
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

    /** Weighted blend of every biome's own water tint - a fixed per-biome constant (see the
     *  table above), so unlike grass this needs no world position. */
    public float[] blendWater(float[] weights) {
        return blendConstantTable(waterByBiome, weights);
    }

    /** Weighted blend of every biome's own surface fog tint - see the table above. */
    public float[] blendFog(float[] weights) {
        return blendConstantTable(fogByBiome, weights);
    }

    /** Weighted blend of every biome's own underwater fog color - see the table above. */
    public float[] blendUnderwaterFog(float[] weights) {
        return blendConstantTable(underwaterFogByBiome, weights);
    }

    /** Weighted blend of every biome's own sky tint - see the table above. Same blend mechanism
     *  as fog/water (IDW weights already normalized, zero-distance-safe via Biome.weights'
     *  own EPSILON) - deliberately not a different formula for a different color property. */
    public float[] blendSkyTint(float[] weights) {
        return blendConstantTable(skyTintByBiome, weights);
    }

    private static float[] blendConstantTable(float[][] table, float[] weights) {
        float[] w = sharpen(weights);
        float r = 0, g = 0, b = 0;
        for (int i = 0; i < table.length; i++) {
            float[] c = table[i];
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
