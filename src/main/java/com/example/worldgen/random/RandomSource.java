package com.example.worldgen.random;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of {@code net.minecraft.util.RandomSource} (26.3): the random-number interface every
 * worldgen algorithm consumes. The default methods are transcribed from the 26.3 bytecode;
 * consumption order (how many raw draws each call makes) is part of the contract.
 */
public interface RandomSource {

    /** {@code RandomSource.GAUSSIAN_SPREAD_FACTOR}. */
    double GAUSSIAN_SPREAD_FACTOR = 2.297;

    /** {@code RandomSource.create()}: a legacy LCG seeded from {@link RandomSupport#generateUniqueSeed()}. */
    static RandomSource create() {
        return create(RandomSupport.generateUniqueSeed());
    }

    /** {@code RandomSource.createThreadSafe()}. */
    static RandomSource createThreadSafe() {
        return new ThreadSafeLegacyRandomSource(RandomSupport.generateUniqueSeed());
    }

    /** {@code RandomSource.create(long)}: a {@link LegacyRandomSource}. */
    static RandomSource create(long seed) {
        return new LegacyRandomSource(seed);
    }

    /**
     * {@code RandomSource.createThreadLocalInstance()}. Vanilla seeds it from Netty's thread-local
     * random; the seed is arbitrary either way, so the JDK thread-local random is used here.
     */
    static RandomSource createThreadLocalInstance() {
        return new SingleThreadedRandomSource(ThreadLocalRandom.current().nextLong());
    }

    /** {@code RandomSource.createThreadLocalInstance(long)}: a {@link SingleThreadedRandomSource}. */
    static RandomSource createThreadLocalInstance(long seed) {
        return new SingleThreadedRandomSource(seed);
    }

    RandomSource fork();

    PositionalRandomFactory forkPositional();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    /** {@code nextInt(max - min + 1) + min}. */
    default int nextIntBetweenInclusive(int min, int max) {
        return nextInt(max - min + 1) + min;
    }

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    double nextGaussian();

    /** {@code mean + deviation * (nextDouble() - nextDouble())}. */
    default double triangle(double mean, double deviation) {
        return mean + deviation * (nextDouble() - nextDouble());
    }

    /** {@code mean + deviation * (nextFloat() - nextFloat())}. */
    default float triangle(float mean, float deviation) {
        return mean + deviation * (nextFloat() - nextFloat());
    }

    /** Draws and discards {@code count} values of {@link #nextInt()}. */
    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            nextInt();
        }
    }

    /** {@code origin + nextInt(bound - origin)}; throws when {@code origin >= bound}. */
    default int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound - origin is non positive");
        }
        return origin + nextInt(bound - origin);
    }
}
