package com.example.worldgen.random;

/**
 * Port of {@code net.minecraft.world.level.levelgen.SingleThreadedRandomSource} (26.3): the same
 * 48-bit LCG as {@link LegacyRandomSource} with a plain field and a lazily created Gaussian
 * source (reset on {@link #setSeed} only once it exists). Its positional fork is a
 * {@link LegacyRandomSource.LegacyPositionalRandomFactory}.
 */
public class SingleThreadedRandomSource implements BitRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private long seed;
    private MarsagliaPolarGaussian gaussianSource;

    public SingleThreadedRandomSource(long seed) {
        setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new SingleThreadedRandomSource(nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MODULUS_MASK;
        if (gaussianSource != null) {
            gaussianSource.reset();
        }
    }

    @Override
    public int next(int bits) {
        long next = seed * MULTIPLIER + INCREMENT & MODULUS_MASK;
        seed = next;
        return (int) (next >> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        if (gaussianSource == null) {
            gaussianSource = new MarsagliaPolarGaussian(this);
        }
        return gaussianSource.nextGaussian();
    }
}
