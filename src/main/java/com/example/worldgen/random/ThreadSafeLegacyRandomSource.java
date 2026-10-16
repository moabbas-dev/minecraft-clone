package com.example.worldgen.random;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Port of {@code net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource} (26.3): the
 * 48-bit LCG advanced with a compare-and-set retry loop. Unlike {@link LegacyRandomSource},
 * vanilla's {@code setSeed} here does not reset the cached Gaussian value; that is kept.
 */
public class ThreadSafeLegacyRandomSource implements BitRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public ThreadSafeLegacyRandomSource(long seed) {
        setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new ThreadSafeLegacyRandomSource(nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed.set((seed ^ MULTIPLIER) & MODULUS_MASK);
    }

    @Override
    public int next(int bits) {
        long current;
        long next;
        do {
            current = seed.get();
            next = current * MULTIPLIER + INCREMENT & MODULUS_MASK;
        } while (!seed.compareAndSet(current, next));
        return (int) (next >>> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        return gaussianSource.nextGaussian();
    }
}
