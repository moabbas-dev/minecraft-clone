package com.example.worldgen.feature.placement;

import com.example.worldgen.feature.Pos;
import com.example.worldgen.random.RandomSource;

import java.util.function.Consumer;

/**
 * Port of {@code net.minecraft.world.level.levelgen.placement.PlacementModifier} (26.3) with its two sub-interfaces:
 * {@link Filter} ({@code PlacementFilter}: emits the origin when {@code shouldPlace}) and {@link Repeating}
 * ({@code RepeatingPlacement}: samples {@code count} once, then emits the origin that many times).
 */
public interface PlacementModifier {

    void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output);

    /** {@code PlacementFilter}. */
    interface Filter extends PlacementModifier {
        boolean shouldPlace(PlacementContext context, RandomSource random, Pos origin);

        @Override
        default void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            if (shouldPlace(context, random, origin)) output.accept(origin);
        }
    }

    /** {@code RepeatingPlacement}. */
    interface Repeating extends PlacementModifier {
        int count(RandomSource random, Pos origin);

        @Override
        default void modify(PlacementContext context, RandomSource random, Pos origin, Consumer<Pos> output) {
            int count = count(random, origin);
            for (int i = 0; i < count; ++i) output.accept(origin);
        }
    }
}
