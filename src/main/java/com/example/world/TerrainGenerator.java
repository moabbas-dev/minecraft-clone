package com.example.world;

import java.io.File;
import java.util.Random;

/**
 * Biome-aware overworld generator driven by continuous 6-axis climate noise (temperature,
 * humidity, continentalness, erosion, weirdness, plus terrain height as a *separate* system -
 * see below). Biome *selection* for discrete properties (surface/subsurface block) uses
 * nearest-match in that climate space via {@link Biome#closest} - never an if/else threshold.
 * Continuous properties (terrain height, tree density, grass/foliage color) are instead blended
 * across every biome's {@link Biome#weights} influence, so borders ramp smoothly instead of
 * switching at a hard line. Grass/foliage color is resolved per-biome from vanilla's real
 * temperature/downfall colormap definitions (see {@link BiomeColors}), then blended with those
 * same weights - not sampled from the raw climate noise, which has no notion of biome identity.
 *
 * Terrain height noise ({@link #heightNoise}) is deliberately *not* one of the climate-selection
 * axes: biome identity and terrain shape are two separate systems that happen to share biome
 * weights as their blending mechanism, not one function where "how tall the ground is" also
 * decides "which biome this is." heightNoise instead only ever appears as the amplitude term in
 * the terrain height formula in {@link #generate}.
 *
 * All of this only ever depends on world (x, z) coordinates - no chunk-local state - so it is
 * automatically seamless across chunk borders: the same world column produces the same climate
 * sample and the same blended values no matter which chunk generates it.
 */
public class TerrainGenerator implements WorldGenerator {

    public static final int SEA_LEVEL = 63;
    private static final double CLIMATE_SCALE = 1.0 / 300.0;
    private static final double MACRO_CLIMATE_SCALE = 1.0 / 800.0; // continentalness/erosion/weirdness: large, slow-varying regions

