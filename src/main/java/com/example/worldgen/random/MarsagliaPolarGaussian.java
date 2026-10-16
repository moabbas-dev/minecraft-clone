package com.example.worldgen.random;

import com.example.worldgen.Mth;

/**
 * Port of {@code net.minecraft.world.level.levelgen.MarsagliaPolarGaussian} (26.3): the polar
 * method with one cached spare value, drawing from {@link RandomSource#nextDouble()}.
 */
public class MarsagliaPolarGaussian {

    public final RandomSource randomSource;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public MarsagliaPolarGaussian(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public void reset() {
        haveNextNextGaussian = false;
    }

    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double x;
        double y;
        double s;
        do {
            x = 2.0 * randomSource.nextDouble() - 1.0;
            y = 2.0 * randomSource.nextDouble() - 1.0;
            s = Mth.square(x) + Mth.square(y);
        } while (s >= 1.0 || s == 0.0);
        double scale = Math.sqrt(-2.0 * Math.log(s) / s);
        nextNextGaussian = y * scale;
        haveNextNextGaussian = true;
        return x * scale;
    }
}
