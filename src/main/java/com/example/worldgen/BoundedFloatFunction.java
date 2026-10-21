package com.example.worldgen;

import java.util.function.Function;

/**
 * Port of {@code net.minecraft.util.BoundedFloatFunction} (26.3): a float-valued function of {@code C} with a
 * declared value {@link Interval}; the coordinate type of {@link CubicSpline}.
 *
 * @param <C> the argument type
 */
public interface BoundedFloatFunction<C> {

    BoundedFloatFunction<Float> IDENTITY = new BoundedFloatFunction<>() {
        @Override
        public float apply(Float value) {
            return value;
        }

        @Override
        public Interval range() {
            return Interval.INFINITE;
        }
    };

    float apply(C c);

    Interval range();

    static <C> BoundedFloatFunction<C> constant(float value) {
        Interval range = Interval.ofExact(value);
        return new BoundedFloatFunction<>() {
            @Override
            public float apply(C c) {
                return value;
            }

            @Override
            public Interval range() {
                return range;
            }
        };
    }

    default <C2> BoundedFloatFunction<C2> comap(Function<C2, C> function) {
        BoundedFloatFunction<C> self = this;
        return new BoundedFloatFunction<>() {
            @Override
            public float apply(C2 c2) {
                return self.apply(function.apply(c2));
            }

            @Override
            public Interval range() {
                return self.range();
            }
        };
    }
}
