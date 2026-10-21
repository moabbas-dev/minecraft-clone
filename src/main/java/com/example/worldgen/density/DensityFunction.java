package com.example.worldgen.density;

import com.example.worldgen.Interval;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityFunction} (26.3). In 26.3 a density
 * function is a pure description: it is compiled into a {@link DensitySampler} against a {@link CompileContext}
 * (which supplies seeded noises and randoms), it reports its value {@link #range()} and the set of block axes it
 * depends on ({@link #domainAxes()}, a bit set of {@link #AXIS_X}, {@link #AXIS_Y}, {@link #AXIS_Z}), and the
 * optimiser rewrites trees through {@link #rewriteChildren}. Values are {@code float}.
 *
 * <p>Vanilla's {@code codec()} (the JSON form) lives in {@link DensityFunctionParser} here. The default helpers
 * build the same nodes as vanilla's ({@code pow(float)} special-cases 0.5, 2 and 3).
 */
public interface DensityFunction {

    int AXIS_X = 1;
    int AXIS_Y = 2;
    int AXIS_Z = 4;
    int NO_AXES = 0;
    int ALL_AXES = 7;

    /** {@code DensityFunction.axesFrom(Direction.Axis)}. */
    static int axesFrom(Axis axis) {
        return switch (axis) {
            case X -> AXIS_X;
            case Y -> AXIS_Y;
            case Z -> AXIS_Z;
        };
    }

    DensitySampler compileSampler(CompileContext context);

    DensityFunction rewriteChildren(DfRewriteRule rule);

    Interval range();

    int domainAxes();

    default DensityFunction clamp(float min, float max) {
        return DensityFunctions.clamp(this, min, max);
    }

    default DensityFunction abs() {
        return DensityFunctions.abs(this);
    }

    default DensityFunction square() {
        return DensityFunctions.square(this);
    }

    default DensityFunction cube() {
        return DensityFunctions.cube(this);
    }

    default DensityFunction sqrt() {
        return DensityFunctions.sqrt(this);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.halfNegative(this);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.quarterNegative(this);
    }

    default DensityFunction reciprocal() {
        return DensityFunctions.reciprocal(this);
    }

    default DensityFunction negate() {
        return DensityFunctions.negate(this);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.squeeze(this);
    }

    default DensityFunction log() {
        return DensityFunctions.log(this);
    }

    default DensityFunction sign() {
        return DensityFunctions.sign(this);
    }

    default DensityFunction add(DensityFunction right) {
        return DensityFunctions.add(this, right);
    }

    default DensityFunction add(float right) {
        return DensityFunctions.add(this, DensityFunctions.constant(right));
    }

    default DensityFunction sub(DensityFunction right) {
        return DensityFunctions.sub(this, right);
    }

    default DensityFunction sub(float right) {
        return DensityFunctions.sub(this, DensityFunctions.constant(right));
    }

    default DensityFunction mul(DensityFunction right) {
        return DensityFunctions.mul(this, right);
    }

    default DensityFunction mul(float right) {
        return DensityFunctions.mul(this, DensityFunctions.constant(right));
    }

    default DensityFunction div(DensityFunction right) {
        return DensityFunctions.div(this, right);
    }

    default DensityFunction div(float right) {
        return DensityFunctions.div(this, DensityFunctions.constant(right));
    }

    default DensityFunction pow(DensityFunction exponent) {
        return DensityFunctions.pow(this, exponent);
    }

    default DensityFunction pow(float exponent) {
        if (exponent == 0.5F) {
            return sqrt();
        }
        if (exponent == 2.0F) {
            return square();
        }
        return exponent == 3.0F ? cube() : DensityFunctions.pow(this, DensityFunctions.constant(exponent));
    }

    /**
     * {@code DensityFunction.CompileContext}: what compilation needs from the world ({@code RandomState} implements
     * it). {@code createRandom} takes the {@code Identifier} string, e.g. {@code "minecraft:terrain"}.
     */
    interface CompileContext {

        Noise createNoiseSampler(Holder<NormalNoise> parameters);

        RandomSource createRandom(String seed);

        /** Deprecated in vanilla; used only by {@code end_outer_islands}. */
        RandomSource createEndIslandRandom();
    }
}
