package com.example.worldgen.feature;

/**
 * The parts of {@code ChunkGenerator} (26.3) the placement code reads: {@code getMinY()}, {@code getGenDepth()},
 * {@code getSeaLevel()} (for {@code PlacementContext}'s {@code WorldGenerationContext}) and
 * {@code getBiomeGenerationSettings(biome).hasFeature(feature)} (for {@code BiomeFilter}).
 */
public interface DecorationGenerator {

    int minY();

    int genDepth();

    int seaLevel();

    /** {@code getBiomeGenerationSettings(biome).hasFeature(feature)}. */
    boolean biomeHasFeature(String biome, PlacedFeature feature);
}
