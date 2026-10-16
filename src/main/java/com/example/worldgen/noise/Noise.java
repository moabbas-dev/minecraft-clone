package com.example.worldgen.noise;

import com.example.worldgen.Interval;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.Noise} (26.3): a float-valued noise
 * sampled at double coordinates, with a declared value {@link #range()}.
 * Vanilla's batch method {@code addToVolume(DensityBuffer, DensityVolume, ...)} belongs to the
 * density-function system and is not part of this port; it evaluates the same values as
 * {@link #get(double, double, double)} at the volume's block coordinates.
 */
public interface Noise {

    Interval range();

    float get(double x, double z);

    float get(double x, double y, double z);
}
