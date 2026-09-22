package com.example.world;

import java.io.File;
import java.util.Random;

/**
 * Biome-aware overworld generator driven by continuous 5-axis climate noise (temperature,
 * humidity, continentalness, erosion, terrain-variation - the last one reuses the height noise
 * itself rather than needing a 6th generator). Biome *selection* for discrete properties
 * (surface/subsurface block) uses nearest-match in that climate space via {@link Biome#closest}
 * - never an if/else threshold. Continuous properties (terrain height, tree density) are instead
 * blended across every biome's {@link Biome#weights} influence, so borders ramp smoothly instead
 * of switching at a hard line. Grass/foliage color is sampled straight from the real vanilla
 * colormap images using each column's own raw climate noise - already smooth because the noise
 * itself is smooth, with no separate blending step needed for color specifically.
 *
 * All of this only ever depends on world (x, z) coordinates - no chunk-local state - so it is
 * automatically seamless across chunk borders: the same world column produces the same climate
 * sample and the same blended values no matter which chunk generates it.
 */
public class TerrainGenerator implements WorldGenerator {

    public static final int SEA_LEVEL = 63;
    private static final double CLIMATE_SCALE = 1.0 / 300.0;
    private static final double MACRO_CLIMATE_SCALE = 1.0 / 800.0; // continentalness/erosion: large, slow-varying regions

    private final long seed;
    private final SimplexNoise heightNoise;
    private final SimplexNoise temperatureNoise;
    private final SimplexNoise humidityNoise;
    private final SimplexNoise continentalnessNoise;
    private final SimplexNoise erosionNoise;

    private static final Colormap GRASS_COLORMAP =
            Colormap.load(new File("textures/colormap/grass.png"), 0x7CBD6B);
    private static final Colormap FOLIAGE_COLORMAP =
            Colormap.load(new File("textures/colormap/foliage.png"), 0x59AE30);

    public TerrainGenerator(long seed) {
        this.seed = seed;
        this.heightNoise = new SimplexNoise(seed);
        this.temperatureNoise = new SimplexNoise(seed + 1000);
        this.humidityNoise = new SimplexNoise(seed + 2000);
        this.continentalnessNoise = new SimplexNoise(seed + 3000);
        this.erosionNoise = new SimplexNoise(seed + 4000);
    }

    /** Mirrors vanilla's classic chunk decoration-seed formula for deterministic per-chunk randomness. */
    private Random chunkRandom(int cx, int cz) {
        long chunkSeed = (seed
                + (long) cx * 341873128712L
                + (long) cz * 132897987541L);
        return new Random(chunkSeed);
    }

