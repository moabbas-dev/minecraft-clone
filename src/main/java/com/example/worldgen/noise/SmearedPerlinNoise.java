package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise} (26.3): a Perlin
 * octave whose gradient y fraction is quantised ("smeared") to steps of {@code fudgeYScale}
 * (the legacy {@code yScale/yMax} behaviour of old {@code ImprovedNoise}, used by
 * {@link BlendedNoise}). The {@code y} argument of {@link #get(double, double, double)} doubles
 * as the "yMax" clamp exactly as in the 26.3 bytecode; the interpolation weight still uses the
 * unsmeared y fraction. Epsilon: {@code SHIFT_UP_EPSILON = 1.0E-7f}, widened to double.
 */
public class SmearedPerlinNoise extends PerlinNoise {

    private static final float SHIFT_UP_EPSILON = 1.0E-7F;

    private final double fudgeYScale;

    public SmearedPerlinNoise(RandomSource random, double fudgeYScale) {
        super(random);
        this.fudgeYScale = fudgeYScale;
    }

    /** {@code ofSymmetric((float) (|fudgeYScale| + 2))}. */
    public static Interval range(double fudgeYScale) {
        return Interval.ofSymmetric((float) (Math.abs(fudgeYScale) + 2.0));
    }

    @Override
    public Interval range() {
        return range(fudgeYScale);
    }

    @Override
    public float get(double x, double y, double z) {
        double px = wrap(x) + offsetX;
        double py = wrap(y) + offsetY;
        double pz = wrap(z) + offsetZ;
        int ix = Mth.floor(px);
        int iy = Mth.floor(py);
        int iz = Mth.floor(pz);
        float fx = (float) (px - (double) ix);
        double fy = py - (double) iy;
        float fz = (float) (pz - (double) iz);
        float smearedFy = (float) (fy - computeFudgeY(y, fy));
        return sampleAndLerp(ix, iy, iz, fx, smearedFy, fz, (float) fy);
    }

    private double computeFudgeY(double yMax, double fy) {
        double d = yMax >= 0.0 && yMax < fy ? yMax : fy;
        return (double) Mth.floor(d / fudgeYScale + (double) SHIFT_UP_EPSILON) * fudgeYScale;
    }
}
