package com.example.worldgen.material;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeDefinitionLoader;
import com.example.worldgen.noise.NoiseStack;
import com.example.worldgen.noise.SimplexNoise;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.WorldgenRandom;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The part of {@code net.minecraft.world.level.biome.Biome} (26.3) the material system reads from a biome holder's value:
 * {@code getTemperature(BlockPos, int seaLevel)} (used by {@code TemperatureCondition} through {@code coldEnoughToSnow} and
 * by {@code MaterialSystem.frozenOceanExtension} through {@code shouldMeltFrozenOceanIcebergSlightly}).
 */
public interface BiomeClimate {

    /** {@code Biome.getTemperature(pos, seaLevel)} (a pure memo of {@code getHeightAdjustedTemperature}). */
    float getTemperature(String biome, int x, int y, int z, int seaLevel);

    /** {@code Biome.warmEnoughToRain}: {@code getTemperature >= 0.15F}; {@code coldEnoughToSnow} is its negation. */
    default boolean coldEnoughToSnow(String biome, int x, int y, int z, int seaLevel) {
        return !(getTemperature(biome, x, y, z, seaLevel) >= 0.15F);
    }

    /** {@code Biome.shouldMeltFrozenOceanIcebergSlightly}: {@code getTemperature > 0.1F}. */
    default boolean shouldMeltFrozenOceanIcebergSlightly(String biome, int x, int y, int z, int seaLevel) {
        return getTemperature(biome, x, y, z, seaLevel) > 0.1F;
    }

    /**
     * The vanilla climate: biome JSON ({@code temperature}, {@code temperature_modifier}) through {@link BiomeDefinition}
     * and the static noises of {@code Biome.<clinit>} (bytecode): {@code TEMPERATURE_NOISE = SimplexNoise(WorldgenRandom(
     * LegacyRandomSource(1234)), true)}; {@code FROZEN_TEMPERATURE_NOISE} = {@code lambda$static$10}: one
     * {@code WorldgenRandom(LegacyRandomSource(3456))} shared by {@code NoiseStack.builder().add(SimplexNoise(r, true), 1.0,
     * 0.14285715F).add(SimplexNoise(r, true), 0.5, 0.2857143F).add(SimplexNoise(r, true), 0.25, 0.5714286F).build()};
     * {@code BIOME_INFO_NOISE = SimplexNoise(WorldgenRandom(LegacyRandomSource(2345)), true)}.
     */
    static BiomeClimate vanilla(Path dataRoot) {
        BiomeDefinitionLoader loader = new BiomeDefinitionLoader(dataRoot);
        SimplexNoise temperatureNoise = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), true);
        WorldgenRandom frozenRandom = new WorldgenRandom(new LegacyRandomSource(3456L));
        NoiseStack frozen = NoiseStack.builder()
                .add(new SimplexNoise(frozenRandom, true), 1.0, 0.14285715F)
                .add(new SimplexNoise(frozenRandom, true), 0.5, 0.2857143F)
                .add(new SimplexNoise(frozenRandom, true), 0.25, 0.5714286F)
                .build();
        SimplexNoise biomeInfoNoise = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), true);
        BiomeDefinition.Noises noises = new BiomeDefinition.Noises(temperatureNoise::get, frozen::get, biomeInfoNoise::get);
        Map<String, BiomeDefinition> definitions = new ConcurrentHashMap<>();
        return (biome, x, y, z, seaLevel) -> definitions.computeIfAbsent(biome, id -> {
            try {
                return loader.load(id);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load biome " + id, e);
            }
        }).getHeightAdjustedTemperature(x, y, z, seaLevel, noises);
    }
}