    private final long seed;
    private final SimplexNoise heightNoise;
    private final SimplexNoise temperatureNoise;
    private final SimplexNoise humidityNoise;
    private final SimplexNoise continentalnessNoise;
    private final SimplexNoise erosionNoise;
    private final SimplexNoise weirdnessNoise;
    private final BiomeColors biomeColors;

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new SimplexNoise(seed);
        this.temperatureNoise = new SimplexNoise(seed + 1000);
        this.humidityNoise = new SimplexNoise(seed + 2000);
        this.continentalnessNoise = new SimplexNoise(seed + 3000);
        this.erosionNoise = new SimplexNoise(seed + 4000);
        this.weirdnessNoise = new SimplexNoise(seed + 5000);
        this.biomeColors = new BiomeColors(seed,
                new File("textures/colormap/grass.png"), new File("textures/colormap/foliage.png"));
    }

    /** Mirrors vanilla's classic chunk decoration-seed formula for deterministic per-chunk randomness. */
    private Random chunkRandom(int cx, int cz) {
        long chunkSeed = (seed
                + (long) cx * 341873128712L
                + (long) cz * 132897987541L);
        return new Random(chunkSeed);
    }

    /**
     * The 5 raw continuous climate-*selection* values at a world column, each roughly in
     * [-1, 1] - deliberately independent of {@link #heightNoise} (see class docs).
     */
    private double[] rawClimateAt(int worldX, int worldZ) {
        double temp = temperatureNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, CLIMATE_SCALE);
        double humidity = humidityNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, CLIMATE_SCALE);
        double continentalness = continentalnessNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, MACRO_CLIMATE_SCALE);
        double erosion = erosionNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, MACRO_CLIMATE_SCALE);
        double weirdness = weirdnessNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, MACRO_CLIMATE_SCALE);
        return new double[]{temp, humidity, continentalness, erosion, weirdness};
    }

    /** Terrain *shape* noise - independent of biome climate, see class docs. */
    private double terrainNoiseAt(int worldX, int worldZ) {
        return heightNoise.fbm(worldX, worldZ, 5, 0.5, 2.0, 1.0 / 96.0);
    }

    public void generate(Chunk chunk, World world) {
        int originX = chunk.worldOriginX();
        int originZ = chunk.worldOriginZ();
        Random rnd = chunkRandom(chunk.chunkX, chunk.chunkZ);

        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        Biome[][] dominantBiome = new Biome[Chunk.SIZE_X][Chunk.SIZE_Z];
        float[][] treeProbability = new float[Chunk.SIZE_X][Chunk.SIZE_Z];

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                int worldX = originX + x;
                int worldZ = originZ + z;
                double[] climate = rawClimateAt(worldX, worldZ);
                double temp = climate[0], humidity = climate[1], continentalness = climate[2],
                        erosion = climate[3], weirdness = climate[4];
                double terrain = terrainNoiseAt(worldX, worldZ);

                Biome biome = Biome.closest(temp, humidity, continentalness, erosion, weirdness);
                dominantBiome[x][z] = biome;

                float[] weights = Biome.weights(temp, humidity, continentalness, erosion, weirdness);
                Biome[] all = Biome.values();
                double blendedHeight = 0;
                double blendedTreeProb = 0;
                for (int i = 0; i < all.length; i++) {
                    // Biome weights control base elevation/amplitude; terrain noise (shared
                    // across every biome at this column) controls the shape - two separate
                    // systems, not one function where terrain height also decides biome identity.
                    blendedHeight += weights[i] * (all[i].baseHeight + terrain * all[i].amplitude);
                    float prob = all[i].treeChance > 0 ? 1f / all[i].treeChance : 0f;
                    blendedTreeProb += weights[i] * prob;
                }
                treeProbability[x][z] = (float) blendedTreeProb;

                int height = (int) Math.round(blendedHeight);
                height = Math.max(4, Math.min(Chunk.SIZE_Y - 20, height));
                heights[x][z] = height;

                boolean beach = height <= SEA_LEVEL + 1;

                for (int y = 0; y <= height; y++) {
                    BlockType type;
                    if (y == 0) {
                        type = BlockType.BEDROCK;
                    } else if (y <= 3 && rnd.nextInt(y + 1) == 0) {
                        // Decreasing probability of bedrock for the bottom few layers, vanilla-style.
                        type = BlockType.BEDROCK;
                    } else if (y == height) {
                        type = beach ? BlockType.SAND : biome.surface;
                    } else if (y > height - 4) {
                        type = beach ? BlockType.SAND : biome.subsurface;
                    } else {
                        type = oreOrStone(rnd, y);
                    }
                    chunk.setLocal(x, y, z, type);
                }

                // Fill water up to sea level where terrain is below it; snowy/frozen biomes get
                // a frozen surface layer instead of open water - same rule, now covering both.
                boolean frozenSurface = biome == Biome.SNOWY_TUNDRA || biome == Biome.FROZEN_OCEAN;
                for (int y = height + 1; y <= SEA_LEVEL; y++) {
                    BlockType liquid = (frozenSurface && y == SEA_LEVEL) ? BlockType.ICE : BlockType.WATER;
                    chunk.setLocal(x, y, z, liquid);
                }
            }
        }

        // After the column fill (so caves see real stone/ore) and before trees (so a rare
        // surface-opening cave naturally stops a tree from being placed over the hole it left -
        // placeTrees' own suitability check re-reads the real post-carve block). See CaveCarver's
        // class docs for why this can't simply carve neighbor chunks directly.
        CaveCarver.carve(chunk, seed);

        placeTrees(chunk, heights, dominantBiome, treeProbability, rnd);
    }

    /**
     * Deterministic minimum-distance rejection with probabilistic satellite clustering - not a
     * fixed grid (would look unnatural) and not independent per-column checks (the old
     * behavior; nothing stopped two trees from landing on adjacent columns with overlapping
     * canopies). A chunk holds at most a few dozen trees, so an O(n) "far enough from every
     * already-placed tree" check against a plain list is cheap and needs no spatial hash -
     * avoids scanning anything world-sized. Deterministic because both the candidate order
     * (raster column order) and the Random source (the existing per-chunk seed) already are.
     *
     * Density stays the continuously-blended probability computed above (ramps smoothly across
     * a biome border); species selection uses the *dominant* biome at each column - a discrete
     * choice, matching how the dominant biome already decides the surface block, so species
     * doesn't flicker into an unnatural mixture near a border the way blending it would.
     */
    static void placeTrees(Chunk chunk, int[][] heights, Biome[][] dominantBiome, float[][] treeProbability, Random rnd) {
        java.util.List<int[]> placed = new java.util.ArrayList<>(); // {x, z, canopyRadius}
        for (int x = 1; x < Chunk.SIZE_X - 1; x++) {
            for (int z = 1; z < Chunk.SIZE_Z - 1; z++) {
                if (!suitableColumn(chunk, x, z, heights[x][z], treeProbability[x][z])) continue;
                if (rnd.nextFloat() >= treeProbability[x][z]) continue;

                TreeSpecies species = speciesFor(dominantBiome[x][z], rnd);
                if (species == null) continue; // Desert/Badlands: never place trees, even if
                // blended density bled in slightly from a neighboring forest biome near the
                // border - "don't place trees merely because a neighbor is close" (request
                // Phase 3) takes precedence over the density blend specifically for these two.

                // A real, separately-configurable "small clearings" knob (Birch Forest's own
                // request), distinct from spacing/density: an otherwise-fully-suitable candidate
                // is still skipped with this probability, deliberately leaving open ground
                // rather than just letting spacing/clustering incidentally produce gaps. Zero
                // for every biome except Birch Forest this round - no behavior change elsewhere.
                if (rnd.nextFloat() < clearingChanceFor(dominantBiome[x][z])) continue;

                if (!fitsAndIsSuitable(chunk, x, z, heights, species, placed)) continue;

                species.place(chunk, x, heights[x][z] + 1, z, rnd);
                placed.add(new int[]{x, z, species.canopyRadius});

                float clusterChance = clusterChanceFor(dominantBiome[x][z]);
                if (rnd.nextFloat() < clusterChance) {
                    placeClusterSatellites(chunk, x, z, species, heights, dominantBiome, treeProbability, placed, rnd);
                }
            }
        }
    }

    private static void placeClusterSatellites(Chunk chunk, int centerX, int centerZ, TreeSpecies species,
                                         int[][] heights, Biome[][] dominantBiome, float[][] treeProbability,
                                         java.util.List<int[]> placed, Random rnd) {
        int satellites = 1 + rnd.nextInt(3);
        int reach = species.minSpacing * 2;
        for (int s = 0; s < satellites; s++) {
            int sx = centerX + rnd.nextInt(reach + 1) - reach / 2;
            int sz = centerZ + rnd.nextInt(reach + 1) - reach / 2;
            if (sx < 1 || sx >= Chunk.SIZE_X - 1 || sz < 1 || sz >= Chunk.SIZE_Z - 1) continue;
            if (!suitableColumn(chunk, sx, sz, heights[sx][sz], treeProbability[sx][sz])) continue;
            // Satellites stay the same species as the cluster's seed tree, regardless of what
            // this exact column's own dominant biome would independently pick - a cluster is
            // visually one clump of one species, not a mixed grove.
            if (!fitsAndIsSuitable(chunk, sx, sz, heights, species, placed)) continue;
            species.place(chunk, sx, heights[sx][sz] + 1, sz, rnd);
            placed.add(new int[]{sx, sz, species.canopyRadius});
        }
    }

    /** Ground-suitability checks shared by both the primary candidate loop and cluster satellites. */
    private static boolean suitableColumn(Chunk chunk, int x, int z, int height, float treeProbability) {
        if (treeProbability <= 0f) return false;
        if (height <= SEA_LEVEL) return false;
        BlockType top = chunk.getLocal(x, height, z);
        // MUD: Mangrove Swamp's own surface block (this round's addition) - without this,
        // switching MANGROVE's surface from grass to mud would have silently stopped every
        // Mangrove tree from ever generating again.
        return top == BlockType.GRASS_BLOCK || top == BlockType.SNOW_BLOCK || top == BlockType.MUD;
    }

    /** Canopy fits inside this chunk, doesn't exceed world height, terrain isn't too steep
     *  underfoot, and isn't within another tree's minimum spacing. */
    static boolean fitsAndIsSuitable(Chunk chunk, int x, int z, int[][] heights, TreeSpecies species, java.util.List<int[]> placed) {
        int r = species.canopyRadius;
        if (x - r < 0 || x + r >= Chunk.SIZE_X || z - r < 0 || z + r >= Chunk.SIZE_Z) return false;
        if (heights[x][z] + species.maxTotalHeight() >= Chunk.SIZE_Y) return false;
        if (!slopeOk(heights, x, z)) return false;
        for (int[] p : placed) {
            double dx = x - p[0], dz = z - p[1];
            double minAllowed = Math.max(species.minSpacing, p[2] + r); // also keep canopies from overlapping
            if (Math.sqrt(dx * dx + dz * dz) < minAllowed) return false;
        }
        return true;
    }

    private static final int SLOPE_LIMIT = 4;

    /** Rejects a candidate whose immediate neighbors' terrain height differs too much - avoids
     *  trees on cliff faces, the mechanism behind "respect slope limits" for Mountains. */
    static boolean slopeOk(int[][] heights, int x, int z) {
        int h = heights[x][z];
        int maxDiff = 0;
        if (x > 0) maxDiff = Math.max(maxDiff, Math.abs(h - heights[x - 1][z]));
        if (x < Chunk.SIZE_X - 1) maxDiff = Math.max(maxDiff, Math.abs(h - heights[x + 1][z]));
        if (z > 0) maxDiff = Math.max(maxDiff, Math.abs(h - heights[x][z - 1]));
        if (z < Chunk.SIZE_Z - 1) maxDiff = Math.max(maxDiff, Math.abs(h - heights[x][z + 1]));
        return maxDiff <= SLOPE_LIMIT;
    }

    /** Discrete species choice at the *dominant* biome (see placeTrees docs) - null means this
     *  biome never grows trees, regardless of any blended density bleeding in from a neighbor. */
    static TreeSpecies speciesFor(Biome dominant, Random rnd) {
        return switch (dominant) {
            case PLAINS -> rnd.nextFloat() < 0.1f ? TreeSpecies.BIRCH : TreeSpecies.OAK;
            case FOREST -> rnd.nextFloat() < 0.4f ? TreeSpecies.BIRCH : TreeSpecies.OAK;
            case TAIGA, SNOWY_TUNDRA, MOUNTAINS -> TreeSpecies.SPRUCE;
            case JUNGLE -> TreeSpecies.JUNGLE;
            case SAVANNA -> TreeSpecies.ACACIA;
            case SWAMP -> TreeSpecies.SWAMP_OAK;
            case MANGROVE -> TreeSpecies.MANGROVE;
            // Birch Forest always resolves to Birch (unlike Forest/Plains' probabilistic roll) -
            // it's the dedicated-biome identity the request asks for, not a denser version of
            // the same mixed roll.
            case BIRCH_FOREST -> TreeSpecies.BIRCH;
            case DARK_FOREST -> TreeSpecies.DARK_OAK;
            case CHERRY_GROVE -> TreeSpecies.CHERRY;
            case DESERT, BADLANDS, OCEAN, FROZEN_OCEAN -> null;
        };
    }

    /** How often an accepted tree tries to spawn 1-3 same-species satellites nearby - biome
     *  by biome, so Forest/Jungle/Swamp clump while Plains/Savanna stay mostly isolated trees. */
    static float clusterChanceFor(Biome dominant) {
        return switch (dominant) {
            // Dark Forest clumps hardest of all - "45-75% coverage, dense vegetation."
            case DARK_FOREST -> 0.65f;
            case FOREST, JUNGLE, SWAMP, MANGROVE, BIRCH_FOREST -> 0.5f;
            case TAIGA, SNOWY_TUNDRA, MOUNTAINS -> 0.35f;
            // Cherry Grove: "medium tree density," smaller/looser clusters than Forest.
            case CHERRY_GROVE -> 0.3f;
            case PLAINS, SAVANNA -> 0.05f;
            case DESERT, BADLANDS, OCEAN, FROZEN_OCEAN -> 0f;
        };
    }

    /** Probability an otherwise fully-suitable candidate is skipped anyway, leaving it as open
     *  ground - a real, separately-tunable "small clearings" knob (see placeTrees), distinct
     *  from spacing/density. Zero for every biome except Birch Forest this round: hardening this
     *  round is scoped to Birch Forest specifically, so every other biome's already-tuned/tested
     *  density is left exactly as it was. */
    static float clearingChanceFor(Biome dominant) {
        return dominant == Biome.BIRCH_FOREST ? 0.15f : 0f;
    }

    @Override
    public void computeTint(Chunk chunk) {
        int originX = chunk.worldOriginX();
        int originZ = chunk.worldOriginZ();
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                int worldX = originX + x, worldZ = originZ + z;
                double[] c = rawClimateAt(worldX, worldZ);
                float[] weights = Biome.weights(c[0], c[1], c[2], c[3], c[4]);
                float[] grass = biomeColors.blendGrass(worldX, worldZ, weights);
                float[] foliage = biomeColors.blendFoliage(worldX, worldZ, weights);
                float[] water = biomeColors.blendWater(weights);
                chunk.setGrassTint(x, z, grass[0], grass[1], grass[2]);
                chunk.setFoliageTint(x, z, foliage[0], foliage[1], foliage[2]);
                chunk.setWaterTint(x, z, water[0], water[1], water[2]);
            }
        }
    }

    @Override
    public ClimateSample climateAt(int worldX, int worldZ) {
        double[] c = rawClimateAt(worldX, worldZ);
        Biome dominant = Biome.closest(c[0], c[1], c[2], c[3], c[4]);
        float[] weights = Biome.weights(c[0], c[1], c[2], c[3], c[4]);
        float[] grass = biomeColors.blendGrass(worldX, worldZ, weights);
        float[] foliage = biomeColors.blendFoliage(worldX, worldZ, weights);
        float[] water = biomeColors.blendWater(weights);
        float[] fog = biomeColors.blendFog(weights);
        float[] underwaterFog = biomeColors.blendUnderwaterFog(weights);
        float[] skyTint = biomeColors.blendSkyTint(weights);
        return new ClimateSample(c[0], c[1], c[2], c[3], c[4], dominant, weights,
                grass, foliage, water, fog, underwaterFog, skyTint);
    }

    private BlockType oreOrStone(Random rnd, int y) {
        if (y < 16 && rnd.nextInt(200) == 0) return BlockType.DIAMOND_ORE;
        if (y < 32 && rnd.nextInt(120) == 0) return BlockType.GOLD_ORE;
        if (y < 64 && rnd.nextInt(70) == 0) return BlockType.IRON_ORE;
        if (y < 128 && rnd.nextInt(40) == 0) return BlockType.COAL_ORE;
        if (rnd.nextInt(500) == 0) return BlockType.GRAVEL;
        return BlockType.STONE;
    }

}
