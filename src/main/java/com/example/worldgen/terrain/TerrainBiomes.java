package com.example.worldgen.terrain;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.biome.MultiNoiseBiomeSource;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityBufferPool;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.levelgen.NoiseRouter;
import com.example.worldgen.levelgen.RandomState;

import java.util.function.BiFunction;

/**
 * The BIOMES chunk status of 26.3 and the biome resolvers built on a {@link MultiNoiseBiomeSource}:
 * <ul>
 *     <li>{@link #doCreateBiomes}: {@code ChunkGenerator.doCreateBiomes}: a pooled, caching
 *     {@code SamplerContext}, {@code randomState.createClimateSampler(context)}, then
 *     {@code biomeSource.createResolverForChunk(sampler, quart(minBlockX), quart(minY), quart(minBlockZ), 4,
 *     quart(height), 4)} and {@code chunk.fillBiomesFromNoise(resolver)} (the empty blender and
 *     {@code BelowZeroRetrogen.getBiomeResolver} of a non-upgrading chunk return the resolver unchanged);</li>
 *     <li>{@link #createResolverForChunk}: {@code MultiNoiseBiomeSource.createResolverForChunk}: the six climate
 *     functions are sampled as whole {@code DensityVolume}s with step 4 into unpooled buffers (temperature,
 *     vegetation, continents, erosion, depth, ridges), the lookup is
 *     {@code getNoiseBiome(Climate.target(...buffer values...))};</li>
 *     <li>{@link #createUncachedResolver}: {@code BiomeSource.createUncachedResolver} (point sampling with
 *     {@code SamplerContext.EMPTY_UNCACHED}; used by the carvers);</li>
 *     <li>{@link #regionResolver}: {@code LevelReader.getNoiseBiome} as a {@code WorldGenRegion} answers it: the
 *     chunk's stored quart biome when the chunk is available, else the uncached resolver.</li>
 * </ul>
 */
public final class TerrainBiomes {

    private TerrainBiomes() {
    }

    public static void doCreateBiomes(RandomState randomState, MultiNoiseBiomeSource source, TerrainChunk chunk) {
        chunk.setQuartBiomes(createBiomeTable(randomState, source, chunk.chunkX(), chunk.chunkZ(), chunk.minY(), chunk.height()));
    }

    /**
     * What {@link #doCreateBiomes} stores in a chunk at {@code (chunkX, chunkZ)} over {@code [minY, minY + height)}, as a
     * quart biome table ({@link TerrainChunk#quartBiomesFromNoise} layout): lets the BIOMES status of a neighbouring chunk be
     * computed (and cached) without building that chunk.
     */
    public static String[] createBiomeTable(RandomState randomState, MultiNoiseBiomeSource source, int chunkX, int chunkZ,
                                            int minY, int height) {
        DensityBufferPool bufferPool = randomState.acquireDensityBufferPool();
        try {
            SamplerContext context = SamplerContext.builder().enableCaches().useBufferArena(bufferPool).build();
            DensitySamplerSet samplers = randomState.samplersWithContext(context);
            BiomeResolver resolver = createResolverForChunk(source, randomState.router(), samplers, (chunkX * 16) >> 2,
                    minY >> 2, (chunkZ * 16) >> 2, 16 >> 2, height >> 2, 16 >> 2);
            return TerrainChunk.quartBiomesFromNoise(resolver, chunkX, chunkZ, minY, height);
        } finally {
            randomState.releaseDensityBufferPool(bufferPool);
        }
    }

    /**
     * {@code createResolverForChunk}; {@code samplers.get(router.x())} is what {@code NoiseRouter.createClimateSampler}
     * binds for each of the six climate fields.
     */
    public static BiomeResolver createResolverForChunk(MultiNoiseBiomeSource source, NoiseRouter router, DensitySamplerSet samplers,
                                                       int minQuartX, int minQuartY, int minQuartZ,
                                                       int quartSizeX, int quartSizeY, int quartSizeZ) {
        DensityVolume volume = new DensityVolume(quartSizeX, quartSizeY, quartSizeZ, minQuartX << 2, minQuartY << 2, minQuartZ << 2, 4, 4, 4);
        DensitySampler.Bound temperatureSampler = samplers.get(router.temperature());
        DensitySampler.Bound humiditySampler = samplers.get(router.vegetation());
        DensitySampler.Bound continentalnessSampler = samplers.get(router.continents());
        DensitySampler.Bound erosionSampler = samplers.get(router.erosion());
        DensitySampler.Bound depthSampler = samplers.get(router.depth());
        DensitySampler.Bound weirdnessSampler = samplers.get(router.ridges());
        DensityBuffer temperature = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer vegetation = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer continents = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer erosion = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer depth = DensityBuffer.createUnpooled(volume.size());
        DensityBuffer ridges = DensityBuffer.createUnpooled(volume.size());
        temperatureSampler.sampleVolume(temperature, volume);
        humiditySampler.sampleVolume(vegetation, volume);
        continentalnessSampler.sampleVolume(continents, volume);
        erosionSampler.sampleVolume(erosion, volume);
        depthSampler.sampleVolume(depth, volume);
        weirdnessSampler.sampleVolume(ridges, volume);
        return (quartX, quartY, quartZ) -> {
            int index = volume.indexUnchecked(quartX - minQuartX, quartY - minQuartY, quartZ - minQuartZ);
            return source.getNoiseBiome(Climate.target(temperature.get(index), vegetation.get(index), continents.get(index),
                    erosion.get(index), depth.get(index), ridges.get(index)));
        };
    }

    /** {@code BiomeSource.createUncachedResolver(randomState)}. */
    public static BiomeResolver createUncachedResolver(RandomState randomState, MultiNoiseBiomeSource source) {
        Climate.Sampler sampler = randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
        return (quartX, quartY, quartZ) -> source.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    /**
     * {@code LevelReader.getNoiseBiome} over a set of chunks ({@code getChunk(QuartPos.toSection(x),
     * QuartPos.toSection(z), BIOMES, false)}): {@code chunks} returns the chunk at a chunk position or {@code null}.
     */
    public static BiomeResolver regionResolver(BiFunction<Integer, Integer, TerrainChunk> chunks, BiomeResolver uncached) {
        return (quartX, quartY, quartZ) -> {
            TerrainChunk chunk = chunks.apply(quartX >> 2, quartZ >> 2);
            return chunk != null ? chunk.getNoiseBiome(quartX, quartY, quartZ) : uncached.getNoiseBiome(quartX, quartY, quartZ);
        };
    }
}
