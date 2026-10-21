package com.example.worldgen;

import java.util.List;

/**
 * Port of {@code net.minecraft.util.Interval} (26.3): a closed float range describing the value range of
 * a noise or density function. Every operation mirrors the 26.3 bytecode, including the shared singleton
 * instances {@code of} returns, the "zero times anything is zero" rule of {@code mulBound}, the NaN handling
 * ({@link #NaI} is "not an interval") and the infinite-bound special cases of {@code pow} and {@code lerp}.
 * Density-function compilation depends on these ranges ({@code min}/{@code max} short-circuits, picking one
 * side of non-overlapping inputs), so they have to be exact.
 */
public final class Interval {

    /** "Not an interval": both bounds NaN. */
    public static final Interval NaI = new Interval(Float.NaN, Float.NaN);
    public static final Interval INFINITE = new Interval(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    private static final Interval NEGATIVE_ONE_TO_ONE = new Interval(-1.0F, 1.0F);
    private static final Interval ZERO_TO_ONE = new Interval(0.0F, 1.0F);

    private final float min;
    private final float max;

    private Interval(float min, float max) {
        this.min = min;
        this.max = max;
    }

    /** A float-to-float function (vanilla uses fastutil's {@code FloatUnaryOperator}). */
    @FunctionalInterface
    public interface FloatUnaryOperator {
        float apply(float value);
    }

    /** {@code Interval.of}: rejects {@code max < min} and NaN bounds; reuses the shared constants. */
    public static Interval of(float min, float max) {
        if (max < min) {
            throw new IllegalArgumentException("max (" + max + ") < min (" + min + ")");
        }
        if (Float.isNaN(min) || Float.isNaN(max)) {
            throw new IllegalArgumentException("Bounds cannot include NaN [" + min + "; " + max + "]: use Interval.NaI explicitly");
        }
        if (min == Float.NEGATIVE_INFINITY && max == Float.POSITIVE_INFINITY) {
            return INFINITE;
        }
        if (max == 1.0F) {
            if (min == 0.0F) {
                return ZERO_TO_ONE;
            }
            if (min == -1.0F) {
                return NEGATIVE_ONE_TO_ONE;
            }
        }
        return new Interval(min, max);
    }

    /** {@code Interval.ofSymmetric(v)} = {@code of(-v, v)}. */
    public static Interval ofSymmetric(float v) {
        return of(-v, v);
    }

    /** {@code Interval.ofExact(v)} = {@code of(v, v)}. */
    public static Interval ofExact(float v) {
        return of(v, v);
    }

    /** {@code Interval.encapsulating(List)}: the hull of the non-NaI intervals; NaI if all are NaI. */
    public static Interval encapsulating(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            throw new IllegalArgumentException("At least one interval required");
        }
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (Interval interval : intervals) {
            if (!interval.isNaI()) {
                lo = Math.min(interval.min, lo);
                hi = Math.max(interval.max, hi);
            }
        }
        if (hi < lo) {
            return NaI;
        }
        return of(lo, hi);
    }

    /** {@code Interval.encapsulating(Interval...)}. */
    public static Interval encapsulating(Interval... intervals) {
        return encapsulating(List.of(intervals));
    }

    /** {@code Interval.encapsulating(float, float)}: NaN arguments are skipped. */
    public static Interval encapsulating(float first, float second) {
        if (Float.isNaN(first) && Float.isNaN(second)) {
            return NaI;
        }
        if (Float.isNaN(first)) {
            return ofExact(second);
        }
        if (Float.isNaN(second)) {
            return ofExact(first);
        }
        return of(Math.min(first, second), Math.max(first, second));
    }

    private static Interval encapsulating(Interval first, float second) {
        if (Float.isNaN(second)) {
            return first;
        }
        if (first.isNaI()) {
            return ofExact(second);
        }
        return of(Math.min(first.min(), second), Math.max(first.max(), second));
    }

