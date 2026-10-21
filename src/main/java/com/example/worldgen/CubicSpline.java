package com.example.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Port of {@code net.minecraft.util.CubicSpline} (26.3): a piecewise cubic Hermite spline over a coordinate
 * function, whose point values may themselves be splines. Sampling, range estimation, coordinate mapping, the
 * builder and the parity string are transcribed from the 26.3 bytecode (all arithmetic in float):
 * <ul>
 *     <li>below the first / above the last location the value is extended linearly with that point's derivative
 *     ({@code linearExtend}; a zero derivative keeps the value constant);</li>
 *     <li>inside, with {@code t = (x - x1) / (x2 - x1)}, {@code a = d1 * (x2 - x1) - (y2 - y1)} and
 *     {@code b = -d2 * (x2 - x1) + (y2 - y1)}, the value is {@code lerp(t, y1, y2) + t * (1 - t) * lerp(t, a, b)};</li>
 *     <li>the interval start is {@code binarySearch(0, n, i -> x < locations[i]) - 1}.</li>
 * </ul>
 * The JSON form ({@code coordinate}, non-empty {@code points} of {@code location}, {@code value},
 * {@code derivative}, or a bare float for a constant) is decoded by the density-function parser.
 *
 * @param <I> the coordinate function type
 */
public sealed interface CubicSpline<I> permits CubicSpline.Multipoint, CubicSpline.Constant {

    void forEachCoordinate(Consumer<I> consumer);

    <R extends BoundedFloatFunction<?>> CubicSpline<R> mapCoordinates(Function<I, R> mapper);

    Interval range();

    String parityString();

    static <C, I extends BoundedFloatFunction<C>> float sample(CubicSpline<I> spline, C coordinate) {
        if (spline instanceof Multipoint<I> multipoint) {
            return Multipoint.sample(multipoint, coordinate);
        }
        return ((Constant<I>) spline).value();
    }

    /** {@code CubicSpline.asSampler}: a bounded function that samples the spline (a constant spline stays constant). */
    static <C, I extends BoundedFloatFunction<C>> BoundedFloatFunction<C> asSampler(CubicSpline<I> spline) {
        if (spline instanceof Multipoint<I> multipoint) {
            return new BoundedFloatFunction<>() {
                @Override
                public float apply(C c) {
                    return Multipoint.sample(multipoint, c);
                }

                @Override
                public Interval range() {
                    return multipoint.range();
                }
            };
        }
        return BoundedFloatFunction.constant(((Constant<I>) spline).value());
    }

    static <I> CubicSpline<I> constant(float value) {
        return new Constant<>(value);
    }

    static <I extends BoundedFloatFunction<?>> Builder<I> builder(I coordinate) {
        return new Builder<>(coordinate);
    }

    static <I extends BoundedFloatFunction<?>> Builder<I> builder(I coordinate, Interval.FloatUnaryOperator valueTransformer) {
        return new Builder<>(coordinate, valueTransformer);
    }

