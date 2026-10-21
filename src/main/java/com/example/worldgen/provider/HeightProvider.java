package com.example.worldgen.provider;

import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.heightproviders.HeightProvider} (26.3) and every type registered
 * by {@code HeightProviderType}: {@code constant}, {@code uniform}, {@code biased_to_bottom},
 * {@code very_biased_to_bottom}, {@code trapezoid}, {@code weighted_list}. {@code sample} bodies are transcribed
 * from the bytecode. On an empty range vanilla logs {@code "Empty height range: {}"} (once per range for
 * {@code uniform}) and returns the resolved minimum without drawing; the log has no effect on generation and is
 * not reproduced.
 */
public interface HeightProvider {

    int sample(RandomSource random, WorldGenerationContext context);

    /**
     * {@code HeightProvider.CODEC}: {@code either(VerticalAnchor.CODEC, dispatch("type"))}; a bare anchor object
     * is a {@link ConstantHeight}.
     */
    static HeightProvider parse(Object json) {
        VerticalAnchor anchor = VerticalAnchor.tryParse(json);
        if (anchor != null) {
            return ConstantHeight.of(anchor);
        }
        Map<String, Object> m = CodecJson.object(json, "height provider");
        String type = CodecJson.type(m);
        switch (type) {
            case "constant":
                return ConstantHeight.of(VerticalAnchor.parse(CodecJson.field(m, "value")));
            case "uniform":
                return new UniformHeight(anchor(m, "min_inclusive"), anchor(m, "max_inclusive"));
            case "biased_to_bottom":
                return new BiasedToBottomHeight(anchor(m, "min_inclusive"), anchor(m, "max_inclusive"), inner(m));
            case "very_biased_to_bottom":
                return new VeryBiasedToBottomHeight(anchor(m, "min_inclusive"), anchor(m, "max_inclusive"), inner(m));
            case "trapezoid": {
                Object plateau = m.get("plateau");
                return new TrapezoidHeight(anchor(m, "min_inclusive"), anchor(m, "max_inclusive"),
                        plateau == null ? 0 : CodecJson.intValue(plateau, "plateau"));
            }
            case "weighted_list":
                return new WeightedListHeight(WeightedList.parseNonEmpty(CodecJson.field(m, "distribution"), HeightProvider::parse));
            default:
                throw new CodecJson.CodecException("Unknown height provider type: " + type);
        }
    }

    private static VerticalAnchor anchor(Map<String, Object> m, String key) {
        return VerticalAnchor.parse(CodecJson.field(m, key));
    }

    /** {@code Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("inner", 1)}. */
    private static int inner(Map<String, Object> m) {
        Object v = m.get("inner");
        return v == null ? 1 : CodecJson.intInRange(v, 1, Integer.MAX_VALUE, "inner");
    }

    /** {@code ConstantHeight}: {@code value.resolveY(context)}. */
    record ConstantHeight(VerticalAnchor value) implements HeightProvider {
        public static final ConstantHeight ZERO = new ConstantHeight(VerticalAnchor.absolute(0));

        public static ConstantHeight of(VerticalAnchor value) {
            return new ConstantHeight(value);
        }

        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            return value.resolveY(context);
        }
    }

    /** {@code UniformHeight}: {@code min > max ? min : Mth.randomBetweenInclusive(random, min, max)}. */
    record UniformHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) implements HeightProvider {
        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            int min = minInclusive.resolveY(context);
            int max = maxInclusive.resolveY(context);
            if (min > max) {
                return min;
            }
            return Mth.randomBetweenInclusive(random, min, max);
        }
    }

    /** {@code BiasedToBottomHeight}: {@code r = nextInt(max - min - inner + 1); return nextInt(r + inner) + min}. */
    record BiasedToBottomHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int inner) implements HeightProvider {
        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            int min = minInclusive.resolveY(context);
            int max = maxInclusive.resolveY(context);
            if (max - min - inner + 1 <= 0) {
                return min;
            }
            int r = random.nextInt(max - min - inner + 1);
            return random.nextInt(r + inner) + min;
        }
    }

    /**
     * {@code VeryBiasedToBottomHeight}: {@code a = Mth.nextInt(random, min + inner, max)},
     * {@code b = Mth.nextInt(random, min, a - 1)}, {@code return Mth.nextInt(random, min, b - 1 + inner)}.
     */
    record VeryBiasedToBottomHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int inner) implements HeightProvider {
        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            int min = minInclusive.resolveY(context);
            int max = maxInclusive.resolveY(context);
            if (max - min - inner + 1 <= 0) {
                return min;
            }
            int a = Mth.nextInt(random, min + inner, max);
            int b = Mth.nextInt(random, min, a - 1);
            return Mth.nextInt(random, min, b - 1 + inner);
        }
    }

    /**
     * {@code TrapezoidHeight}: {@code min > max} returns min; {@code plateau >= range} is uniform; otherwise
     * {@code min + randomBetweenInclusive(0, range - slope) + randomBetweenInclusive(0, slope)} with
     * {@code slope = (range - plateau) / 2}.
     */
    record TrapezoidHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) implements HeightProvider {
        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            int min = minInclusive.resolveY(context);
            int max = maxInclusive.resolveY(context);
            if (min > max) {
                return min;
            }
            int range = max - min;
            if (plateau >= range) {
                return Mth.randomBetweenInclusive(random, min, max);
            }
            int slope = (range - plateau) / 2;
            int top = range - slope;
            return min + Mth.randomBetweenInclusive(random, 0, top) + Mth.randomBetweenInclusive(random, 0, slope);
        }
    }

    /** {@code WeightedListHeight}: picks an entry ({@code nextInt(totalWeight)}) then samples it. */
    record WeightedListHeight(WeightedList<HeightProvider> distribution) implements HeightProvider {
        @Override
        public int sample(RandomSource random, WorldGenerationContext context) {
            return distribution.getRandomOrThrow(random).sample(random, context);
        }
    }
}