    /** {@code Interval.add}: bound-wise sum, NaN result gives {@link #NaI}. */
    public static Interval add(Interval a, Interval b) {
        float lo = a.min + b.min;
        float hi = a.max + b.max;
        if (Float.isNaN(lo) || Float.isNaN(hi)) {
            return NaI;
        }
        return of(lo, hi);
    }

    /** {@code Interval.sub}: {@code [a.min - b.max, a.max - b.min]}. */
    public static Interval sub(Interval a, Interval b) {
        float lo = a.min - b.max;
        float hi = a.max - b.min;
        if (Float.isNaN(lo) || Float.isNaN(hi)) {
            return NaI;
        }
        return of(lo, hi);
    }

    /** {@code Interval.mul}: min/max over the four bound products. */
    public static Interval mul(Interval a, Interval b) {
        if (a.isNaI() || b.isNaI()) {
            return NaI;
        }
        float p0 = mulBound(a.min, b.min);
        float p1 = mulBound(a.min, b.max);
        float p2 = mulBound(a.max, b.min);
        float p3 = mulBound(a.max, b.max);
        return of(Math.min(Math.min(p0, p1), Math.min(p2, p3)), Math.max(Math.max(p0, p1), Math.max(p2, p3)));
    }

    /** {@code Interval.mulBound}: a zero factor gives 0 even against an infinity. */
    private static float mulBound(float a, float b) {
        return a != 0.0F && b != 0.0F ? a * b : 0.0F;
    }

    /** {@code Interval.reciprocal}. */
    public static Interval reciprocal(Interval input) {
        if (input.isNaI() || input.min == 0.0F && input.max == 0.0F) {
            return NaI;
        }
        if (!input.contains(0.0F)) {
            return of(1.0F / input.max, 1.0F / input.min);
        }
        if (input.max == 0.0F) {
            return of(Float.NEGATIVE_INFINITY, 1.0F / input.min);
        }
        return input.min == 0.0F ? of(1.0F / input.max, Float.POSITIVE_INFINITY) : INFINITE;
    }

    /** {@code Interval.div}: {@code mul(left, reciprocal(right))}. */
    public static Interval div(Interval left, Interval right) {
        return mul(left, reciprocal(right));
    }

    /** {@code Interval.min}. */
    public static Interval min(Interval left, Interval right) {
        if (left.isNaI() || right.isNaI()) {
            return NaI;
        }
        return of(Math.min(left.min, right.min), Math.min(left.max, right.max));
    }

    /** {@code Interval.max}. */
    public static Interval max(Interval left, Interval right) {
        if (left.isNaI() || right.isNaI()) {
            return NaI;
        }
        return of(Math.max(left.min, right.min), Math.max(left.max, right.max));
    }

