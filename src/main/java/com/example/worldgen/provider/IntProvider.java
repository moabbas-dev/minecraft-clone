package com.example.worldgen.provider;

import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

import java.util.Map;

/**
 * Port of {@code net.minecraft.util.valueproviders.IntProvider} (26.3) and every type registered by
 * {@code IntProviders.bootstrap}: {@code constant}, {@code uniform}, {@code biased_to_bottom},
 * {@code very_biased_to_bottom}, {@code clamped}, {@code weighted_list}, {@code clamped_normal}, {@code trapezoid}.
 * The nested records keep the vanilla class names; their {@code sample} bodies are transcribed from the bytecode
 * (random-draw count and order included). JSON decoding is {@link #parse(Object)}.
 */
public interface IntProvider {

    int sample(RandomSource random);

    int minInclusive();

    int maxInclusive();

    /**
     * {@code IntProviders.CODEC}: {@code either(Codec.INT, dispatch("type"))}; a bare number is a {@link ConstantInt}.
     * Each record's {@code MapCodec.validate} rule is applied.
     */
    static IntProvider parse(Object json) {
        if (CodecJson.isNumber(json)) {
            return ConstantInt.of(CodecJson.intValue(json, "int provider"));
        }
        Map<String, Object> m = CodecJson.object(json, "int provider");
        String type = CodecJson.type(m);
        switch (type) {
            case "constant":
                return ConstantInt.of(CodecJson.intField(m, "value"));
            case "uniform": {
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                requireOrdered(min, max);
                return new UniformInt(min, max);
            }
            case "biased_to_bottom": {
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                requireOrdered(min, max);
                return new BiasedToBottomInt(min, max);
            }
            case "very_biased_to_bottom": {
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                requireOrdered(min, max);
                return new VeryBiasedToBottomInt(min, max);
            }
            case "clamped": {
                IntProvider source = parse(CodecJson.field(m, "source"));
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                requireOrdered(min, max);
                return new ClampedInt(source, min, max);
            }
            case "weighted_list":
                return new WeightedListInt(WeightedList.parseNonEmpty(CodecJson.field(m, "distribution"), IntProvider::parse));
            case "clamped_normal": {
                float mean = CodecJson.floatField(m, "mean");
                float deviation = CodecJson.floatField(m, "deviation");
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                requireOrdered(min, max);
                return new ClampedNormalInt(mean, deviation, min, max);
            }
            case "trapezoid": {
                int min = CodecJson.intField(m, "min");
                int max = CodecJson.intField(m, "max");
                int plateau = CodecJson.intField(m, "plateau");
                requireOrdered(min, max);
                if (plateau > max - min) {
                    throw new CodecJson.CodecException("Plateau can at most be the full span: " + min + "-" + max);
                }
                return new TrapezoidInt(min, max, plateau);
            }
            default:
                throw new CodecJson.CodecException("Unknown int provider type: " + type);
        }
    }

    /** {@code IntProviders.codec(min, max)}: {@link #parse} then {@code minInclusive >= min}, {@code maxInclusive <= max}. */
    static IntProvider parse(Object json, int min, int max) {
        IntProvider p = parse(json);
        if (p.minInclusive() < min) {
            throw new CodecJson.CodecException("Value provider too low: " + min + " [" + p.minInclusive() + "-" + p.maxInclusive() + "]");
        }
        if (p.maxInclusive() > max) {
            throw new CodecJson.CodecException("Value provider too high: " + max + " [" + p.minInclusive() + "-" + p.maxInclusive() + "]");
        }
        return p;
    }

    /** {@code IntProviders.NON_NEGATIVE_CODEC}. */
    static IntProvider parseNonNegative(Object json) {
        return parse(json, 0, Integer.MAX_VALUE);
    }

    /** {@code IntProviders.POSITIVE_CODEC}. */
    static IntProvider parsePositive(Object json) {
        return parse(json, 1, Integer.MAX_VALUE);
    }

    private static void requireOrdered(int min, int max) {
        if (max < min) {
            throw new CodecJson.CodecException("Max must be at least min, min_inclusive: " + min + ", max_inclusive: " + max);
        }
    }

    /** {@code ConstantInt}: returns {@code value}, draws nothing. */
    record ConstantInt(int value) implements IntProvider {
        public static final ConstantInt ZERO = new ConstantInt(0);

        public static ConstantInt of(int value) {
            return value == 0 ? ZERO : new ConstantInt(value);
        }

        @Override
        public int sample(RandomSource random) {
            return value;
        }

        @Override
        public int minInclusive() {
            return value;
        }

