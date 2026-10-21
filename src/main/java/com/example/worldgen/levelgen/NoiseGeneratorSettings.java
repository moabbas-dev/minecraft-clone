package com.example.worldgen.levelgen;

import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.random.WorldgenRandom;

import java.util.List;
import java.util.Optional;

/**
 * Port of {@code net.minecraft.world.level.levelgen.NoiseGeneratorSettings} (26.3), decoded by
 * {@link NoiseGeneratorSettingsLoader}. Fields and JSON keys from the 26.3 {@code DIRECT_CODEC}:
 * {@code noise} ({@link NoiseSettings}), {@code default_block}, {@code default_fluid} (block states, kept as their
 * raw JSON until the block registry stage), {@code noise_router}, {@code material_rule} (kept as the raw JSON tree
 * for the surface stage; a string id is resolved to its {@code worldgen/material_rule} file),
 * {@code spawn_target}, {@code sea_level}, {@code disable_mob_generation}, optional {@code aquifers},
 * {@code legacy_random_source}, optional {@code debug_functions} (default empty).
 *
 * @param defaultBlock        the raw {@code default_block} JSON (vanilla {@code BlockState.CODEC})
 * @param defaultFluid        the raw {@code default_fluid} JSON
 * @param materialRuleId      the registry id when {@code material_rule} was a reference, else empty
 * @param materialRule        the material rule JSON tree (the referenced file's content for an id)
 */
public record NoiseGeneratorSettings(NoiseSettings noiseSettings, Object defaultBlock, Object defaultFluid,
                                     NoiseRouter noiseRouter, Optional<String> materialRuleId, Object materialRule,
                                     List<SpawnTargetPoint> spawnTarget, int seaLevel, boolean disableMobGeneration,
                                     Optional<AquiferConfig> aquifers, boolean useLegacyRandomSource,
                                     List<DebugFunctionEntry> debugFunctions) {

    public NoiseGeneratorSettings {
        spawnTarget = List.copyOf(spawnTarget);
        debugFunctions = List.copyOf(debugFunctions);
    }

    /** {@code getRandomSource()}: {@code LEGACY} when {@code legacy_random_source}, else {@code XOROSHIRO}. */
    public WorldgenRandom.Algorithm getRandomSource() {
        return useLegacyRandomSource ? WorldgenRandom.Algorithm.LEGACY : WorldgenRandom.Algorithm.XOROSHIRO;
    }

    /** {@code NoiseGeneratorSettings.DebugFunctionEntry}: JSON {@code label} and {@code function}. */
    public record DebugFunctionEntry(String label, DensityFunction function) {
    }
}