    /** The 5 raw continuous climate values at a world column, each roughly in [-1, 1]. */
    private double[] rawClimateAt(int worldX, int worldZ) {
        double temp = temperatureNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, CLIMATE_SCALE);
        double humidity = humidityNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, CLIMATE_SCALE);
        double continentalness = continentalnessNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, MACRO_CLIMATE_SCALE);
        double erosion = erosionNoise.fbm(worldX, worldZ, 2, 0.5, 2.0, MACRO_CLIMATE_SCALE);
        double terrainVariation = heightNoise.fbm(worldX, worldZ, 5, 0.5, 2.0, 1.0 / 96.0);
        return new double[]{temp, humidity, continentalness, erosion, terrainVariation};
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
                        erosion = climate[3], terrainVariation = climate[4];

                Biome biome = Biome.closest(temp, humidity, continentalness, erosion, terrainVariation);
                dominantBiome[x][z] = biome;

                float[] weights = Biome.weights(temp, humidity, continentalness, erosion, terrainVariation);
                Biome[] all = Biome.values();
                double blendedHeight = 0;
                double blendedTreeProb = 0;
                for (int i = 0; i < all.length; i++) {
                    blendedHeight += weights[i] * (all[i].baseHeight + terrainVariation * all[i].amplitude);
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

                // Fill water up to sea level where terrain is below it; snowy biomes get a
                // frozen surface layer instead of open water.
                for (int y = height + 1; y <= SEA_LEVEL; y++) {
                    BlockType liquid = (biome == Biome.SNOWY_TUNDRA && y == SEA_LEVEL)
                            ? BlockType.ICE : BlockType.WATER;
                    chunk.setLocal(x, y, z, liquid);
                }
            }
        }

        // Trees: only on interior columns so the whole canopy fits inside this chunk. Density
        // is the continuously-blended probability computed above, not the dominant biome's
        // raw treeChance, so tree density itself ramps smoothly across a biome border too.
        for (int x = 2; x < Chunk.SIZE_X - 2; x++) {
            for (int z = 2; z < Chunk.SIZE_Z - 2; z++) {
                if (treeProbability[x][z] <= 0f) continue;
                int height = heights[x][z];
                if (height <= SEA_LEVEL) continue;
                BlockType top = chunk.getLocal(x, height, z);
                if (top != BlockType.GRASS_BLOCK && top != BlockType.SNOW_BLOCK) continue;
                if (rnd.nextFloat() >= treeProbability[x][z]) continue;
                placeTree(chunk, x, height + 1, z, rnd);
            }
        }
    }

    @Override
    public void computeTint(Chunk chunk) {
        int originX = chunk.worldOriginX();
        int originZ = chunk.worldOriginZ();
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                double temp = temperatureNoise.fbm(originX + x, originZ + z, 2, 0.5, 2.0, CLIMATE_SCALE);
                double humidity = humidityNoise.fbm(originX + x, originZ + z, 2, 0.5, 2.0, CLIMATE_SCALE);
                float[] grass = GRASS_COLORMAP.sample(temp, humidity);
                float[] foliage = FOLIAGE_COLORMAP.sample(temp, humidity);
                chunk.setGrassTint(x, z, grass[0], grass[1], grass[2]);
                chunk.setFoliageTint(x, z, foliage[0], foliage[1], foliage[2]);
            }
        }
    }

    @Override
    public ClimateSample climateAt(int worldX, int worldZ) {
        double[] c = rawClimateAt(worldX, worldZ);
        Biome dominant = Biome.closest(c[0], c[1], c[2], c[3], c[4]);
        float[] weights = Biome.weights(c[0], c[1], c[2], c[3], c[4]);
        return new ClimateSample(c[0], c[1], c[2], c[3], c[4], dominant, weights);
    }

    private BlockType oreOrStone(Random rnd, int y) {
        if (y < 16 && rnd.nextInt(200) == 0) return BlockType.DIAMOND_ORE;
        if (y < 32 && rnd.nextInt(120) == 0) return BlockType.GOLD_ORE;
        if (y < 64 && rnd.nextInt(70) == 0) return BlockType.IRON_ORE;
        if (y < 128 && rnd.nextInt(40) == 0) return BlockType.COAL_ORE;
        if (rnd.nextInt(500) == 0) return BlockType.GRAVEL;
        return BlockType.STONE;
    }

    private void placeTree(Chunk chunk, int x, int baseY, int z, Random rnd) {
        int trunkHeight = 4 + rnd.nextInt(2);
        int top = baseY + trunkHeight - 1;
        if (top + 2 >= Chunk.SIZE_Y) return;

        for (int dy = 0; dy < trunkHeight; dy++) {
            chunk.setLocal(x, baseY + dy, z, BlockType.OAK_LOG);
        }

        // Two wide leaf layers (radius 2, corners trimmed), then a narrow cap (radius 1, plus-shape).
        for (int dy = -2; dy <= -1; dy++) {
            int ly = top + dy + 1;
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
                    if (lx == 0 && lz == 0 && dy != -2) continue;
                    placeLeaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            int ly = top + dy + 1;
            for (int lx = -1; lx <= 1; lx++) {
                for (int lz = -1; lz <= 1; lz++) {
                    if (dy == 1 && lx != 0 && lz != 0) continue;
                    if (lx == 0 && lz == 0 && dy == 0) continue;
                    placeLeaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
    }

    private void placeLeaf(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) return;
        if (chunk.getLocal(x, y, z).isAir()) {
            chunk.setLocal(x, y, z, BlockType.OAK_LEAVES);
        }
    }
}