    /** {@code CubicSpline.Multipoint}. Equality compares the arrays by content, as vanilla's override does. */
    record Multipoint<I extends BoundedFloatFunction<?>>(I coordinate, float[] locations, List<CubicSpline<I>> values,
                                                         float[] derivatives) implements CubicSpline<I> {

        public Multipoint {
            validateSizes(locations, values, derivatives);
        }

        @Override
        public Interval range() {
            int lastIndex = locations.length - 1;
            float minValue = Float.POSITIVE_INFINITY;
            float maxValue = Float.NEGATIVE_INFINITY;
            Interval inputRange = coordinate.range();
            if (inputRange.isNaI()) {
                return inputRange;
            }
            if (inputRange.min() < locations[0]) {
                Interval firstRange = values.get(0).range();
                float edge1 = linearExtend(inputRange.min(), locations, firstRange.min(), derivatives, 0);
                float edge2 = linearExtend(inputRange.min(), locations, firstRange.max(), derivatives, 0);
                minValue = Math.min(minValue, Math.min(edge1, edge2));
                maxValue = Math.max(maxValue, Math.max(edge1, edge2));
            }
            if (inputRange.max() > locations[lastIndex]) {
                Interval lastRange = values.get(lastIndex).range();
                float edge1 = linearExtend(inputRange.max(), locations, lastRange.min(), derivatives, lastIndex);
                float edge2 = linearExtend(inputRange.max(), locations, lastRange.max(), derivatives, lastIndex);
                minValue = Math.min(minValue, Math.min(edge1, edge2));
                maxValue = Math.max(maxValue, Math.max(edge1, edge2));
            }
            List<Interval> valueRanges = new ArrayList<>(values.size());
            for (CubicSpline<I> value : values) {
                valueRanges.add(value.range());
            }
            for (Interval range : valueRanges) {
                minValue = Math.min(minValue, range.min());
                maxValue = Math.max(maxValue, range.max());
            }
            for (int i = 0; i < lastIndex; ++i) {
                float x1 = locations[i];
                float x2 = locations[i + 1];
                float xDiff = x2 - x1;
                Interval range1 = valueRanges.get(i);
                Interval range2 = valueRanges.get(i + 1);
                float min1 = range1.min();
                float max1 = range1.max();
                float min2 = range2.min();
                float max2 = range2.max();
                float d1 = derivatives[i];
                float d2 = derivatives[i + 1];
                if (d1 != 0.0F || d2 != 0.0F) {
                    float p1 = d1 * xDiff;
                    float p2 = d2 * xDiff;
                    float minLerp1 = Math.min(min1, min2);
                    float maxLerp1 = Math.max(max1, max2);
                    float minA = p1 - max2 + min1;
                    float maxA = p1 - min2 + max1;
                    float minB = -p2 + min2 - max1;
                    float maxB = -p2 + max2 - min1;
                    float minLerp2 = Math.min(minA, minB);
                    float maxLerp2 = Math.max(maxA, maxB);
                    minValue = Math.min(minValue, minLerp1 + 0.25F * minLerp2);
                    maxValue = Math.max(maxValue, maxLerp1 + 0.25F * maxLerp2);
                }
            }
            return Interval.of(minValue, maxValue);
        }

        private static float linearExtend(float input, float[] locations, float value, float[] derivatives, int index) {
            float derivative = derivatives[index];
            return derivative == 0.0F ? value : value + derivative * (input - locations[index]);
        }

        private static <I> void validateSizes(float[] locations, List<CubicSpline<I>> values, float[] derivatives) {
            if (locations.length != values.size() || locations.length != derivatives.length) {
                throw new IllegalArgumentException("All lengths must be equal, got: " + locations.length + " "
                        + values.size() + " " + derivatives.length);
            }
            if (locations.length == 0) {
                throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
            }
        }

        public static <C, I extends BoundedFloatFunction<C>> float sample(Multipoint<I> sampler, C c) {
            return sample(sampler.coordinate, sampler.derivatives, sampler.locations, sampler.values, c);
        }

        private static <C, I extends BoundedFloatFunction<C>> float sample(I coordinate, float[] derivatives,
                                                                          float[] locations, List<CubicSpline<I>> values, C c) {
            float input = coordinate.apply(c);
            int start = findIntervalStart(locations, input);
            int lastIndex = locations.length - 1;
            if (start < 0) {
                return linearExtend(input, locations, CubicSpline.sample(values.get(0), c), derivatives, 0);
            }
            if (start == lastIndex) {
                return linearExtend(input, locations, CubicSpline.sample(values.get(lastIndex), c), derivatives, lastIndex);
            }
            float x1 = locations[start];
            float x2 = locations[start + 1];
            float t = (input - x1) / (x2 - x1);
            CubicSpline<I> f1 = values.get(start);
            CubicSpline<I> f2 = values.get(start + 1);
            float d1 = derivatives[start];
            float d2 = derivatives[start + 1];
            float y1 = CubicSpline.sample(f1, c);
            float y2 = CubicSpline.sample(f2, c);
            float a = d1 * (x2 - x1) - (y2 - y1);
            float b = -d2 * (x2 - x1) + (y2 - y1);
            return Mth.lerp(t, y1, y2) + t * (1.0F - t) * Mth.lerp(t, a, b);
        }

        private static int findIntervalStart(float[] locations, float input) {
            return Mth.binarySearch(0, locations.length, i -> input < locations[i]) - 1;
        }

        @Override
        public String parityString() {
            return "Spline{coordinate=" + coordinate + ", locations=" + toString(locations) + ", derivatives="
                    + toString(derivatives) + ", values="
                    + values.stream().map(CubicSpline::parityString).collect(Collectors.joining(", ", "[", "]")) + "}";
        }

        private static String toString(float[] arr) {
            return "[" + IntStream.range(0, arr.length).mapToDouble(i -> arr[i])
                    .mapToObj(f -> String.format(Locale.ROOT, "%.3f", f)).collect(Collectors.joining(", ")) + "]";
        }

        @Override
        public void forEachCoordinate(Consumer<I> consumer) {
            consumer.accept(coordinate);
            for (CubicSpline<I> spline : values) {
                spline.forEachCoordinate(consumer);
            }
        }

        @Override
        public <R extends BoundedFloatFunction<?>> CubicSpline<R> mapCoordinates(Function<I, R> mapper) {
            return new Multipoint<>(mapper.apply(coordinate), locations,
                    values.stream().map(v -> v.mapCoordinates(mapper)).toList(), derivatives);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Multipoint<?> multipoint)) {
                return false;
            }
            return Objects.equals(coordinate, multipoint.coordinate) && Arrays.equals(locations, multipoint.locations)
                    && Arrays.equals(derivatives, multipoint.derivatives) && Objects.equals(values, multipoint.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(coordinate, Arrays.hashCode(locations), values, Arrays.hashCode(derivatives));
        }
    }

    /** {@code CubicSpline.Constant}. */
    record Constant<I>(float value) implements CubicSpline<I> {

        @Override
        public String parityString() {
            return String.format(Locale.ROOT, "k=%.3f", value);
        }

        @Override
        public Interval range() {
            return Interval.ofExact(value);
        }

        @Override
        public void forEachCoordinate(Consumer<I> consumer) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R extends BoundedFloatFunction<?>> CubicSpline<R> mapCoordinates(Function<I, R> mapper) {
            return (CubicSpline<R>) this;
        }
    }

    /** {@code CubicSpline.Builder}: points must be added in strictly ascending location order. */
    final class Builder<I extends BoundedFloatFunction<?>> {

        private final I coordinate;
        private final Interval.FloatUnaryOperator valueTransformer;
        private final List<Float> locations = new ArrayList<>();
        private final List<CubicSpline<I>> values = new ArrayList<>();
        private final List<Float> derivatives = new ArrayList<>();

        private Builder(I coordinate) {
            this(coordinate, v -> v);
        }

        private Builder(I coordinate, Interval.FloatUnaryOperator valueTransformer) {
            this.coordinate = coordinate;
            this.valueTransformer = valueTransformer;
        }

        public Builder<I> addPoint(float location, float value) {
            return addPoint(location, new Constant<>(valueTransformer.apply(value)), 0.0F);
        }

        public Builder<I> addPoint(float location, float value, float derivative) {
            return addPoint(location, new Constant<>(valueTransformer.apply(value)), derivative);
        }

        public Builder<I> addPoint(float location, CubicSpline<I> sampler) {
            return addPoint(location, sampler, 0.0F);
        }

        private Builder<I> addPoint(float location, CubicSpline<I> sampler, float derivative) {
            if (!locations.isEmpty() && location <= locations.get(locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            }
            locations.add(location);
            values.add(sampler);
            derivatives.add(derivative);
            return this;
        }

        public CubicSpline<I> build() {
            if (locations.isEmpty()) {
                throw new IllegalStateException("No elements added");
            }
            float[] locationArray = new float[locations.size()];
            float[] derivativeArray = new float[derivatives.size()];
            for (int i = 0; i < locationArray.length; i++) {
                locationArray[i] = locations.get(i);
                derivativeArray[i] = derivatives.get(i);
            }
            return new Multipoint<>(coordinate, locationArray, List.copyOf(values), derivativeArray);
        }
    }
}
