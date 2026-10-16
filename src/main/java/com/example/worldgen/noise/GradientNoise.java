package com.example.worldgen.noise;

import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.GradientNoise} (26.3): the shared base of
 * {@link PerlinNoise} and {@link SimplexNoise}. Construction draws three {@code nextDouble()}
 * offsets (scaled by 256 unless told otherwise) and then shuffles a 256-entry byte permutation
 * with {@code nextInt(256 - i)}, exactly in that order. The 16 gradient vectors are copied from the
 * 26.3 static initialiser.
 */
public abstract class GradientNoise implements Noise {

    protected static final Gradient[] GRADIENT = {
            new Gradient(1, 1, 0), new Gradient(-1, 1, 0), new Gradient(1, -1, 0), new Gradient(-1, -1, 0),
            new Gradient(1, 0, 1), new Gradient(-1, 0, 1), new Gradient(1, 0, -1), new Gradient(-1, 0, -1),
            new Gradient(0, 1, 1), new Gradient(0, -1, 1), new Gradient(0, 1, -1), new Gradient(0, -1, -1),
            new Gradient(1, 1, 0), new Gradient(0, -1, 1), new Gradient(-1, 1, 0), new Gradient(0, -1, -1)
    };

    private static final int ROUND_OFF = 33554432;
    private static final double HALF_ROUND_OFF = Math.nextDown(1.6777216E7);

    protected final byte[] perms = new byte[256];
    protected final double offsetX;
    protected final double offsetY;
    protected final double offsetZ;

    protected GradientNoise(RandomSource random) {
        this(random, 256.0);
    }

    protected GradientNoise(RandomSource random, double offsetScale) {
        offsetX = random.nextDouble() * offsetScale;
        offsetY = random.nextDouble() * offsetScale;
        offsetZ = random.nextDouble() * offsetScale;
        for (int i = 0; i < 256; i++) {
            perms[i] = (byte) i;
        }
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            byte b = perms[i];
            perms[i] = perms[j + i];
            perms[j + i] = b;
        }
    }

    protected int permute(int i) {
        return perms[i & 255] & 255;
    }

    protected Gradient permuteToGrad(int i) {
        return GRADIENT[permute(i) & 15];
    }

    protected static float gradDot(int hash, float x, float y, float z) {
        return GRADIENT[hash & 15].dot(x, y, z);
    }

    /**
     * Keeps coordinates inside ±2^24 by subtracting a multiple of 2^25 (vanilla's anti-far-lands
     * wrap). Vanilla skips it when {@code SharedConstants.DEBUG_ENABLE_FARLANDS} is set, a debug flag
     * that is always false in release builds ({@code debugFlag} returns false unless
     * {@code DEBUG_ENABLED}), so the wrap is unconditional here.
     */
    protected static double wrap(double v) {
        if (v >= -HALF_ROUND_OFF && v < HALF_ROUND_OFF) {
            return v;
        }
        return v - Math.floor(v / (double) ROUND_OFF + 0.5) * (double) ROUND_OFF;
    }

    /** {@code GradientNoise.Gradient}: an integer gradient vector with float and double dot products. */
    public record Gradient(int x, int y, int z) {

        public double dot(double dx, double dy, double dz) {
            return (double) x * dx + (double) y * dy + (double) z * dz;
        }

        public float dot(float dx, float dy, float dz) {
            return (float) x * dx + (float) y * dy + (float) z * dz;
        }

        public float dotXz(float dx, float dz) {
            return (float) x * dx + (float) z * dz;
        }
    }
}
