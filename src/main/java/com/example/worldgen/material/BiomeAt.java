package com.example.worldgen.material;

/**
 * The biome getter the material system receives: vanilla passes {@code biomeManager::getBiome}
 * ({@code BiomeManager.getBiome(BlockPos)}, the fiddled zoom over the chunk's stored noise biomes). Returns the biome
 * id ({@code minecraft:plains}, ...). The integration supplies the terrain stage's {@code BiomeManager} here.
 */
@FunctionalInterface
public interface BiomeAt {
    String getBiome(int x, int y, int z);
}
