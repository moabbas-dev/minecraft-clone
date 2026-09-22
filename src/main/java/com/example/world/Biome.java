package com.example.world;

/**
 * A representative slice of modern Minecraft's biome system: one entry per major climate
 * category (temperate, arid, cold, tropical, wetland, alpine, iconic) rather than the full
 * 60+ biome roster. Each biome is a target point in 5D climate space (temperature, humidity,
 * continentalness, erosion, terrain-variation), the same idea as vanilla's real multi-noise
 * placement. Selection is always nearest-match / weighted-distance in this space - never a
 * hard if/else threshold - so both the dominant biome and every other biome's *influence* at
 * a point are well-defined, which is what makes continuous blending possible in
 * {@link TerrainGenerator}.
 */
public enum Biome {
    //                    temp   humid  cont.  eros.  terrVar  height amp  surface                 subsurface            treeChance
    PLAINS(0.3f, 0.0f, 0.0f, 0.5f, 0.0f, 68, 10, BlockType.GRASS_BLOCK, BlockType.DIRT, 400),
    FOREST(0.2f, 0.4f, 0.1f, 0.3f, 0.1f, 70, 14, BlockType.GRASS_BLOCK, BlockType.DIRT, 35),
    DESERT(0.9f, -0.8f, 0.2f, 0.4f, -0.2f, 66, 8, BlockType.SAND, BlockType.SAND, 0),
    SAVANNA(0.7f, -0.4f, 0.1f, 0.3f, 0.0f, 70, 12, BlockType.GRASS_BLOCK, BlockType.DIRT, 220),
    TAIGA(-0.4f, 0.3f, 0.2f, 0.1f, 0.2f, 72, 18, BlockType.GRASS_BLOCK, BlockType.DIRT, 30),
    SNOWY_TUNDRA(-0.9f, -0.2f, 0.0f, 0.4f, 0.0f, 70, 12, BlockType.SNOW_BLOCK, BlockType.DIRT, 260),
    JUNGLE(0.8f, 0.8f, 0.0f, 0.2f, 0.2f, 72, 16, BlockType.GRASS_BLOCK, BlockType.DIRT, 15),
    SWAMP(0.3f, 0.7f, -0.4f, 0.7f, -0.5f, 64, 5, BlockType.GRASS_BLOCK, BlockType.DIRT, 130),
    MOUNTAINS(-0.6f, 0.0f, 0.7f, -0.8f, 0.9f, 95, 46, BlockType.STONE, BlockType.STONE, 260),
    BADLANDS(0.95f, -0.9f, 0.3f, 0.0f, 0.1f, 68, 18, BlockType.TERRACOTTA, BlockType.RED_SAND, 0);

    // Temperature/humidity weighted more heavily than the other three axes in the distance
    // metric - they're the primary climate drivers, continentalness/erosion/terrain-variation
    // are secondary character axes.
    private static final float PRIMARY_AXIS_WEIGHT = 2.0f;
    private static final double EPSILON = 1e-4;

    public final float temperature;
    public final float humidity;
    public final float continentalness;
    public final float erosion;
    public final float terrainVariation;
    public final int baseHeight;
    public final int amplitude;
    public final BlockType surface;
    public final BlockType subsurface;
    /** 1-in-N chance an eligible column grows a tree; 0 means this biome never does. */
    public final int treeChance;

    Biome(float temperature, float humidity, float continentalness, float erosion, float terrainVariation,
          int baseHeight, int amplitude, BlockType surface, BlockType subsurface, int treeChance) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.terrainVariation = terrainVariation;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.surface = surface;
        this.subsurface = subsurface;
        this.treeChance = treeChance;
    }

    private double distanceSquared(double temperature, double humidity, double continentalness,
                                    double erosion, double terrainVariation) {
        double dt = temperature - this.temperature;
        double dh = humidity - this.humidity;
        double dc = continentalness - this.continentalness;
        double de = erosion - this.erosion;
        double dv = terrainVariation - this.terrainVariation;
        return PRIMARY_AXIS_WEIGHT * (dt * dt + dh * dh) + dc * dc + de * de + dv * dv;
    }

    /** The single nearest biome - used only for discrete properties (surface/subsurface block). */
    public static Biome closest(double temperature, double humidity, double continentalness,
                                 double erosion, double terrainVariation) {
        Biome best = PLAINS;
        double bestDist = Double.MAX_VALUE;
        for (Biome b : values()) {
            double dist = b.distanceSquared(temperature, humidity, continentalness, erosion, terrainVariation);
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
     * (terrain height, tree density) across biome borders instead of switching abruptly.
     */
    public static float[] weights(double temperature, double humidity, double continentalness,
                                   double erosion, double terrainVariation) {
        Biome[] all = values();
        float[] w = new float[all.length];
        double sum = 0;
        for (int i = 0; i < all.length; i++) {
            double dist = all[i].distanceSquared(temperature, humidity, continentalness, erosion, terrainVariation);
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
