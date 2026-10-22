package com.example.worldgen.terrain;

import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.RandomState;

import java.util.EnumSet;

/**
 * Facade for the 26.3 BIOMES and TERRAIN chunk statuses of one chunk, in vanilla's order:
 * <ol>
 *     <li>BIOMES: {@link TerrainBiomes#doCreateBiomes} ({@code ChunkGenerator.createBiomes}); persisted status
 *     becomes {@code BIOMES};</li>
 *     <li>TERRAIN ({@code NoiseBasedChunkGenerator.buildTerrain}, supplier {@code lambda$buildTerrain$0}):
 *     {@code createNoiseChunk}, {@code doFill}, then <b>{@code buildSurface}</b> (material system stage:
 *     {@code randomState.surfaceSystem().buildSurface(randomState, biomeManager, context, chunk, noiseChunk,
 *     materialRule, possibleBiomes)}, it uses {@code noiseChunk.cachingSamplers()}), then <b>{@code generateCarvers}</b>
 *     (carver stage: the mask is applied with {@code noiseChunk.aquifer().computeSubstance(x, y, z, 0.0)} and
 *     {@code chunk.setBlockState}, so the same {@link NoiseChunk} and its aquifer must stay alive until the carvers
 *     are done), then {@code noiseChunk.close()};</li>
 *     <li>{@code ChunkStatusTasks.buildTerrain}'s completion ({@code lambda$buildTerrain$0}) primes
 *     {@code MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES, OCEAN_FLOOR, WORLD_SURFACE}; persisted status becomes
 *     {@code TERRAIN} (from then on {@code setBlockState} keeps the four final heightmaps).</li>
 * </ol>
 * {@link #generate} runs biomes and the fill and finishes the status; the surface and carver stages plug in between
 * {@link #fill} and {@link #finishTerrainStatus} (use {@link #beginTerrain} to hold the {@link NoiseChunk}).
 */
public final class VanillaTerrain {

    private VanillaTerrain() {
    }

    /** Biomes + noise fill + heightmap priming of one chunk (no surface, no carvers yet). */
    public static TerrainChunk generate(RandomState state, NoiseGeneratorSettings settings, MultiNoiseBiomeSource source,
                                        BlockTags tags, int chunkX, int chunkZ) {
        TerrainFiller filler = new TerrainFiller(settings);
        TerrainChunk chunk = createBiomes(state, settings, source, tags, chunkX, chunkZ);
        try (NoiseChunk noiseChunk = beginTerrain(state, filler, chunk)) {
            fill(filler, noiseChunk, chunk);
        }
        finishTerrainStatus(chunk);
        return chunk;
    }

    /** A new chunk over the settings' noise height with its BIOMES status done. */
    public static TerrainChunk createBiomes(RandomState state, NoiseGeneratorSettings settings, MultiNoiseBiomeSource source,
                                            BlockTags tags, int chunkX, int chunkZ) {
        TerrainChunk chunk = new TerrainChunk(chunkX, chunkZ, settings.noiseSettings().minY(), settings.noiseSettings().height(), tags);
        TerrainBiomes.doCreateBiomes(state, source, chunk);
        chunk.setPersistedStatus(TerrainChunk.Status.BIOMES);
        return chunk;
    }

    /** {@code createNoiseChunk}: the caller closes it after the carver stage. */
    public static NoiseChunk beginTerrain(RandomState state, TerrainFiller filler, TerrainChunk chunk) {
        return filler.createNoiseChunk(state, chunk);
    }

    /** {@code doFill}. */
    public static void fill(TerrainFiller filler, NoiseChunk noiseChunk, TerrainChunk chunk) {
        filler.doFill(noiseChunk, chunk);
    }

    /** End of the TERRAIN task: prime the four final heightmaps and persist status {@code TERRAIN}. */
    public static void finishTerrainStatus(TerrainChunk chunk) {
        chunk.primeHeightmaps(EnumSet.of(HeightmapType.MOTION_BLOCKING, HeightmapType.MOTION_BLOCKING_NO_LEAVES,
                HeightmapType.OCEAN_FLOOR, HeightmapType.WORLD_SURFACE));
        chunk.setPersistedStatus(TerrainChunk.Status.TERRAIN);
    }
}
