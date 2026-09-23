package com.example.world;

/**
 * A snapshot of the climate-*selection* axes at one world column plus the resulting biome
 * blend, for the debug overlay AND for the per-frame atmosphere colors in
 * {@code Game.renderWorld} - one call to {@link TerrainGenerator#climateAt} at the player's
 * position gives both, avoiding a second climate/weight computation for fog specifically.
 * {@code grassColor}/{@code foliageColor}/{@code waterColor}/{@code fogColor}/
 * {@code underwaterFogColor} are the already-blended sRGB tints (see {@link BiomeColors}) - not
 * per-biome values, since the whole point of this sample is what the player actually sees at
 * this exact column.
 *
 * There is no "depth" field: vanilla's depth climate parameter comes from a 3D density
 * function, and this engine generates surface heightmaps rather than a density field, so that
 * axis has no honest analog here - the overlay says so explicitly rather than inventing one.
 */
public record ClimateSample(double temperature, double humidity, double continentalness,
                             double erosion, double weirdness, Biome dominant, float[] weights,
                             float[] grassColor, float[] foliageColor, float[] waterColor,
                             float[] fogColor, float[] underwaterFogColor) {
}
