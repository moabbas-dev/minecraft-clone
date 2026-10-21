package com.example.worldgen.provider;

import com.example.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Port of {@code net.minecraft.util.random.WeightedList} (26.3) with {@code Weighted}, {@code WeightedList$Flat},
 * {@code WeightedList$Compact} and {@code WeightedRandom.getTotalWeight}. {@code totalWeight} is the long sum of the
 * weights (above {@code Integer.MAX_VALUE} throws); zero total means empty; below {@code FLAT_THRESHOLD = 64} the
 * selector is a flat array filled entry by entry, otherwise a linear scan subtracting weights until negative.
 * {@link #getRandomOrThrow} draws exactly one {@code random.nextInt(totalWeight)}.
 */
public final class WeightedList<E> {

    private static final int FLAT_THRESHOLD = 64;

    /** {@code net.minecraft.util.random.Weighted}: weight must be {@code >= 0}. */
    public record Weighted<T>(T value, int weight) {
        public Weighted {
            if (weight < 0) {
                throw new IllegalArgumentException("Weight should be >= 0");
            }
        }
    }

    private final int totalWeight;
    private final List<Weighted<E>> items;
    private final Object[] flat;
    private final boolean compact;

    private WeightedList(List<Weighted<E>> items) {
        this.items = List.copyOf(items);
        long sum = 0L;
        for (Weighted<E> w : items) {
            sum += (long) w.weight();
        }
        if (sum > 2147483647L) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        }
        this.totalWeight = (int) sum;
        if (totalWeight == 0 || totalWeight >= FLAT_THRESHOLD) {
            this.flat = null;
            this.compact = totalWeight != 0;
        } else {
            this.flat = new Object[totalWeight];
            int i = 0;
            for (Weighted<E> w : this.items) {
                Arrays.fill(flat, i, i + w.weight(), w.value());
                i += w.weight();
            }
            this.compact = false;
        }
    }

    public static <E> WeightedList<E> of(List<Weighted<E>> items) {
        return new WeightedList<>(items);
    }

    public boolean isEmpty() {
        return totalWeight == 0;
    }

    public List<Weighted<E>> unwrap() {
        return items;
    }

    @SuppressWarnings("unchecked")
    private E select(int index) {
        if (!compact) {
            return (E) flat[index];
        }
        for (Weighted<E> w : items) {
            index -= w.weight();
            if (index < 0) {
                return w.value();
            }
        }
        throw new IllegalStateException(index + " exceeded total weight");
    }

    /** {@code getRandomOrThrow}: {@code selector.get(random.nextInt(totalWeight))}. */
    public E getRandomOrThrow(RandomSource random) {
        if (isEmpty()) {
            throw new IllegalStateException("Weighted list has no elements");
        }
        return select(random.nextInt(totalWeight));
    }

    /**
     * {@code WeightedList.nonEmptyCodec(elementCodec)}: a JSON list of {@code {"data": element, "weight": n}}
     * ({@code weight} is {@code ExtraCodecs.NON_NEGATIVE_INT}); a zero total weight is an error.
     */
    public static <E> WeightedList<E> parseNonEmpty(Object json, Function<Object, E> element) {
        List<Weighted<E>> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "weighted list")) {
            Map<String, Object> m = CodecJson.object(o, "weighted entry");
            E data = element.apply(CodecJson.field(m, "data"));
            int weight = CodecJson.intInRange(CodecJson.field(m, "weight"), 0, Integer.MAX_VALUE, "weight");
            out.add(new Weighted<>(data, weight));
        }
        WeightedList<E> list = new WeightedList<>(out);
        if (list.isEmpty()) {
            throw new CodecJson.CodecException("Weighted list must contain at least one entry with non-zero weight");
        }
        return list;
    }
}
