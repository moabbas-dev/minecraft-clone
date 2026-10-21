package com.example.worldgen.density;

import com.example.worldgen.CubicSpline;
import com.example.worldgen.Interval;
import com.example.worldgen.density.generator.ConstantFunction;
import com.example.worldgen.density.generator.DistanceToPointFunction;
import com.example.worldgen.density.generator.EndIslandFunction;
import com.example.worldgen.density.generator.GradientFunction;
import com.example.worldgen.density.generator.NoiseFunction;
import com.example.worldgen.density.generator.ShiftNoiseFunction;
import com.example.worldgen.density.generator.SimpleDensityFunction;
import com.example.worldgen.density.op.BinaryFunction;
import com.example.worldgen.density.op.BlendDensityFunction;
import com.example.worldgen.density.op.CacheFunction;
import com.example.worldgen.density.op.ClampFunction;
import com.example.worldgen.density.op.FindTopSurfaceFunction;
import com.example.worldgen.density.op.InterpolatedFunction;
import com.example.worldgen.density.op.IntervalSelectFunction;
import com.example.worldgen.density.op.LerpFunction;
import com.example.worldgen.density.op.PowFunction;
import com.example.worldgen.density.op.RangeChoiceFunction;
import com.example.worldgen.density.op.RoundFunction;
import com.example.worldgen.density.op.SliceFunction;
import com.example.worldgen.density.op.SplineFunction;
import com.example.worldgen.density.op.UnaryFunction;
import com.example.worldgen.noise.NormalNoise;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityFunctions} (26.3): the factory methods
 * and the registry reference wrapper {@link HolderHolder}. The registry bootstrap (the list of JSON type ids) is
 * {@link DensityFunctionParser#TYPES}.
 */
public final class DensityFunctions {

    /** {@code MAX_REASONABLE_NOISE_VALUE}: the bound of {@code NOISE_VALUE_CODEC = Codec.floatRange(-1e6, 1e6)}. */
    public static final float MAX_REASONABLE_NOISE_VALUE = 1000000.0F;
    private static final ConstantFunction ZERO = new ConstantFunction(0.0F);

    private DensityFunctions() {
    }

    public static DensityFunction zero() {
        return ZERO;
    }

    public static DensityFunction constant(float value) {
        return new ConstantFunction(value);
    }

    public static DensityFunction abs(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.ABS, input);
    }

    public static DensityFunction square(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.SQUARE, input);
    }

    public static DensityFunction cube(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.CUBE, input);
    }

    public static DensityFunction sqrt(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.SQRT, input);
    }

    public static DensityFunction halfNegative(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.HALF_NEGATIVE, input);
    }

    public static DensityFunction quarterNegative(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.QUARTER_NEGATIVE, input);
    }

    public static DensityFunction reciprocal(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.RECIPROCAL, input);
    }

    public static DensityFunction negate(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.NEGATE, input);
    }

    public static DensityFunction squeeze(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.SQUEEZE, input);
    }

    public static DensityFunction log(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.LOG, input);
    }

    public static DensityFunction sign(DensityFunction input) {
        return new UnaryFunction(UnaryFunction.Type.SIGN, input);
    }

    public static DensityFunction interpolated(DensityFunction function, int cellSizeXz, int cellSizeY) {
        return new InterpolatedFunction(function, cellSizeXz, cellSizeY);
    }

    public static DensityFunction cache(DensityFunction function) {
        return new CacheFunction(function);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise> noiseData, double xzScale, double yScale,
                                              float minTarget, float maxTarget) {
        DensityFunction noise = new NoiseFunction(noiseData, xzScale, yScale, zero(), zero(), zero());
        return remap(noise, -1.0F, 1.0F, minTarget, maxTarget);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise> noiseData, double yScale, float minTarget, float maxTarget) {
        return mappedNoise(noiseData, 1.0, yScale, minTarget, maxTarget);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise> noiseData, float minTarget, float maxTarget) {
        return mappedNoise(noiseData, 1.0, 1.0, minTarget, maxTarget);
    }

    public static DensityFunction shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale,
                                                 Holder<NormalNoise> noiseData) {
        return new NoiseFunction(noiseData, xzScale, 0.0, shiftX, zero(), shiftZ);
    }

    public static DensityFunction noise(Holder<NormalNoise> noiseData) {
        return noise(noiseData, 1.0, 1.0);
    }

    public static DensityFunction noise(Holder<NormalNoise> noiseData, double xzScale, double yScale) {
        return new NoiseFunction(noiseData, xzScale, yScale, zero(), zero(), zero());
    }

    public static DensityFunction noise(Holder<NormalNoise> noiseData, double yScale) {
        return noise(noiseData, 1.0, yScale);
    }

    public static DensityFunction rangeChoice(DensityFunction input, float minInclusive, float maxExclusive,
                                              DensityFunction whenInRange, DensityFunction whenOutOfRange) {
        return new RangeChoiceFunction(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction intervalSelect(DensityFunction input, List<Float> thresholds, List<DensityFunction> functions) {
        return new IntervalSelectFunction(input, thresholds, functions);
    }

    public static DensityFunction shiftA(Holder<NormalNoise> noiseData) {
        return new ShiftNoiseFunction.ShiftA(noiseData);
    }

    public static DensityFunction shiftB(Holder<NormalNoise> noiseData) {
        return new ShiftNoiseFunction.ShiftB(noiseData);
    }

    public static DensityFunction shift(Holder<NormalNoise> noiseData) {
        return new ShiftNoiseFunction.Shift(noiseData);
    }

    public static DensityFunction blendDensity(DensityFunction input) {
        return new BlendDensityFunction(input);
    }

    public static DensityFunction endOuterIslands() {
        return new EndIslandFunction();
    }

    public static DensityFunction distanceToPoint(int pointX, int pointY, int pointZ, DistanceMetric metric) {
        return new DistanceToPointFunction(pointX, pointY, pointZ, metric);
    }

    public static DensityFunction add(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.ADD, left, right);
    }

    public static DensityFunction sub(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.SUB, left, right);
    }

    public static DensityFunction mul(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.MUL, left, right);
    }

    public static DensityFunction div(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.DIV, left, right);
    }

    public static DensityFunction pow(DensityFunction base, DensityFunction exponent) {
        return new PowFunction(base, exponent);
    }

    public static DensityFunction clamp(DensityFunction input, float min, float max) {
        return new ClampFunction(input, min, max);
    }

    public static DensityFunction min(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.MIN, left, right);
    }

    public static DensityFunction max(DensityFunction left, DensityFunction right) {
        return new BinaryFunction(BinaryFunction.Type.MAX, left, right);
    }

    public static DensityFunction spline(CubicSpline<SplineFunction.Coordinate> spline) {
        return new SplineFunction(spline);
    }

    public static DensityFunction sliceY(DensityFunction input, int y) {
        return slice(Axis.Y, y, input);
    }

    public static DensityFunction slice(Axis axis, int coordinate, DensityFunction input) {
        return new SliceFunction(axis, coordinate, input);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, float fromValue, float toValue) {
        return gradient(Axis.Y, TilingMode.CLAMP_TO_EDGE, fromY, toY, fromValue, toValue);
    }

    public static DensityFunction gradient(Axis axis, TilingMode tiling, int fromCoordinate, int toCoordinate,
                                           float fromValue, float toValue) {
        return new GradientFunction(axis, tiling, fromCoordinate, toCoordinate, fromValue, toValue);
    }

    public static DensityFunction blendAlpha() {
        return SimpleDensityFunction.BLEND_ALPHA;
    }

    public static DensityFunction blendOffset() {
        return SimpleDensityFunction.BLEND_OFFSET;
    }

    public static DensityFunction beardifier() {
        return SimpleDensityFunction.BEARDIFIER;
    }

    public static DensityFunction lerp(DensityFunction alpha, DensityFunction first, DensityFunction second) {
        return new LerpFunction(alpha, first, second);
    }

    public static DensityFunction lerp(DensityFunction factor, float first, DensityFunction second) {
        return lerp(factor, constant(first), second);
    }

    public static DensityFunction round(RoundFunction.Type type, DensityFunction input, DensityFunction multiple) {
        return new RoundFunction(type, input, multiple);
    }

    public static DensityFunction round(RoundFunction.Type type, DensityFunction input) {
        return round(type, input, constant(1.0F));
    }

    public static DensityFunction findTopSurface(DensityFunction density, DensityFunction upperBound, int lowerBound, int stepSize) {
        return new FindTopSurfaceFunction(density, upperBound, lowerBound, stepSize);
    }

    /** {@code DensityFunctions.remap}: {@code input * factor (+ offset when non-zero)}, all in float. */
    public static DensityFunction remap(DensityFunction input, float fromMin, float fromMax, float toMin, float toMax) {
        float factor = (toMax - toMin) / (fromMax - fromMin);
        float offset = toMin - fromMin * factor;
        return offset == 0.0F ? input.mul(factor) : input.mul(factor).add(offset);
    }

    public static DensityFunction clampedMap(DensityFunction input, float fromMin, float fromMax, float toMin, float toMax) {
        return remap(input.clamp(fromMin, fromMax), fromMin, fromMax, toMin, toMax);
    }

    /**
     * {@code DensityFunctions.HolderHolder}: a reference to a registered density function (a JSON string id). It
     * forwards range and axes to the referenced function; the compiler inlines it ({@link DfRewriteRule#INLINE_REFERENCE}).
     */
    public record HolderHolder(Holder<DensityFunction> function) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            return function.value().compileSampler(context);
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            DensityFunction newFunction = rule.rewrite(function.value());
            return newFunction == function.value() ? this : newFunction;
        }

        @Override
        public Interval range() {
            return function.value().range();
        }

        @Override
        public int domainAxes() {
            return function.value().domainAxes();
        }
    }
}
