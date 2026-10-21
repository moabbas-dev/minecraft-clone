package com.example.worldgen.levelgen;

import com.example.worldgen.Mth;
import com.example.worldgen.biome.Climate;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.Holder;

import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.SpawnTargetPoint} (26.3): one {@code spawn_target} entry, a map
 * from density-function registry reference to a climate {@link Climate.Parameter}. {@link #sampleFitness} sums the
 * squared distance of each quantised value to its parameter.
 */
public record SpawnTargetPoint(Map<Holder<DensityFunction>, Climate.Parameter> parameters) {

    public long sampleFitness(DensitySamplerSet samplers, int blockX, int blockY, int blockZ) {
        long fitness = 0L;
        for (Map.Entry<Holder<DensityFunction>, Climate.Parameter> parameter : parameters.entrySet()) {
            DensityFunction function = parameter.getKey().value();
            long value = Climate.quantizeCoord(samplers.sampleValue(function, blockX, blockY, blockZ));
            fitness += Mth.square(parameter.getValue().distance(value));
        }
        return fitness;
    }
}
