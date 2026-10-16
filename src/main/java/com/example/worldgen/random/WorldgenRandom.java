package com.example.worldgen.random;

import java.util.function.LongFunction;

/**
 * Port of {@code net.minecraft.world.level.levelgen.WorldgenRandom} (26.3): a
 * {@link LegacyRandomSource} facade that forwards to a wrapped source and offers the chunk
 * decoration / feature / structure seeding helpers. Bits come from the wrapped source's
 * {@code next(bits)} when it is a {@link LegacyRandomSource}, otherwise from the top bits of its
 * {@code nextLong()}. Salt constants ({@code 341873128712L}, {@code 132897987541L},
 * {@code 10000}) and the slime-chunk formula are read from the 26.3 bytecode.
 */
public class WorldgenRandom extends LegacyRandomSource {

    private final RandomSource randomSource;
    private int count;

    public WorldgenRandom(RandomSource randomSource) {
        super(0L);
        this.randomSource = randomSource;
    }

    public int getCount() {
        return count;
    }

    @Override
    public RandomSource fork() {
        return randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return randomSource.forkPositional();
    }

    @Override
    public int next(int bits) {
        count++;
        if (randomSource instanceof LegacyRandomSource legacy) {
            return legacy.next(bits);
        }
        return (int) (randomSource.nextLong() >>> 64 - bits);
    }

    /** Vanilla's {@code setSeed} is ignored while the super constructor runs (wrapped source still null). */
    @Override
    public synchronized void setSeed(long seed) {
        if (randomSource == null) {
            return;
        }
        randomSource.setSeed(seed);
    }

    public long setDecorationSeed(long levelSeed, int minBlockX, int minBlockZ) {
        setSeed(levelSeed);
        long a = nextLong() | 1L;
        long b = nextLong() | 1L;
        long seed = (long) minBlockX * a + (long) minBlockZ * b ^ levelSeed;
        setSeed(seed);
        return seed;
    }

    public void setFeatureSeed(long decorationSeed, int index, int step) {
        long seed = decorationSeed + (long) index + (long) (10000 * step);
        setSeed(seed);
    }

    public void setLargeFeatureSeed(long levelSeed, int chunkX, int chunkZ) {
        setSeed(levelSeed);
        long a = nextLong();
        long b = nextLong();
        long seed = (long) chunkX * a ^ (long) chunkZ * b ^ levelSeed;
        setSeed(seed);
    }

    public void setLargeFeatureWithSalt(long levelSeed, int regionX, int regionZ, int salt) {
        long seed = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + levelSeed + (long) salt;
        setSeed(seed);
    }

    public static RandomSource seedSlimeChunk(int chunkX, int chunkZ, long levelSeed, long salt) {
        return RandomSource.createThreadLocalInstance(levelSeed
                + (long) (chunkX * chunkX * 4987142)
                + (long) (chunkX * 5947611)
                + (long) (chunkZ * chunkZ) * 4392871L
                + (long) (chunkZ * 389711)
                ^ salt);
    }

    /** {@code WorldgenRandom.Algorithm}: which random source a level uses. */
    public enum Algorithm {
        LEGACY(LegacyRandomSource::new),
        XOROSHIRO(XoroshiroRandomSource::new);

        private final LongFunction<RandomSource> constructor;

        Algorithm(LongFunction<RandomSource> constructor) {
            this.constructor = constructor;
        }

        public RandomSource newInstance(long seed) {
            return constructor.apply(seed);
        }
    }
}
