package com.example.worldgen.levelgen;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySamplerSet;

/**
 * Port of {@code net.minecraft.world.level.levelgen.NoiseRouter} (26.3). The 26.3 router has exactly eight fields
 * (JSON keys {@code temperature}, {@code vegetation}, {@code continents}, {@code erosion}, {@code depth},
 * {@code ridges}, {@code chunk_surface_level}, {@code final_density}); the old barrier/fluid/lava/vein/
 * initial-density entries moved to {@code NoiseGeneratorSettings.aquifers} or were removed.
 */
public record NoiseRouter(DensityFunction temperature, DensityFunction vegetation, DensityFunction continents,
                          DensityFunction erosion, DensityFunction depth, DensityFunction ridges,
                          DensityFunction chunkSurfaceLevel, DensityFunction finalDensity) {

    /** {@code createClimateSampler}: binds the six climate functions through the sampler set. */
    public Climate.Sampler createClimateSampler(DensitySamplerSet densitySamplers) {
        return new Climate.Sampler(densitySamplers.get(temperature), densitySamplers.get(vegetation),
                densitySamplers.get(continents), densitySamplers.get(erosion), densitySamplers.get(depth),
                densitySamplers.get(ridges));
    }
}
