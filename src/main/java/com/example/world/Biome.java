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
    BADLANDS(0.95f, -0.9f, 0.3f, 0.0f, 0.1f, 68, 18, BlockType.TERRACOTTA, BlockType.RED_SAND, 0, 2.0f, 0.0f),
    // continentalness is real vanilla's actual mechanism for ocean-vs-land placement - these 3
    // are simply the first entries in this roster to use its negative extreme, not a new axis
    // or a new selection mechanism. baseHeight below SEA_LEVEL (63) is what actually makes these
    // "ocean": no other biome here has ever had one, so nothing generated below sea level before.
    OCEAN(0.2f, 0.0f, -0.85f, 0.0f, 0.0f, 40, 12, BlockType.SAND, BlockType.GRAVEL, 0, 0.5f, 0.5f),
    FROZEN_OCEAN(-0.8f, -0.1f, -0.85f, 0.0f, 0.0f, 40, 10, BlockType.SAND, BlockType.GRAVEL, 0, 0.0f, 0.5f),
    // Shallow/coastal (continentalness between Swamp's -0.4 and dry land), warm and humid like
    // Jungle/Swamp, baseHeight right at sea level - real mangrove's own shallow-water identity.
    // Surface/subsurface are MUD (this round's addition) rather than grass/dirt - real vanilla
    // Mangrove Swamp's actual ground block, and a prerequisite for the new root-placement rule
    // in TreeSpecies.MANGROVE (see its docs).
    MANGROVE(0.75f, 0.75f, -0.3f, 0.5f, -0.3f, 63, 4, BlockType.MUD, BlockType.MUD, 60, 0.8f, 0.9f),
    // Carved out along weirdness/erosion, axes Forest itself barely uses, so these three new
    // Forest-family biomes get their own reachable climate niches instead of just diluting
    // Forest's - see BiomeAtmosphereTest/NewBiomesTest-style reachability tests.
    //
    // Birch Forest: warmer/less humid than Forest (real vanilla's own def temp/downfall are
    // 0.6/0.6, cooler-toned than Forest's 0.7/0.8 - a paler colormap sample, matching "light or
    // distinctive birch foliage"), gentler terrain, denser tree coverage via TerrainGenerator's
    // per-biome treeChance/clusterChance (see speciesFor/clusterChanceFor) rather than a new
    // species - it reuses the existing BIRCH species at a dedicated-biome density instead of
    // Forest's ~10-40% probabilistic roll.
    BIRCH_FOREST(0.5f, 0.15f, 0.15f, 0.35f, -0.3f, 69, 12, BlockType.GRASS_BLOCK, BlockType.DIRT, 22, 0.6f, 0.6f),
    // Dark Forest: same climate family as Forest but pushed to weirdness's positive extreme (an
    // axis Forest doesn't use at all) for a distinct, reachable niche. Very low treeChance (see
    // TerrainGenerator) for the request's "45-75% coverage, dense vegetation, large canopies."
    // defTemperature/defDownfall match Forest's own values (0.7/0.8) - already one of the
    // darker-sampling corners of the real foliage colormap - rather than inventing a fixed tint
    // the way Spruce/Cherry need to (see BiomeColors/ChunkMeshBuilder and TreeSpecies.DARK_OAK).
    DARK_FOREST(0.15f, 0.5f, 0.15f, 0.15f, 0.5f, 69, 12, BlockType.GRASS_BLOCK, BlockType.DIRT, 10, 0.7f, 0.8f),
    // Cherry Grove: drier and more elevated/hilly than Forest, its own weirdness pocket (+0.7,
    // separated from Dark Forest's +0.5) so the two don't collide in climate space. Real vanilla
    // Cherry Grove's colormap definition isn't as long-documented as Forest/Swamp's - defTemp/
    // defDownfall below are a best-effort placement in the temperate/moderate-downfall region,
    // not independently verified this session - moot in practice anyway, since Cherry leaves
    // bypass the colormap blend entirely for a fixed pink tint (see ChunkMeshBuilder), the same
    // real vanilla special case Spruce already uses here.
    CHERRY_GROVE(0.35f, 0.1f, 0.3f, -0.2f, 0.7f, 76, 16, BlockType.GRASS_BLOCK, BlockType.DIRT, 45, 0.5f, 0.6f);

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
