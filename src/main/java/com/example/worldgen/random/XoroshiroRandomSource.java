package com.example.worldgen.random;

import com.example.worldgen.Mth;

/**
 * Port of {@code net.minecraft.world.level.levelgen.XoroshiroRandomSource} (26.3), the random
 * source of modern world generation. A {@code long} seed goes through
 * {@link RandomSupport#upgradeSeedTo128bit}; floats take the top 24 bits and doubles the top 53
 * bits of one {@code nextLong}; {@code nextInt(bound)} is Lemire's multiply-shift with rejection.
 */
public class XoroshiroRandomSource implements RandomSource {

    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;

    private Xoroshiro128PlusPlus randomNumberGenerator;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    @Override
    public RandomSource fork() {
        return new XoroshiroRandomSource(randomNumberGenerator.nextLong(), randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(randomNumberGenerator.nextLong(), randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
        gaussianSource.reset();
    }

    @Override
    public int nextInt() {
        return (int) randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long r = Integer.toUnsignedLong(nextInt());
        long m = r * (long) bound;
        long low = m & 4294967295L;
        if (low < (long) bound) {
            int threshold = Integer.remainderUnsigned(~bound + 1, bound);
            while (low < (long) threshold) {
                r = Integer.toUnsignedLong(nextInt());
                m = r * (long) bound;
                low = m & 4294967295L;
            }
        }
        long result = m >> 32;
        return (int) result;
    }

    @Override
    public long nextLong() {
        return randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float) nextBits(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return (double) nextBits(53) * DOUBLE_UNIT;
    }

    @Override
    public double nextGaussian() {
        return gaussianSource.nextGaussian();
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            randomNumberGenerator.nextLong();
        }
    }

    private long nextBits(int bits) {
        return randomNumberGenerator.nextLong() >>> 64 - bits;
    }

    /** {@code XoroshiroRandomSource.XoroshiroPositionalRandomFactory}. */
    public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {

        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        /** {@code new XoroshiroRandomSource(Mth.getSeed(x, y, z) ^ seedLo, seedHi)}. */
        @Override
        public RandomSource at(int x, int y, int z) {
            long positional = Mth.getSeed(x, y, z);
            return new XoroshiroRandomSource(positional ^ seedLo, seedHi);
        }

        /** MD5 seed of the name xor'ed with this factory's state. */
        @Override
        public RandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit hash = RandomSupport.seedFromHashOf(name);
            return new XoroshiroRandomSource(hash.xor(seedLo, seedHi));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed ^ seedLo, seed ^ seedHi);
        }

        @Override
        public void parityConfigString(StringBuilder sb) {
            sb.append("seedLo: ").append(seedLo).append(", seedHi: ").append(seedHi);
        }
    }
}