    /** {@code Interval.clamp}. */
    public static Interval clamp(Interval input, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") > max (" + max + ")");
        }
        if (input.isNaI()) {
            return NaI;
        }
        if (input.min >= max) {
            return of(max, max);
        }
        if (input.max <= min) {
            return of(min, min);
        }
        return of(Math.max(input.min, min), Math.min(input.max, max));
    }

    /** {@code Interval.abs}. */
    public static Interval abs(Interval input) {
        if (input.isNaI()) {
            return NaI;
        }
        float hi = Math.max(Math.abs(input.min), Math.abs(input.max));
        if (input.contains(0.0F)) {
            return of(0.0F, hi);
        }
        return of(Math.min(Math.abs(input.min), Math.abs(input.max)), hi);
    }

    /** {@code Interval.square}. */
    public static Interval square(Interval input) {
        if (input.isNaI()) {
            return NaI;
        }
        float hi = Math.max(Mth.square(input.min), Mth.square(input.max));
        if (input.contains(0.0F)) {
            return of(0.0F, hi);
        }
        return of(Math.min(Mth.square(input.min), Mth.square(input.max)), hi);
    }

    /** {@code Interval.pow(Interval, Interval)}. */
    public static Interval pow(Interval base, Interval exponent) {
        if (base.isNaI() || exponent.isNaI()) {
            return NaI;
        }
        if (base.min() == base.max()) {
            return pow(base.min(), exponent);
        }
        Interval result = encapsulating(pow(base.min(), exponent), pow(base.max(), exponent));
        if (base.contains(0.0F)) {
            if (base.max() > 0.0F) {
                result = encapsulating(result, pow(0.0F, exponent));
            }
            if (base.min() < 0.0F) {
                result = encapsulating(result, pow(-0.0F, exponent));
            }
        }
        return result;
    }

    private static Interval pow(float base, Interval exponent) {
        if (Float.isNaN(base) || exponent.isNaI()) {
            return NaI;
        }
        if (exponent.min() == exponent.max()) {
            float value = (float) Math.pow(base, exponent.min());
            return Float.isNaN(value) ? NaI : ofExact(value);
        }
        if (base == 0.0F) {
            return mul(powZeroBase(exponent), ofExact(Math.copySign(1.0F, base)));
        }
        if (base == 1.0F) {
            return ofExact(1.0F);
        }
        return base > 0.0F ? powPositiveBase(base, exponent) : powNegativeBase(base, exponent);
    }

    private static Interval powPositiveBase(float base, Interval exponent) {
        if (Float.isFinite(exponent.min()) && Float.isFinite(exponent.max())) {
            return encapsulating((float) Math.pow(base, exponent.min()), (float) Math.pow(base, exponent.max()));
        }
        return powInfiniteExponent(base, exponent);
    }

    private static Interval powZeroBase(Interval exponent) {
        if (exponent.contains(0.0F)) {
            if (exponent.max() == 0.0F) {
                return of(1.0F, Float.POSITIVE_INFINITY);
            }
            return exponent.min() == 0.0F ? ZERO_TO_ONE : of(0.0F, Float.POSITIVE_INFINITY);
        }
        return exponent.max() < 0.0F ? ofExact(Float.POSITIVE_INFINITY) : ofExact(0.0F);
    }

    private static Interval powInfiniteExponent(float base, Interval exponent) {
        if (Float.isInfinite(exponent.min()) && Float.isInfinite(exponent.max())) {
            return of(0.0F, Float.POSITIVE_INFINITY);
        }
        if (Float.isInfinite(exponent.min())) {
            return base < 1.0F
                    ? of((float) Math.pow(base, exponent.max()), Float.POSITIVE_INFINITY)
                    : of(0.0F, (float) Math.pow(base, exponent.max()));
        }
        return base < 1.0F
                ? of(0.0F, (float) Math.pow(base, exponent.min()))
                : of((float) Math.pow(base, exponent.min()), Float.POSITIVE_INFINITY);
    }

    private static Interval powNegativeBase(float base, Interval exponent) {
        float exponentMinInt = (float) Math.ceil(exponent.min());
        float exponentMaxInt = (float) Math.floor(exponent.max());
        if (exponentMaxInt < exponentMinInt) {
            return NaI;
        }
        float baseToMinInt = (float) Math.pow(base, exponentMinInt);
        float baseToMaxInt = (float) Math.pow(base, exponentMaxInt);
        Interval result = encapsulating(baseToMinInt, baseToMaxInt);
        if (Float.isInfinite(exponentMinInt)) {
            result = encapsulating(result, -baseToMinInt);
        } else if (exponentMinInt + 1.0F < exponentMaxInt) {
            result = encapsulating(result, (float) Math.pow(base, exponentMinInt + 1.0F));
        }
        if (Float.isInfinite(exponentMaxInt)) {
            result = encapsulating(result, -baseToMaxInt);
        } else if (exponentMaxInt - 1.0F > exponentMinInt) {
            result = encapsulating(result, (float) Math.pow(base, exponentMaxInt - 1.0F));
        }
        return result;
    }

    /** {@code Interval.log}: NaI below zero, otherwise {@code log} mapped over {@code max(input, [0, 0])}. */
    public static Interval log(Interval input) {
        if (input.max() < 0.0F) {
            return NaI;
        }
        Interval clipped = max(input, ofExact(0.0F));
        return mapMonotonic(clipped, x -> (float) Math.log(x));
    }

    /** {@code Interval.mapMonotonic}: maps both bounds and reorders them. */
    public static Interval mapMonotonic(Interval input, FloatUnaryOperator monotonicOp) {
        if (input.isNaI()) {
            return NaI;
        }
        float mappedMin = monotonicOp.apply(input.min);
        float mappedMax = monotonicOp.apply(input.max);
        if (Float.isNaN(mappedMin) || Float.isNaN(mappedMax)) {
            throw new IllegalStateException("Monotonic operator should not produce NaN");
        }
        return of(Math.min(mappedMin, mappedMax), Math.max(mappedMin, mappedMax));
    }

    /** {@code Interval.lerp(Interval, Interval, Interval)}: hull of the four corner lerps. */
    public static Interval lerp(Interval alpha, Interval first, Interval second) {
        if (alpha.isNaI() || first.isNaI() || second.isNaI()) {
            return NaI;
        }
        return encapsulating(lerp(alpha, first.min, second.min), lerp(alpha, first.max, second.min),
                lerp(alpha, first.min, second.max), lerp(alpha, first.max, second.max));
    }

    /** {@code Interval.lerp(Interval, float, float)}. */
    public static Interval lerp(Interval alpha, float first, float second) {
        if (alpha.isNaI() || Float.isNaN(first) || Float.isNaN(second)) {
            return NaI;
        }
        return Float.isFinite(first) && Float.isFinite(second)
                ? lerpFiniteBounds(alpha, first, second)
                : lerpInfiniteBounds(alpha, first, second);
    }

    private static Interval lerpFiniteBounds(Interval alpha, float first, float second) {
        return encapsulating(lerpFiniteBound(alpha.min, first, second), lerpFiniteBound(alpha.max, first, second));
    }

    private static float lerpFiniteBound(float alpha, float first, float second) {
        return first + mulBound(alpha, second - first);
    }

    private static Interval lerpInfiniteBounds(Interval alpha, float first, float second) {
        if (first == second) {
            return ofExact(first);
        }
        float newMin = lerpInfiniteBound(alpha.min, first, second);
        float newMax = lerpInfiniteBound(alpha.max, first, second);
        if (Float.isNaN(newMin) || Float.isNaN(newMax)) {
            return NaI;
        }
        return encapsulating(newMin, newMax);
    }

    private static float lerpInfiniteBound(float alpha, float first, float second) {
        float firstPart = mulBound(1.0F - alpha, first);
        float secondPart = mulBound(alpha, second);
        if (Float.isInfinite(firstPart) && Float.isInfinite(secondPart)) {
            if (alpha <= 0.0F) {
                return second > first ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            }
            if (alpha >= 1.0F) {
                return second > first ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }
            return Float.NaN;
        }
        return firstPart + secondPart;
    }

    /** {@code Interval.sign}. */
    public static Interval sign(Interval input) {
        if (input.isNaI()) {
            return NaI;
        }
        if (input.min() == input.max()) {
            return ofExact(Math.signum(input.min()));
        }
        if (input.contains(0.0F)) {
            if (input.min() == 0.0F) {
                return of(0.0F, 1.0F);
            }
            return input.max() == 0.0F ? of(-1.0F, 0.0F) : of(-1.0F, 1.0F);
        }
        return ofExact(input.min() > 0.0F ? 1.0F : -1.0F);
    }

    public boolean contains(float v) {
        return v >= min && v <= max;
    }

    public boolean intersects(Interval other) {
        return this.min <= other.max && this.max >= other.min;
    }

    public boolean isNaI() {
        return this == NaI;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        return o instanceof Interval other && min == other.min && max == other.max;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(min) * 31 + Float.hashCode(max);
    }

    @Override
    public String toString() {
        return isNaI() ? "[NaN]" : "[" + min + "; " + max + "]";
    }
}
