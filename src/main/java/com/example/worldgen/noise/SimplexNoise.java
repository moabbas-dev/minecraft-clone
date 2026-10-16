package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.SimplexNoise} (26.3), used by the End
 * islands and legacy biome code. Evaluated in double and narrowed to float at the end:
 * 2D uses skew {@code F2 = 0.5 * (sqrt(3) - 1)}, unskew {@code G2 = (3 - sqrt(3)) / 6}, corner
 * radius 0.5 and scale 70; 3D uses 1/3 and 1/6, corner radius 0.6 and scale 32. Gradient indices
 * are {@code permute(...) % 12}. Note that the 2D form offsets its second coordinate with
 * {@code offsetY} (not {@code offsetZ}), as vanilla does. Range: {@code [-2, 2]}.
 */
public class SimplexNoise extends GradientNoise {

    public static final Interval RANGE = Interval.ofSymmetric(2.0F);
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    public SimplexNoise(RandomSource random) {
        super(random);
    }

    /** {@code zeroOffsets == true} still draws the three offset doubles but scales them by 0. */
    public SimplexNoise(RandomSource random, boolean zeroOffsets) {
        super(random, zeroOffsets ? 0.0 : 256.0);
    }

    @Override
    public Interval range() {
        return RANGE;
    }

    private double getCornerNoise3D(int index, double x, double y, double z, double base) {
        double t = base - x * x - y * y - z * z;
        double result;
        if (t < 0.0) {
            result = 0.0;
        } else {
            t *= t;
            result = t * t * GRADIENT[index].dot(x, y, z);
        }
        return result;
    }

    @Override
    public float get(double x, double y) {
        double px = x + offsetX;
        double py = y + offsetY;
        double s = (px + py) * F2;
        int i = Mth.floor(px + s);
        int j = Mth.floor(py + s);
        double t = (double) (i + j) * G2;
        double x0Origin = (double) i - t;
        double y0Origin = (double) j - t;
        double x0 = px - x0Origin;
        double y0 = py - y0Origin;
        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - (double) i1 + G2;
        double y1 = y0 - (double) j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permute(ii + permute(jj)) % 12;
        int gi1 = permute(ii + i1 + permute(jj + j1)) % 12;
        int gi2 = permute(ii + 1 + permute(jj + 1)) % 12;
        double n0 = getCornerNoise3D(gi0, x0, y0, 0.0, 0.5);
        double n1 = getCornerNoise3D(gi1, x1, y1, 0.0, 0.5);
        double n2 = getCornerNoise3D(gi2, x2, y2, 0.0, 0.5);
        return (float) (70.0 * (n0 + n1 + n2));
    }

    @Override
    public float get(double x, double y, double z) {
        double px = x + offsetX;
        double py = y + offsetY;
        double pz = z + offsetZ;
        double s = (px + py + pz) * 0.3333333333333333;
        int i = Mth.floor(px + s);
        int j = Mth.floor(py + s);
        int k = Mth.floor(pz + s);
        double t = (double) (i + j + k) * 0.16666666666666666;
        double x0Origin = (double) i - t;
        double y0Origin = (double) j - t;
        double z0Origin = (double) k - t;
        double x0 = px - x0Origin;
        double y0 = py - y0Origin;
        double z0 = pz - z0Origin;
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
        } else if (x0 < z0) {
            i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
        } else {
            i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
        }
        double x1 = x0 - (double) i1 + 0.16666666666666666;
        double y1 = y0 - (double) j1 + 0.16666666666666666;
        double z1 = z0 - (double) k1 + 0.16666666666666666;
        double x2 = x0 - (double) i2 + 0.3333333333333333;
        double y2 = y0 - (double) j2 + 0.3333333333333333;
        double z2 = z0 - (double) k2 + 0.3333333333333333;
        double x3 = x0 - 1.0 + 0.5;
        double y3 = y0 - 1.0 + 0.5;
        double z3 = z0 - 1.0 + 0.5;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        int gi0 = permute(ii + permute(jj + permute(kk))) % 12;
        int gi1 = permute(ii + i1 + permute(jj + j1 + permute(kk + k1))) % 12;
        int gi2 = permute(ii + i2 + permute(jj + j2 + permute(kk + k2))) % 12;
        int gi3 = permute(ii + 1 + permute(jj + 1 + permute(kk + 1))) % 12;
        double n0 = getCornerNoise3D(gi0, x0, y0, z0, 0.6);
        double n1 = getCornerNoise3D(gi1, x1, y1, z1, 0.6);
        double n2 = getCornerNoise3D(gi2, x2, y2, z2, 0.6);
        double n3 = getCornerNoise3D(gi3, x3, y3, z3, 0.6);
        return (float) (32.0 * (n0 + n1 + n2 + n3));
    }
}
