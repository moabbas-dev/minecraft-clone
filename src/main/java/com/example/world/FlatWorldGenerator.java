package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeDefinitionLoader;
import com.example.worldgen.biome.Biomes;
import com.example.worldgen.chunk.OverworldGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Vanilla's classic flat preset (worldgen/flat_level_generator_preset/classic_flat.json): layers bedrock x1, dirt x2, grass_block x1,
 * stacked from the bottom of the world ({@link Chunk#MIN_Y}) upward - so bedrock is at y -64 and the grass at y -61 - and the preset's
 * single biome, plains, for tint, atmosphere and precipitation.
 */
public class FlatWorldGenerator implements WorldGenerator {
    private static final int DIRT_LAYERS = 2;
    /** World Y of the grass layer, the top block of the ground. */
    public static final int GRASS_HEIGHT = Chunk.MIN_Y + 1 + DIRT_LAYERS;

    @Override
    public void generate(Chunk chunk, World world) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                chunk.setLocal(x, Chunk.MIN_Y, z, BlockType.BEDROCK);
                for (int y = Chunk.MIN_Y + 1; y < GRASS_HEIGHT; y++) {
                    chunk.setLocal(x, y, z, BlockType.DIRT);
                }
                chunk.setLocal(x, GRASS_HEIGHT, z, BlockType.GRASS_BLOCK);
            }
        }
    }

    /**
     * The preset's {@code biome}: {@code FlatLevelSource} uses a {@code FixedBiomeSource}, so every quart cell (and through the
     * {@code BiomeManager} zoom every block) is this biome.
     */
    public static final String BIOME = Biomes.PLAINS;
    /** {@code FlatLevelSource.getSeaLevel()} ({@code bipush -63} in the 26.3 jar). */
    public static final int SEA_LEVEL = -63;

    private static final String[] BIOME_TABLE = new String[16 * (Chunk.SIZE_Y >> 2)];

    static {
        java.util.Arrays.fill(BIOME_TABLE, BIOME);
    }

    private final Path dataRoot;
    private BiomeDefinition plains;
    private final BiomeDefinition.Noises noises = BiomeDefinition.Noises.vanilla();
    private final BiomeTints tints = new BiomeTints(this::biomeDefinition, noises);

    public FlatWorldGenerator() {
        this(VanillaChunkGenerator.DATA_ROOT);
    }

    /** @param dataRoot the folder holding {@code minecraft/worldgen/biome} (the plains biome file is read from it on first use) */
    public FlatWorldGenerator(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    @Override
    public boolean hasBiomes() {
        return true;
    }

    @Override
    public void computeBiomes(Chunk chunk) {
        if (!chunk.hasBiomes()) chunk.setQuartBiomes(BIOME_TABLE.clone());
    }

    @Override
    public String noiseBiome(int quartX, int quartY, int quartZ) {
        return BIOME;
    }

    @Override
    public BiomeDefinition biomeDefinition(String biomeId) {
        if (!BIOME.equals(biomeId)) throw new IllegalArgumentException("a classic flat world has only " + BIOME + ", not " + biomeId);
        if (plains == null) {
            try {
                plains = new BiomeDefinitionLoader(dataRoot).load(BIOME);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read the plains biome file under " + dataRoot.toAbsolutePath()
                        + " (restore it with " + OverworldGenerator.RESTORE_HINT + ")", e);
            }
        }
        return plains;
    }

    @Override
    public BiomeDefinition.Noises biomeNoises() {
        return noises;
    }

    @Override
    public int seaLevel() {
        return SEA_LEVEL;
    }

    /** Vanilla's block tint from the plains biome file (grass and foliage from the colormaps at its temperature and downfall). */
    @Override
    public void computeTint(Chunk chunk, World world) {
        tints.computeTint(chunk, world);
    }
}
