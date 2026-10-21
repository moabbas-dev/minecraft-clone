package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityVolume;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.Noise} (26.3): a float-valued noise sampled at double
 * coordinates, with a declared value {@link #range()}, plus the batch form {@link #addToVolume} used by compiled
 * density samplers.
 */
public interface Noise {

    Interval range();

    float get(double x, double z);

    float get(double x, double y, double z);

    /**
     * {@code Noise.addToVolume}: adds {@code amplitude * get(blockX * xzScale, blockY * yScale, blockZ * xzScale)}
     * to each buffer slot, in the volume's z/x/y buffer order. {@link PerlinNoise}, {@link SmearedPerlinNoise} and
     * {@link NoiseStack} override it exactly as vanilla does.
     */
    default void addToVolume(DensityBuffer buffer, DensityVolume volume, double xzScale, double yScale, float amplitude) {
        int index = 0;
        for (int indexZ = 0; indexZ < volume.sizeZ(); ++indexZ) {
            double z = (double) volume.blockZ(indexZ) * xzScale;
            for (int indexX = 0; indexX < volume.sizeX(); ++indexX) {
                double x = (double) volume.blockX(indexX) * xzScale;
                for (int indexY = 0; indexY < volume.sizeY(); ++indexY) {
                    double y = (double) volume.blockY(indexY) * yScale;
                    buffer.addTo(index, amplitude * get(x, y, z));
                    ++index;
                }
            }
        }
    }
}
