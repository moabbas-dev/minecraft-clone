package com.example.worldgen.levelgen;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.density.DensityBufferPool;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensityFunctionCompiler;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.Holder;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.noise.BlendedNoise;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.random.WorldgenRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Port of {@code net.minecraft.world.level.levelgen.RandomState} (26.3): the per-seed worldgen state.
 * <ul>
 *     <li>{@code random = (legacy ? LegacyRandomSource : XoroshiroRandomSource)(seed).forkPositional()};</li>
 *     <li>{@link #getOrCreateNoise(String)}: {@code NormalNoise.create(random.fromHashOf(id))}, memoised per id;</li>
 *     <li>{@link #getOrCreateRandomFactory(String)}: {@code random.fromHashOf(id).forkPositional()}, memoised
 *     (vanilla uses {@code "minecraft:aquifer"} for the aquifer);</li>
 *     <li>the density {@link DensityFunction.CompileContext}: {@code minecraft:nether/temperature} and
 *     {@code minecraft:nether/vegetation} use the legacy nether construction with {@code LegacyRandomSource(seed)}
 *     and {@code (seed + 1)}; every other noise must be a registry reference and goes through
 *     {@link #getOrCreateNoise}; {@code createRandom(id)} is {@code LegacyRandomSource(seed)} for
 *     {@code minecraft:terrain} under legacy random, else {@code random.fromHashOf(id)};
 *     {@code createEndIslandRandom()} is {@code LegacyRandomSource(seed)};</li>
 *     <li>compiled samplers come from one {@link DensityFunctionCompiler}; {@link #samplersWithContext} binds them to a
 *     {@link SamplerContext}; buffer pools (max age 20, at most 16 kept) are handed out to chunks.</li>
 * </ul>
 * Vanilla also builds its {@code MaterialSystem} (surface rules) here; that is the surface stage and is not part
 * of this class yet.
 */
public final class RandomState {

    private static final int MAX_BUFFER_POOLS = 16;
    private static final int MAX_BUFFER_AGE_TICKS = 20;

    private final long seed;
    private final PositionalRandomFactory random;
    private final WorldgenRegistries noises;
    private final NoiseRouter router;
    private final Map<String, Noise> noiseInstances = new ConcurrentHashMap<>();
    private final Map<String, PositionalRandomFactory> positionalRandoms = new ConcurrentHashMap<>();
    private final DensityFunctionCompiler densityFunctionCompiler;
    private final ReentrantLock densityBufferPoolLock = new ReentrantLock();
    private final List<DensityBufferPool> densityBufferPools = new ArrayList<>(MAX_BUFFER_POOLS);

    public static RandomState create(WorldgenRegistries noises, long seed, NoiseGeneratorSettings settings) {
        return create(noises, seed, settings.useLegacyRandomSource(), settings.noiseRouter());
    }

    public static RandomState create(WorldgenRegistries noises, long seed, boolean useLegacyRandom, NoiseRouter noiseRouter) {
        return new RandomState(noises, seed, useLegacyRandom, noiseRouter);
    }

    private RandomState(WorldgenRegistries noises, long seed, boolean useLegacyRandom, NoiseRouter router) {
        WorldgenRandom.Algorithm randomAlgorithm = useLegacyRandom ? WorldgenRandom.Algorithm.LEGACY : WorldgenRandom.Algorithm.XOROSHIRO;
        this.seed = seed;
        this.random = randomAlgorithm.newInstance(seed).forkPositional();
        this.noises = noises;
        this.router = router;
        this.densityFunctionCompiler = new DensityFunctionCompiler(new DensityFunction.CompileContext() {
            private RandomSource newLegacyInstance(long seedOffset) {
                return new LegacyRandomSource(seed + seedOffset);
            }

            @Override
            public Noise createNoiseSampler(Holder<NormalNoise> parameters) {
                if (parameters.is(Noises.TEMPERATURE_NETHER)) {
                    return parameters.value().createForLegacyNetherBiome(newLegacyInstance(0L));
                }
                if (parameters.is(Noises.VEGETATION_NETHER)) {
                    return parameters.value().createForLegacyNetherBiome(newLegacyInstance(1L));
                }
                return getOrCreateNoise(parameters.unwrapKey().orElseThrow());
            }

            @Override
            public RandomSource createRandom(String seedId) {
                return useLegacyRandom && seedId.equals(BlendedNoise.NOISE_SEED) ? newLegacyInstance(0L) : random.fromHashOf(seedId);
            }

            @Override
            public RandomSource createEndIslandRandom() {
                return new LegacyRandomSource(seed);
            }
        });
    }

    public NoiseRouter router() {
        return router;
    }

    /** {@code samplersWithContext}: compiled samplers bound to {@code context}. */
    public DensitySamplerSet samplersWithContext(SamplerContext context) {
        return new DensitySamplerSet() {
            @Override
            public DensitySampler.Bound get(DensityFunction function) {
                return getSampler(function).bind(context);
            }

            @Override
            public float sampleValue(DensityFunction function, int blockX, int blockY, int blockZ) {
                return getSampler(function).sampleValue(context, blockX, blockY, blockZ);
            }
        };
    }

    public Climate.Sampler createClimateSampler(SamplerContext context) {
        return router.createClimateSampler(samplersWithContext(context));
    }

    public Noise getOrCreateNoise(String noise) {
        String key = WorldgenRegistries.normalize(noise);
        return noiseInstances.computeIfAbsent(key, k -> Noises.instantiate(noises, random, k));
    }

    public PositionalRandomFactory getOrCreateRandomFactory(String name) {
        String key = WorldgenRegistries.normalize(name);
        return positionalRandoms.computeIfAbsent(key, k -> random.fromHashOf(k).forkPositional());
    }

    /** The positional factory all named noises and randoms are derived from ({@code RandomState.random}). */
    public PositionalRandomFactory random() {
        return random;
    }

    public DensitySampler getSampler(DensityFunction function) {
        return densityFunctionCompiler.getSampler(function);
    }

    /** {@code sampleBlockValueUncached}: point evaluation with {@link SamplerContext#EMPTY_UNCACHED}. */
    public float sampleBlockValueUncached(DensityFunction function, int x, int y, int z) {
        return getSampler(function).sampleValue(SamplerContext.EMPTY_UNCACHED, x, y, z);
    }

    public DensityBufferPool acquireDensityBufferPool() {
        densityBufferPoolLock.lock();
        try {
            if (!densityBufferPools.isEmpty()) {
                return densityBufferPools.remove(densityBufferPools.size() - 1);
            }
            return new DensityBufferPool(MAX_BUFFER_AGE_TICKS);
        } finally {
            densityBufferPoolLock.unlock();
        }
    }

    public void releaseDensityBufferPool(DensityBufferPool pool) {
        densityBufferPoolLock.lock();
        try {
            if (densityBufferPools.size() < MAX_BUFFER_POOLS) {
                densityBufferPools.add(pool);
            }
        } finally {
            densityBufferPoolLock.unlock();
        }
    }

    public void garbageCollect() {
        densityBufferPoolLock.lock();
        try {
            densityBufferPools.removeIf(pool -> {
                pool.garbageCollect();
                return pool.isEmpty();
            });
        } finally {
            densityBufferPoolLock.unlock();
        }
    }

    /** Deprecated in vanilla. */
    public long seed() {
        return seed;
    }
}