        @Override
        public int maxInclusive() {
            return value;
        }
    }

    /** {@code UniformInt}: {@code Mth.randomBetweenInclusive(random, min, max)}. */
    record UniformInt(int minInclusive, int maxInclusive) implements IntProvider {
        @Override
        public int sample(RandomSource random) {
            return Mth.randomBetweenInclusive(random, minInclusive, maxInclusive);
        }
    }

    /** {@code BiasedToBottomInt}: {@code min + random.nextInt(random.nextInt(max - min + 1) + 1)}. */
    record BiasedToBottomInt(int minInclusive, int maxInclusive) implements IntProvider {
        @Override
        public int sample(RandomSource random) {
            return minInclusive + random.nextInt(random.nextInt(maxInclusive - minInclusive + 1) + 1);
        }
    }

    /** {@code VeryBiasedToBottomInt}: three nested {@code nextInt(... + 1)} draws. */
    record VeryBiasedToBottomInt(int minInclusive, int maxInclusive) implements IntProvider {
        @Override
        public int sample(RandomSource random) {
            return minInclusive + random.nextInt(random.nextInt(random.nextInt(maxInclusive - minInclusive + 1) + 1) + 1);
        }
    }

    /** {@code ClampedInt}: {@code Mth.clamp(source.sample(random), min, max)}; bounds intersect the source's. */
    record ClampedInt(IntProvider source, int clampMin, int clampMax) implements IntProvider {
        @Override
        public int sample(RandomSource random) {
            return Mth.clamp(source.sample(random), clampMin, clampMax);
        }

        @Override
        public int minInclusive() {
            return Math.max(clampMin, source.minInclusive());
        }

        @Override
        public int maxInclusive() {
            return Math.min(clampMax, source.maxInclusive());
        }
    }

    /** {@code WeightedListInt}: picks an entry ({@code nextInt(totalWeight)}) then samples it; bounds over all entries. */
    final class WeightedListInt implements IntProvider {
        private final WeightedList<IntProvider> distribution;
        private final int minValue;
        private final int maxValue;

        public WeightedListInt(WeightedList<IntProvider> distribution) {
            this.distribution = distribution;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (WeightedList.Weighted<IntProvider> w : distribution.unwrap()) {
                min = Math.min(min, w.value().minInclusive());
                max = Math.max(max, w.value().maxInclusive());
            }
            this.minValue = min;
            this.maxValue = max;
        }

        public WeightedList<IntProvider> distribution() {
            return distribution;
        }

        @Override
        public int sample(RandomSource random) {
            return distribution.getRandomOrThrow(random).sample(random);
        }

        @Override
        public int minInclusive() {
            return minValue;
        }

        @Override
        public int maxInclusive() {
            return maxValue;
        }
    }

    /** {@code ClampedNormalInt}: {@code (int) Mth.clamp(Mth.normal(random, mean, deviation), (float) min, (float) max)}. */
    record ClampedNormalInt(float mean, float deviation, int minInclusive, int maxInclusive) implements IntProvider {
        @Override
        public int sample(RandomSource random) {
            return sample(random, mean, deviation, (float) minInclusive, (float) maxInclusive);
        }

        public static int sample(RandomSource random, float mean, float deviation, float min, float max) {
            return (int) Mth.clamp(Mth.normal(random, mean, deviation), min, max);
        }
    }

    /**
     * {@code TrapezoidInt}: symmetric triangle ({@code plateau == 0 && max == -min}) is
     * {@code nextInt(max + 1) - nextInt(max + 1)}; full plateau is uniform; otherwise
     * {@code min + randomBetweenInclusive(0, range - (range - plateau) / 2) + randomBetweenInclusive(0, (range - plateau) / 2)}.
     */
    record TrapezoidInt(int minInclusive, int maxInclusive, int plateau) implements IntProvider {
        public static IntProvider triangle(int range) {
            return new TrapezoidInt(-range, range, 0);
        }

        @Override
        public int sample(RandomSource random) {
            if (plateau == 0 && maxInclusive == -minInclusive) {
                return random.nextInt(maxInclusive + 1) - random.nextInt(maxInclusive + 1);
            }
            int range = maxInclusive - minInclusive;
            if (plateau == range) {
                return Mth.randomBetweenInclusive(random, minInclusive, maxInclusive);
            }
            int slope = (range - plateau) / 2;
            int top = range - slope;
            return minInclusive + Mth.randomBetweenInclusive(random, 0, top) + Mth.randomBetweenInclusive(random, 0, slope);
        }
    }
}
