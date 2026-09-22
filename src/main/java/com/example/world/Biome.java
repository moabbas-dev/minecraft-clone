package com.example.world;

/**
 * A representative slice of modern Minecraft's biome system: one entry per major climate
 * category (temperate, arid, cold, tropical, wetland, alpine, iconic) rather than the full
 * 60+ biome roster. Each biome is a target point in 5D climate-*selection* space (temperature,
 * humidity, continentalness, erosion, weirdness), the same idea as vanilla's real multi-noise
 * placement. Selection is always nearest-match / weighted-distance in this space - never a
 * hard if/else threshold - so both the dominant biome and every other biome's *influence* at
 * a point are well-defined, which is what makes continuous blending possible in
 * {@link TerrainGenerator}.
 *
 * The 5 selection-space fields ({@link #temperature}, {@link #humidity}, etc.) are hand-tuned
 * targets that only shape *where* this biome is placed - they are a different thing from
 * {@link #defTemperature}/{@link #defDownfall}, which are vanilla's real per-biome climate
 * definition values used only to sample the grass/foliage colormap (see {@link BiomeColors}).
 * Deliberately not the same numbers: vanilla's own selection noise and its color-definition
 * values are two separate systems too (a biome's colormap point does not move when its
 * multi-noise placement region does).
 */
public enum Biome {
    //          temp   humid  cont.  eros.  weird   height amp  surface                 subsurface            treeChance  defTemp defDownfall
    PLAINS(0.3f, 0.0f, 0.0f, 0.5f, 0.0f, 68, 10, BlockType.GRASS_BLOCK, BlockType.DIRT, 400, 0.8f, 0.4f),
    FOREST(0.2f, 0.4f, 0.1f, 0.3f, 0.1f, 70, 14, BlockType.GRASS_BLOCK, BlockType.DIRT, 35, 0.7f, 0.8f),
    DESERT(0.9f, -0.8f, 0.2f, 0.4f, -0.2f, 66, 8, BlockType.SAND, BlockType.SAND, 0, 2.0f, 0.0f),
    SAVANNA(0.7f, -0.4f, 0.1f, 0.3f, 0.0f, 70, 12, BlockType.GRASS_BLOCK, BlockType.DIRT, 220, 2.0f, 0.0f),
    TAIGA(-0.4f, 0.3f, 0.2f, 0.1f, 0.2f, 72, 18, BlockType.GRASS_BLOCK, BlockType.DIRT, 30, 0.25f, 0.8f),
    SNOWY_TUNDRA(-0.9f, -0.2f, 0.0f, 0.4f, 0.0f, 70, 12, BlockType.SNOW_BLOCK, BlockType.DIRT, 260, 0.0f, 0.5f),
    JUNGLE(0.8f, 0.8f, 0.0f, 0.2f, 0.2f, 72, 16, BlockType.GRASS_BLOCK, BlockType.DIRT, 15, 0.95f, 0.9f),
    SWAMP(0.3f, 0.7f, -0.4f, 0.7f, -0.5f, 64, 5, BlockType.GRASS_BLOCK, BlockType.DIRT, 130, 0.8f, 0.9f),
    MOUNTAINS(-0.6f, 0.0f, 0.7f, -0.8f, 0.9f, 95, 46, BlockType.STONE, BlockType.STONE, 260, 0.2f, 0.3f),
    BADLANDS(0.95f, -0.9f, 0.3f, 0.0f, 0.1f, 68, 18, BlockType.TERRACOTTA, BlockType.RED_SAND, 0, 2.0f, 0.0f);

    // Temperature/humidity weighted more heavily than the other three axes in the distance
    // metric - they're the primary climate drivers, continentalness/erosion/weirdness are
    // secondary character axes.
    private static final float PRIMARY_AXIS_WEIGHT = 2.0f;
    private static final double EPSILON = 1e-4;

    public final float temperature;
    public final float humidity;
    public final float continentalness;
    public final float erosion;
    public final float weirdness;
    public final int baseHeight;
    public final int amplitude;
    public final BlockType surface;
    public final BlockType subsurface;
    /** 1-in-N chance an eligible column grows a tree; 0 means this biome never does. */
    public final int treeChance;
    /**
     * Vanilla's real per-biome temperature/downfall definition (roughly 0.0-2.0 / 0.0-1.0),
     * sourced from long-stable vanilla worldgen data (unchanged across many releases) rather
     * than extracted from any file in this project - that data lives in the server jar, not
     * the client asset dump this project copies textures from. Used only by {@link BiomeColors}
     * to sample the real grass/foliage colormaps; has no effect on biome placement.
     */
    public final float defTemperature;
    public final float defDownfall;

    Biome(float temperature, float humidity, float continentalness, float erosion, float weirdness,
          int baseHeight, int amplitude, BlockType surface, BlockType subsurface, int treeChance,
          float defTemperature, float defDownfall) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.weirdness = weirdness;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.surface = surface;
        this.subsurface = subsurface;
        this.treeChance = treeChance;
        this.defTemperature = defTemperature;
        this.defDownfall = defDownfall;
    }

    private double distanceSquared(double temperature, double humidity, double continentalness,
                                    double erosion, double weirdness) {
        double dt = temperature - this.temperature;
        double dh = humidity - this.humidity;
        double dc = continentalness - this.continentalness;
        double de = erosion - this.erosion;
        double dv = weirdness - this.weirdness;
        return PRIMARY_AXIS_WEIGHT * (dt * dt + dh * dh) + dc * dc + de * de + dv * dv;
    }

    /** The single nearest biome - used only for discrete properties (surface/subsurface block). */
    public static Biome closest(double temperature, double humidity, double continentalness,
                                 double erosion, double weirdness) {
        Biome best = PLAINS;
        double bestDist = Double.MAX_VALUE;
        for (Biome b : values()) {
            double dist = b.distanceSquared(temperature, humidity, continentalness, erosion, weirdness);
            if (dist < bestDist) {
                bestDist = dist;
                best = b;
            }
        }
        return best;
    }

    /**
     * Every biome's influence at this climate point, as inverse-distance-squared weights
     * normalized to sum to 1 (a real partition-of-unity blend, not a blur of a final ID) -
     * indexed by {@link #ordinal()}. Used for continuously blending numeric properties
     * (terrain height, tree density, grass/foliage color) across biome borders instead of
     * switching abruptly.
     */
    public static float[] weights(double temperature, double humidity, double continentalness,
                                   double erosion, double weirdness) {
        Biome[] all = values();
        float[] w = new float[all.length];
        double sum = 0;
        for (int i = 0; i < all.length; i++) {
            double dist = all[i].distanceSquared(temperature, humidity, continentalness, erosion, weirdness);
            double weight = 1.0 / (dist + EPSILON);
            w[i] = (float) weight;
            sum += weight;
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (w[i] / sum);
        }
        return w;
    }
}
