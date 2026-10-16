package com.example.worldgen.random;

import com.example.worldgen.Mth;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Port of {@code net.minecraft.world.level.levelgen.LegacyRandomSource} (26.3): the 48-bit LCG of
 * {@code java.util.Random} (multiplier {@code 25214903917}, increment {@code 11}, mask
 * {@code 2^48-1}, seed scrambled with {@code ^ 25214903917}). Like vanilla it stores the seed in an
 * {@link AtomicLong} and fails loudly when two threads use one instance at the same time
 * (vanilla throws {@code ThreadingDetector.makeThreadingException}; here an
 * {@link IllegalStateException}).
 */
public class LegacyRandomSource implements BitRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public LegacyRandomSource(long seed) {
        setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyPositionalRandomFactory(nextLong());
    }

    @Override
    public void setSeed(long seed) {
        if (!this.seed.compareAndSet(this.seed.get(), (seed ^ MULTIPLIER) & MODULUS_MASK)) {
            throw new IllegalStateException("LegacyRandomSource accessed from multiple threads");
        }
        gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        long current = seed.get();
        long next = current * MULTIPLIER + INCREMENT & MODULUS_MASK;
        if (!seed.compareAndSet(current, next)) {
            throw new IllegalStateException("LegacyRandomSource accessed from multiple threads");
        }
        return (int) (next >> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() {
        return gaussianSource.nextGaussian();
    }

    /** {@code LegacyRandomSource.LegacyPositionalRandomFactory}. */
    public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {

        private final long seed;

        public LegacyPositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        /** {@code new LegacyRandomSource(Mth.getSeed(x, y, z) ^ seed)}. */
        @Override
        public RandomSource at(int x, int y, int z) {
            long positional = Mth.getSeed(x, y, z);
            return new LegacyRandomSource(positional ^ seed);
        }

        /** {@code new LegacyRandomSource((long) name.hashCode() ^ seed)}. */
        @Override
        public RandomSource fromHashOf(String name) {
            int hash = name.hashCode();
            return new LegacyRandomSource((long) hash ^ seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new LegacyRandomSource(seed);
        }

        @Override
        public void parityConfigString(StringBuilder sb) {
            sb.append("LegacyPositionalRandomFactory{").append(seed).append("}");
        }
    }
}
