package com.example.world;

import java.util.Random;

/**
 * Standard 2D simplex noise (Gustavson/Perlin construction). The permutation table is
 * generated from a seeded Fisher-Yates shuffle rather than a hand-copied literal table,
 * so it is guaranteed to be a valid permutation of 0..255 (no transcription risk).
 */
public class SimplexNoise {

    private static final int[][] GRAD3 = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    public SimplexNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    private static double dot(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    public double noise(double xin, double yin) {
        double n0, n1, n2;
        final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;

        int i1, j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        n0 = t0 < 0 ? 0.0 : (t0 *= t0) * t0 * dot(GRAD3[gi0], x0, y0);

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        n1 = t1 < 0 ? 0.0 : (t1 *= t1) * t1 * dot(GRAD3[gi1], x1, y1);

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        n2 = t2 < 0 ? 0.0 : (t2 *= t2) * t2 * dot(GRAD3[gi2], x2, y2);

        return 70.0 * (n0 + n1 + n2);
    }

    /** Fractal Brownian motion: layered octaves for natural-looking terrain. */
    public double fbm(double x, double y, int octaves, double persistence, double lacunarity, double baseFrequency) {
        double total = 0;
        double amplitude = 1;
        double frequency = baseFrequency;
        double maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / maxValue;
    }
}
