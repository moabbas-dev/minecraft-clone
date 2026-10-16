package com.example.worldgen.random;

/**
 * Port of {@code net.minecraft.world.level.levelgen.BitRandomSource} (26.3): derives every
 * {@link RandomSource} draw from {@link #next(int)} exactly like {@code java.util.Random}
 * (including the rejection loop of {@code nextInt(bound)} and the 26+27-bit {@code nextDouble}).
 */
public interface BitRandomSource extends RandomSource {

    float FLOAT_MULTIPLIER = 5.9604645E-8F;
    double DOUBLE_MULTIPLIER = 1.1102230246251565E-16;

    int next(int bits);

    @Override
    default int nextInt() {
        return next(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        if ((bound & bound - 1) == 0) {
            return (int) ((long) bound * (long) next(31) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    @Override
    default long nextLong() {
        int hi = next(32);
        int lo = next(32);
        long shifted = (long) hi << 32;
        return shifted + (long) lo;
    }

    @Override
    default boolean nextBoolean() {
        return next(1) != 0;
    }

    @Override
    default float nextFloat() {
        return (float) next(24) * FLOAT_MULTIPLIER;
    }

    @Override
    default double nextDouble() {
        int hi = next(26);
        int lo = next(27);
        long bits = ((long) hi << 27) + (long) lo;
        return (double) bits * DOUBLE_MULTIPLIER;
    }
}
