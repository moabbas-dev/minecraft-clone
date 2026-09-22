package com.example.world;

/**
 * A snapshot of the climate-*selection* axes at one world column plus the resulting biome
 * blend, for the debug overlay. {@code grassColor}/{@code foliageColor} are the already-blended
 * sRGB tint (see {@link BiomeColors}) - not a per-biome value, since the whole point of the
 * overlay is to show what the player actually sees at this exact column.
 *
 * There is no "depth" field: vanilla's depth climate parameter comes from a 3D density
 * function, and this engine generates surface heightmaps rather than a density field, so that
 * axis has no honest analog here - the overlay says so explicitly rather than inventing one.
 */
public record ClimateSample(double temperature, double humidity, double continentalness,
                             double erosion, double weirdness, Biome dominant, float[] weights,
                             float[] grassColor, float[] foliageColor) {
}
