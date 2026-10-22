package com.example.worldgen.terrain;

/** Port of {@code net.minecraft.world.level.biome.BiomeResolver} (26.3); biomes are resource id strings. */
@FunctionalInterface
public interface BiomeResolver {
    String getNoiseBiome(int quartX, int quartY, int quartZ);
}
