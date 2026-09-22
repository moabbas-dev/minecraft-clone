package com.example.world;

/** A snapshot of the 5-axis climate at one world column, for the debug overlay. */
public record ClimateSample(double temperature, double humidity, double continentalness,
                             double erosion, double terrainVariation, Biome dominant, float[] weights) {
}
