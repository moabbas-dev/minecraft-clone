package com.example.worldgen.biome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port of {@code net.minecraft.world.level.biome.MultiNoiseBiomeSource} (Minecraft Java Edition 26.3),
 * biomes identified by resource id strings.
 *
 * <p>Verified from 26.3 bytecode: {@code createResolver(sampler)} returns
 * {@code (x, y, z) -> getNoiseBiome(sampler.sample(x, y, z))} and {@code getNoiseBiome(TargetPoint)} is
 * {@code parameters().findValue(target)}. {@code possibleBiomes} mirrors {@code BiomeSource}'s memoised
 * {@code collectPossibleBiomes().distinct()} collected into an insertion-ordered immutable set.
 * Not ported: {@code createResolverForChunk}, which samples the six climate densities as whole
 * {@code DensityVolume}s of the density-function engine before doing the same per-point lookup, and the
 * F3 {@code addDebugInfo} text.
 */
public final class MultiNoiseBiomeSource {
    private final Climate.ParameterList<String> parameters;
    private Set<String> possibleBiomes;

    private MultiNoiseBiomeSource(Climate.ParameterList<String> parameters) {
        this.parameters = parameters;
    }

    public static MultiNoiseBiomeSource createFromList(Climate.ParameterList<String> parameters) {
        return new MultiNoiseBiomeSource(parameters);
    }

    public static MultiNoiseBiomeSource createFromPreset(MultiNoiseBiomeSourceParameterList preset) {
        return new MultiNoiseBiomeSource(preset.parameters());
    }

    public Climate.ParameterList<String> parameters() {
        return this.parameters;
    }

    public synchronized Set<String> possibleBiomes() {
        if (this.possibleBiomes == null) {
            LinkedHashSet<String> biomes = this.parameters.values().stream()
                .map(Climate.Entry::value).distinct().collect(Collectors.toCollection(LinkedHashSet::new));
            this.possibleBiomes = Collections.unmodifiableSet(biomes);
        }
        return this.possibleBiomes;
    }

    /** Vanilla {@code getNoiseBiome(TargetPoint)}. */
    public String getNoiseBiome(Climate.TargetPoint target) {
        return this.parameters.findValue(target);
    }

    /** Vanilla {@code createResolver(sampler).getNoiseBiome(quartX, quartY, quartZ)}. */
    public String getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.getNoiseBiome(sampler.sample(quartX, quartY, quartZ));
    }
}
