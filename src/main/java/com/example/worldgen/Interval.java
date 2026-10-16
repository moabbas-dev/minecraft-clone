package com.example.worldgen;

/**
 * Port of {@code net.minecraft.util.Interval} (26.3): a closed float range used to describe the
 * value range of a noise. Only the members the noise primitives use are ported here
 * ({@code of}, {@code ofSymmetric}, {@code ofExact}, {@code add}, {@code sub}, {@code mul},
 * {@code contains}); each mirrors the 26.3 bytecode, including the shared singleton instances
 * {@code of} returns and the "zero times anything is zero" rule of {@code mulBound}.
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

    public boolean contains(float v) {
        return v >= min && v <= max;
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
