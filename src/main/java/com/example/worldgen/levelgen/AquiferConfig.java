package com.example.worldgen.levelgen;

import com.example.worldgen.density.DensityFunction;

/**
 * Port of the data of {@code net.minecraft.world.level.levelgen.Aquifer.Config} (26.3), the optional
 * {@code aquifers} section of the noise settings: JSON {@code barrier}, {@code fluid_level_floodedness},
 * {@code fluid_level_spread}, {@code lava}, {@code exclusion}, {@code surface_level} (all required density
 * functions). Vanilla's {@code create(...)} builds the {@code NoiseBasedAquifer}, which is the next stage.
 */
public record AquiferConfig(DensityFunction barrierNoise, DensityFunction fluidLevelFloodednessNoise,
                            DensityFunction fluidLevelSpreadNoise, DensityFunction lavaNoise, DensityFunction exclusion,
                            DensityFunction surfaceLevel) {
}
